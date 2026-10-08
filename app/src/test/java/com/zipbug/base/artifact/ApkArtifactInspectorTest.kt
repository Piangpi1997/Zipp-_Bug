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

    @Test
    fun installSafetyStatusEvaluation() {
        val dummyFile = tempFolder.newFile("test.apk")
        val newInstallMeta = ApkMetadata(
            file = dummyFile,
            packageName = "com.test.app",
            versionName = "1.0.0",
            versionCode = 1L,
            minSdk = 24,
            targetSdk = 34,
            sizeBytes = 100L,
            sha256 = "dummy-sha",
            signerCertificateSha256 = "signer1",
            isInstalled = false
        )
        assertEquals(InstallSafetyStatus.NEW_INSTALL, newInstallMeta.safetyStatus)

        val safeUpdateMeta = newInstallMeta.copy(
            isInstalled = true,
            installedVersionName = "0.9.0",
            installedSignerSha256 = "signer1",
            signerConflict = false
        )
        assertEquals(InstallSafetyStatus.SAFE_UPDATE, safeUpdateMeta.safetyStatus)

        val conflictMeta = newInstallMeta.copy(
            isInstalled = true,
            installedVersionName = "0.9.0",
            installedSignerSha256 = "signer2",
            signerConflict = true
        )
        assertEquals(InstallSafetyStatus.SIGNER_CONFLICT, conflictMeta.safetyStatus)
        assertEquals(
            "Same package name is already installed but signed with a different key.",
            conflictMeta.conflictExplanation
        )
    }

    @Test
    fun apkArtifactScannerFindsNonstandardModuleApks() {
        val root = tempFolder.newFolder("project_root")
        val customModule = tempFolder.newFolder("project_root", "feature_chat", "build", "outputs", "apk", "debug")
        val apkFile = java.io.File(customModule, "feature-chat-debug.apk")
        apkFile.writeText("content")

        val gitDir = tempFolder.newFolder("project_root", ".git")
        val ignoredApk = java.io.File(gitDir, "ignored.apk")
        ignoredApk.writeText("ignored")

        val found = ApkArtifactScanner.findApkFiles(listOf(root))
        assertEquals(1, found.size)
        assertEquals("feature-chat-debug.apk", found[0].name)
    }
}
