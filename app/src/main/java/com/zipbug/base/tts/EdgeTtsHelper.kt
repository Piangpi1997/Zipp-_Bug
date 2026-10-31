package com.zipbug.base.tts

import com.zipbug.base.termux.TermuxBridge

object EdgeTtsHelper {
    const val VOICE_NILAR = "my-MM-NilarNeural"
    const val VOICE_THIHA = "my-MM-ThihaNeural"
    const val VOICE_JENNY = "en-US-JennyNeural"
    const val VOICE_PREMWADEE = "th-TH-PremwadeeNeural"
    const val VOICE_XIAOXIAO = "zh-CN-XiaoxiaoNeural"

    val AVAILABLE_VOICES = listOf(
        VOICE_NILAR,
        VOICE_THIHA,
        VOICE_JENNY,
        VOICE_PREMWADEE,
        VOICE_XIAOXIAO
    )

    fun defaultVoiceForLanguage(langCode: String): String = when (langCode.lowercase()) {
        "en" -> VOICE_JENNY
        "th" -> VOICE_PREMWADEE
        "zh" -> VOICE_XIAOXIAO
        else -> VOICE_NILAR
    }

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
        require(maxChunkChars >= 32) {
            "TTS chunk size must be at least 32 characters"
        }

        val trimmed = text.trim()
        if (trimmed.isEmpty()) return emptyList()
        if (trimmed.length <= maxChunkChars) return listOf(trimmed)

        val chunks = mutableListOf<String>()
        var current = StringBuilder()

        fun flushCurrent() {
            val value = current.toString().trim()
            if (value.isNotEmpty()) chunks += value
            current = StringBuilder()
        }

        fun appendBounded(segment: String) {
            var remaining = segment.trim()
            while (remaining.isNotEmpty()) {
                val capacity = maxChunkChars - current.length
                if (capacity <= 0) {
                    flushCurrent()
                    continue
                }

                if (remaining.length <= capacity) {
                    current.append(remaining)
                    break
                }

                if (current.isNotEmpty()) {
                    flushCurrent()
                    continue
                }

                val window = remaining.take(maxChunkChars + 1)
                val preferredBreak = window
                    .lastIndexOfAny(
                        charArrayOf(' ', '။', '.', '!', '?', '\n', ',')
                    )
                    .takeIf { it >= maxChunkChars / 2 }
                    ?: maxChunkChars

                val cut = preferredBreak.coerceIn(1, maxChunkChars)
                val piece = remaining.substring(0, cut).trim()
                if (piece.isNotEmpty()) chunks += piece
                remaining = remaining.substring(cut).trimStart()
            }
        }

        text.split(Regex("(?<=[။\\n.!?。！？])")).forEach { sentence ->
            val clean = sentence.trim()
            if (clean.isEmpty()) return@forEach

            val separator = if (current.isEmpty()) "" else " "
            if (
                current.length +
                    separator.length +
                    clean.length <= maxChunkChars
            ) {
                current.append(separator).append(clean)
            } else {
                flushCurrent()
                appendBounded(clean)
            }
        }

        flushCurrent()
        return chunks
    }

    /**
     * Extracts a short preview snippet (up to [maxChars], ending cleanly).
     */
    fun createPreviewText(text: String, maxChars: Int = 120): String {
        val trimmed = text.trim()
        if (trimmed.length <= maxChars) return trimmed
        val cut = trimmed.take(maxChars)
        val lastSentenceEnd = cut.lastIndexOfAny(charArrayOf('။', '\n', '.', '。'))
        return if (lastSentenceEnd > 20) {
            cut.substring(0, lastSentenceEnd + 1)
        } else {
            cut + "…"
        }
    }
}
