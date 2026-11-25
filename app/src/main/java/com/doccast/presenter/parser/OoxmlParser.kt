package com.doccast.presenter.parser

import android.util.Base64
import android.util.Xml
import com.doccast.presenter.model.DocFormat
import com.doccast.presenter.model.HtmlPage
import com.doccast.presenter.model.LoadedDocument
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Lightweight, zero-dependency pure-Kotlin parser for modern OOXML files:
 * .docx (Word), .pptx (PowerPoint), and .xlsx (Excel).
 * Uses Android's built-in ZipInputStream + XmlPullParser for instant opening and small APK size.
 */
object OoxmlParser {

    fun parseDocx(title: String, inputStream: InputStream): LoadedDocument.HtmlPages {
        val entries = readZipEntries(inputStream)
        val docXml = entries["word/document.xml"]
            ?: return singleErrorPage(title, DocFormat.DOCX, "Không tìm thấy nội dung word/document.xml.")
        val relsMap = parseRelationships(entries["word/_rels/document.xml.rels"], "word/")

        val bodyHtml = StringBuilder()
        val parser = newXmlParser(docXml)
        var eventType = parser.eventType

        var inParagraph = false
        var inTable = false
        var inRow = false
        var inCell = false
        var inRun = false
        var runBold = false
        var runItalic = false
        var headingLevel = 0
        val paragraphBuf = StringBuilder()

        while (eventType != XmlPullParser.END_DOCUMENT) {
            val tag = localName(parser.name)
            when (eventType) {
                XmlPullParser.START_TAG -> when (tag) {
                    "tbl" -> {
                        inTable = true
                        bodyHtml.append("<div class='table-wrap'><table>")
                    }
                    "tr" -> if (inTable) {
                        inRow = true
                        bodyHtml.append("<tr>")
                    }
                    "tc" -> if (inRow) {
                        inCell = true
                        bodyHtml.append("<td>")
                    }
                    "p" -> {
                        inParagraph = true
                        headingLevel = 0
                        paragraphBuf.setLength(0)
                    }
                    "pStyle" -> if (inParagraph) {
                        val styleVal = getAttr(parser, "val").lowercase()
                        headingLevel = when {
                            styleVal.contains("heading1") || styleVal == "title" -> 1
                            styleVal.contains("heading2") || styleVal == "subtitle" -> 2
                            styleVal.contains("heading3") -> 3
                            styleVal.contains("heading4") -> 4
                            else -> 0
                        }
                    }
                    "r" -> {
                        inRun = true
                        runBold = false
                        runItalic = false
                    }
                    "b" -> if (inRun) {
                        val v = getAttr(parser, "val")
                        runBold = v.isEmpty() || v == "1" || v.equals("true", true)
                    }
                    "i" -> if (inRun) {
                        val v = getAttr(parser, "val")
                        runItalic = v.isEmpty() || v == "1" || v.equals("true", true)
                    }
                    "t" -> if (inParagraph) {
                        val text = escapeHtml(parser.nextText())
                        if (text.isNotEmpty()) {
                            var formatted = text
                            if (runBold) formatted = "<strong>$formatted</strong>"
                            if (runItalic) formatted = "<em>$formatted</em>"
                            paragraphBuf.append(formatted)
                        }
                    }
                    "tab" -> if (inParagraph) paragraphBuf.append("&emsp;")
                    "br" -> if (inParagraph) paragraphBuf.append("<br/>")
                    "blip" -> if (inParagraph) {
                        val rId = getAttr(parser, "embed")
                        val mediaPath = relsMap[rId]
                        val bytes = if (mediaPath != null) entries[mediaPath] else null
                        if (bytes != null) {
                            val mime = guessImageMime(mediaPath!!)
                            val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                            paragraphBuf.append("<div class='img-box'><img src='data:$mime;base64,$b64'/></div>")
                        }
                    }
                }
                XmlPullParser.END_TAG -> when (tag) {
                    "r" -> inRun = false
                    "p" -> {
                        inParagraph = false
                        val content = paragraphBuf.toString().trim()
                        if (content.isNotEmpty()) {
                            val htmlBlock = when (headingLevel) {
                                1 -> "<h1>$content</h1>"
                                2 -> "<h2>$content</h2>"
                                3 -> "<h3>$content</h3>"
                                4 -> "<h4>$content</h4>"
                                else -> "<p>$content</p>"
                            }
                            bodyHtml.append(htmlBlock)
                        }
                    }
                    "tc" -> if (inCell) {
                        inCell = false
                        bodyHtml.append("</td>")
                    }
                    "tr" -> if (inRow) {
                        inRow = false
                        bodyHtml.append("</tr>")
                    }
                    "tbl" -> if (inTable) {
                        inTable = false
                        bodyHtml.append("</table></div>")
                    }
                }
            }
            eventType = parser.next()
        }

        val finalHtml = HtmlTemplates.wrapDocument(
            title = title,
            bodyHtml = if (bodyHtml.isEmpty()) "<p class='muted'>Tài liệu trống hoặc không có văn bản đọc được.</p>" else bodyHtml.toString()
        )
        return LoadedDocument.HtmlPages(title, DocFormat.DOCX, listOf(HtmlPage("Trang 1", finalHtml)))
    }

