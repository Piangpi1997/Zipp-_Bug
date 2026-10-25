package com.zipbug.base.data.recap

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recap_projects")
data class RecapProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val sourceUri: String? = null,
    val sourceFileName: String? = null,
    val sourceLocalPath: String? = null,
    val sourceDurationMs: Long = 0L,
    val language: String = "my", // "my" (Myanmar), "en" (English), "th" (Thai), "zh" (Chinese)
    val recapMode: String = "Recap", // "Recap", "Recap + Dialogue", "Dub"
    val status: String = STATUS_DRAFT,
    val outputFilePath: String? = null,
    val outputFileSize: Long? = null,
    val outputDurationMs: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val STATUS_DRAFT = "DRAFT"
        const val STATUS_READY = "READY"
        const val STATUS_RENDERING = "RENDERING"
        const val STATUS_COMPLETED = "COMPLETED"
        const val STATUS_FAILED = "FAILED"

        const val MODE_RECAP = "Recap"
        const val MODE_RECAP_DIALOGUE = "Recap + Dialogue"
        const val MODE_DUB = "Dub"

        const val LANG_MYANMAR = "my"
        const val LANG_ENGLISH = "en"
        const val LANG_THAI = "th"
        const val LANG_CHINESE = "zh"
    }
}
