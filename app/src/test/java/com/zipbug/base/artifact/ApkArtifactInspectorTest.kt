package com.zipbug.base.artifact

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ApkArtifactInspectorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun calculateSha256OfSampleFile() {
        val sample = tempFolder.newFile("sample.apk")
        sample.writeText("sample apk content", Charsets.UTF_8)

        val hash = ApkArtifactInspector.calculateSha256(sample)
        assertTrue(hash.isNotEmpty())
        assertEquals(64, hash.length)
    }

    @Test
    fun hashBytesSha256ReturnsConsistentHash() {
        val data = "test-bytes".toByteArray(Charsets.UTF_8)
        val hash1 = ApkArtifactInspector.hashBytesSha256(data)
        val hash2 = ApkArtifactInspector.hashBytesSha256(data)
        assertEquals(hash1, hash2)
        assertEquals(64, hash1.length)
    }
}
