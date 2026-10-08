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
        BuildJobEntity::class,
        ApkArtifactEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class ZipBugDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun buildJobDao(): BuildJobDao
    abstract fun apkArtifactDao(): ApkArtifactDao

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

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS apk_artifacts (
                        id TEXT NOT NULL PRIMARY KEY,
                        filePath TEXT NOT NULL,
                        fileName TEXT NOT NULL,
                        packageName TEXT NOT NULL,
                        versionName TEXT NOT NULL,
                        versionCode INTEGER NOT NULL,
                        minSdk INTEGER NOT NULL,
                        targetSdk INTEGER NOT NULL,
                        sizeBytes INTEGER NOT NULL,
                        sha256 TEXT NOT NULL,
                        signerCertificateSha256 TEXT,
                        discoveredAt INTEGER NOT NULL
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
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
                .also { INSTANCE = it }
        }
    }
}
