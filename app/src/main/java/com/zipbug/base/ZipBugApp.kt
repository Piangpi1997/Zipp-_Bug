package com.zipbug.base

import android.app.Application
import com.zipbug.base.data.ZipBugDatabase

class ZipBugApp : Application() {
    val database by lazy { ZipBugDatabase.get(this) }
}
