package com.zipbug.base.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "apk_artifacts")
data class ApkArtifactEntity(
    @PrimaryKey val id: String, // SHA-256 or canonical file path
    val filePath: String,
    val fileName: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val minSdk: Int,
    val targetSdk: Int,
    val sizeBytes: Long,
    val sha256: String,
    val signerCertificateSha256: String?,
    val discoveredAt: Long = System.currentTimeMillis()
)
