package com.zipbug.base.termux

import android.app.IntentService
import android.content.Intent
import com.zipbug.base.data.BuildJobEntity
import com.zipbug.base.data.ZipBugDatabase
import kotlinx.coroutines.runBlocking

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
            ZipBugDatabase.get(applicationContext)
                .buildJobDao()
                .finish(
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
        }
    }

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
