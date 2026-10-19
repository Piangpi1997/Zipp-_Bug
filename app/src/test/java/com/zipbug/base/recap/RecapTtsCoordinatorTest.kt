package com.zipbug.base.recap

import com.zipbug.base.data.recap.RecapScriptEntity
import com.zipbug.base.tts.EdgeTtsHelper
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class RecapTtsCoordinatorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testBuildVoiceoverRequestSuccess() {
        val script = RecapScriptEntity(
            id = "s1",
            projectId = "p1",
            partNumber = 1,
            title = "Intro",
            scriptText = "ရှေးရှေးတုန်းက ရွာငယ်လေးတစ်ရွာမှာ ဖြစ်ပျက်ခဲ့တဲ့ အဖြစ်အပျက်ပါ။",
            ttsVoice = EdgeTtsHelper.VOICE_NILAR,
            ttsSpeed = "+10%",
            ttsPitch = "+0Hz"
        )
        val reqResult = RecapTtsCoordinator.buildVoiceoverRequest(
            script = script,
            outputFilePath = "/tmp/out.mp3"
        )
        assertTrue(reqResult.isSuccess)
        val req = reqResult.getOrThrow()
        assertEquals("edge-tts", req.tool)
        assertTrue(req.args.contains("--voice"))
        assertTrue(req.args.contains(EdgeTtsHelper.VOICE_NILAR))
        assertTrue(req.args.contains("--rate"))
        assertTrue(req.args.contains("+10%"))
        assertTrue(req.args.contains("/tmp/out.mp3"))
    }

    @Test
    fun testBuildVoiceoverRequestRejectsBlankScript() {
        val blankScript = RecapScriptEntity(
            id = "s1",
            projectId = "p1",
            partNumber = 1,
            title = "Intro",
            scriptText = "   "
        )
        val reqResult = RecapTtsCoordinator.buildVoiceoverRequest(
            script = blankScript,
            outputFilePath = "/tmp/out.mp3"
        )
        assertTrue(reqResult.isFailure)
        assertTrue(reqResult.exceptionOrNull()!!.message!!.contains("Script text is empty"))
    }

    @Test
    fun testVerifyAudioOutputNonExistentFile() {
        val nonExistent = File(tempFolder.root, "does_not_exist.mp3")
        val res = RecapTtsCoordinator.verifyAudioOutput(nonExistent.absolutePath)
        assertTrue(res.isFailure)
        assertTrue(res.exceptionOrNull()!!.message!!.contains("does not exist"))
    }

    @Test
    fun testVerifyAudioOutputZeroBytesFile() {
        val zeroByte = tempFolder.newFile("empty.mp3")
        val res = RecapTtsCoordinator.verifyAudioOutput(zeroByte.absolutePath)
        assertTrue(res.isFailure)
        assertTrue(res.exceptionOrNull()!!.message!!.contains("0 bytes"))
    }

    @Test
    fun testVerifyAudioOutputValidFile() {
        val validFile = tempFolder.newFile("valid.mp3")
        validFile.writeBytes(ByteArray(1024) { 1 })
        val res = RecapTtsCoordinator.verifyAudioOutput(validFile.absolutePath)
        assertTrue(res.isSuccess)
        assertEquals(1024L, res.getOrThrow())
    }
}
