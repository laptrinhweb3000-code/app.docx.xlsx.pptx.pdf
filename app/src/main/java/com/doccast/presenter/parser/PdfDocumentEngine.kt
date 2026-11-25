package com.doccast.presenter.parser

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.LruCache
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

/**
 * High-speed, thread-safe PDF renderer powered by Android's native PdfRenderer.
 * Copies content:// URIs into a local cache file when needed for seekable random access,
 * and caches rendered bitmaps for instant slide/page transitions.
 */
class PdfDocumentEngine(
    private val pfd: ParcelFileDescriptor,
    private val renderer: PdfRenderer,
    private val tempFile: File?
) {
    private val renderLock = Any()

    // Cache up to ~24 MB of rendered pages for smooth navigation
    private val bitmapCache = object : LruCache<String, Bitmap>(24 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    val pageCount: Int get() = renderer.pageCount

    fun renderPage(pageIndex: Int, targetWidthPx: Int): Bitmap {
        val safeIndex = pageIndex.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
        val clampedWidth = targetWidthPx.coerceIn(600, 2048)
        val cacheKey = "${safeIndex}_${clampedWidth}"

        bitmapCache.get(cacheKey)?.let { cached ->
            if (!cached.isRecycled) return cached
        }

        synchronized(renderLock) {
            bitmapCache.get(cacheKey)?.let { cached ->
                if (!cached.isRecycled) return cached
            }

            renderer.openPage(safeIndex).use { page ->
                val aspectRatio = page.height.toFloat() / page.width.coerceAtLeast(1).toFloat()
                val width = clampedWidth
                val height = (width * aspectRatio).toInt().coerceIn(300, 3200)

                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.WHITE)

                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmapCache.put(cacheKey, bitmap)
                return bitmap
            }
        }
    }

    fun renderPageAsJpeg(pageIndex: Int, targetWidthPx: Int = 1600, quality: Int = 88): ByteArray {
        val bmp = renderPage(pageIndex, targetWidthPx)
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, quality, out)
        return out.toByteArray()
    }

    fun close() {
        synchronized(renderLock) {
            bitmapCache.evictAll()
            runCatching { renderer.close() }
            runCatching { pfd.close() }
            runCatching { tempFile?.delete() }
        }
    }

    companion object {
        fun open(context: Context, uri: Uri): PdfDocumentEngine {
            // Copy to a temporary seekable cache file so cloud/SAF URIs always work reliably
            val cacheFile = File(context.cacheDir, "active_pdf_${System.currentTimeMillis()}.pdf")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(cacheFile).use { output ->
                    input.copyTo(output)
                }
            } ?: throw IllegalStateException("Không thể đọc tệp PDF từ nguồn đã chọn.")

            val pfd = ParcelFileDescriptor.open(cacheFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            return PdfDocumentEngine(pfd, renderer, cacheFile)
        }
    }
}
