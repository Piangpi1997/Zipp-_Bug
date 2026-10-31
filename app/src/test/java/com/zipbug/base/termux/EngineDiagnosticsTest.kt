package com.zipbug.base.termux

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineDiagnosticsTest {

    @Test
    fun initialChecksHas15Items() {
        val checks = EngineDiagnostics.createInitialChecks()
        assertEquals(15, checks.size)
        assertTrue(checks.any { it.id == "termux_installed" })
        assertTrue(checks.any { it.id == "permission_run_command" })
        assertTrue(checks.any { it.id == "allow_external_apps" })
        assertTrue(checks.any { it.id == "sdk34_available" })
    }

    @Test
    fun parseDiagnosticOutputParsesJsonCorrectly() {
        val checks = EngineDiagnostics.createInitialChecks()
        val sampleOutput = """
        Some banner text
        JSON_START
        {
            "allow_external_apps": true,
            "java": true,
            "java_detail": "openjdk 17.0.1",
            "java_17_plus": true,
            "gradle": true,
            "gradle_detail": "Gradle 8.9",
            "python": true,
            "python_detail": "/data/data/com.termux/files/usr/bin/python",
            "aapt2": true,
            "aapt2_detail": "/data/data/com.termux/files/usr/bin/aapt2",
            "sdk34": true,
            "sdk34_detail": "Platforms 34 found",
            "ffmpeg": true,
            "ffmpeg_detail": "ffmpeg 6.1",
            "yt_dlp": true,
            "yt_dlp_detail": "2024.08",
            "edge_tts": true,
            "edge_tts_detail": "6.1.12"
        }
        JSON_END
        """.trimIndent()

        val report = EngineDiagnostics.parseDiagnosticOutput(sampleOutput, checks)
        val allowExt = report.checks.first { it.id == "allow_external_apps" }
        assertEquals(DiagnosticStatus.PASS, allowExt.status)
        assertEquals("allow-external-apps=true verified", allowExt.detail)

        val javaCheck = report.checks.first { it.id == "java_available" }
        assertEquals(DiagnosticStatus.PASS, javaCheck.status)

        val aapt2Check = report.checks.first { it.id == "aapt2_available" }
        assertEquals(DiagnosticStatus.PASS, aapt2Check.status)
    }

    @Test
    fun reportFailsWhenMandatoryCheckMissing() {
        val checks = EngineDiagnostics.createInitialChecks()
        val sampleOutput = """
        JSON_START
        {
            "allow_external_apps": false,
            "java": false,
            "sdk34": false
        }
        JSON_END
        """.trimIndent()

        val report = EngineDiagnostics.parseDiagnosticOutput(sampleOutput, checks)
        assertFalse(report.allMandatoryPassed)
        assertTrue(report.summary.contains("NOT READY"))
    }
}
