package com.zipbug.base.data.recap

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "recap_render_jobs",
    indices = [Index(value = ["projectId"]), Index(value = ["status"])]
)
data class RecapRenderJobEntity(
    @PrimaryKey val jobId: String,
    val projectId: String,
    val jobType: String = JOB_TYPE_FFMPEG_RECAP,
    val stage: String = STAGE_VALIDATION,
    val status: String = STATUS_QUEUED,
    val progress: Int = 0,
    val createdTime: Long = System.currentTimeMillis(),
    val startedTime: Long? = null,
    val finishedTime: Long? = null,
    val errorReason: String? = null,
    val failureDiagnosis: String? = null,
    val outputReference: String? = null,
    val outputSizeBytes: Long? = null,
    val durationMs: Long? = null,
    val log: String = ""
) {
    companion object {
        const val JOB_TYPE_FFMPEG_RECAP = "FFMPEG_RECAP_RENDER"

        // Explicit 8-stage pipeline
        const val STAGE_VALIDATION = "VALIDATION"
        const val STAGE_MEDIA_PREPARATION = "MEDIA_PREPARATION"
        const val STAGE_CLIP_PROCESSING = "CLIP_PROCESSING"
        const val STAGE_VOICEOVER = "VOICEOVER"
        const val STAGE_SUBTITLES = "SUBTITLES"
        const val STAGE_EFFECTS = "EFFECTS"
        const val STAGE_FINAL_ENCODE = "FINAL_ENCODE"
        const val STAGE_OUTPUT_VERIFICATION = "OUTPUT_VERIFICATION"

        val PIPELINE_STAGES = listOf(
            STAGE_VALIDATION,
            STAGE_MEDIA_PREPARATION,
            STAGE_CLIP_PROCESSING,
            STAGE_VOICEOVER,
            STAGE_SUBTITLES,
            STAGE_EFFECTS,
            STAGE_FINAL_ENCODE,
            STAGE_OUTPUT_VERIFICATION
        )

        // Job states
        const val STATUS_QUEUED = "QUEUED"
        const val STATUS_RUNNING = "RUNNING"
        const val STATUS_SUCCESS = "SUCCESS"
        const val STATUS_FAILED = "FAILED"
        const val STATUS_CANCELLED = "CANCELLED"
        const val STATUS_TIMEOUT = "TIMEOUT"
        const val STATUS_INTERRUPTED = "INTERRUPTED"
    }
}
