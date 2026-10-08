package com.zipbug.base.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EdgeTtsHelperTest {

    @Test
    fun buildRequestCreatesCorrectEdgeTtsArguments() {
        val request = EdgeTtsHelper.buildRequest(
            text = "မင်္ဂလာပါ",
            outputFilePath = "/storage/emulated/0/Download/test.mp3",
            voice = EdgeTtsHelper.VOICE_NILAR
        )

        assertEquals("edge-tts", request.tool)
        assertEquals(listOf("--voice", "my-MM-NilarNeural", "--text", "မင်္ဂလာပါ", "--write-media", "/storage/emulated/0/Download/test.mp3"), request.args)
        assertTrue(request.label.contains("my-MM-NilarNeural"))
    }

    @Test
    fun availableVoicesContainsBurmeseVoices() {
        assertTrue(EdgeTtsHelper.AVAILABLE_VOICES.contains("my-MM-NilarNeural"))
        assertTrue(EdgeTtsHelper.AVAILABLE_VOICES.contains("my-MM-ThihaNeural"))
    }
}
