package com.zipbug.base.project

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ZipImporterTest {

    @Test
    fun resolveInsideAllowsNormalProjectPaths() {
        val root = Files.createTempDirectory("zipbug-safe").toFile()

        try {
            val resolved = ZipImporter.resolveInside(
                root = root,
                relativePath = "folder/file.txt",
                label = "ZIP entry"
            )

            val prefix = root.canonicalPath + File.separator
            assertTrue(resolved.canonicalPath.startsWith(prefix))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun resolveInsideRejectsParentTraversal() {
        val root = Files.createTempDirectory("zipbug-traversal").toFile()

        try {
            ZipImporter.resolveInside(
                root = root,
                relativePath = "../escape.txt",
                label = "ZIP entry"
            )
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun resolveInsideNormalizesRootWithoutEscaping() {
        val root = Files.createTempDirectory("zipbug-root").toFile()

        try {
            val resolved = ZipImporter.resolveInside(
                root = root,
                relativePath = ".",
                label = "ZIP entry"
            )

            assertEquals(root.canonicalPath, resolved.canonicalPath)
        } finally {
            root.deleteRecursively()
        }
    }
}
