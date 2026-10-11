package com.zipbug.base.creator

import android.content.Context
import com.zipbug.base.ZipBugApp
import com.zipbug.base.data.ProjectEntity
import com.zipbug.base.termux.LogSanitizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object AppProjectGenerator {

    suspend fun generate(context: Context, spec: GeneratedProjectSpec): Result<ProjectEntity> =
        withContext(Dispatchers.IO) {
            val safeName = spec.name
                .replace(Regex("[^a-zA-Z0-9_-]"), "_")
                .ifBlank { "GeneratedApp" }
            val createdAt = System.currentTimeMillis()
            val projectId = "proj_" + safeName + "_" + createdAt
            val projectsDir = File(context.filesDir, "projects").apply { mkdirs() }
            val targetDir = File(projectsDir, projectId)

            runCatching {
                require(targetDir.mkdirs()) {
                    "Unable to create a clean generated project directory."
                }
                val canonicalRoot = targetDir.canonicalFile

                val baseFiles = AppScaffolder.files(
                    AppSpec(
                        name = spec.name,
                        packageName = spec.packageName,
                        goal = "Target: " + spec.target,
                        minSdk = spec.minSdk,
                        targetSdk = spec.targetSdk,
                        permissions = spec.permissions,
                        dependencies = spec.dependencies
                    )
                )

                val allFiles = baseFiles.toMutableMap()

                for (generatedFile in spec.files) {
                    val cleanPath = normalizeRelativePath(generatedFile.path)
                    require(
                        !LogSanitizer.containsRecognizableSecret(
                            generatedFile.content
                        )
                    ) {
                        "Generated file contains a recognizable API key/token: " +
                            cleanPath +
                            ". Remove the secret and regenerate."
                    }
                    allFiles[cleanPath] = generatedFile.content
                }

                for ((relPath, content) in allFiles) {
                    val normalizedPath = normalizeRelativePath(relPath)
                    val targetFile = resolveInside(canonicalRoot, normalizedPath)
                    targetFile.parentFile?.let { parent ->
                        require(parent.exists() || parent.mkdirs()) {
                            "Unable to create project directory: " + parent.path
                        }
                    }
                    targetFile.writeText(content, Charsets.UTF_8)
                }

                val packagePath = spec.packageName.replace('.', '/')
                val mainActivityFile = File(
                    canonicalRoot,
                    "app/src/main/java/" + packagePath + "/MainActivity.kt"
                )
                val entryFilePath = if (mainActivityFile.isFile) {
                    mainActivityFile.absolutePath
                } else {
                    File(canonicalRoot, "build.gradle.kts").absolutePath
                }

                val projectEntity = ProjectEntity(
                    id = projectId,
                    name = spec.name,
                    kind = spec.target,
                    rootPath = canonicalRoot.absolutePath,
                    entryFile = entryFilePath,
                    createdAt = createdAt,
                    lastOpenedAt = createdAt
                )

                val app = context.applicationContext as ZipBugApp
                app.database.projectDao().upsert(projectEntity)

                projectEntity
            }.onFailure {
                targetDir.deleteRecursively()
            }
        }

    private fun normalizeRelativePath(rawPath: String): String {
        val normalized = rawPath
            .trim()
            .replace('\\', '/')
            .trimStart('/')

        require(normalized.isNotBlank()) {
            "Generated file path cannot be blank."
        }
        require(!WINDOWS_DRIVE_PREFIX.containsMatchIn(normalized)) {
            "Absolute Windows path rejected: " + rawPath
        }
        require(normalized.split('/').none { it == ".." || it.isBlank() }) {
            "Unsafe generated file path rejected: " + rawPath
        }
        return normalized
    }

    private fun resolveInside(root: File, relativePath: String): File {
        val candidate = File(root, relativePath).canonicalFile
        val prefix = root.path + File.separator
        require(candidate.path.startsWith(prefix)) {
            "Generated file escapes project sandbox: " + relativePath
        }
        return candidate
    }

    private val WINDOWS_DRIVE_PREFIX = Regex("^[A-Za-z]:[/\\\\]")
}
