package com.zipbug.base.engine

import java.io.File

object EngineManager {
    private var server: LocalHttpServer? = null

    fun run(wwwRoot: File) {
        server?.stop()
        server = LocalHttpServer(wwwRoot)
        server?.start()
    }

    fun stop() {
        server?.stop()
        server = null
    }
}
