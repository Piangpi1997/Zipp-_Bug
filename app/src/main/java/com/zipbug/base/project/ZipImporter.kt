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
                            error("ZIP contains too many files (exceeds $MAX_FILES)")
                        }

                        val rawName = entry.name
                        require(rawName.isNotBlank()) { "ZIP contains an empty path" }
                        require(!rawName.contains('\u0000')) { "Malicious ZIP entry: contains null byte" }
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
            } ?: error("Unable to open ZIP stream from $uri")

            val zipBugManifest = File(canonicalRoot, "zipbug.json")
            val legacyManifest = File(canonicalRoot, "app.json")
            val manifestFile = when {
                zipBugManifest.isFile -> zipBugManifest
                legacyManifest.isFile -> legacyManifest
                else -> legacyManifest
            }

            val isAndroid = File(canonicalRoot, "build.gradle").exists() ||
                File(canonicalRoot, "build.gradle.kts").exists() ||
                File(canonicalRoot, "settings.gradle").exists() ||
                File(canonicalRoot, "settings.gradle.kts").exists()

            val manifest = if (manifestFile.isFile) {
                runCatching { JSONObject(manifestFile.readText(Charsets.UTF_8)) }.getOrDefault(JSONObject())
            } else {
                JSONObject()
            }

            val rawEntry = manifest.optString("entry", if (isAndroid) "" else "www/index.html").trim()
            val normalizedEntry = if (rawEntry.isNotBlank()) {
                val norm = rawEntry.replace('\\', '/')
                require(!norm.startsWith("/")) { "project manifest entry must be relative" }
                require(!WINDOWS_DRIVE_PREFIX.containsMatchIn(norm)) { "project manifest entry must be relative" }
                val entryFile = resolveInside(canonicalRoot, norm, "project manifest entry")
                require(entryFile.isFile) { "Missing project entry file: $norm" }
                norm
            } else if (isAndroid) {
                if (File(canonicalRoot, "build.gradle.kts").exists()) "build.gradle.kts" else "build.gradle"
            } else {
                val firstFile = canonicalRoot.walkTopDown().firstOrNull { it.isFile }
                firstFile?.relativeTo(canonicalRoot)?.path?.replace('\\', '/') ?: ""
            }

            val defaultKind = if (isAndroid) "android" else "web-mini-app"
            val kind = manifest.optString("kind", defaultKind)
                .trim()
                .ifBlank { defaultKind }
                .take(40)

            val name = manifest.optString("name", "Imported project")
                .trim()
                .ifBlank {
                    uri.lastPathSegment?.substringAfterLast('/')?.removeSuffix(".zip")?.ifBlank { "Imported project" } ?: "Imported project"
                }
                .take(80)

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

    companion object {
        const val MAX_FILES = 5_000
        const val MAX_EXTRACTED_BYTES = 512L * 1024 * 1024 // 512 MB
        private const val BUFFER_SIZE = 8 * 1024
        private val WINDOWS_DRIVE_PREFIX = Regex("^[A-Za-z]:[/\\\\]")

        internal fun resolveInside(
            root: File,
            relativePath: String,
            label: String
        ): File {
            val canonicalRoot = root.canonicalFile
            val candidate = File(canonicalRoot, relativePath).canonicalFile
            val prefix = canonicalRoot.path + File.separator

            require(
                candidate.path == canonicalRoot.path ||
                    candidate.path.startsWith(prefix)
            ) {
                "$label escapes the project sandbox: $relativePath"
            }

            return candidate
        }
    }
}
