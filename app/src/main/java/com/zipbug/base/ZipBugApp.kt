package com.zipbug.base

import android.app.Application
import com.zipbug.base.data.ZipBugDatabase
import com.zipbug.base.ui.AppLanguage

class ZipBugApp : Application() {
    val database by lazy { ZipBugDatabase.get(this) }

    override fun onCreate() {
        super.onCreate()
        AppLanguage.initialize(this)
    }
}
