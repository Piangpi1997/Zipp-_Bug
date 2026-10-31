package com.zipbug.base.recap

import android.content.Context
import com.zipbug.base.data.recap.RecapScriptEntity
import com.zipbug.base.termux.TermuxBridge
import com.zipbug.base.tts.EdgeTtsHelper
import java.io.File

object RecapTtsCoordinator {

    fun buildVoiceoverRequest(
        script: RecapScriptEntity,
        outputFilePath: String,
        workDir: String = "/data/data/com.termux/files/home"
    ): Result<TermuxBridge.Request> {
        if (script.scriptText.isBlank()) {
            val diag = RecapFailureDiagnosis.missingVoiceover(script.partNumber, "Script text is empty")
            return Result.failure(IllegalArgumentException(diag.format()))
        }

        val request = EdgeTtsHelper.buildRequest(
            text = script.scriptText,
            outputFilePath = outputFilePath,
            voice = script.ttsVoice,
            rate = script.ttsSpeed,
            pitch = script.ttsPitch,
            workDir = workDir
        )

        return Result.success(request)
    }

    fun buildPreviewRequest(
        script: RecapScriptEntity,
        previewOutputFilePath: String,
        workDir: String = "/data/data/com.termux/files/home"
    ): Result<TermuxBridge.Request> {
        val snippet = EdgeTtsHelper.createPreviewText(script.scriptText)
        if (snippet.isBlank()) {
            return Result.failure(IllegalArgumentException("Preview snippet is empty"))
        }

        val request = EdgeTtsHelper.buildRequest(
            text = snippet,
            outputFilePath = previewOutputFilePath,
            voice = script.ttsVoice,
            rate = script.ttsSpeed,
            pitch = script.ttsPitch,
            workDir = workDir
        )

        return Result.success(request)
    }

    fun verifyAudioOutput(filePath: String): Result<Long> {
        val file = File(filePath)
        if (!file.exists()) {
            val diag = RecapFailureDiagnosis.outputVerificationFailed(filePath, exists = false, sizeBytes = 0L)
            return Result.failure(IllegalStateException(diag.format()))
        }
        val size = file.length()
        if (size <= 0L) {
            val diag = RecapFailureDiagnosis.outputVerificationFailed(filePath, exists = true, sizeBytes = size)
            return Result.failure(IllegalStateException(diag.format()))
        }
        return Result.success(size)
    }
}
