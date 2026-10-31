package com.zipbug.base.recap

import com.zipbug.base.data.recap.RecapScriptEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class SubtitleExporterTest {

    private val sampleScripts = listOf(
        RecapScriptEntity(
            id = "s1",
            projectId = "p1",
            partNumber = 1,
            title = "Hook",
            scriptText = "12345678901234567890"
        ),
        RecapScriptEntity(
            id = "s2",
            projectId = "p1",
            partNumber = 2,
            title = "Climax",
            scriptText = "12345678901234567890"
        )
    )

    @Test
    fun `toTxt formats structured parts cleanly`() {
        val txt = SubtitleExporter.toTxt(sampleScripts)
        assertTrue(txt.contains("estimated from text length"))
        assertTrue(txt.contains("[PART 1: Hook]"))
        assertTrue(txt.contains("[PART 2: Climax]"))
    }

    @Test
    fun `toSrt formats comma-separated timestamps and sequential indices`() {
        val srt = SubtitleExporter.toSrt(sampleScripts, totalDurationMs = 10_000L)
        assertTrue(srt.contains("1\n00:00:00,000 --> 00:00:05,000"))
        assertTrue(srt.contains("2\n00:00:05,000 --> 00:00:10,000"))
    }

    @Test
    fun `toVtt starts with WEBVTT header and dot-separated timestamps`() {
        val vtt = SubtitleExporter.toVtt(sampleScripts, totalDurationMs = 10_000L)
        assertTrue(vtt.startsWith("WEBVTT\n\n"))
        assertTrue(vtt.contains("00:00:00.000 --> 00:00:05.000"))
        assertTrue(vtt.contains("00:00:05.000 --> 00:00:10.000"))
    }

    @Test
    fun `exportToFile writes non-empty file and verifies byte length`() {
        val tempDir = Files.createTempDirectory("subtitle_test").toFile()
        try {
            val outFile = java.io.File(tempDir, "test.vtt")
            val res = SubtitleExporter.exportToFile(outFile, SubtitleExporter.FORMAT_VTT, sampleScripts, 10_000L)
            assertTrue(res.isSuccess)
            assertTrue(res.getOrThrow() > 20L)
            assertEquals(outFile.length(), res.getOrThrow())
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
