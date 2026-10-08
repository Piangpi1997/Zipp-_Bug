package com.zipbug.base.creator

import android.content.Context
import com.zipbug.base.ZipBugApp
import com.zipbug.base.data.ProjectEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object AppProjectGenerator {

    suspend fun generate(context: Context, spec: GeneratedProjectSpec): Result<ProjectEntity> =
        withContext(Dispatchers.IO) {
            runCatching {
                val safeName = spec.name.replace(Regex("[^a-zA-Z0-9_-]"), "_").ifBlank { "GeneratedApp" }
                val projectsDir = File(context.filesDir, "projects").apply { mkdirs() }
                val targetDir = File(projectsDir, safeName).apply { mkdirs() }

                // 1. Generate base project structure
                val baseFiles = runCatching {
                    AppScaffolder.files(
                        AppSpec(
                            name = spec.name,
                            packageName = spec.packageName,
                            goal = "Target: ${spec.target}\nPermissions: ${spec.permissions.joinToString()}"
                        )
                    )
                }.getOrDefault(emptyMap())

                val allFiles = baseFiles.toMutableMap()

                // 2. Overlay / write AI generated files
                for (gf in spec.files) {
                    val cleanPath = gf.path.trimStart('/', '\\')
                    require(!cleanPath.contains("..")) { "Path traversal rejected: ${gf.path}" }
                    allFiles[cleanPath] = gf.content
                }

                // 3. Write all files to disk
                for ((relPath, content) in allFiles) {
                    val targetFile = File(targetDir, relPath)
                    targetFile.parentFile?.mkdirs()
                    targetFile.writeText(content, Charsets.UTF_8)
                }

                val packagePath = spec.packageName.replace('.', '/')
                val mainActivityFile = File(targetDir, "app/src/main/java/$packagePath/MainActivity.kt")
                val entryFilePath = if (mainActivityFile.exists()) {
                    mainActivityFile.absolutePath
                } else {
                    File(targetDir, "build.gradle.kts").absolutePath
                }

                val projectEntity = ProjectEntity(
                    id = "proj_${safeName}_${System.currentTimeMillis()}",
                    name = spec.name,
                    kind = spec.target,
                    rootPath = targetDir.absolutePath,
                    entryFile = entryFilePath,
                    createdAt = System.currentTimeMillis(),
                    lastOpenedAt = System.currentTimeMillis()
                )

                val app = context.applicationContext as ZipBugApp
                app.database.projectDao().upsert(projectEntity)

                projectEntity
            }
        }
}
