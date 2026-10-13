package com.zipbug.base.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "build_jobs")
data class BuildJobEntity(
    @PrimaryKey val id: String,
    val tool: String,
    val argsJson: String,
    val workDir: String,
    val status: String,
    val stdout: String = "",
    val stderr: String = "",
    val exitCode: Int? = null,
    val errorCode: Int? = null,
    val errorMessage: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val startedAt: Long? = null,
    val finishedAt: Long? = null
) {
    companion object {
        const val QUEUED = "QUEUED"
        const val RUNNING = "RUNNING"
        const val SUCCESS = "SUCCESS"
        const val FAILED = "FAILED"
    }
}
