package com.zipbug.base.creator

import android.content.Context
import android.net.Uri
import java.io.BufferedInputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class DirectoryZipValidation(
    val entryCount: Int,
    val totalUncompressedBytes: Long
)

class ProjectDirectoryZipWriter(
    private val context: Context
) {
    fun writeAndValidate(
        destination: Uri,
        projectRoot: File
    ): DirectoryZipValidation {
        val root = projectRoot.canonicalFile
        require(root.isDirectory) {
            "Project source directory is missing."
        }

        val files = root.walkTopDown()
            .filter { it.isFile }
            .map { file ->
                val canonical = file.canonicalFile
                require(
                    canonical.path.startsWith(root.path + File.separator)
                ) {
                    "Project contains a file outside its source sandbox."
                }
                canonical
            }
            .toList()

        require(files.isNotEmpty()) {
            "Project source directory contains no files."
        }
        require(files.size <= MAX_FILES) {
            "Project contains too many files to export safely."
        }

        var sourceBytes = 0L
        val expected = linkedSetOf<String>()

        context.contentResolver
            .openOutputStream(destination, "w")
            ?.use { output ->
                ZipOutputStream(output.buffered()).use { zip ->
                    for (file in files) {
                        val relative = file
                            .relativeTo(root)
                            .invariantSeparatorsPath

                        validateRelativePath(relative)
                        require(expected.add(relative)) {
                            "Duplicate project path: " + relative
                        }

                        sourceBytes += file.length()
                        require(sourceBytes <= MAX_SOURCE_BYTES) {
                            "Project source exceeds the 512 MB export limit."
                        }

                        zip.putNextEntry(ZipEntry(relative))
                        file.inputStream().buffered().use { input ->
                            input.copyTo(zip, BUFFER_SIZE)
                        }
                        zip.closeEntry()
                    }
                }
            }
            ?: error("Unable to open the selected ZIP destination.")

        return validate(destination, expected, sourceBytes)
    }

    private fun validate(
        destination: Uri,
        expected: Set<String>,
        expectedBytes: Long
    ): DirectoryZipValidation {
        val actual = linkedSetOf<String>()
        var expandedBytes = 0L

        context.contentResolver
            .openInputStream(destination)
            ?.use { input ->
                ZipInputStream(BufferedInputStream(input)).use { zip ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        validateRelativePath(entry.name)

                        if (!entry.isDirectory) {
                            require(actual.add(entry.name)) {
                                "Exported ZIP contains duplicate entry: " + entry.name
                            }

                            while (true) {
                                val read = zip.read(buffer)
                                if (read <= 0) break
                                expandedBytes += read
                                require(expandedBytes <= MAX_SOURCE_BYTES) {
                                    "Exported ZIP exceeds the validation limit."
                                }
                            }
                        }
                        zip.closeEntry()
                    }
                }
            }
            ?: error("Unable to reopen exported project ZIP.")

        require(actual == expected) {
            "Exported ZIP file list does not match the project source."
        }
        require(expandedBytes == expectedBytes) {
            "Exported ZIP byte count does not match the project source."
        }

        return DirectoryZipValidation(
            entryCount = actual.size,
            totalUncompressedBytes = expandedBytes
        )
    }

    private fun validateRelativePath(path: String) {
        require(path.isNotBlank()) {
            "ZIP entry path is blank."
        }
        require(!path.startsWith("/") && !path.startsWith("\\")) {
            "Absolute ZIP path rejected: " + path
        }

        val normalized = path.replace('\\', '/')
        require(
            normalized.split('/').none { it.isBlank() || it == ".." }
        ) {
            "Unsafe ZIP path rejected: " + path
        }
        require(!WINDOWS_DRIVE_PREFIX.containsMatchIn(normalized)) {
            "Windows absolute ZIP path rejected: " + path
        }
    }

    companion object {
        private const val MAX_FILES = 5_000
        private const val MAX_SOURCE_BYTES = 512L * 1024 * 1024
        private const val BUFFER_SIZE = 8 * 1024
        private val WINDOWS_DRIVE_PREFIX =
            Regex("^[A-Za-z]:[/\\\\]")
    }
}
