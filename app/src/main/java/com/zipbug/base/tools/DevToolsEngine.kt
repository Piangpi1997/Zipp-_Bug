package com.zipbug.base.tools

import android.content.Context
import android.os.Build
import com.zipbug.base.data.ProjectEntity
import com.zipbug.base.termux.LogSanitizer
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Core engine for Developer Tools:
 * - JSON validation, pretty-printing, and minification
 * - Cryptographic file hash calculation (SHA-256, SHA-1, MD5)
 * - Non-destructive ZIP archive inspection and Zip-Slip path validation
 * - Text search across workspace files
 * - Versioned Project Backup & Restore (`zipbug-backup-manifest.json`, formatVersion = 1)
 * - Sanitized diagnostic report generation
 */
object DevToolsEngine {

    const val BACKUP_MANIFEST_ENTRY = "zipbug-backup-manifest.json"
    const val BACKUP_FORMAT_VERSION = 1
    private const val MAX_INSPECT_ENTRIES = 2_000
    private const val MAX_RESTORE_BYTES = 150L * 1024L * 1024L // 150 MB

    data class FileHashResult(
        val sha256: String,
        val sha1: String,
        val md5: String,
        val sizeBytes: Long
    )

    data class ZipEntryInfo(
        val name: String,
        val isDirectory: Boolean,
        val compressedBytes: Long,
        val uncompressedBytes: Long,
        val compressionRatio: Double,
        val isSafePath: Boolean,
        val warning: String? = null
    )

    data class ZipInspectionResult(
        val entryCount: Int,
        val fileCount: Int,
        val dirCount: Int,
        val totalCompressedBytes: Long,
        val totalUncompressedBytes: Long,
        val hasUnsafeEntries: Boolean,
        val entries: List<ZipEntryInfo>
    )

    data class SearchMatch(
        val projectName: String,
        val relativePath: String,
        val lineNumber: Int,
        val linePreview: String
    )

    data class BackupManifest(
        val formatVersion: Int,
        val appPackage: String,
        val createdAt: Long,
        val projectCount: Int,
        val totalFiles: Int
    )

    data class RestoreSummary(
        val formatVersion: Int,
        val restoredProjects: List<ProjectEntity>,
        val restoredFilesCount: Int,
        val totalBytesRestored: Long
    )

    // -------------------------------------------------------------------------
    // 1. JSON Validation & Formatting
    // -------------------------------------------------------------------------

    fun validateAndFormatJson(raw: String, indentSpaces: Int = 2): Result<String> = runCatching {
        val trimmed = raw.trim()
        require(trimmed.isNotEmpty()) { "JSON input is empty" }
        val tokener = JSONTokener(trimmed)
        val parsed = tokener.nextValue()
        if (tokener.more()) {
            val nextChar = tokener.nextClean()
            if (nextChar != '\u0000') {
                error("Unexpected trailing character '$nextChar' after root JSON value")
            }
        }
        when (parsed) {
            is JSONObject -> if (indentSpaces > 0) parsed.toString(indentSpaces) else parsed.toString()
            is JSONArray -> if (indentSpaces > 0) parsed.toString(indentSpaces) else parsed.toString()
            else -> error("Root JSON element must be an Object {...} or Array [...]")
        }
    }

    fun minifyJson(raw: String): Result<String> = validateAndFormatJson(raw, indentSpaces = 0)

    // -------------------------------------------------------------------------
    // 2. File Hash Calculator
    // -------------------------------------------------------------------------

    fun computeHashes(input: InputStream): FileHashResult {
        val sha256Digest = MessageDigest.getInstance("SHA-256")
        val sha1Digest = MessageDigest.getInstance("SHA-1")
        val md5Digest = MessageDigest.getInstance("MD5")

        val buffer = ByteArray(16 * 1024)
        var totalBytes = 0L
        var read = input.read(buffer)
        while (read != -1) {
            if (read > 0) {
                sha256Digest.update(buffer, 0, read)
                sha1Digest.update(buffer, 0, read)
                md5Digest.update(buffer, 0, read)
                totalBytes += read
            }
            read = input.read(buffer)
        }

        return FileHashResult(
            sha256 = sha256Digest.digest().toHex(),
            sha1 = sha1Digest.digest().toHex(),
            md5 = md5Digest.digest().toHex(),
            sizeBytes = totalBytes
        )
    }

    private fun ByteArray.toHex(): String =
        joinToString("") { "%02x".format(Locale.US, it) }

