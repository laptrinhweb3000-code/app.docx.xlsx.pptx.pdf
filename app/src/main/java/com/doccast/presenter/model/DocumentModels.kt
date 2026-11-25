package com.doccast.presenter.model

import android.net.Uri

enum class DocFormat(val label: String, val badgeHex: String) {
    PDF("PDF", "#DC2626"),
    DOCX("DOCX", "#2563EB"),
    DOC("DOC", "#1D4ED8"),
    PPTX("PPTX", "#EA580C"),
    PPT("PPT", "#C2410C"),
    XLSX("XLSX", "#16A34A"),
    XLS("XLS", "#15803D"),
    UNKNOWN("FILE", "#64748B");

    companion object {
        fun fromFileNameAndMime(fileName: String, mimeType: String?): DocFormat {
            val ext = fileName.substringAfterLast('.', "").lowercase()
            return when (ext) {
                "pdf" -> PDF
                "docx" -> DOCX
                "doc" -> DOC
                "pptx" -> PPTX
                "ppt" -> PPT
                "xlsx" -> XLSX
                "xls" -> XLS
                else -> when (mimeType?.lowercase()) {
                    "application/pdf" -> PDF
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> DOCX
                    "application/msword" -> DOC
                    "application/vnd.openxmlformats-officedocument.presentationml.presentation" -> PPTX
                    "application/vnd.ms-powerpoint" -> PPT
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> XLSX
                    "application/vnd.ms-excel" -> XLS
                    else -> UNKNOWN
                }
            }
        }
    }
}

data class RecentFileItem(
    val uriString: String,
    val displayName: String,
    val format: DocFormat,
    val lastOpenedAt: Long
) {
    val uri: Uri get() = Uri.parse(uriString)
}

/**
 * Unified representation of a loaded document.
 * - PDF uses native PdfRenderer (pages rendered on-demand as sharp Bitmaps).
 * - Office formats (DOCX, DOC, PPTX, PPT, XLSX, XLS) are parsed into 1 or more
 *   self-contained HTML pages (e.g. 1 slide = 1 page for PPT/PPTX, 1 sheet = 1 page for XLS/XLSX,
 *   or clean paginated/full document view for DOC/DOCX).
 */
sealed class LoadedDocument(
    open val title: String,
    open val format: DocFormat
) {
    data class Pdf(
        override val title: String,
        val pageCount: Int
    ) : LoadedDocument(title, DocFormat.PDF)

    data class HtmlPages(
        override val title: String,
        override val format: DocFormat,
        val pages: List<HtmlPage>
    ) : LoadedDocument(title, format)
}

data class HtmlPage(
    val label: String,
    val html: String
)

data class NormalizedPoint(val x: Float, val y: Float)

data class NormalizedStroke(
    val points: MutableList<NormalizedPoint> = mutableListOf(),
    val colorHex: String = "#EF4444",
    val strokeWidthRatio: Float = 0.006f
)
