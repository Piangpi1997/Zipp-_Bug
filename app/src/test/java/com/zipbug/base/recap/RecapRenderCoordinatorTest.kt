package com.zipbug.base.recap

import com.zipbug.base.data.recap.RecapClipEntity
import com.zipbug.base.data.recap.RecapScriptEntity
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.UUID

class RecapRenderCoordinatorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun sampleClips(): List<RecapClipEntity> = listOf(
        RecapClipEntity(UUID.randomUUID().toString(), "p1", 0, "/tmp/src.mp4", 0L, 5000L, 20000L, RecapClipEntity.EFFECT_NONE),
        RecapClipEntity(UUID.randomUUID().toString(), "p1", 1, "/tmp/src.mp4", 5000L, 10000L, 20000L, RecapClipEntity.EFFECT_FREEZE_ZOOM),
        RecapClipEntity(UUID.randomUUID().toString(), "p1", 2, "/tmp/src.mp4", 10000L, 15000L, 20000L, RecapClipEntity.EFFECT_BLUR),
        RecapClipEntity(UUID.randomUUID().toString(), "p1", 3, "/tmp/src.mp4", 15000L, 20000L, 20000L, RecapClipEntity.EFFECT_NONE)
    )

    @Test
    fun testBuildFfmpegArgsStructuredAndSafe() {
        val clips = sampleClips()
        val argsResult = RecapRenderCoordinator.buildFfmpegArgs(
            clips = clips,
            ttsAudioPath = "/tmp/audio.mp3",
            outputFilePath = "/tmp/final_recap.mp4"
        )

        assertTrue("Args should be generated successfully", argsResult.isSuccess)
        val args = argsResult.getOrThrow()

        // Verify key FFmpeg arguments are present as individual tokens
        assertTrue(args.contains("-y"))
        assertTrue(args.contains("-i"))
        assertTrue(args.contains("-filter_complex"))
        assertTrue(args.contains("-c:v"))
        assertTrue(args.contains("libx264"))
        assertTrue(args.contains("-c:a"))
        assertTrue(args.contains("aac"))
        assertTrue(args.contains("/tmp/final_recap.mp4"))

        // Ensure zero raw command operators that would indicate loose shell concatenation
        for (arg in args) {
            assertFalse("Argument should not contain dangerous shell chaining: $arg", arg.contains("&&") || arg.contains("||"))
            assertFalse("Argument should not contain command substitution: $arg", arg.contains("$(") || arg.contains("`"))
        }
    }

    @Test
    fun testGenerateSrtContent() {
        val scripts = listOf(
            RecapScriptEntity("s1", "p1", 1, "Intro", "အစပိုင်း ဇာတ်ကွက်။"),
            RecapScriptEntity("s2", "p1", 2, "Action", "အလယ်ပိုင်း ရင်ခုန်စရာ အခန်း။"),
            RecapScriptEntity("s3", "p1", 3, "Climax", "အမြင့်ဆုံး အလှည့်အပြောင်း။"),
            RecapScriptEntity("s4", "p1", 4, "End", "ဇာတ်သိမ်းပိုင်း။")
        )
        val clips = sampleClips()
        val srt = RecapRenderCoordinator.generateSrtContent(scripts, clips)

        assertTrue(srt.contains("1\n"))
        assertTrue(srt.contains("-->"))
        assertTrue(srt.contains("အစပိုင်း ဇာတ်ကွက်။"))
        assertTrue(srt.contains("ဇာတ်သိမ်းပိုင်း။"))
    }

    @Test
    fun testVerifyRenderResultNonZeroExitCode() {
        val res = RecapRenderCoordinator.verifyRenderResult(exitCode = 1, outputFilePath = "/tmp/video.mp4")
        assertTrue("Exit code 1 must fail verification", res.isFailure)
        assertTrue(res.exceptionOrNull()!!.message!!.contains("exit code 1"))
    }

    @Test
    fun testVerifyRenderResultMissingOutputFile() {
        val missing = File(tempFolder.root, "not_there.mp4")
        val res = RecapRenderCoordinator.verifyRenderResult(exitCode = 0, outputFilePath = missing.absolutePath)
        assertTrue("Missing file must fail verification even with exit code 0", res.isFailure)
        assertTrue(res.exceptionOrNull()!!.message!!.contains("does not exist"))
    }

    @Test
    fun testVerifyRenderResultEmptyOutputFile() {
        val empty = tempFolder.newFile("empty_video.mp4")
        val res = RecapRenderCoordinator.verifyRenderResult(exitCode = 0, outputFilePath = empty.absolutePath)
        assertTrue("Zero-byte file must fail verification even with exit code 0", res.isFailure)
        assertTrue(res.exceptionOrNull()!!.message!!.contains("0 bytes"))
    }

    @Test
    fun testVerifyRenderResultSuccess() {
        val valid = tempFolder.newFile("rendered_video.mp4")
        valid.writeBytes(ByteArray(8192) { 42 })

        val res = RecapRenderCoordinator.verifyRenderResult(exitCode = 0, outputFilePath = valid.absolutePath)
        assertTrue("Valid file with exit code 0 must pass verification", res.isSuccess)
        val info = res.getOrThrow()
        assertEquals(valid.name, info.fileName)
        assertEquals(8192L, info.sizeBytes)
        assertTrue(info.verified)
    }

    @Test
    fun testRecapFailureDiagnosisFormatting() {
        val diag = RecapFailureDiagnosis(
            whatFailed = "FFmpeg Video Render",
            why = "Codec not supported",
            safeNextStep = "Select H.264 compatible source",
            canRetry = true
        )
        val formatted = diag.format()
        assertTrue(formatted.contains("WHAT FAILED: FFmpeg Video Render"))
        assertTrue(formatted.contains("WHY: Codec not supported"))
        assertTrue(formatted.contains("SAFE NEXT STEP: Select H.264 compatible source"))
        assertTrue(formatted.contains("RETRY: Available"))
    }
}
