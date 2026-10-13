package com.zipbug.base.recap

import com.zipbug.base.data.recap.RecapClipEntity
import java.util.UUID

object RecapTimelineManager {
    const val MIN_CLIPS = 1
    const val MAX_CLIPS = 18

    fun validateTimeline(clips: List<RecapClipEntity>): Result<Unit> {
        if (clips.size < MIN_CLIPS || clips.size > MAX_CLIPS) {
            val diag = RecapFailureDiagnosis.invalidClipCount(clips.size)
            return Result.failure(IllegalArgumentException(diag.format()))
        }

        for ((index, clip) in clips.withIndex()) {
            if (clip.startMs < 0) {
                val diag = RecapFailureDiagnosis.invalidTrimRange(index, clip.startMs, clip.endMs, clip.durationMs)
                return Result.failure(IllegalArgumentException("Clip #${index + 1}: startMs cannot be negative. " + diag.format()))
            }
            if (clip.startMs >= clip.endMs) {
                val diag = RecapFailureDiagnosis.invalidTrimRange(index, clip.startMs, clip.endMs, clip.durationMs)
                return Result.failure(IllegalArgumentException("Clip #${index + 1}: startMs (${clip.startMs}) must be strictly less than endMs (${clip.endMs}). " + diag.format()))
            }
            if (clip.durationMs > 0 && clip.endMs > clip.durationMs) {
                val diag = RecapFailureDiagnosis.invalidTrimRange(index, clip.startMs, clip.endMs, clip.durationMs)
                return Result.failure(IllegalArgumentException("Clip #${index + 1}: endMs (${clip.endMs}) exceeds clip duration (${clip.durationMs}). " + diag.format()))
            }
            if (!RecapClipEntity.SUPPORTED_EFFECTS.contains(clip.effectType)) {
                return Result.failure(IllegalArgumentException("Clip #${index + 1}: Unsupported effect '${clip.effectType}'"))
            }
        }

        return Result.success(Unit)
    }

    fun reorderClips(clips: List<RecapClipEntity>, fromIndex: Int, toIndex: Int): List<RecapClipEntity> {
        if (fromIndex !in clips.indices || toIndex !in clips.indices || fromIndex == toIndex) {
            return clips
        }
        val mutable = clips.toMutableList()
        val item = mutable.removeAt(fromIndex)
        mutable.add(toIndex, item)
        return mutable.mapIndexed { idx, clip ->
            clip.copy(orderIndex = idx)
        }
    }

    fun createInitialClips(
        projectId: String,
        sourcePath: String,
        totalDurationMs: Long,
        clipCount: Int = 4
    ): List<RecapClipEntity> {
        val safeCount = clipCount.coerceIn(MIN_CLIPS, MAX_CLIPS)
        val validDuration = if (totalDurationMs > 0) totalDurationMs else 60_000L
        val interval = validDuration / safeCount

        return (0 until safeCount).map { i ->
            val start = i * interval
            val end = if (i == safeCount - 1) validDuration else (i + 1) * interval
            RecapClipEntity(
                id = UUID.randomUUID().toString(),
                projectId = projectId,
                orderIndex = i,
                sourcePath = sourcePath,
                startMs = start,
                endMs = end,
                durationMs = validDuration,
                effectType = RecapClipEntity.EFFECT_NONE,
                effectParam = "",
                isEnabled = true
            )
        }
    }
}
