package com.zipbug.base.recap

import com.zipbug.base.ai.AiMode
import com.zipbug.base.ai.AiProvider
import com.zipbug.base.ai.AiRepository
import com.zipbug.base.ai.AiRequest
import com.zipbug.base.data.recap.RecapProjectEntity
import com.zipbug.base.data.recap.RecapScriptEntity
import com.zipbug.base.termux.LogSanitizer
import com.zipbug.base.tts.EdgeTtsHelper
import java.util.UUID

object RecapScriptGenerator {

    fun buildPrompt(
        transcriptOrTopic: String,
        mode: String,
        targetLanguage: String
    ): String {
        val langLabel = if (targetLanguage.equals(RecapProjectEntity.LANG_ENGLISH, ignoreCase = true)) {
            "English"
        } else {
            "Myanmar (Burmese)"
        }

        val modeGuidance = when (mode) {
            RecapProjectEntity.MODE_RECAP_DIALOGUE ->
                "Include key dialogue and character quotes where present in the source transcript, marked with speaker labels."
            RecapProjectEntity.MODE_DUB ->
                "Format as a dramatic dubbing voiceover script, timed for conversational delivery."
            else ->
                "Focus on concise, engaging storytelling recap preserving essential plot points and facts."
        }

        return """
You are an expert video recap screenwriter.
Create a structured 4-part video recap script in $langLabel.
Script style: $mode ($modeGuidance)

Rules:
1. Divide the script into exactly 4 parts:
   [PART 1: Hook & Introduction]
   [PART 2: Rising Action & Development]
   [PART 3: Climax & Turning Point]
   [PART 4: Resolution & Conclusion]
2. Language: Write entirely in $langLabel.
3. Keep each part suitable for 15-45 seconds of voiceover narration.
4. Do not include markdown codeblocks or meta commentary.

Source content / Transcript:
$transcriptOrTopic
        """.trimIndent()
    }

    suspend fun generateScript(
        aiRepository: AiRepository,
        provider: AiProvider,
        apiKey: String,
        model: String,
        transcriptOrTopic: String,
        mode: String,
        targetLanguage: String,
        projectId: String
    ): Result<List<RecapScriptEntity>> {
        if (apiKey.isBlank()) {
            val diag = RecapFailureDiagnosis.missingApiKey(provider.label)
            return Result.failure(IllegalStateException(diag.format()))
        }

        val sanitizedInput = LogSanitizer.sanitize(transcriptOrTopic)
        val prompt = buildPrompt(sanitizedInput, mode, targetLanguage)

        val request = AiRequest(
            provider = provider,
            apiKey = apiKey,
            model = model.ifBlank { provider.defaultModel },
            system = "You are a professional video recap scriptwriter. Always partition script output into exactly 4 parts marked with [PART 1], [PART 2], [PART 3], and [PART 4].",
            prompt = prompt,
            mode = AiMode.CHAT
        )

        val responseResult = aiRepository.complete(request)
        if (responseResult.isFailure) {
            val errorMsg = responseResult.exceptionOrNull()?.message ?: "Unknown AI completion failure"
            val sanitizedError = LogSanitizer.sanitize(errorMsg)
            return Result.failure(RuntimeException("AI completion failed: $sanitizedError"))
        }

        val outputText = LogSanitizer.sanitize(responseResult.getOrThrow())
        val parts = parsePartsFromOutput(projectId, outputText, targetLanguage)
        return Result.success(parts)
    }

    fun parsePartsFromOutput(
        projectId: String,
        output: String,
        targetLanguage: String
    ): List<RecapScriptEntity> {
        val defaultVoice = if (targetLanguage.equals(RecapProjectEntity.LANG_ENGLISH, ignoreCase = true)) {
            EdgeTtsHelper.VOICE_JENNY
        } else {
            EdgeTtsHelper.VOICE_NILAR
        }

        val regex = Regex("\\[PART\\s*([1-4])[:\\s]*(.*?)\\]([\\s\\S]*?)(?=\\[PART\\s*[1-4]|$)", RegexOption.IGNORE_CASE)
        val matches = regex.findAll(output).toList()

        if (matches.size >= 4) {
            return matches.take(4).mapIndexed { index, match ->
                val partNum = index + 1
                val title = match.groupValues[2].trim().ifBlank { defaultTitleForPart(partNum) }
                val content = match.groupValues[3].trim()
                RecapScriptEntity(
                    id = UUID.randomUUID().toString(),
                    projectId = projectId,
                    partNumber = partNum,
                    title = title,
                    scriptText = content,
                    ttsVoice = defaultVoice,
                    isGenerated = true
                )
            }
        }

        // Fallback: split text cleanly into 4 approximately equal paragraphs
        val paragraphs = output.split(Regex("\n\n+")).filter { it.isNotBlank() }
        val chunks = if (paragraphs.size >= 4) {
            val step = paragraphs.size / 4
            (0 until 4).map { i ->
                val from = i * step
                val to = if (i == 3) paragraphs.size else (i + 1) * step
                paragraphs.subList(from, to).joinToString("\n\n")
            }
        } else {
            EdgeTtsHelper.chunkText(output, maxChunkChars = (output.length / 4).coerceAtLeast(100)).take(4).let { list ->
                if (list.size < 4) {
                    val extended = list.toMutableList()
                    while (extended.size < 4) {
                        extended.add("")
                    }
                    extended
                } else list
            }
        }

        return (1..4).map { partNum ->
            RecapScriptEntity(
                id = UUID.randomUUID().toString(),
                projectId = projectId,
                partNumber = partNum,
                title = defaultTitleForPart(partNum),
                scriptText = chunks.getOrElse(partNum - 1) { "" }.trim(),
                ttsVoice = defaultVoice,
                isGenerated = true
            )
        }
    }

    private fun defaultTitleForPart(partNumber: Int): String = when (partNumber) {
        1 -> "Hook & Introduction"
        2 -> "Rising Action"
        3 -> "Climax & Turning Point"
        4 -> "Resolution & Conclusion"
        else -> "Part $partNumber"
    }
}
