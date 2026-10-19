package com.zipbug.base.data.recap

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "recap_clips",
    indices = [Index(value = ["projectId", "orderIndex"])]
)
data class RecapClipEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val orderIndex: Int,
    val sourcePath: String,
    val startMs: Long,
    val endMs: Long,
    val durationMs: Long,
    val effectType: String = EFFECT_NONE,
    val effectParam: String = "",
    val isEnabled: Boolean = true
) {
    companion object {
        const val MAX_CLIPS_PER_PROJECT = 18

        const val EFFECT_NONE = "NONE"
        const val EFFECT_FREEZE_ZOOM = "FREEZE_ZOOM"
        const val EFFECT_TRANSITION = "TRANSITION"
        const val EFFECT_BLUR = "BLUR"
        const val EFFECT_LOGO_OVERLAY = "LOGO_OVERLAY"
        const val EFFECT_SUBTITLES = "SUBTITLES"

        val SUPPORTED_EFFECTS = listOf(
            EFFECT_NONE,
            EFFECT_FREEZE_ZOOM,
            EFFECT_TRANSITION,
            EFFECT_BLUR,
            EFFECT_LOGO_OVERLAY,
            EFFECT_SUBTITLES
        )
    }
}
