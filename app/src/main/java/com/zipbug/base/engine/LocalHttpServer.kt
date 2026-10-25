package com.zipbug.base.engine

import java.io.BufferedOutputStream
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.concurrent.Executors

class LocalHttpServer(
    private val root: File,
    private val port: Int = 3131
) {
    @Volatile private var running = false
    private var server: ServerSocket? = null
    private val pool = Executors.newCachedThreadPool()

    fun start() {
        if (running) return
        running = true
        pool.execute {
            runCatching {
                val srv = ServerSocket()
                srv.reuseAddress = true
                srv.bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), port), 20)
                server = srv
                while (running) {
                    runCatching { srv.accept() }
                        .getOrNull()
                        ?.let { socket -> pool.execute { serve(socket) } }
                }
            }
        }
    }

    fun stop() {
        running = false
        runCatching { server?.close() }
        server = null
    }

    private fun serve(socket: Socket) {
        socket.use { s ->
            val reader = s.getInputStream().bufferedReader()
            val firstLine = reader.readLine() ?: return
            val raw = firstLine.split(" ").getOrNull(1) ?: "/"

            while (true) {
                val header = reader.readLine() ?: break
                if (header.isEmpty()) break
            }

            val path = URLDecoder.decode(
                raw.substringBefore('?'),
                "UTF-8"
            ).removePrefix("/").ifBlank { "index.html" }

            val base = root.canonicalFile
            val file = File(base, path).canonicalFile
            val out = BufferedOutputStream(s.getOutputStream())

            if (!file.path.startsWith(base.path + File.separator) || !file.isFile) {
                val notFoundBody = """
                    <!doctype html>
                    <html><head><meta charset="utf-8"><title>404 Not Found</title></head>
                    <body style="background:#121212;color:#ff4d4d;font-family:monospace;padding:24px;">
                    <h2>HTTP 404 — File Not Found</h2>
                    <p>Requested path: /${path.replace("<", "&lt;")}</p>
                    <script>console.error("HTTP 404 Not Found: /" + ${org.json.JSONObject.quote(path)});</script>
                    </body></html>
                """.trimIndent().toByteArray(Charsets.UTF_8)
                val head = "HTTP/1.1 404 Not Found\r\n" +
                    "Content-Type: text/html; charset=utf-8\r\n" +
                    "Content-Length: ${notFoundBody.size}\r\n" +
                    "Cache-Control: no-store\r\n\r\n"
                out.write(head.toByteArray())
                out.write(notFoundBody)
                out.flush()
                return
            }

            val mime = when (file.extension.lowercase()) {
                "html" -> "text/html; charset=utf-8"
                "js", "mjs", "jsx", "ts", "tsx" -> "application/javascript; charset=utf-8"
                "css" -> "text/css; charset=utf-8"
                "json" -> "application/json; charset=utf-8"
                "png" -> "image/png"
                "jpg", "jpeg" -> "image/jpeg"
                "svg" -> "image/svg+xml"
                else -> "application/octet-stream"
            }

            val head = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: $mime\r\n" +
                "Content-Length: ${file.length()}\r\n" +
                "Cache-Control: no-store\r\n\r\n"

            out.write(head.toByteArray())
            file.inputStream().use { it.copyTo(out) }
            out.flush()
        }
    }
}