    fun parsePptx(title: String, inputStream: InputStream): LoadedDocument.HtmlPages {
        val entries = readZipEntries(inputStream)
        val slideRegex = Regex("^ppt/slides/slide(\\d+)\\.xml$")
        val slidePaths = entries.keys
            .mapNotNull { key ->
                slideRegex.matchEntire(key)?.let { key to it.groupValues[1].toInt() }
            }
            .sortedBy { it.second }
            .map { it.first }

        if (slidePaths.isEmpty()) {
            return singleErrorPage(title, DocFormat.PPTX, "Không tìm thấy trang trình chiếu nào trong tệp PPTX.")
        }

        val pages = slidePaths.mapIndexed { index, slidePath ->
            val slideBytes = entries[slidePath]!!
            val slideFileName = slidePath.substringAfterLast('/')
            val relsPath = "ppt/slides/_rels/$slideFileName.rels"
            val relsMap = parseRelationships(entries[relsPath], "ppt/slides/")

            val slideHtml = parseSinglePptxSlide(slideBytes, relsMap, entries, index + 1, slidePaths.size)
            HtmlPage("Slide ${index + 1}", slideHtml)
        }

        return LoadedDocument.HtmlPages(title, DocFormat.PPTX, pages)
    }

    private fun parseSinglePptxSlide(
        xmlBytes: ByteArray,
        relsMap: Map<String, String>,
        entries: Map<String, ByteArray>,
        slideNumber: Int,
        totalSlides: Int
    ): String {
        val blocks = mutableListOf<String>()
        val images = mutableListOf<String>()
        val parser = newXmlParser(xmlBytes)
        var eventType = parser.eventType

        var inParagraph = false
        val paraBuf = StringBuilder()

        while (eventType != XmlPullParser.END_DOCUMENT) {
            val tag = localName(parser.name)
            when (eventType) {
                XmlPullParser.START_TAG -> when (tag) {
                    "p" -> {
                        inParagraph = true
                        paraBuf.setLength(0)
                    }
                    "t" -> if (inParagraph) {
                        paraBuf.append(escapeHtml(parser.nextText()))
                    }
                    "br" -> if (inParagraph) paraBuf.append("<br/>")
                    "blip" -> {
                        val rId = getAttr(parser, "embed")
                        val mediaPath = relsMap[rId]
                        val bytes = if (mediaPath != null) entries[mediaPath] else null
                        if (bytes != null) {
                            val mime = guessImageMime(mediaPath!!)
                            val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                            images.add("<img class='slide-img' src='data:$mime;base64,$b64'/>")
                        }
                    }
                }
                XmlPullParser.END_TAG -> when (tag) {
                    "p" -> {
                        inParagraph = false
                        val txt = paraBuf.toString().trim()
                        if (txt.isNotEmpty()) blocks.add(txt)
                    }
                }
            }
            eventType = parser.next()
        }

        val content = StringBuilder()
        if (blocks.isNotEmpty()) {
            content.append("<h1 class='slide-title'>${blocks.first()}</h1>")
            if (blocks.size > 1) {
                content.append("<ul class='slide-bullets'>")
                blocks.drop(1).forEach { line ->
                    content.append("<li>$line</li>")
                }
                content.append("</ul>")
            }
        }
        if (images.isNotEmpty()) {
            content.append("<div class='slide-gallery'>")
            images.take(4).forEach { content.append(it) }
            content.append("</div>")
        }
        if (blocks.isEmpty() && images.isEmpty()) {
            content.append("<p class='muted'>Slide trống</p>")
        }

        return HtmlTemplates.wrapSlide(slideNumber, totalSlides, content.toString())
    }