    // -------------------------------------------------------------------------
    // 3. Safe ZIP Archive Inspection
    // -------------------------------------------------------------------------

    fun isSafeEntryPath(entryName: String): Boolean {
        if (entryName.isBlank()) return false
        if (entryName.contains('\u0000')) return false
        if (entryName.startsWith("/") || entryName.startsWith("\\")) return false
        if (entryName.contains(":")) return false
        val normalized = entryName.replace('\\', '/')
        val segments = normalized.split('/')
        if (segments.any { it == ".." }) return false
        return true
    }

    fun inspectZip(input: InputStream): Result<ZipInspectionResult> = runCatching {
        val entries = mutableListOf<ZipEntryInfo>()
        var totalComp = 0L
        var totalUncomp = 0L
        var fileCount = 0
        var dirCount = 0
        var hasUnsafe = false
        val countBuffer = ByteArray(8 * 1024)

        ZipInputStream(input).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                if (entries.size >= MAX_INSPECT_ENTRIES) {
                    error("Archive exceeds maximum inspectable entry count ($MAX_INSPECT_ENTRIES)")
                }
                val name = entry.name
                val safe = isSafeEntryPath(name)
                if (!safe) hasUnsafe = true

                var measuredUncompressed = 0L
                if (!entry.isDirectory) {
                    var r = zis.read(countBuffer)
                    while (r != -1) {
                        measuredUncompressed += r
                        r = zis.read(countBuffer)
                    }
                    fileCount++
                } else {
                    dirCount++
                }

                val compSize = if (entry.compressedSize >= 0) entry.compressedSize else 0L
                val uncompSize = if (entry.size >= 0) entry.size else measuredUncompressed
                totalComp += compSize
                totalUncomp += uncompSize

                val ratio = if (compSize > 0L) uncompSize.toDouble() / compSize.toDouble() else 1.0
                val warning = when {
                    !safe -> "UNSAFE PATH (Zip-Slip / traversal risk)"
                    ratio > 100.0 && uncompSize > 10 * 1024 * 1024 -> "SUSPICIOUS COMPRESSION RATIO (${String.format(Locale.US, "%.1fx", ratio)})"
                    else -> null
                }

                entries.add(
                    ZipEntryInfo(
                        name = name,
                        isDirectory = entry.isDirectory,
                        compressedBytes = compSize,
                        uncompressedBytes = uncompSize,
                        compressionRatio = ratio,
                        isSafePath = safe,
                        warning = warning
                    )
                )
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        ZipInspectionResult(
            entryCount = entries.size,
            fileCount = fileCount,
            dirCount = dirCount,
            totalCompressedBytes = totalComp,
            totalUncompressedBytes = totalUncomp,
            hasUnsafeEntries = hasUnsafe,
            entries = entries
        )
    }

    // -------------------------------------------------------------------------
    // 4. Workspace Text Search
    // -------------------------------------------------------------------------

    private val textExtensions = setOf(
        "html", "htm", "css", "js", "jsx", "ts", "tsx", "json", "md", "txt",
        "kt", "kts", "java", "xml", "gradle", "properties", "py", "sh", "yml", "yaml", "srt", "vtt"
    )

