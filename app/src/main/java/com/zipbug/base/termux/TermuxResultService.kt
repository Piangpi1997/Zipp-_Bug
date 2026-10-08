package com.zipbug.base.termux

import android.app.IntentService
import android.content.Intent
import com.zipbug.base.data.BuildJobEntity
import com.zipbug.base.data.ZipBugDatabase
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import java.io.File

@Suppress("DEPRECATION")
class TermuxResultService : IntentService("ZipBugTermuxResult") {
    override fun onHandleIntent(intent: Intent?) {
        if (intent == null) return

        val jobId = intent.getStringExtra(EXTRA_JOB_ID) ?: return
        val result = intent.getBundleExtra(EXTRA_PLUGIN_RESULT_BUNDLE)

        val stdout = result?.getString(EXTRA_STDOUT).orEmpty()
        val stderr = result?.getString(EXTRA_STDERR).orEmpty()
        val exitCode = if (result?.containsKey(EXTRA_EXIT_CODE) == true) {
            result.getInt(EXTRA_EXIT_CODE)
        } else {
            null
        }
        val errorCode = if (result?.containsKey(EXTRA_ERR) == true) {
            result.getInt(EXTRA_ERR)
        } else {
            null
        }
        val errorMessage = result?.getString(EXTRA_ERRMSG).orEmpty()

        val status = if (
            result != null &&
            exitCode == 0 &&
            (errorCode == null || errorCode == -1) &&
            errorMessage.isBlank()
        ) {
            BuildJobEntity.SUCCESS
        } else {
            BuildJobEntity.FAILED
        }

        runBlocking {
            val dao = ZipBugDatabase.get(applicationContext)
                .buildJobDao()

            val job = dao.get(jobId)

            dao.finish(
                id = jobId,
                status = status,
                stdout = stdout,
                stderr = stderr,
                exitCode = exitCode,
                errorCode = errorCode,
                errorMessage = if (result == null) {
                    "Termux returned no result bundle"
                } else {
                    errorMessage
                }
            )

            // A successful /apkbuilder is followed by a real Termux copy
            // of the generated APK into shared storage. If storage access
            // is not configured in Termux, the copy becomes a FAILED job
            // and the UI reports the actual stderr/exit code.
            if (
                status == BuildJobEntity.SUCCESS &&
                job != null &&
                job.tool == "gradle" &&
                hasAssembleDebug(job.argsJson)
            ) {
                exportDebugApk(job)
            }
        }
    }

    private suspend fun exportDebugApk(
        job: BuildJobEntity
    ) {
        val source = File(
            job.workDir,
            "app/build/outputs/apk/debug/app-debug.apk"
        ).absolutePath

        val prefs = getSharedPreferences(
            "zipbug.settings",
            MODE_PRIVATE
        )

        val destination = prefs.getString(
            "artifactExportPath",
            "/storage/emulated/0/Download/Zip_Bug-debug.apk"
        )!!

        require(
            destination.startsWith("/storage/emulated/0/") ||
                destination.startsWith(
                    "/data/data/com.termux/files/home/storage/"
                )
        ) {
            "APK export path must be shared storage"
        }

        TermuxBridge.send(
            applicationContext,
            TermuxBridge.Request(
                tool = "cp",
                args = listOf(source, destination),
                workDir = job.workDir,
                label = "Zip_Bug APK Export"
            )
        )
    }

    private fun hasAssembleDebug(
        argsJson: String
    ): Boolean = runCatching {
        val args = JSONArray(argsJson)
        (0 until args.length())
            .map { args.getString(it) }
            .any { it == "assembleDebug" }
    }.getOrDefault(false)

    companion object {
        const val EXTRA_JOB_ID = "zipbug_job_id"

        // TermuxConstants.TERMUX_APP.TERMUX_SERVICE
        const val EXTRA_PLUGIN_RESULT_BUNDLE = "result"
        const val EXTRA_STDOUT = "stdout"
        const val EXTRA_STDERR = "stderr"
        const val EXTRA_EXIT_CODE = "exitCode"
        const val EXTRA_ERR = "err"
        const val EXTRA_ERRMSG = "errmsg"
    }
}
