package com.zipbug.base.data.recap

/**
 * Standalone-JVM stub (no Room annotations) used ONLY to compile
 * SubtitleExporter.kt outside Android for the render-smoke / unit-test
 * harness. Never shipped in the APK (it lives under scripts/, not app/src).
 */
data class RecapScriptEntity(
    val partNumber: Int = 0,
    val title: String = "",
    val scriptText: String = ""
)
