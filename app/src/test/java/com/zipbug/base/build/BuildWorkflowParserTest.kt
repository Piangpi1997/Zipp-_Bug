package com.zipbug.base.build

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class BuildWorkflowParserTest {

    @Test
    fun parsesStagesArtifactAndResult() {
        val output = """
            ZIPBUG_STAGE|VALIDATION|PASS|Project structure valid.
            ZIPBUG_STAGE|DEPENDENCIES|PASS|Dependencies ready.
            ZIPBUG_STAGE|BUILD|RUNNING|Gradle started.
            ZIPBUG_STAGE|BUILD|PASS|Gradle exited 0.
            ZIPBUG_STAGE|PACKAGING|PASS|APK validated and exported.
            ZIPBUG_ARTIFACT|/storage/emulated/0/Download/Test.apk|2048|aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa|PASS
            ZIPBUG_RESULT|SUCCESS|debug
        """.trimIndent()

        val parsed = BuildWorkflowParser.parse(output)

        assertEquals(
            "PASS",
            parsed.stage("validation")?.state
        )
        assertEquals(
            "PASS",
            parsed.stage("build")?.state
        )

        val artifact = parsed.artifact
        assertNotNull(artifact)
        assertEquals(
            "/storage/emulated/0/Download/Test.apk",
            artifact?.path
        )
        assertEquals(2048L, artifact?.sizeBytes)
        assertEquals("PASS", artifact?.signatureVerification)
        assertEquals("SUCCESS", parsed.result)
        assertEquals("debug", parsed.resultDetail)
    }

    @Test
    fun rejectsMalformedArtifactProof() {
        val parsed = BuildWorkflowParser.parse(
            "ZIPBUG_ARTIFACT|/tmp/app.apk|0|bad|PASS"
        )
        assertNull(parsed.artifact)
    }

    @Test
    fun keepsLatestStateForEachStage() {
        val parsed = BuildWorkflowParser.parse(
            """
            ZIPBUG_STAGE|BUILD|RUNNING|Started
            ZIPBUG_STAGE|BUILD|TIMEOUT|Exceeded limit
            """.trimIndent()
        )

        assertEquals(
            "TIMEOUT",
            parsed.stage("BUILD")?.state
        )
    }
}