    fun parseXlsx(title: String, inputStream: InputStream): LoadedDocument.HtmlPages {
        val entries = readZipEntries(inputStream)
        val sharedStrings = entries["xl/sharedStrings.xml"]?.let { parseSharedStrings(it) } ?: emptyList()

        val sheetRegex = Regex("^xl/worksheets/sheet(\\d+)\\.xml$")
        val sheetPaths = entries.keys
            .mapNotNull { key ->
                sheetRegex.matchEntire(key)?.let { key to it.groupValues[1].toInt() }
            }
            .sortedBy { it.second }
            .map { it.first }

        if (sheetPaths.isEmpty()) {
            return singleErrorPage(title, DocFormat.XLSX, "Không tìm thấy trang tính (sheet) nào trong tệp XLSX.")
        }

        val sheetNames = entries["xl/workbook.xml"]?.let { parseWorkbookSheetNames(it) } ?: emptyList()

        val pages = sheetPaths.mapIndexed { idx, path ->
            val sheetTitle = sheetNames.getOrNull(idx) ?: "Sheet ${idx + 1}"
            val tableHtml = parseWorksheetTable(entries[path]!!, sharedStrings)
            val fullHtml = HtmlTemplates.wrapSpreadsheet("$title — $sheetTitle", tableHtml)
            HtmlPage(sheetTitle, fullHtml)
        }

        return LoadedDocument.HtmlPages(title, DocFormat.XLSX, pages)
    }

    private fun parseSharedStrings(xmlBytes: ByteArray): List<String> {
        val list = mutableListOf<String>()
        val parser = newXmlParser(xmlBytes)
        var eventType = parser.eventType
        var inSi = false
        val sb = StringBuilder()

        while (eventType != XmlPullParser.END_DOCUMENT) {
            val tag = localName(parser.name)
            when (eventType) {
                XmlPullParser.START_TAG -> when (tag) {
                    "si" -> {
                        inSi = true
                        sb.setLength(0)
                    }
                    "t" -> if (inSi) sb.append(parser.nextText())
                }
                XmlPullParser.END_TAG -> if (tag == "si") {
                    inSi = false
                    list.add(sb.toString())
                }
            }
            eventType = parser.next()
        }
        return list
    }

