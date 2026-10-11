package com.zipbug.base.tts

import com.zipbug.base.termux.TermuxBridge

object EdgeTtsHelper {
    const val VOICE_NILAR = "my-MM-NilarNeural"
    const val VOICE_THIHA = "my-MM-ThihaNeural"
    const val VOICE_JENNY = "en-US-JennyNeural"

    val AVAILABLE_VOICES = listOf(
        VOICE_NILAR,
        VOICE_THIHA,
        VOICE_JENNY
    )

    fun buildRequest(
        text: String,
        outputFilePath: String,
        voice: String = VOICE_NILAR,
        rate: String? = null,
        pitch: String? = null,
        workDir: String = "/data/data/com.termux/files/home"
    ): TermuxBridge.Request {
        val args = mutableListOf(
            "--voice", voice,
            "--text", text,
            "--write-media", outputFilePath
        )
        if (!rate.isNullOrBlank()) {
            args.add("--rate")
            args.add(rate)
        }
        if (!pitch.isNullOrBlank()) {
            args.add("--pitch")
            args.add(pitch)
        }
        return TermuxBridge.Request(
            tool = "edge-tts",
            args = args,
            workDir = workDir,
            label = "Zip_Bug Edge TTS ($voice)"
        )
    }

    /**
     * Splits long text into chunks of at most [maxChunkChars], respecting sentence boundaries
     * (Myanmar sentence terminator "။" or newlines/periods).
     */
    fun chunkText(text: String, maxChunkChars: Int = 500): List<String> {
        if (text.length <= maxChunkChars) return listOf(text)
        val chunks = mutableListOf<String>()
        val sentences = text.split(Regex("(?<=[။\n.!?])"))
        var current = StringBuilder()
        for (sentence in sentences) {
            if (current.length + sentence.length > maxChunkChars && current.isNotEmpty()) {
                chunks.add(current.toString().trim())
                current = StringBuilder()
            }
            current.append(sentence)
        }
        if (current.isNotBlank()) {
            chunks.add(current.toString().trim())
        }
        return if (chunks.isEmpty()) listOf(text) else chunks
    }

    /**
     * Extracts a short preview snippet (up to [maxChars], ending cleanly).
     */
    fun createPreviewText(text: String, maxChars: Int = 120): String {
        val trimmed = text.trim()
        if (trimmed.length <= maxChars) return trimmed
        val cut = trimmed.take(maxChars)
        val lastSentenceEnd = cut.lastIndexOfAny(charArrayOf('။', '\n', '.'))
        return if (lastSentenceEnd > 20) {
            cut.substring(0, lastSentenceEnd + 1)
        } else {
            cut + "…"
        }
    }
}
