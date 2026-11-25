package com.doccast.presenter.parser

import com.doccast.presenter.model.DocFormat
import com.doccast.presenter.model.HtmlPage
import com.doccast.presenter.model.LoadedDocument
import org.apache.poi.hslf.usermodel.HSLFSlideShow
import org.apache.poi.hslf.usermodel.HSLFTextShape
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.apache.poi.hwpf.HWPFDocument
import org.apache.poi.hwpf.extractor.WordExtractor
import org.apache.poi.ss.usermodel.DataFormatter
import java.io.InputStream

/**
 * Lightweight legacy binary Office parser (.doc, .ppt, .xls) using Apache POI 4.1.2.
 */
object LegacyOfficeParser {

    fun parseDoc(title: String, inputStream: InputStream): LoadedDocument.HtmlPages {
        return try {
            HWPFDocument(inputStream).use { doc ->
                val extractor = WordExtractor(doc)
                val paragraphs = extractor.paragraphText
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }

                val body = if (paragraphs.isEmpty()) {
                    "<p class='muted'>Tài liệu trống.</p>"
                } else {
                    paragraphs.joinToString("\n") { "<p>${OoxmlParser.escapeHtml(it)}</p>" }
                }

                LoadedDocument.HtmlPages(
                    title = title,
                    format = DocFormat.DOC,
                    pages = listOf(HtmlPage("Trang 1", HtmlTemplates.wrapDocument(title, body)))
                )
            }
        } catch (e: Exception) {
            errorPages(title, DocFormat.DOC, "Không thể đọc tệp .doc: ${e.message}")
        }
    }

    fun parsePpt(title: String, inputStream: InputStream): LoadedDocument.HtmlPages {
        return try {
            HSLFSlideShow(inputStream).use { ppt ->
                val slides = ppt.slides
                if (slides.isEmpty()) {
                    return errorPages(title, DocFormat.PPT, "Tệp PPT không có slide nào.")
                }

                val pages = slides.mapIndexed { idx, slide ->
                    val lines = mutableListOf<String>()
                    for (shape in slide.shapes) {
                        if (shape is HSLFTextShape) {
                            val txt = shape.text?.trim().orEmpty()
                            if (txt.isNotEmpty()) {
                                txt.split('\n', '\r')
                                    .map { it.trim() }
                                    .filter { it.isNotEmpty() }
                                    .forEach { lines.add(OoxmlParser.escapeHtml(it)) }
                            }
                        }
                    }

                    val body = StringBuilder()
                    if (lines.isNotEmpty()) {
                        body.append("<h1 class='slide-title'>${lines.first()}</h1>")
                        if (lines.size > 1) {
                            body.append("<ul class='slide-bullets'>")
                            lines.drop(1).forEach { body.append("<li>$it</li>") }
                            body.append("</ul>")
                        }
                    } else {
                        body.append("<p class='muted'>Slide trống</p>")
                    }

                    HtmlPage(
                        label = "Slide ${idx + 1}",
                        html = HtmlTemplates.wrapSlide(idx + 1, slides.size, body.toString())
                    )
                }

                LoadedDocument.HtmlPages(title, DocFormat.PPT, pages)
            }
        } catch (e: Exception) {
            errorPages(title, DocFormat.PPT, "Không thể đọc tệp .ppt: ${e.message}")
        }
    }

    fun parseXls(title: String, inputStream: InputStream): LoadedDocument.HtmlPages {
        return try {
            HSSFWorkbook(inputStream).use { wb ->
                val formatter = DataFormatter()
                val sheetCount = wb.numberOfSheets
                if (sheetCount == 0) {
                    return errorPages(title, DocFormat.XLS, "Tệp Excel không có trang tính nào.")
                }

                val pages = (0 until sheetCount).map { sIdx ->
                    val sheet = wb.getSheetAt(sIdx)
                    val sheetName = sheet.sheetName ?: "Sheet ${sIdx + 1}"
                    val sb = StringBuilder("<table>")
                    var rowCounter = 0
                    for (row in sheet) {
                        if (rowCounter >= 500) break
                        val lastCol = row.lastCellNum.toInt().coerceAtLeast(0)
                        if (lastCol == 0) continue
                        val cellTag = if (rowCounter == 0) "th" else "td"
                        sb.append("<tr>")
                        for (c in 0 until lastCol) {
                            val cell = row.getCell(c)
                            val value = if (cell != null) formatter.formatCellValue(cell) else ""
                            sb.append("<$cellTag>${OoxmlParser.escapeHtml(value)}</$cellTag>")
                        }
                        sb.append("</tr>")
                        rowCounter++
                    }
                    sb.append("</table>")
                    val content = if (rowCounter == 0) "<p class='muted'>Trang tính trống.</p>" else sb.toString()
                    HtmlPage(sheetName, HtmlTemplates.wrapSpreadsheet("$title — $sheetName", content))
                }

                LoadedDocument.HtmlPages(title, DocFormat.XLS, pages)
            }
        } catch (e: Exception) {
            errorPages(title, DocFormat.XLS, "Không thể đọc tệp .xls: ${e.message}")
        }
    }

    private fun errorPages(title: String, format: DocFormat, msg: String): LoadedDocument.HtmlPages {
        return LoadedDocument.HtmlPages(
            title,
            format,
            listOf(HtmlPage("Lỗi", HtmlTemplates.wrapDocument(title, "<p class='muted'>${OoxmlParser.escapeHtml(msg)}</p>")))
        )
    }
}
