package com.zipbug.base.termux

object EngineDoctor {
    fun request(
        home: String
    ): TermuxBridge.Request {
        val script = EngineDiagnostics.buildDiagnosticScript()

        return TermuxBridge.Request(
            tool = "python",
            args = listOf("-c", script),
            workDir = home,
            label = "Zip_Bug Engine Diagnostics"
        )
    }
}
