package com.zipbug.base.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EdgeTtsHelperTest {

    @Test
    fun testBuildRequestWithRateAndPitch() {
        val req = EdgeTtsHelper.buildRequest(
            text = "မင်္ဂလာပါ",
            outputFilePath = "/storage/emulated/0/Download/test.mp3",
            voice = EdgeTtsHelper.VOICE_NILAR,
            rate = "+15%",
            pitch = "+5Hz"
        )
        assertEquals("edge-tts", req.tool)
        assertTrue(req.args.contains("--voice"))
        assertTrue(req.args.contains("my-MM-NilarNeural"))
        assertTrue(req.args.contains("--text"))
        assertTrue(req.args.contains("မင်္ဂလာပါ"))
        assertTrue(req.args.contains("--rate"))
        assertTrue(req.args.contains("+15%"))
        assertTrue(req.args.contains("--pitch"))
        assertTrue(req.args.contains("+5Hz"))
    }

    @Test
    fun testChunkTextPreservesSentences() {
        val myanmarText = "ပထမဝါကျ ဖြစ်ပါသည်။။ ဒုတိယဝါကျ ဖြစ်ပါသည်။။ တတိယဝါကျ ဖြစ်ပါသည်။။ စတုတ္ထဝါကျ ဖြစ်ပါသည်။။"
        val chunks = EdgeTtsHelper.chunkText(myanmarText, maxChunkChars = 35)
        assertTrue(chunks.size >= 2)
        chunks.forEach { chunk ->
            assertTrue(chunk.isNotBlank())
            assertTrue(chunk.length <= 35)
        }
    }

    @Test
    fun testCreatePreviewTextEndsCleanly() {
        val longText = "ဒါဟာ မြန်မာစာသား အစမ်းလေ့လာမှု ဖြစ်ပါတယ်။ ဒီနေရာမှာ အသံဖိုင် စမ်းသပ်ထုတ်လုပ်ဖို့ ရည်ရွယ်ပြီး ရေးသားထားတာ ဖြစ်ပါတယ်။ အားလုံး အဆင်ပြေကြပါစေ။"
        val preview = EdgeTtsHelper.createPreviewText(longText, maxChars = 50)
        assertTrue(preview.length <= 55)
        assertTrue(preview.isNotBlank())
    }

    @Test
    fun testChunkTextHardSplitsSingleOversizedSentence() {
        val longWord = "က".repeat(180)
        val chunks = EdgeTtsHelper.chunkText(longWord, maxChunkChars = 40)

        assertTrue(chunks.size >= 4)
        assertTrue(chunks.all { it.isNotBlank() && it.length <= 40 })
        assertEquals(longWord, chunks.joinToString(""))
    }

    @Test(expected = IllegalArgumentException::class)
    fun testChunkTextRejectsUnsafeTinyChunkSize() {
        EdgeTtsHelper.chunkText("မင်္ဂလာပါ", maxChunkChars = 8)
    }

}
