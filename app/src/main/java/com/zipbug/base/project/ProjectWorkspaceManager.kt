package com.zipbug.base.project

import android.content.Context
import com.zipbug.base.ZipBugApp
import com.zipbug.base.data.ProjectEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object ProjectWorkspaceManager {

    fun isTermuxAccessible(rootPath: String): Boolean {
        return rootPath.startsWith("/data/data/com.termux/files/") ||
            rootPath.startsWith("/storage/emulated/0/")
    }

    fun getTermuxHome(context: Context): String {
        return context.getSharedPreferences("zipbug.settings", Context.MODE_PRIVATE)
            .getString("termuxHome", "/data/data/com.termux/files/home")!!
    }

    fun getRecommendedWorkspace(context: Context, projectName: String): File {
        val home = getTermuxHome(context)
        val safeName = projectName.replace(Regex("[^a-zA-Z0-9_-]"), "_").ifBlank { "Project" }
        return File(home, "OpenDots/projects/$safeName")
    }

    suspend fun transferToTermuxWorkspace(
        context: Context,
        project: ProjectEntity,
        targetDir: File = getRecommendedWorkspace(context, project.name)
    ): Result<ProjectEntity> = withContext(Dispatchers.IO) {
        runCatching {
            val sourceDir = File(project.rootPath)
            require(sourceDir.exists() && sourceDir.isDirectory) {
                "Source project directory does not exist: ${project.rootPath}"
            }

            // Create target directory
            targetDir.mkdirs()

            // Copy all project files recursively
            sourceDir.copyRecursively(targetDir, overwrite = true)

            // Update entry file path if it was absolute
            val newEntryFile = if (project.entryFile.startsWith(project.rootPath)) {
                project.entryFile.replace(project.rootPath, targetDir.absolutePath)
            } else {
                project.entryFile
            }

            val updatedProject = project.copy(
                rootPath = targetDir.absolutePath,
                entryFile = newEntryFile,
                lastOpenedAt = System.currentTimeMillis()
            )

            // Persist updated project in Room DB
            val app = context.applicationContext as ZipBugApp
            app.database.projectDao().upsert(updatedProject)

            updatedProject
        }
    }
}
