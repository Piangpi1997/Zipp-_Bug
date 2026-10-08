package com.zipbug.base.termux

import android.content.ComponentName
import android.content.Context
import android.content.Intent

object TermuxBridge {
    private const val PREFIX = "/data/data/com.termux/files/usr/bin/"

    private val tools = mapOf(
        "python" to "python",
        "ffmpeg" to "ffmpeg",
        "yt-dlp" to "yt-dlp",
        "gradle" to "gradle",
        "git" to "git",
        "aapt2" to "aapt2",
        "zipalign" to "zipalign"
    )

    data class Request(
        val tool: String,
        val args: List<String>,
        val workDir: String = "/data/data/com.termux/files/home"
    )

    fun send(context: Context, request: Request): Result<Unit> = runCatching {
        val binary = tools[request.tool]
            ?: error("Tool not allowed: ${request.tool}")

        require(
            request.args.none { arg ->
                arg.contains('\u0000') || arg.length > 4096
            }
        ) { "Invalid argument" }

        val intent = Intent().apply {
            component = ComponentName(
                "com.termux",
                "com.termux.app.RunCommandService"
            )
            action = "com.termux.RUN_COMMAND"
            putExtra("com.termux.RUN_COMMAND_PATH", PREFIX + binary)
            putExtra(
                "com.termux.RUN_COMMAND_ARGUMENTS",
                request.args.toTypedArray()
            )
            putExtra("com.termux.RUN_COMMAND_WORKDIR", request.workDir)
            putExtra("com.termux.RUN_COMMAND_BACKGROUND", false)
        }

        context.startService(intent)
    }
}
