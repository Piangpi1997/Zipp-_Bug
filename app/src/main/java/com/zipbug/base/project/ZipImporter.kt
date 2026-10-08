package com.zipbug.base.project

import android.content.Context
import android.net.Uri
import com.zipbug.base.data.ProjectEntity
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.zip.ZipInputStream

class ZipImporter(private val context: Context) {

    suspend fun importZip(uri: Uri): ProjectEntity {
        val id = UUID.randomUUID().toString()
        val root = File(context.filesDir, "projects/$id").apply { mkdirs() }
        val canonicalRoot = root.canonicalFile

        return runCatching {
            var count = 0
            var total = 0L

            context.contentResolver.openInputStream(uri)?.use { input ->
                ZipInputStream(BufferedInputStream(input)).use { zin ->
                    while (true) {
                        val entry = zin.nextEntry ?: break
                        if (++count > MAX_FILES) {
                            error("ZIP contains too many files")
                        }

                        val rawName = entry.name
                        require(rawName.isNotBlank()) { "ZIP contains an empty path" }
                        require(!rawName.startsWith("/") && !rawName.startsWith("\\")) {
                            "Absolute ZIP paths are not allowed: $rawName"
                        }
                        require(!WINDOWS_DRIVE_PREFIX.containsMatchIn(rawName)) {
                            "Windows absolute ZIP paths are not allowed: $rawName"
                        }

                        val normalizedName = rawName.replace('\\', '/')
                        val out = resolveInside(canonicalRoot, normalizedName, "ZIP entry")

                        if (entry.isDirectory) {
                            if (!out.exists() && !out.mkdirs()) {
                                error("Unable to create ZIP directory: $normalizedName")
                            }
                        } else {
                            out.parentFile?.let { parent ->
                                if (!parent.exists() && !parent.mkdirs()) {
                                    error("Unable to create ZIP directory: ${parent.path}")
                                }
                            }

                            FileOutputStream(out).use { fos ->
                                val buffer = ByteArray(BUFFER_SIZE)
                                while (true) {
                                    val n = zin.read(buffer)
                                    if (n <= 0) break

                                    total += n
                                    if (total > MAX_EXTRACTED_BYTES) {
                                        error("ZIP exceeds 512 MB extracted size limit")
                                    }
                                    fos.write(buffer, 0, n)
                                }
                            }
                        }

                        zin.closeEntry()
                    }
                }
            } ?: error("Unable to open ZIP")

            val manifestFile = File(canonicalRoot, "app.json")
            val manifest = if (manifestFile.isFile) {
                JSONObject(manifestFile.readText(Charsets.UTF_8))
            } else {
                JSONObject()
            }

            val rawEntry = manifest.optString("entry", "www/index.html").trim()
            require(rawEntry.isNotBlank()) { "app.json entry must not be blank" }

            val normalizedEntry = rawEntry.replace('\\', '/')
            require(!normalizedEntry.startsWith("/")) {
                "app.json entry must be relative"
            }
            require(!WINDOWS_DRIVE_PREFIX.containsMatchIn(normalizedEntry)) {
                "app.json entry must be relative"
            }

            val entryFile = resolveInside(canonicalRoot, normalizedEntry, "app.json entry")
            require(entryFile.isFile) { "Missing entry file: $normalizedEntry" }

            val name = manifest.optString("name", "Imported project")
                .trim()
                .ifBlank { "Imported project" }
                .take(80)

            val kind = manifest.optString("kind", "web-mini-app")
                .trim()
                .ifBlank { "web-mini-app" }
                .take(40)

            ProjectEntity(
                id = id,
                name = name,
                kind = kind,
                rootPath = canonicalRoot.absolutePath,
                entryFile = normalizedEntry,
                createdAt = System.currentTimeMillis(),
                lastOpenedAt = System.currentTimeMillis()
            )
        }.getOrElse { error ->
            root.deleteRecursively()
            throw error
        }
    }

    private fun resolveInside(
        root: File,
        relativePath: String,
        label: String
    ): File {
        val candidate = File(root, relativePath).canonicalFile
        val prefix = root.path + File.separator

        require(candidate.path.startsWith(prefix)) {
            "$label escapes the project sandbox: $relativePath"
        }

        return candidate
    }

    companion object {
        private const val MAX_FILES = 5_000
        private const val MAX_EXTRACTED_BYTES = 512L * 1024 * 1024
        private const val BUFFER_SIZE = 8 * 1024
        private val WINDOWS_DRIVE_PREFIX = Regex("^[A-Za-z]:[/\\\\]")
    }
}
