package com.doccast.presenter.ui

import android.content.Intent
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.doccast.presenter.data.RecentFilesStore
import com.doccast.presenter.databinding.ActivityMainBinding
import com.doccast.presenter.parser.DocumentParser

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var recentStore: RecentFilesStore
    private lateinit var adapter: RecentFilesAdapter
    private lateinit var displayManager: DisplayManager

    private val openDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            openViewer(uri)
        }
    }

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = updateExternalDisplayStatus()
        override fun onDisplayRemoved(displayId: Int) = updateExternalDisplayStatus()
        override fun onDisplayChanged(displayId: Int) = updateExternalDisplayStatus()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        recentStore = RecentFilesStore(this)
        displayManager = getSystemService(DISPLAY_SERVICE) as DisplayManager

        adapter = RecentFilesAdapter { item -> openViewer(item.uri) }
        binding.rvRecentFiles.layoutManager = LinearLayoutManager(this)
        binding.rvRecentFiles.adapter = adapter

        binding.btnOpenFile.setOnClickListener {
            openDocumentLauncher.launch(
                arrayOf(
                    "application/pdf",
                    "application/msword",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "application/vnd.ms-powerpoint",
                    "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                    "application/vnd.ms-excel",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    "*/*"
                )
            )
        }

        binding.btnWirelessCast.setOnClickListener {
            runCatching {
                startActivity(Intent(Settings.ACTION_CAST_SETTINGS))
            }.onFailure {
                Toast.makeText(
                    this,
                    "Vui lòng mở Cài đặt → Kết nối → Chiếu màn hình (Smart View / Cast) trên thiết bị.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        // If launched directly from Zalo / Files / Drive via ACTION_VIEW
        if (intent?.action == Intent.ACTION_VIEW && intent.data != null) {
            openViewer(intent.data!!)
        }
    }

    override fun onResume() {
        super.onResume()
        displayManager.registerDisplayListener(displayListener, null)
        refreshRecentFiles()
        updateExternalDisplayStatus()
    }

    override fun onPause() {
        super.onPause()
        displayManager.unregisterDisplayListener(displayListener)
    }

    private fun refreshRecentFiles() {
        val list = recentStore.loadAll()
        adapter.submitList(list)
        binding.tvEmptyRecent.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun updateExternalDisplayStatus() {
        val displays = displayManager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
        if (displays.isNotEmpty()) {
            val name = displays.first().name ?: "Màn hình ngoài"
            binding.tvExternalDisplayStatus.text = "Màn hình ngoài (HDMI / Miracast): Đã kết nối ($name)"
        } else {
            binding.tvExternalDisplayStatus.text = "Màn hình ngoài (HDMI / Miracast): Chưa kết nối"
        }
    }

    private fun openViewer(uri: Uri) {
        val name = DocumentParser.resolveDisplayName(this, uri)
        val format = DocumentParser.detectFormat(this, uri, name)
        recentStore.recordOpened(uri.toString(), name, format)

        val viewerIntent = Intent(this, ViewerActivity::class.java).apply {
            data = uri
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            putExtra(ViewerActivity.EXTRA_FILE_NAME, name)
        }
        startActivity(viewerIntent)
    }
}
