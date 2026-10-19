package com.zipbug.base.data.recap

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RecapProjectDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(project: RecapProjectEntity)

    @Query("SELECT * FROM recap_projects WHERE id = :id LIMIT 1")
    suspend fun get(id: String): RecapProjectEntity?

    @Query("SELECT * FROM recap_projects WHERE id = :id LIMIT 1")
    fun observe(id: String): Flow<RecapProjectEntity?>

    @Query("SELECT * FROM recap_projects ORDER BY updatedAt DESC")
    fun getAll(): Flow<List<RecapProjectEntity>>

    @Query("SELECT * FROM recap_projects ORDER BY updatedAt DESC")
    suspend fun getAllSync(): List<RecapProjectEntity>

    @Query("UPDATE recap_projects SET name = :newName, updatedAt = :updatedAt WHERE id = :id")
    suspend fun rename(id: String, newName: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE recap_projects SET status = :status, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, updatedAt: Long = System.currentTimeMillis())

    @Query(
        """
        UPDATE recap_projects
        SET outputFilePath = :path,
            outputFileSize = :size,
            outputDurationMs = :durationMs,
            status = 'COMPLETED',
            updatedAt = :updatedAt
        WHERE id = :id
        """
    )
    suspend fun updateOutput(
        id: String,
        path: String?,
        size: Long?,
        durationMs: Long?,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query("DELETE FROM recap_projects WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface RecapClipDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(clip: RecapClipEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(clips: List<RecapClipEntity>)

    @Query("SELECT * FROM recap_clips WHERE projectId = :projectId ORDER BY orderIndex ASC")
    suspend fun getClipsForProject(projectId: String): List<RecapClipEntity>

    @Query("SELECT * FROM recap_clips WHERE projectId = :projectId ORDER BY orderIndex ASC")
    fun observeClipsForProject(projectId: String): Flow<List<RecapClipEntity>>

    @Query("DELETE FROM recap_clips WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM recap_clips WHERE projectId = :projectId")
    suspend fun deleteClipsForProject(projectId: String)
}

@Dao
interface RecapScriptDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(script: RecapScriptEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(scripts: List<RecapScriptEntity>)

    @Query("SELECT * FROM recap_scripts WHERE projectId = :projectId ORDER BY partNumber ASC")
    suspend fun getScriptsForProject(projectId: String): List<RecapScriptEntity>

    @Query("SELECT * FROM recap_scripts WHERE projectId = :projectId ORDER BY partNumber ASC")
    fun observeScriptsForProject(projectId: String): Flow<List<RecapScriptEntity>>

    @Query("SELECT * FROM recap_scripts WHERE projectId = :projectId AND partNumber = :partNumber LIMIT 1")
    suspend fun getScriptPart(projectId: String, partNumber: Int): RecapScriptEntity?

    @Query("DELETE FROM recap_scripts WHERE projectId = :projectId")
    suspend fun deleteScriptsForProject(projectId: String)
}

@Dao
interface RecapRenderJobDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(job: RecapRenderJobEntity)

    @Query("SELECT * FROM recap_render_jobs WHERE jobId = :jobId LIMIT 1")
    suspend fun get(jobId: String): RecapRenderJobEntity?

    @Query("SELECT * FROM recap_render_jobs WHERE jobId = :jobId LIMIT 1")
    fun observe(jobId: String): Flow<RecapRenderJobEntity?>

    @Query("SELECT * FROM recap_render_jobs WHERE projectId = :projectId ORDER BY createdTime DESC LIMIT 1")
    fun observeLatestForProject(projectId: String): Flow<RecapRenderJobEntity?>

    @Query("SELECT * FROM recap_render_jobs ORDER BY createdTime DESC")
    fun getAllJobs(): Flow<List<RecapRenderJobEntity>>

    @Query(
        """
        UPDATE recap_render_jobs
        SET stage = :stage,
            progress = :progress,
            log = log || :logChunk
        WHERE jobId = :jobId
        """
    )
    suspend fun updateStage(
        jobId: String,
        stage: String,
        progress: Int,
        logChunk: String = ""
    )

    @Query(
        """
        UPDATE recap_render_jobs
        SET status = :status,
            failureDiagnosis = :failureDiagnosis,
            errorReason = :errorReason,
            outputReference = :outputReference,
            outputSizeBytes = :outputSizeBytes,
            durationMs = :durationMs,
            finishedTime = :finishedTime
        WHERE jobId = :jobId
        """
    )
    suspend fun markFinished(
        jobId: String,
        status: String,
        failureDiagnosis: String? = null,
        errorReason: String? = null,
        outputReference: String? = null,
        outputSizeBytes: Long? = null,
        durationMs: Long? = null,
        finishedTime: Long = System.currentTimeMillis()
    )

    @Query(
        """
        UPDATE recap_render_jobs
        SET status = 'INTERRUPTED',
            errorReason = 'Process interrupted while application was not running',
            failureDiagnosis = 'WHAT FAILED: Render Execution\nWHY: App was terminated or restart occurred during rendering\nSAFE NEXT STEP: Reopen project and restart render\nRETRY: Retry available',
            finishedTime = :currentTime
        WHERE status IN ('RUNNING', 'QUEUED')
        """
    )
    suspend fun markStaleJobsInterrupted(currentTime: Long = System.currentTimeMillis())
}
