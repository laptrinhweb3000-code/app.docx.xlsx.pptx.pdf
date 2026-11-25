package com.doccast.presenter.cast

import android.app.Presentation
import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.view.Display
import android.view.View
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.ImageView
import com.doccast.presenter.R
import com.doccast.presenter.model.NormalizedStroke
import com.doccast.presenter.ui.AnnotationOverlayView

/**
 * Clean secondary-screen presentation for HDMI / USB-C / Miracast / Smart View / Chromecast.
 * Displays full-screen content without presenter buttons or distractions.
 */
class ExternalScreenPresentation(
    outerContext: Context,
    display: Display
) : Presentation(outerContext, display) {

    private var ivPdfPage: ImageView? = null
    private var webOfficeView: WebView? = null
    private var annotationOverlay: AnnotationOverlayView? = null
    private var blackoutCurtain: View? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.presentation_external)

        ivPdfPage = findViewById(R.id.extIvPdfPage)
        webOfficeView = findViewById(R.id.extWebOfficeView)
        annotationOverlay = findViewById(R.id.extAnnotationOverlay)
        blackoutCurtain = findViewById(R.id.extBlackoutCurtain)

        annotationOverlay?.isReadOnlyMirror = true
        webOfficeView?.settings?.apply {
            javaScriptEnabled = false
            builtInZoomControls = false
            displayZoomControls = false
            loadWithOverviewMode = true
            useWideViewPort = true
            layoutAlgorithm = WebSettings.LayoutAlgorithm.TEXT_AUTOSIZING
        }
    }

    fun showPdfBitmap(bitmap: Bitmap) {
        webOfficeView?.visibility = View.GONE
        ivPdfPage?.apply {
            visibility = View.VISIBLE
            setImageBitmap(bitmap)
        }
    }

    fun showHtmlContent(html: String) {
        ivPdfPage?.visibility = View.GONE
        webOfficeView?.apply {
            visibility = View.VISIBLE
            loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
        }
    }

    fun syncAnnotations(
        strokes: List<NormalizedStroke>,
        laserActive: Boolean,
        laserX: Float,
        laserY: Float
    ) {
        annotationOverlay?.syncFromRemote(strokes, laserActive, laserX, laserY)
    }

    fun setBlackout(blackout: Boolean) {
        blackoutCurtain?.visibility = if (blackout) View.VISIBLE else View.GONE
    }
}
