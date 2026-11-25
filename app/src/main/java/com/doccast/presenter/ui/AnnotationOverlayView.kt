package com.doccast.presenter.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.doccast.presenter.model.NormalizedPoint
import com.doccast.presenter.model.NormalizedStroke
import java.util.Locale

class AnnotationOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class Mode { NONE, LASER, PEN }

    var mode: Mode = Mode.NONE
    var isReadOnlyMirror: Boolean = false
    var onAnnotationChanged: (() -> Unit)? = null

    private val strokes = mutableListOf<NormalizedStroke>()
    private var activeStroke: NormalizedStroke? = null

    var laserActive: Boolean = false
        private set
    var laserX: Float = 0.5f
        private set
    var laserY: Float = 0.5f
        private set

    private val penPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#EF4444")
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = 7f
    }

    private val laserOuterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(90, 239, 68, 68)
        style = Paint.Style.FILL
    }

    private val laserCorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF1E1E")
        style = Paint.Style.FILL
    }

    private val drawPath = Path()

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (isReadOnlyMirror || mode == Mode.NONE || width <= 0 || height <= 0) {
            return false
        }

        val nx = (event.x / width.toFloat()).coerceIn(0f, 1f)
        val ny = (event.y / height.toFloat()).coerceIn(0f, 1f)

        when (mode) {
            Mode.LASER -> {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                        laserActive = true
                        laserX = nx
                        laserY = ny
                        invalidate()
                        onAnnotationChanged?.invoke()
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        laserActive = false
                        invalidate()
                        onAnnotationChanged?.invoke()
                    }
                }
                return true
            }
            Mode.PEN -> {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        val stroke = NormalizedStroke()
                        stroke.points.add(NormalizedPoint(nx, ny))
                        strokes.add(stroke)
                        activeStroke = stroke
                        invalidate()
                        onAnnotationChanged?.invoke()
                    }
                    MotionEvent.ACTION_MOVE -> {
                        activeStroke?.points?.add(NormalizedPoint(nx, ny))
                        invalidate()
                        onAnnotationChanged?.invoke()
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        activeStroke = null
                        invalidate()
                        onAnnotationChanged?.invoke()
                    }
                }
                return true
            }
            Mode.NONE -> return false
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val baseScale = minOf(w, h)
        for (stroke in strokes) {
            if (stroke.points.isEmpty()) continue
            penPaint.color = Color.parseColor(stroke.colorHex)
            penPaint.strokeWidth = (baseScale * stroke.strokeWidthRatio).coerceAtLeast(4f)
            drawPath.reset()
            val first = stroke.points[0]
            drawPath.moveTo(first.x * w, first.y * h)
            for (i in 1 until stroke.points.size) {
                val pt = stroke.points[i]
                drawPath.lineTo(pt.x * w, pt.y * h)
            }
            canvas.drawPath(drawPath, penPaint)
        }

        if (laserActive) {
            val lx = laserX * w
            val ly = laserY * h
            val radius = (baseScale * 0.022f).coerceIn(10f, 28f)
            canvas.drawCircle(lx, ly, radius * 1.9f, laserOuterPaint)
            canvas.drawCircle(lx, ly, radius * 0.85f, laserCorePaint)
        }
    }

    fun clearAll() {
        strokes.clear()
        activeStroke = null
        laserActive = false
        invalidate()
        onAnnotationChanged?.invoke()
    }

    fun snapshotStrokes(): List<NormalizedStroke> =
        strokes.map { s -> NormalizedStroke(s.points.toMutableList(), s.colorHex, s.strokeWidthRatio) }

    fun syncFromRemote(
        remoteStrokes: List<NormalizedStroke>,
        remoteLaserActive: Boolean,
        remoteLaserX: Float,
        remoteLaserY: Float
    ) {
        strokes.clear()
        strokes.addAll(remoteStrokes)
        laserActive = remoteLaserActive
        laserX = remoteLaserX
        laserY = remoteLaserY
        invalidate()
    }

    /**
     * Generates lightweight inline SVG markup (0..1000 viewBox) for real-time Wi-Fi browser casting.
     */
    fun toSvgMarkup(): String {
        val sb = StringBuilder()
        sb.append("<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 1000 1000' preserveAspectRatio='none' style='width:100%;height:100%;'>")
        for (stroke in strokes) {
            if (stroke.points.size < 2) continue
            sb.append("<polyline fill='none' stroke='${stroke.colorHex}' stroke-width='6' stroke-linecap='round' stroke-linejoin='round' points='")
            for (pt in stroke.points) {
                sb.append(String.format(Locale.US, "%.1f,%.1f ", pt.x * 1000f, pt.y * 1000f))
            }
            sb.append("'/>")
        }
        if (laserActive) {
            val cx = String.format(Locale.US, "%.1f", laserX * 1000f)
            val cy = String.format(Locale.US, "%.1f", laserY * 1000f)
            sb.append("<circle cx='$cx' cy='$cy' r='26' fill='rgba(239,68,68,0.35)'/>")
            sb.append("<circle cx='$cx' cy='$cy' r='11' fill='#ff1e1e'/>")
        }
        sb.append("</svg>")
        return sb.toString()
    }
}