    private fun parseWorkbookSheetNames(xmlBytes: ByteArray): List<String> {
        val names = mutableListOf<String>()
        val parser = newXmlParser(xmlBytes)
        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG && localName(parser.name) == "sheet") {
                val name = getAttr(parser, "name")
                if (name.isNotEmpty()) names.add(name)
            }
            eventType = parser.next()
        }
        return names
    }

    private fun parseWorksheetTable(xmlBytes: ByteArray, sharedStrings: List<String>): String {
        val rows = mutableListOf<List<String>>()
        val parser = newXmlParser(xmlBytes)
        var eventType = parser.eventType

        var currentRow = mutableListOf<String>()
        var cellType = ""
        var cellVal = ""
        var rowCount = 0

        while (eventType != XmlPullParser.END_DOCUMENT && rowCount < 500) {
            val tag = localName(parser.name)
            when (eventType) {
                XmlPullParser.START_TAG -> when (tag) {
                    "row" -> currentRow = mutableListOf()
                    "c" -> {
                        cellType = getAttr(parser, "t")
                        cellVal = ""
                    }
                    "v" -> {
                        val raw = parser.nextText()
                        cellVal = if (cellType == "s") {
                            raw.toIntOrNull()?.let { sharedStrings.getOrNull(it) } ?: raw
                        } else {
                            raw
                        }
                    }
                    "t" -> if (cellType == "inlineStr") {
                        cellVal += parser.nextText()
                    }
                }
                XmlPullParser.END_TAG -> when (tag) {
                    "c" -> currentRow.add(cellVal)
                    "row" -> {
                        if (currentRow.any { it.isNotBlank() }) {
                            rows.add(currentRow)
                            rowCount++
                        }
                    }
                }
            }
            eventType = parser.next()
        }

        if (rows.isEmpty()) return "<p class='muted'>Trang tính trống.</p>"

        val sb = StringBuilder("<table>")
        rows.forEachIndexed { rIdx, row ->
            sb.append("<tr>")
            val cellTag = if (rIdx == 0) "th" else "td"
            row.forEach { cell ->
                sb.append("<$cellTag>${escapeHtml(cell)}</$cellTag>")
            }
            sb.append("</tr>")
        }
        sb.append("</table>")
        return sb.toString()
    }

    private fun parseRelationships(relsBytes: ByteArray?, baseFolder: String): Map<String, String> {
        if (relsBytes == null) return emptyMap()
        val map = mutableMapOf<String, String>()
        val parser = newXmlParser(relsBytes)
        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG && localName(parser.name) == "Relationship") {
                val id = getAttr(parser, "Id")
                val target = getAttr(parser, "Target")
                if (id.isNotEmpty() && target.isNotEmpty()) {
                    map[id] = normalizeZipPath(baseFolder, target)
                }
            }
            eventType = parser.next()
        }
        return map
    }

    private fun normalizeZipPath(baseFolder: String, target: String): String {
        val combined = if (target.startsWith("/")) target.drop(1) else "$baseFolder$target"
        val parts = mutableListOf<String>()
        for (segment in combined.split('/')) {
            when {
                segment.isEmpty() || segment == "." -> Unit
                segment == ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.lastIndex)
                else -> parts.add(segment)
            }
        }
        return parts.joinToString("/")
    }

    private fun readZipEntries(inputStream: InputStream): Map<String, ByteArray> {
        val map = mutableMapOf<String, ByteArray>()
        ZipInputStream(inputStream).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    map[entry.name] = zis.readBytes()
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        return map
    }

    private fun newXmlParser(bytes: ByteArray): XmlPullParser {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(ByteArrayInputStream(bytes), "UTF-8")
        return parser
    }

    private fun localName(rawName: String?): String {
        if (rawName == null) return ""
        return rawName.substringAfter(':')
    }

    private fun getAttr(parser: XmlPullParser, name: String): String {
        for (i in 0 until parser.attributeCount) {
            val attrName = parser.getAttributeName(i)
            if (attrName == name || attrName.substringAfter(':') == name) {
                return parser.getAttributeValue(i) ?: ""
            }
        }
        return ""
    }

    private fun guessImageMime(path: String): String = when (path.substringAfterLast('.', "").lowercase()) {
        "png" -> "image/png"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        else -> "image/jpeg"
    }

    private fun singleErrorPage(title: String, format: DocFormat, msg: String): LoadedDocument.HtmlPages {
        return LoadedDocument.HtmlPages(
            title,
            format,
            listOf(HtmlPage("Thông báo", HtmlTemplates.wrapDocument(title, "<p class='muted'>$msg</p>")))
        )
    }

    fun escapeHtml(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}
