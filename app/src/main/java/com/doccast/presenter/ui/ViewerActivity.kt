package com.doccast.presenter.ui

import android.content.Intent
import android.graphics.Color
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Display
import android.view.View
import android.webkit.WebSettings
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.doccast.presenter.R
import com.doccast.presenter.cast.ExternalScreenPresentation
import com.doccast.presenter.cast.QrCodeHelper
import com.doccast.presenter.cast.WifiCastServer
import com.doccast.presenter.databinding.ActivityViewerBinding
import com.doccast.presenter.model.DocFormat
import com.doccast.presenter.model.LoadedDocument
import com.doccast.presenter.parser.DocumentParser
import com.doccast.presenter.parser.PdfDocumentEngine
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class ViewerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityViewerBinding
    private lateinit var displayManager: DisplayManager

    private var pdfEngine: PdfDocumentEngine? = null
    private var loadedDoc: LoadedDocument? = null
    private var currentPageIndex: Int = 0
    private var totalPages: Int = 1
    private var isBlackout: Boolean = false

    private var externalPresentation: ExternalScreenPresentation? = null
    private var wifiCastServer: WifiCastServer? = null
    private var wifiCastPort: Int = 8080

    private var contentVersion: Long = 1L
    private var overlayVersion: Long = 1L

    private val timerHandler = Handler(Looper.getMainLooper())
    private var elapsedSeconds: Int = 0
    private val timerRunnable = object : Runnable {
        override fun run() {
            elapsedSeconds++
            val mins = elapsedSeconds / 60
            val secs = elapsedSeconds % 60
            binding.tvTimer.text = String.format(Locale.US, "%02d:%02d", mins, secs)
            timerHandler.postDelayed(this, 1000L)
        }
    }

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = attachExternalDisplayIfAvailable()
        override fun onDisplayRemoved(displayId: Int) = detachExternalDisplay()
        override fun onDisplayChanged(displayId: Int) = attachExternalDisplayIfAvailable()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        displayManager = getSystemService(DISPLAY_SERVICE) as DisplayManager
        setupWebView()
        setupControls()

        val uri = intent.data
        if (uri == null) {
            Toast.makeText(this, "Không tìm thấy đường dẫn tệp.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val displayName = intent.getStringExtra(EXTRA_FILE_NAME)
            ?: DocumentParser.resolveDisplayName(this, uri)
        binding.tvDocTitle.text = displayName

        loadDocumentAsync(uri, displayName)
        timerHandler.postDelayed(timerRunnable, 1000L)
    }

    override fun onResume() {
        super.onResume()
        displayManager.registerDisplayListener(displayListener, null)
        attachExternalDisplayIfAvailable()
        updateCastBadge()
    }

    override fun onPause() {
        super.onPause()
        displayManager.unregisterDisplayListener(displayListener)
    }

    override fun onDestroy() {
        super.onDestroy()
        timerHandler.removeCallbacks(timerRunnable)
        detachExternalDisplay()
        stopWifiServer()
        pdfEngine?.close()
    }

    private fun setupWebView() {
        binding.webOfficeView.settings.apply {
            javaScriptEnabled = false
            builtInZoomControls = true
            displayZoomControls = false
            loadWithOverviewMode = true
            useWideViewPort = true
            layoutAlgorithm = WebSettings.LayoutAlgorithm.TEXT_AUTOSIZING
        }
    }

    private fun setupControls() {
        binding.btnBack.setOnClickListener { finish() }

        binding.btnPrevPage.setOnClickListener {
            if (currentPageIndex > 0) {
                binding.annotationOverlay.clearAll()
                showPage(currentPageIndex - 1)
            }
        }

        binding.btnNextPage.setOnClickListener {
            if (currentPageIndex < totalPages - 1) {
                binding.annotationOverlay.clearAll()
                showPage(currentPageIndex + 1)
            }
        }

        binding.btnModeNormal.setOnClickListener { setToolMode(AnnotationOverlayView.Mode.NONE) }
        binding.btnModeLaser.setOnClickListener { setToolMode(AnnotationOverlayView.Mode.LASER) }
        binding.btnModePen.setOnClickListener { setToolMode(AnnotationOverlayView.Mode.PEN) }
        binding.btnClearInk.setOnClickListener { binding.annotationOverlay.clearAll() }

        binding.btnBlackout.setOnClickListener {
            isBlackout = !isBlackout
            binding.blackoutPreviewBanner.visibility = if (isBlackout) View.VISIBLE else View.GONE
            binding.tvBlackoutNotice.visibility = if (isBlackout) View.VISIBLE else View.GONE
            externalPresentation?.setBlackout(isBlackout)
            overlayVersion++
        }

        binding.annotationOverlay.onAnnotationChanged = {
            overlayVersion++
            externalPresentation?.syncAnnotations(
                binding.annotationOverlay.snapshotStrokes(),
                binding.annotationOverlay.laserActive,
                binding.annotationOverlay.laserX,
                binding.annotationOverlay.laserY
            )
        }

        binding.btnDisplayCast.setOnClickListener {
            runCatching {
                startActivity(Intent(Settings.ACTION_CAST_SETTINGS))
            }.onFailure {
                Toast.makeText(
                    this,
                    "Hãy cắm cáp HDMI/USB-C hoặc bật Smart View / Chiếu không dây trong Cài đặt.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        binding.btnWifiCast.setOnClickListener {
            showWifiCastDialog()
        }

        setToolMode(AnnotationOverlayView.Mode.NONE)
    }

    private fun setToolMode(mode: AnnotationOverlayView.Mode) {
        binding.annotationOverlay.mode = mode
        val activeColor = Color.parseColor("#2563EB")
        val idleColor = Color.TRANSPARENT

        binding.btnModeNormal.setBackgroundColor(if (mode == AnnotationOverlayView.Mode.NONE) activeColor else idleColor)
        binding.btnModeLaser.setBackgroundColor(if (mode == AnnotationOverlayView.Mode.LASER) activeColor else idleColor)
        binding.btnModePen.setBackgroundColor(if (mode == AnnotationOverlayView.Mode.PEN) activeColor else idleColor)
    }

    private fun loadDocumentAsync(uri: Uri, displayName: String) {
        binding.progressLoading.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val format = DocumentParser.detectFormat(this@ViewerActivity, uri, displayName)
                val result = withContext(Dispatchers.IO) {
                    if (format == DocFormat.PDF) {
                        val engine = PdfDocumentEngine.open(this@ViewerActivity, uri)
                        pdfEngine = engine
                        LoadedDocument.Pdf(displayName, engine.pageCount)
                    } else {
                        DocumentParser.loadOfficeDocument(this@ViewerActivity, uri, displayName, format)
                    }
                }

                loadedDoc = result
                totalPages = when (result) {
                    is LoadedDocument.Pdf -> result.pageCount.coerceAtLeast(1)
                    is LoadedDocument.HtmlPages -> result.pages.size.coerceAtLeast(1)
                }

                binding.progressLoading.visibility = View.GONE
                showPage(0)
            } catch (e: Exception) {
                binding.progressLoading.visibility = View.GONE
                Toast.makeText(
                    this@ViewerActivity,
                    "Lỗi mở tài liệu: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun showPage(index: Int) {
        val doc = loadedDoc ?: return
        currentPageIndex = index.coerceIn(0, (totalPages - 1).coerceAtLeast(0))
        contentVersion++

        when (doc) {
            is LoadedDocument.Pdf -> {
                binding.webOfficeView.visibility = View.GONE
                binding.ivPdfPage.visibility = View.VISIBLE
                binding.tvPageCounter.text = "${currentPageIndex + 1} / $totalPages"

                val engine = pdfEngine ?: return
                val targetWidth = resources.displayMetrics.widthPixels.coerceAtLeast(1080)
                lifecycleScope.launch {
                    val bmp = withContext(Dispatchers.IO) {
                        engine.renderPage(currentPageIndex, targetWidth)
                    }
                    binding.ivPdfPage.setImageBitmap(bmp)
                    externalPresentation?.showPdfBitmap(bmp)
                }
            }
            is LoadedDocument.HtmlPages -> {
                binding.ivPdfPage.visibility = View.GONE
                binding.webOfficeView.visibility = View.VISIBLE
                val page = doc.pages.getOrNull(currentPageIndex) ?: return
                binding.tvPageCounter.text = "${page.label} (${currentPageIndex + 1}/$totalPages)"
                binding.webOfficeView.loadDataWithBaseURL(null, page.html, "text/html", "UTF-8", null)
                externalPresentation?.showHtmlContent(page.html)
            }
        }

        binding.btnPrevPage.isEnabled = currentPageIndex > 0
        binding.btnNextPage.isEnabled = currentPageIndex < totalPages - 1
    }

    private fun attachExternalDisplayIfAvailable() {
        val displays = displayManager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
        val targetDisplay: Display? = displays.firstOrNull()
        if (targetDisplay == null) {
            detachExternalDisplay()
            return
        }

        if (externalPresentation?.display?.displayId == targetDisplay.displayId && externalPresentation?.isShowing == true) {
            return
        }

        detachExternalDisplay()
        runCatching {
            val presentation = ExternalScreenPresentation(this, targetDisplay)
            presentation.show()
            presentation.setBlackout(isBlackout)
            externalPresentation = presentation
            // Immediately push current page to the newly attached screen
            showPage(currentPageIndex)
        }
        updateCastBadge()
    }

    private fun detachExternalDisplay() {
        runCatching { externalPresentation?.dismiss() }
        externalPresentation = null
        updateCastBadge()
    }

    private fun ensureWifiServerStarted(): Boolean {
        if (wifiCastServer != null) return true
        for (port in 8080..8090) {
            try {
                val server = WifiCastServer(
                    port = port,
                    stateProvider = {
                        val doc = loadedDoc
                        WifiCastServer.CastSnapshot(
                            contentVersion = contentVersion,
                            overlayVersion = overlayVersion,
                            isPdf = doc is LoadedDocument.Pdf,
                            title = doc?.title ?: "Tài liệu",
                            pageIndex = currentPageIndex,
                            totalPages = totalPages,
                            blackout = isBlackout,
                            overlaySvg = binding.annotationOverlay.toSvgMarkup()
                        )
                    },
                    pdfJpegProvider = { idx ->
                        pdfEngine?.renderPageAsJpeg(idx, 1600, 88)
                    },
                    htmlPageProvider = { idx ->
                        (loadedDoc as? LoadedDocument.HtmlPages)?.pages?.getOrNull(idx)?.html
                    }
                )
                server.start()
                wifiCastServer = server
                wifiCastPort = port
                updateCastBadge()
                return true
            } catch (_: Exception) {
                // Try next port
            }
        }
        return false
    }

    private fun stopWifiServer() {
        runCatching { wifiCastServer?.stop() }
        wifiCastServer = null
        updateCastBadge()
    }

    private fun showWifiCastDialog() {
        if (wifiCastServer == null) {
            val started = ensureWifiServerStarted()
            if (!started) {
                Toast.makeText(this, "Không thể khởi động máy chủ phát Wi-Fi.", Toast.LENGTH_LONG).show()
                return
            }
        }

        val dialogView = layoutInflater.inflate(R.layout.dialog_wifi_cast, null)
        val ivQr = dialogView.findViewById<ImageView>(R.id.ivQrCode)
        val tvUrl = dialogView.findViewById<TextView>(R.id.tvCastUrl)
        val btnToggle = dialogView.findViewById<MaterialButton>(R.id.btnToggleWifiServer)
        val btnClose = dialogView.findViewById<MaterialButton>(R.id.btnCloseDialog)

        val ip = QrCodeHelper.findLocalWifiIpAddress()
        val castUrl = "http://$ip:$wifiCastPort"
        tvUrl.text = castUrl
        ivQr.setImageBitmap(QrCodeHelper.generateQrBitmap(castUrl, 480))

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        btnToggle.setOnClickListener {
            if (wifiCastServer != null) {
                stopWifiServer()
                Toast.makeText(this, "Đã tắt phát trình chiếu Wi-Fi.", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
        }
        btnClose.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun updateCastBadge() {
        val parts = mutableListOf<String>()
        if (externalPresentation?.isShowing == true) {
            parts.add("HDMI/TV: Đang chiếu")
        }
        if (wifiCastServer != null) {
            val ip = QrCodeHelper.findLocalWifiIpAddress()
            parts.add("Wi-Fi: http://$ip:$wifiCastPort")
        }
        if (parts.isEmpty()) {
            binding.tvCastBadge.text = "Chế độ đọc trên máy • Chưa kết nối màn chiếu"
            binding.tvCastBadge.setTextColor(Color.parseColor("#94A3B8"))
        } else {
            binding.tvCastBadge.text = parts.joinToString("  |  ")
            binding.tvCastBadge.setTextColor(Color.parseColor("#4ADE80"))
        }
    }

    companion object {
        const val EXTRA_FILE_NAME = "extra_file_name"
    }
}
