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
        workDir: String = "/data/data/com.termux/files/home"
    ): TermuxBridge.Request {
        val args = listOf(
            "--voice", voice,
            "--text", text,
            "--write-media", outputFilePath
        )
        return TermuxBridge.Request(
            tool = "edge-tts",
            args = args,
            workDir = workDir,
            label = "Zip_Bug Edge TTS ($voice)"
        )
    }
}
