package com.zipbug.base.artifact

import android.content.Context
import android.util.Log
import com.zipbug.base.ZipBugApp
import com.zipbug.base.data.ApkArtifactEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object ApkArtifactScanner {
    private const val TAG = "ApkArtifactScanner"

    /**
     * Recursively discovers all valid APK files in the provided root directories,
     * supporting nonstandard module names and output paths.
     */
    fun findApkFiles(searchRoots: List<File>): List<File> {
        val found = mutableListOf<File>()
        val visited = mutableSetOf<String>()

        for (root in searchRoots) {
            if (!root.exists() || !root.isDirectory) continue
            try {
                root.walkTopDown()
                    .maxDepth(12)
                    .onEnter { dir ->
                        // Skip version control or cache directories
                        val name = dir.name
                        name != ".git" && name != ".gradle" && name != "node_modules" && name != ".idea"
                    }
                    .filter { file ->
                        file.isFile && file.extension.equals("apk", ignoreCase = true)
                    }
                    .forEach { apkFile ->
                        val canonical = apkFile.canonicalPath
                        if (visited.add(canonical)) {
                            found.add(apkFile)
                        }
                    }
            } catch (e: Exception) {
                Log.w(TAG, "Error traversing search root: ${root.absolutePath}", e)
            }
        }
        return found
    }

    /**
     * Scans known project roots and standard Termux directories, inspects found APKs,
     * and persists discovered artifacts into Room database.
     */
    suspend fun scanAndPersist(context: Context, extraRoots: List<File> = emptyList()): List<ApkArtifactEntity> =
        withContext(Dispatchers.IO) {
            val app = context.applicationContext as ZipBugApp
            val candidateRoots = mutableListOf<File>()

            // 1. All registered project paths from Room
            val projects = runCatching { app.database.projectDao().listAll() }.getOrDefault(emptyList())
            for (p in projects) {
                candidateRoots.add(File(p.rootPath))
            }

            // 2. Extra roots passed explicitly
            candidateRoots.addAll(extraRoots)

            // 3. App internal and external files directories
            context.filesDir?.let { candidateRoots.add(it) }
            context.getExternalFilesDir(null)?.let { candidateRoots.add(it) }

            // 4. Standard Termux storage locations
            candidateRoots.add(File("/data/data/com.termux/files/home"))
            candidateRoots.add(File("/sdcard/Download"))
            candidateRoots.add(File("/storage/emulated/0/Download"))

            val apkFiles = findApkFiles(candidateRoots)
            val entities = mutableListOf<ApkArtifactEntity>()

            for (file in apkFiles) {
                val inspection = ApkArtifactInspector.inspect(context, file)
                inspection.onSuccess { meta ->
                    val entity = ApkArtifactEntity(
                        id = meta.sha256,
                        filePath = file.absolutePath,
                        fileName = file.name,
                        packageName = meta.packageName,
                        versionName = meta.versionName,
                        versionCode = meta.versionCode,
                        minSdk = meta.minSdk,
                        targetSdk = meta.targetSdk,
                        sizeBytes = meta.sizeBytes,
                        sha256 = meta.sha256,
                        signerCertificateSha256 = meta.signerCertificateSha256,
                        discoveredAt = System.currentTimeMillis()
                    )
                    entities.add(entity)
                }
            }

            if (entities.isNotEmpty()) {
                app.database.apkArtifactDao().upsertAll(entities)
            }

            entities
        }
}
