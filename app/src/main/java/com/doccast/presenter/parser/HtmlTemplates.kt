package com.doccast.presenter.parser

/**
 * Responsive, projector-friendly HTML/CSS templates for Word, PowerPoint, and Excel views.
 */
object HtmlTemplates {

    fun wrapDocument(title: String, bodyHtml: String): String = """
        <!DOCTYPE html>
        <html lang="vi">
        <head>
        <meta charset="UTF-8" />
        <meta name="viewport" content="width=device-width, initial-scale=1.0" />
        <style>
          body {
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
            background: #ffffff;
            color: #0f172a;
            margin: 0;
            padding: 20px 22px 48px 22px;
            line-height: 1.65;
            font-size: 17px;
          }
          h1, h2, h3, h4 { color: #1e3a8a; margin-top: 1.1em; margin-bottom: 0.45em; }
          h1 { font-size: 1.55em; border-bottom: 2px solid #e2e8f0; padding-bottom: 6px; }
          h2 { font-size: 1.3em; }
          p { margin: 0.6em 0; }
          .table-wrap { overflow-x: auto; margin: 14px 0; }
          table { border-collapse: collapse; width: 100%; font-size: 15px; }
          td, th { border: 1px solid #cbd5e1; padding: 8px 10px; text-align: left; vertical-align: top; }
          th { background: #eff6ff; color: #1e3a8a; font-weight: 600; }
          .img-box { text-align: center; margin: 14px 0; }
          .img-box img { max-width: 100%; height: auto; border-radius: 6px; }
          .muted { color: #64748b; font-style: italic; }
        </style>
        <title>${OoxmlParser.escapeHtml(title)}</title>
        </head>
        <body>$bodyHtml</body>
        </html>
    """.trimIndent()

    fun wrapSlide(slideNumber: Int, totalSlides: Int, contentHtml: String): String = """
        <!DOCTYPE html>
        <html lang="vi">
        <head>
        <meta charset="UTF-8" />
        <meta name="viewport" content="width=device-width, initial-scale=1.0" />
        <style>
          html, body {
            margin: 0; padding: 0; width: 100%; height: 100%;
            background: #0f172a; color: #0f172a;
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
            display: flex; align-items: center; justify-content: center;
          }
          .slide-canvas {
            width: 94vw; max-width: 1200px; min-height: 76vh;
            background: linear-gradient(180deg, #ffffff 0%, #f8fafc 100%);
            border-radius: 14px;
            box-shadow: 0 18px 40px rgba(0,0,0,0.45);
            padding: 36px 42px;
            box-sizing: border-box;
            position: relative;
            display: flex; flex-direction: column; justify-content: flex-start;
          }
          .slide-title {
            color: #1e40af; font-size: clamp(22px, 3.2vw, 38px);
            margin: 0 0 20px 0; padding-bottom: 12px;
            border-bottom: 3px solid #2563eb;
          }
          .slide-bullets {
            font-size: clamp(16px, 2.1vw, 26px);
            line-height: 1.55; padding-left: 26px; margin: 0;
          }
          .slide-bullets li { margin-bottom: 12px; }
          .slide-gallery {
            display: flex; flex-wrap: wrap; gap: 14px;
            justify-content: center; margin-top: 20px;
          }
          .slide-img { max-width: 100%; max-height: 46vh; object-fit: contain; border-radius: 8px; }
          .slide-footer {
            position: absolute; right: 24px; bottom: 14px;
            font-size: 13px; color: #64748b; font-weight: 600;
          }
          .muted { color: #64748b; font-size: 18px; }
        </style>
        </head>
        <body>
          <div class="slide-canvas">
            $contentHtml
            <div class="slide-footer">Slide $slideNumber / $totalSlides</div>
          </div>
        </body>
        </html>
    """.trimIndent()

    fun wrapSpreadsheet(title: String, tableHtml: String): String = """
        <!DOCTYPE html>
        <html lang="vi">
        <head>
        <meta charset="UTF-8" />
        <meta name="viewport" content="width=device-width, initial-scale=1.0" />
        <style>
          body {
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
            background: #ffffff; color: #0f172a; margin: 0; padding: 12px;
          }
          .sheet-header {
            font-size: 15px; font-weight: 700; color: #166534;
            margin-bottom: 10px; padding: 6px 10px;
            background: #dcfce7; border-radius: 8px;
          }
          .grid-wrap { overflow: auto; }
          table { border-collapse: collapse; min-width: 100%; font-size: 14px; }
          th, td {
            border: 1px solid #cbd5e1; padding: 7px 10px;
            white-space: nowrap; text-align: left;
          }
          th {
            background: #16a34a; color: #ffffff;
            position: sticky; top: 0; font-weight: 600;
          }
          tr:nth-child(even) td { background: #f8fafc; }
          .muted { color: #64748b; padding: 16px; }
        </style>
        </head>
        <body>
          <div class="sheet-header">${OoxmlParser.escapeHtml(title)}</div>
          <div class="grid-wrap">$tableHtml</div>
        </body>
        </html>
    """.trimIndent()
}
