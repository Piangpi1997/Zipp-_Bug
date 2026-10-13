package com.zipbug.base.data.recap

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.zipbug.base.tts.EdgeTtsHelper

@Entity(
    tableName = "recap_scripts",
    indices = [Index(value = ["projectId", "partNumber"], unique = true)]
)
data class RecapScriptEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val partNumber: Int, // 1 to 4
    val title: String,
    val scriptText: String = "",
    val ttsVoice: String = EdgeTtsHelper.VOICE_NILAR,
    val ttsSpeed: String = "+0%",
    val ttsPitch: String = "+0Hz",
    val ttsAudioPath: String? = null,
    val ttsAudioSize: Long? = null,
    val isGenerated: Boolean = false
) {
    companion object {
        const val MIN_PART = 1
        const val MAX_PART = 4
    }
}
