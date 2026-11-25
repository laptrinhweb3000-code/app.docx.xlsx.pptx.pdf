package com.doccast.presenter.cast

import fi.iki.elonen.NanoHTTPD
import org.json.JSONObject
import java.io.ByteArrayInputStream

/**
 * Lightweight embedded HTTP server for Wi-Fi / LAN browser casting.
 * Any Smart TV, laptop, or classroom PC on the same Wi-Fi opens http://<phone-ip>:<port>
 * to receive synchronized PDF pages, Office slides/docs, laser pointer, and pen strokes.
 */
class WifiCastServer(
    port: Int,
    private val stateProvider: () -> CastSnapshot,
    private val pdfJpegProvider: (Int) -> ByteArray?,
    private val htmlPageProvider: (Int) -> String?
) : NanoHTTPD(port) {

    data class CastSnapshot(
        val contentVersion: Long,
        val overlayVersion: Long,
        val isPdf: Boolean,
        val title: String,
        val pageIndex: Int,
        val totalPages: Int,
        val blackout: Boolean,
        val overlaySvg: String
    )

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri ?: "/"
        return when {
            uri == "/" || uri == "/index.html" -> {
                newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", RECEIVER_HTML)
            }
            uri == "/api/state" -> {
                val snap = stateProvider()
                val json = JSONObject().apply {
                    put("contentVersion", snap.contentVersion)
                    put("overlayVersion", snap.overlayVersion)
                    put("isPdf", snap.isPdf)
                    put("title", snap.title)
                    put("pageIndex", snap.pageIndex)
                    put("totalPages", snap.totalPages)
                    put("blackout", snap.blackout)
                    put("overlaySvg", snap.overlaySvg)
                }
                newFixedLengthResponse(Response.Status.OK, "application/json; charset=utf-8", json.toString())
            }
            uri.startsWith("/api/page/") -> {
                val idx = uri.removePrefix("/api/page/").toIntOrNull() ?: 0
                val bytes = pdfJpegProvider(idx)
                if (bytes != null) {
                    newFixedLengthResponse(
                        Response.Status.OK,
                        "image/jpeg",
                        ByteArrayInputStream(bytes),
                        bytes.size.toLong()
                    )
                } else {
                    newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Page not found")
                }
            }
            uri.startsWith("/api/html/") -> {
                val idx = uri.removePrefix("/api/html/").toIntOrNull() ?: 0
                val html = htmlPageProvider(idx) ?: "<html><body><p>Không có nội dung</p></body></html>"
                newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", html)
            }
            else -> newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not Found")
        }
    }

    companion object {
        private val RECEIVER_HTML = """
            <!DOCTYPE html>
            <html lang="vi">
            <head>
              <meta charset="UTF-8" />
              <meta name="viewport" content="width=device-width, initial-scale=1.0" />
              <title>DocCast Presenter — Màn hình chiếu</title>
              <style>
                html, body {
                  margin: 0; padding: 0; width: 100%; height: 100%;
                  background: #000000; overflow: hidden;
                  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
                }
                #stage {
                  position: relative; width: 100vw; height: 100vh;
                  display: flex; align-items: center; justify-content: center;
                }
                #pdfImg {
                  max-width: 100%; max-height: 100%; object-fit: contain; display: none;
                }
                #officeFrame {
                  width: 100%; height: 100%; border: none; background: #ffffff; display: none;
                }
                #overlayLayer {
                  position: absolute; inset: 0; pointer-events: none; z-index: 10;
                }
                #blackout {
                  position: absolute; inset: 0; background: #000000; z-index: 20; display: none;
                }
                #hud {
                  position: fixed; right: 14px; bottom: 14px; z-index: 30;
                  display: flex; gap: 8px; align-items: center;
                  background: rgba(15,23,42,0.75); color: #fff;
                  padding: 6px 12px; border-radius: 999px; font-size: 13px;
                }
                #hud button {
                  background: #2563eb; color: #fff; border: none;
                  padding: 5px 10px; border-radius: 999px; cursor: pointer; font-weight: 600;
                }
              </style>
            </head>
            <body>
              <div id="stage">
                <img id="pdfImg" alt="Trang trình chiếu" />
                <iframe id="officeFrame" title="Nội dung Office"></iframe>
                <div id="overlayLayer"></div>
                <div id="blackout"></div>
              </div>
              <div id="hud">
                <span id="statusText">Đang kết nối…</span>
                <button onclick="toggleFs()">Toàn màn hình</button>
              </div>
              <script>
                let lastContentVer = -1;
                let lastOverlayVer = -1;

                function toggleFs() {
                  if (!document.fullscreenElement) {
                    document.documentElement.requestFullscreen().catch(() => {});
                  } else {
                    document.exitFullscreen().catch(() => {});
                  }
                }

                async function poll() {
                  try {
                    const res = await fetch('/api/state', { cache: 'no-store' });
                    if (!res.ok) return;
                    const data = await res.json();

                    document.getElementById('statusText').textContent =
                      data.title + ' (' + (data.pageIndex + 1) + '/' + data.totalPages + ')';

                    document.getElementById('blackout').style.display = data.blackout ? 'block' : 'none';

                    if (data.contentVersion !== lastContentVer) {
                      lastContentVer = data.contentVersion;
                      const pdfImg = document.getElementById('pdfImg');
                      const frame = document.getElementById('officeFrame');
                      if (data.isPdf) {
                        frame.style.display = 'none';
                        pdfImg.style.display = 'block';
                        pdfImg.src = '/api/page/' + data.pageIndex + '?v=' + data.contentVersion;
                      } else {
                        pdfImg.style.display = 'none';
                        frame.style.display = 'block';
                        frame.src = '/api/html/' + data.pageIndex + '?v=' + data.contentVersion;
                      }
                    }

                    if (data.overlayVersion !== lastOverlayVer) {
                      lastOverlayVer = data.overlayVersion;
                      document.getElementById('overlayLayer').innerHTML = data.overlaySvg || '';
                    }
                  } catch (e) {
                    document.getElementById('statusText').textContent = 'Mất kết nối — đang thử lại…';
                  }
                }

                poll();
                setInterval(poll, 350);
              </script>
            </body>
            </html>
        """.trimIndent()
    }
}
