package com.zipbug.base.creator

import android.content.Context
import android.net.Uri
import java.io.BufferedInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class ZipValidation(
    val entryCount: Int,
    val totalUncompressedBytes: Long,
    val entries: Set<String>
)

class ProjectZipWriter(
    private val context: Context
) {
    fun write(
        uri: Uri,
        files: Map<String, String>
    ) {
        require(files.isNotEmpty()) {
            "Project contains no files"
        }

        context.contentResolver
            .openOutputStream(uri, "w")
            ?.use { output ->
                ZipOutputStream(output.buffered()).use { zip ->
                    files.forEach { (path, content) ->
                        validateRelativePath(path)

                        zip.putNextEntry(ZipEntry(path))
                        zip.write(
                            content.toByteArray(Charsets.UTF_8)
                        )
                        zip.closeEntry()
                    }
                }
            }
            ?: error("Unable to open destination")
    }

    fun validate(
        uri: Uri,
        expectedPaths: Set<String>
    ): ZipValidation {
        require(expectedPaths.isNotEmpty()) {
            "Expected project file list is empty"
        }

        val entries = linkedSetOf<String>()
        var totalBytes = 0L

        context.contentResolver
            .openInputStream(uri)
            ?.use { input ->
                ZipInputStream(
                    BufferedInputStream(input)
                ).use { zip ->
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        validateRelativePath(entry.name)

                        if (!entry.isDirectory) {
                            require(entries.add(entry.name)) {
                                "Duplicate ZIP entry: ${entry.name}"
                            }

                            val buffer = ByteArray(8 * 1024)
                            while (true) {
                                val read = zip.read(buffer)
                                if (read <= 0) break
                                totalBytes += read
                                require(totalBytes <= MAX_VALIDATION_BYTES) {
                                    "ZIP expanded size exceeds validation limit"
                                }
                            }
                        }

                        zip.closeEntry()
                    }
                }
            }
            ?: error("Unable to reopen exported ZIP")

        val missing = expectedPaths - entries
        val unexpected = entries - expectedPaths

        require(missing.isEmpty()) {
            "ZIP validation failed; missing: ${missing.take(5).joinToString()}"
        }
        require(unexpected.isEmpty()) {
            "ZIP validation failed; unexpected: ${unexpected.take(5).joinToString()}"
        }

        return ZipValidation(
            entryCount = entries.size,
            totalUncompressedBytes = totalBytes,
            entries = entries
        )
    }

    private fun validateRelativePath(path: String) {
        require(path.isNotBlank()) {
            "ZIP entry path is blank"
        }
        require(!path.startsWith("/") && !path.startsWith("\\")) {
            "Absolute ZIP path rejected: $path"
        }
        require(!WINDOWS_DRIVE_PREFIX.containsMatchIn(path)) {
            "Windows absolute ZIP path rejected: $path"
        }

        val normalized = path.replace('\\', '/')
        require(
            normalized.split('/').none { it == ".." }
        ) {
            "Unsafe ZIP path rejected: $path"
        }
    }

    companion object {
        private const val MAX_VALIDATION_BYTES =
            64L * 1024 * 1024

        private val WINDOWS_DRIVE_PREFIX =
            Regex("^[A-Za-z]:[/\\\\]")
    }
}
