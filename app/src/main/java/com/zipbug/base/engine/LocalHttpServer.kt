package com.zipbug.base.engine

import java.io.BufferedOutputStream
import java.io.File
import java.net.InetAddress
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
            server = ServerSocket(
                port,
                20,
                InetAddress.getByName("127.0.0.1")
            )
            while (running) {
                runCatching { server?.accept() }
                    .getOrNull()
                    ?.let { socket -> pool.execute { serve(socket) } }
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
                out.write(
                    "HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\n\r\n"
                        .toByteArray()
                )
                out.flush()
                return
            }

            val mime = when (file.extension.lowercase()) {
                "html" -> "text/html; charset=utf-8"
                "js" -> "application/javascript"
                "css" -> "text/css"
                "json" -> "application/json"
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
