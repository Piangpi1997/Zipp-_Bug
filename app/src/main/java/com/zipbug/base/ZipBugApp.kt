package com.zipbug.base

import android.app.Application
import com.zipbug.base.data.ZipBugDatabase
import com.zipbug.base.ui.LocaleThemeHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ZipBugApp : Application() {
    val database by lazy { ZipBugDatabase.get(this) }

    override fun onCreate() {
        super.onCreate()
        LocaleThemeHelper.applySavedPreferences(this)
        cleanupStaleJobs()
    }

    private fun cleanupStaleJobs() {
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                database.recapRenderJobDao().markStaleJobsInterrupted()
                database.buildJobDao().markStaleJobsInterrupted()
            }
        }
    }
}
