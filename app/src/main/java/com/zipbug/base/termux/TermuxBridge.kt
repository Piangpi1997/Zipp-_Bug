package com.zipbug.base.termux

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.zipbug.base.data.BuildJobEntity
import com.zipbug.base.data.ZipBugDatabase
import org.json.JSONArray
import java.util.UUID

object TermuxBridge {
    const val PERMISSION_RUN_COMMAND = "com.termux.permission.RUN_COMMAND"
    private const val PREFIX = "/data/data/com.termux/files/usr/bin/"

    private const val EXTRA_PENDING_INTENT =
        "com.termux.RUN_COMMAND_PENDING_INTENT"

    private val tools = mapOf(
        "python" to "python",
        "java" to "java",
        "ffmpeg" to "ffmpeg",
        "yt-dlp" to "yt-dlp",
        "gradle" to "gradle",
        "git" to "git",
        "aapt2" to "aapt2",
        "zipalign" to "zipalign",
        "apksigner" to "apksigner",
        "cp" to "cp",
        "edge-tts" to "edge-tts",
        "sh" to "sh",
        "bash" to "bash"
    )

    fun isTermuxInstalled(context: Context): Boolean =
        try {
            context.packageManager.getPackageInfo("com.termux", 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            PERMISSION_RUN_COMMAND
        ) == PackageManager.PERMISSION_GRANTED

    data class Request(
        val tool: String,
        val args: List<String>,
        val workDir: String = "/data/data/com.termux/files/home",
        val label: String = "Zip_Bug command"
    )

    suspend fun send(
        context: Context,
        request: Request
    ): Result<String> {
        val appContext = context.applicationContext
        val dao = ZipBugDatabase.get(appContext).buildJobDao()
        val jobId = UUID.randomUUID().toString()

        val job = BuildJobEntity(
            id = jobId,
            tool = request.tool,
            argsJson = JSONArray(request.args).toString(),
            workDir = request.workDir,
            status = BuildJobEntity.QUEUED
        )

        dao.upsert(job)

        return runCatching {
            require(isTermuxInstalled(appContext)) {
                "Termux package (com.termux) is not installed on this device"
            }
            require(hasPermission(appContext)) {
                "Run commands in Termux environment permission (com.termux.permission.RUN_COMMAND) is not granted"
            }

            val binary = tools[request.tool]
                ?: error("Tool not allowed: ${request.tool}")

            require(request.workDir.startsWith("/data/data/com.termux/files/")) {
                "Work directory must be inside Termux storage"
            }

            require(
                request.args.none { arg ->
                    arg.contains('\u0000') || arg.length > 4096
                }
            ) { "Invalid argument" }

            val resultIntent = Intent(
                appContext,
                TermuxResultService::class.java
            ).putExtra(
                TermuxResultService.EXTRA_JOB_ID,
                jobId
            )

            val flags = PendingIntent.FLAG_ONE_SHOT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    PendingIntent.FLAG_MUTABLE
                } else {
                    0
                }

            val resultPendingIntent = PendingIntent.getService(
                appContext,
                jobId.hashCode(),
                resultIntent,
                flags
            )

            val intent = Intent().apply {
                component = ComponentName(
                    "com.termux",
                    "com.termux.app.RunCommandService"
                )
                action = "com.termux.RUN_COMMAND"

                putExtra(
                    "com.termux.RUN_COMMAND_PATH",
                    PREFIX + binary
                )
                putExtra(
                    "com.termux.RUN_COMMAND_ARGUMENTS",
                    request.args.toTypedArray()
                )
                putExtra(
                    "com.termux.RUN_COMMAND_WORKDIR",
                    request.workDir
                )

                // Background mode gives separate stdout and stderr in
                // the Termux result bundle.
                putExtra(
                    "com.termux.RUN_COMMAND_BACKGROUND",
                    true
                )
                putExtra(
                    "com.termux.RUN_COMMAND_COMMAND_LABEL",
                    request.label
                )
                putExtra(
                    EXTRA_PENDING_INTENT,
                    resultPendingIntent
                )
            }

            appContext.startService(intent)
                ?: error("Termux RunCommandService was not started")

            dao.markStarted(jobId)
            jobId
        }.onFailure { error ->
            dao.finish(
                id = jobId,
                status = BuildJobEntity.FAILED,
                stdout = "",
                stderr = "",
                exitCode = null,
                errorCode = null,
                errorMessage = error.message ?: error.javaClass.simpleName
            )
        }
    }
}
