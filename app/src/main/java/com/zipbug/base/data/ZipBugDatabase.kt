package com.zipbug.base.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [ProjectEntity::class], version = 1, exportSchema = false)
abstract class ZipBugDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao

    companion object {
        @Volatile private var INSTANCE: ZipBugDatabase? = null

        fun get(context: Context): ZipBugDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext,
                ZipBugDatabase::class.java,
                "zipbug.db"
            ).build().also { INSTANCE = it }
        }
    }
}
