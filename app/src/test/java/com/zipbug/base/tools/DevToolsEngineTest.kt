package com.zipbug.base.tools

import com.zipbug.base.data.ProjectEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class DevToolsEngineTest {

    @Test
    fun `validateAndFormatJson formats valid object and minifyJson compacts it`() {
        val raw = """{"name": "Zip_Bug", "version": 2, "features": ["workspace", "devtools"]}"""
        val formatted = DevToolsEngine.validateAndFormatJson(raw, indentSpaces = 2)
        assertTrue(formatted.isSuccess)
        assertTrue(formatted.getOrThrow().contains("\n"))

        val minified = DevToolsEngine.minifyJson(formatted.getOrThrow())
        assertTrue(minified.isSuccess)
        assertFalse(minified.getOrThrow().contains("\n"))
        assertTrue(minified.getOrThrow().contains("\"name\":\"Zip_Bug\""))
    }

    @Test
    fun `validateAndFormatJson rejects malformed JSON`() {
        val invalid = """{"name": "Zip_Bug", "missingClose": [1, 2}"""
        val result = DevToolsEngine.validateAndFormatJson(invalid)
        assertTrue(result.isFailure)
    }

    @Test
    fun `computeHashes calculates accurate SHA256 SHA1 and MD5 digests`() {
        val bytes = "Zip_Bug Studio Verification Payload".toByteArray(Charsets.UTF_8)
        val result = DevToolsEngine.computeHashes(ByteArrayInputStream(bytes))
        assertEquals(bytes.size.toLong(), result.sizeBytes)
        assertEquals(64, result.sha256.length)
        assertEquals(40, result.sha1.length)
        assertEquals(32, result.md5.length)
    }

    @Test
    fun `inspectZip detects unsafe path traversal entries without extracting`() {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            zos.putNextEntry(ZipEntry("www/index.html"))
            zos.write("<h1>Safe</h1>".toByteArray())
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("../../etc/passwd"))
            zos.write("unsafe".toByteArray())
            zos.closeEntry()
        }

        val inspectResult = DevToolsEngine.inspectZip(ByteArrayInputStream(baos.toByteArray()))
        assertTrue(inspectResult.isSuccess)
        val report = inspectResult.getOrThrow()
        assertEquals(2, report.entryCount)
        assertTrue(report.hasUnsafeEntries)
        assertTrue(report.entries.first { it.name == "www/index.html" }.isSafePath)
        assertFalse(report.entries.first { it.name == "../../etc/passwd" }.isSafePath)
    }

    @Test
    fun `backup and restore roundtrip preserves project files and validates formatVersion 1`() {
        val tempRoot = Files.createTempDirectory("zipbug_backup_test").toFile()
        try {
            val srcProjectsDir = File(tempRoot, "src_projects").apply { mkdirs() }
            val projDir = File(srcProjectsDir, "proj-1").apply { mkdirs() }
            File(projDir, "www").mkdirs()
            File(projDir, "www/index.html").writeText("<!doctype html><title>Backup Test</title>")

            val project = ProjectEntity(
                id = "proj-1",
                name = "Backup Test App",
                kind = "web-mini-app",
                rootPath = projDir.canonicalPath,
                entryFile = "www/index.html"
            )

            val backupBaos = ByteArrayOutputStream()
            val manifestResult = DevToolsEngine.createBackupZip(listOf(project), backupBaos)
            assertTrue(manifestResult.isSuccess)
            val manifest = manifestResult.getOrThrow()
            assertEquals(1, manifest.formatVersion)
            assertEquals(1, manifest.projectCount)
            assertEquals(1, manifest.totalFiles)

            // Restore into a clean directory
            val dstProjectsDir = File(tempRoot, "dst_projects").apply { mkdirs() }
            val restoreResult = DevToolsEngine.validateAndRestoreBackupZip(
                dstProjectsDir,
                ByteArrayInputStream(backupBaos.toByteArray())
            )
            assertTrue(restoreResult.isSuccess)
            val summary = restoreResult.getOrThrow()
            assertEquals(1, summary.formatVersion)
            assertEquals(1, summary.restoredProjects.size)
            val restoredProj = summary.restoredProjects.first()
            assertEquals("Backup Test App", restoredProj.name)
            val restoredHtml = File(restoredProj.rootPath, "www/index.html")
            assertTrue(restoredHtml.isFile)
            assertTrue(restoredHtml.readText().contains("Backup Test"))
        } finally {
            tempRoot.deleteRecursively()
        }
    }
}
