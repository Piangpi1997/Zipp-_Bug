package com.zipbug.base.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BuildJobDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(job: BuildJobEntity)

    @Query("SELECT * FROM build_jobs ORDER BY createdAt DESC LIMIT 1")
    fun observeLatest(): Flow<BuildJobEntity?>

    @Query("SELECT * FROM build_jobs WHERE id = :id LIMIT 1")
    suspend fun get(id: String): BuildJobEntity?

    @Query("SELECT * FROM build_jobs WHERE id = :id LIMIT 1")
    fun observe(id: String): Flow<BuildJobEntity?>

    @Query(
        """
        UPDATE build_jobs
        SET status = :status,
            startedAt = :startedAt
        WHERE id = :id
        """
    )
    suspend fun markStarted(
        id: String,
        status: String = BuildJobEntity.RUNNING,
        startedAt: Long = System.currentTimeMillis()
    )

    @Query(
        """
        UPDATE build_jobs
        SET status = :status,
            stdout = :stdout,
            stderr = :stderr,
            exitCode = :exitCode,
            errorCode = :errorCode,
            errorMessage = :errorMessage,
            finishedAt = :finishedAt
        WHERE id = :id
        """
    )
    suspend fun finish(
        id: String,
        status: String,
        stdout: String,
        stderr: String,
        exitCode: Int?,
        errorCode: Int?,
        errorMessage: String,
        finishedAt: Long = System.currentTimeMillis()
    )
}
