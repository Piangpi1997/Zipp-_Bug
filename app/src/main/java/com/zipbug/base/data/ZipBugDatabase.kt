package com.zipbug.base.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ProjectEntity::class,
        BuildJobEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class ZipBugDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun buildJobDao(): BuildJobDao

    companion object {
        @Volatile private var INSTANCE: ZipBugDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS build_jobs (
                        id TEXT NOT NULL PRIMARY KEY,
                        tool TEXT NOT NULL,
                        argsJson TEXT NOT NULL,
                        workDir TEXT NOT NULL,
                        status TEXT NOT NULL,
                        stdout TEXT NOT NULL,
                        stderr TEXT NOT NULL,
                        exitCode INTEGER,
                        errorCode INTEGER,
                        errorMessage TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        startedAt INTEGER,
                        finishedAt INTEGER
                    )
                    """.trimIndent()
                )
            }
        }

        fun get(context: Context): ZipBugDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext,
                ZipBugDatabase::class.java,
                "zipbug.db"
            )
                .addMigrations(MIGRATION_1_2)
                .build()
                .also { INSTANCE = it }
        }
    }
}
