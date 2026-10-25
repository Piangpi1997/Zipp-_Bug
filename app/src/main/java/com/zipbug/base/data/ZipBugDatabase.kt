package com.zipbug.base.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.zipbug.base.data.recap.*

@Database(
    entities = [
        ProjectEntity::class,
        BuildJobEntity::class,
        ApkArtifactEntity::class,
        RecapProjectEntity::class,
        RecapClipEntity::class,
        RecapScriptEntity::class,
        RecapRenderJobEntity::class,
        ChatSessionEntity::class,
        ChatMessageEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class ZipBugDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun buildJobDao(): BuildJobDao
    abstract fun apkArtifactDao(): ApkArtifactDao
    abstract fun recapProjectDao(): RecapProjectDao
    abstract fun recapClipDao(): RecapClipDao
    abstract fun recapScriptDao(): RecapScriptDao
    abstract fun recapRenderJobDao(): RecapRenderJobDao
    abstract fun chatDao(): ChatDao

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

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS recap_projects (
                        id TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        sourceUri TEXT,
                        sourceFileName TEXT,
                        sourceLocalPath TEXT,
                        sourceDurationMs INTEGER NOT NULL,
                        language TEXT NOT NULL,
                        recapMode TEXT NOT NULL,
                        status TEXT NOT NULL,
                        outputFilePath TEXT,
                        outputFileSize INTEGER,
                        outputDurationMs INTEGER,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS recap_clips (
                        id TEXT NOT NULL PRIMARY KEY,
                        projectId TEXT NOT NULL,
                        orderIndex INTEGER NOT NULL,
                        sourcePath TEXT NOT NULL,
                        startMs INTEGER NOT NULL,
                        endMs INTEGER NOT NULL,
                        durationMs INTEGER NOT NULL,
                        effectType TEXT NOT NULL,
                        effectParam TEXT NOT NULL,
                        isEnabled INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS index_recap_clips_projectId_orderIndex 
                    ON recap_clips(projectId, orderIndex)
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS recap_scripts (
                        id TEXT NOT NULL PRIMARY KEY,
                        projectId TEXT NOT NULL,
                        partNumber INTEGER NOT NULL,
                        title TEXT NOT NULL,
                        scriptText TEXT NOT NULL,
                        ttsVoice TEXT NOT NULL,
                        ttsSpeed TEXT NOT NULL,
                        ttsPitch TEXT NOT NULL,
                        ttsAudioPath TEXT,
                        ttsAudioSize INTEGER,
                        isGenerated INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS index_recap_scripts_projectId_partNumber 
                    ON recap_scripts(projectId, partNumber)
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS recap_render_jobs (
                        jobId TEXT NOT NULL PRIMARY KEY,
                        projectId TEXT NOT NULL,
                        jobType TEXT NOT NULL,
                        stage TEXT NOT NULL,
                        status TEXT NOT NULL,
                        progress INTEGER NOT NULL,
                        createdTime INTEGER NOT NULL,
                        startedTime INTEGER,
                        finishedTime INTEGER,
                        errorReason TEXT,
                        failureDiagnosis TEXT,
                        outputReference TEXT,
                        outputSizeBytes INTEGER,
                        durationMs INTEGER,
                        log TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS index_recap_render_jobs_projectId 
                    ON recap_render_jobs(projectId)
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS index_recap_render_jobs_status 
                    ON recap_render_jobs(status)
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS chat_sessions (
                        id TEXT NOT NULL PRIMARY KEY,
                        title TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        model TEXT NOT NULL,
                        provider TEXT NOT NULL,
                        isPinned INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS chat_messages (
                        id TEXT NOT NULL PRIMARY KEY,
                        sessionId TEXT NOT NULL,
                        sender TEXT NOT NULL,
                        content TEXT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        status TEXT NOT NULL,
                        codeSnippet TEXT,
                        codeLanguage TEXT,
                        toolAttachment TEXT
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS index_chat_messages_sessionId 
                    ON chat_messages(sessionId)
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
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .build()
                .also { INSTANCE = it }
        }
    }
}