    fun searchInDirectory(
        rootDir: File,
        query: String,
        maxResults: Int = 100
    ): List<SearchMatch> {
        val cleanQuery = query.trim()
        if (cleanQuery.isEmpty() || !rootDir.exists()) return emptyList()
        val results = mutableListOf<SearchMatch>()

        val topDirs = if (rootDir.isDirectory) rootDir.listFiles().orEmpty().toList() else listOf(rootDir)
        for (projectDir in topDirs) {
            val projName = projectDir.name
            projectDir.walkTopDown()
                .onEnter { dir ->
                    dir.name != ".git" && dir.name != "node_modules" && dir.name != "build" && dir.name != ".gradle"
                }
                .filter { file ->
                    file.isFile && file.length() <= 512 * 1024L && isLikelyTextFile(file)
                }
                .forEach { file ->
                    if (results.size >= maxResults) return results
                    val relPath = file.relativeToOrSelf(projectDir).path.replace('\\', '/')
                    runCatching {
                        file.useLines(Charsets.UTF_8) { lines ->
                            lines.forEachIndexed { idx, line ->
                                if (results.size < maxResults && line.contains(cleanQuery, ignoreCase = true)) {
                                    results.add(
                                        SearchMatch(
                                            projectName = projName,
                                            relativePath = relPath,
                                            lineNumber = idx + 1,
                                            linePreview = line.trim().take(140)
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
        }
        return results
    }

    fun isLikelyTextFile(file: File): Boolean {
        val ext = file.extension.lowercase(Locale.US)
        if (ext in textExtensions) return true
        if (file.name.equals("Dockerfile", ignoreCase = true) || file.name.equals("Makefile", ignoreCase = true)) return true
        return false
    }

    // -------------------------------------------------------------------------
    // 5. Project Backup & Restore (formatVersion = 1)
    // -------------------------------------------------------------------------

    fun createBackupZip(
        projects: List<ProjectEntity>,
        outputStream: OutputStream,
        appPackage: String = "com.zipbug.base"
    ): Result<BackupManifest> = runCatching {
        var totalFiles = 0
        val projectsJsonArray = JSONArray()

        projects.forEach { proj ->
            val obj = JSONObject().apply {
                put("id", proj.id)
                put("name", proj.name)
                put("kind", proj.kind)
                put("entryFile", proj.entryFile)
                put("createdAt", proj.createdAt)
            }
            projectsJsonArray.put(obj)
        }

        ZipOutputStream(outputStream).use { zos ->
            for (proj in projects) {
                val root = File(proj.rootPath)
                if (!root.exists() || !root.isDirectory) continue
                root.walkTopDown()
                    .onEnter { dir -> dir.name != "node_modules" && dir.name != ".gradle" && dir.name != "build" }
                    .filter { it.isFile }
                    .forEach { file ->
                        val rel = file.relativeTo(root).path.replace('\\', '/')
                        val entryName = "projects/${proj.id}/$rel"
                        if (isSafeEntryPath(entryName)) {
                            zos.putNextEntry(ZipEntry(entryName))
                            FileInputStream(file).use { fis -> fis.copyTo(zos) }
                            zos.closeEntry()
                            totalFiles++
                        }
                    }
            }

            val manifest = BackupManifest(
                formatVersion = BACKUP_FORMAT_VERSION,
                appPackage = appPackage,
                createdAt = System.currentTimeMillis(),
                projectCount = projects.size,
                totalFiles = totalFiles
            )
            val manifestJson = JSONObject().apply {
                put("formatVersion", manifest.formatVersion)
                put("appPackage", manifest.appPackage)
                put("createdAt", manifest.createdAt)
                put("projectCount", manifest.projectCount)
                put("totalFiles", manifest.totalFiles)
                put("projects", projectsJsonArray)
            }
            zos.putNextEntry(ZipEntry(BACKUP_MANIFEST_ENTRY))
            zos.write(manifestJson.toString(2).toByteArray(Charsets.UTF_8))
            zos.closeEntry()
            manifest
        }
    }

    fun validateAndRestoreBackupZip(
        projectsBaseDir: File,
        inputStream: InputStream
    ): Result<RestoreSummary> = runCatching {
        if (!projectsBaseDir.exists()) projectsBaseDir.mkdirs()
        val stagingDir = File(projectsBaseDir.parentFile ?: projectsBaseDir, "restore_staging_${System.currentTimeMillis()}")
        stagingDir.mkdirs()

        try {
            var manifestContent: String? = null
            var totalBytes = 0L
            var restoredFiles = 0
            val buffer = ByteArray(8 * 1024)

            ZipInputStream(inputStream).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    val name = entry.name.replace('\\', '/')
                    require(isSafeEntryPath(name)) {
                        "Unsafe path in backup archive: $name"
                    }

                    if (name == BACKUP_MANIFEST_ENTRY) {
                        val baos = ByteArrayOutputStream()
                        var r = zis.read(buffer)
                        while (r != -1) {
                            totalBytes += r
                            require(totalBytes <= MAX_RESTORE_BYTES) { "Backup exceeds max size limit" }
                            baos.write(buffer, 0, r)
                            r = zis.read(buffer)
                        }
                        manifestContent = baos.toString("UTF-8")
                    } else if (name.startsWith("projects/") && !entry.isDirectory) {
                        val outFile = File(stagingDir, name)
                        val canonStaging = stagingDir.canonicalPath
                        val canonOut = outFile.canonicalPath
                        require(canonOut.startsWith(canonStaging + File.separator)) {
                            "Path traversal blocked during backup extraction: $name"
                        }
                        outFile.parentFile?.mkdirs()
                        FileOutputStream(outFile).use { fos ->
                            var r = zis.read(buffer)
                            while (r != -1) {
                                totalBytes += r
                                require(totalBytes <= MAX_RESTORE_BYTES) { "Backup exceeds max size limit" }
                                fos.write(buffer, 0, r)
                                r = zis.read(buffer)
                            }
                        }
                        restoredFiles++
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            val rawManifest = manifestContent
                ?: error("Invalid backup archive: missing $BACKUP_MANIFEST_ENTRY")
            val manifestJson = JSONObject(rawManifest)
            val formatVersion = manifestJson.optInt("formatVersion", -1)
            require(formatVersion == BACKUP_FORMAT_VERSION) {
                "Unsupported backup formatVersion: $formatVersion (expected $BACKUP_FORMAT_VERSION)"
            }

            val projectsArray = manifestJson.optJSONArray("projects") ?: JSONArray()
            val restoredEntities = mutableListOf<ProjectEntity>()

            for (i in 0 until projectsArray.length()) {
                val pObj = projectsArray.getJSONObject(i)
                val origId = pObj.optString("id").ifBlank { UUID.randomUUID().toString() }
                val origName = pObj.optString("name", "Restored Project")
                val kind = pObj.optString("kind", pObj.optString("template", "web-mini-app"))
                val entryFile = pObj.optString("entryFile", "www/index.html")
                val createdAt = pObj.optLong("createdAt", System.currentTimeMillis())

                val stagedProjDir = File(stagingDir, "projects/$origId")
                val existingTarget = File(projectsBaseDir, origId)
                val finalId = if (existingTarget.exists()) UUID.randomUUID().toString() else origId
                val finalName = if (existingTarget.exists()) "$origName (Restored)" else origName
                val finalTargetDir = File(projectsBaseDir, finalId)
                finalTargetDir.mkdirs()

                if (stagedProjDir.exists()) {
                    stagedProjDir.copyRecursively(finalTargetDir, overwrite = true)
                }

                restoredEntities.add(
                    ProjectEntity(
                        id = finalId,
                        name = finalName,
                        kind = kind,
                        rootPath = finalTargetDir.canonicalPath,
                        entryFile = entryFile,
                        createdAt = createdAt,
                        lastOpenedAt = System.currentTimeMillis()
                    )
                )
            }

            RestoreSummary(
                formatVersion = formatVersion,
                restoredProjects = restoredEntities,
                restoredFilesCount = restoredFiles,
                totalBytesRestored = totalBytes
            )
        } finally {
            runCatching { stagingDir.deleteRecursively() }
        }
    }

    // -------------------------------------------------------------------------
    // 6. Storage Usage & Sanitized Diagnostic Report
    // -------------------------------------------------------------------------

    fun calculateDirectoryBytes(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0L
        if (dir.isFile) return dir.length()
        var total = 0L
        dir.walkTopDown().forEach { f ->
            if (f.isFile) total += f.length()
        }
        return total
    }

    fun formatBytes(bytes: Long): String {
        val safe = bytes.coerceAtLeast(0L)
        return when {
            safe < 1024L -> "$safe B"
            safe < 1024L * 1024L -> String.format(Locale.US, "%.1f KB", safe / 1024.0)
            else -> String.format(Locale.US, "%.2f MB", safe / (1024.0 * 1024.0))
        }
    }

    fun buildDiagnosticReport(
        context: Context,
        projectCount: Int,
        jobCount: Int,
        engineUrl: String,
        termuxInstalled: Boolean,
        termuxPermitted: Boolean
    ): String {
        val filesBytes = calculateDirectoryBytes(context.filesDir)
        val cacheBytes = calculateDirectoryBytes(context.cacheDir)
        val raw = buildString {
            appendLine("=== ZIP_BUG DIAGNOSTIC REPORT (SANITIZED) ===")
            appendLine("Timestamp: ${System.currentTimeMillis()}")
            appendLine("Package: ${context.packageName}")
            appendLine("Android SDK: ${Build.VERSION.SDK_INT} (${Build.VERSION.RELEASE})")
            appendLine("Device ABI: ${Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"}")
            appendLine("Termux Installed: $termuxInstalled")
            appendLine("Termux RUN_COMMAND Granted: $termuxPermitted")
            appendLine("Configured Engine URL: $engineUrl")
            appendLine("Projects Stored: $projectCount")
            appendLine("Jobs Recorded: $jobCount")
            appendLine("App Files Storage: ${formatBytes(filesBytes)} ($filesBytes bytes)")
            appendLine("App Cache Storage: ${formatBytes(cacheBytes)} ($cacheBytes bytes)")
            appendLine("Secrets / API Keys: [REDACTED BY POLICY]")
        }
        return LogSanitizer.sanitize(raw)
    }
}
