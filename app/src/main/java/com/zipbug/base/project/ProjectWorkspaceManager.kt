package com.zipbug.base.project

import android.content.Context
import com.zipbug.base.data.ProjectEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object ProjectWorkspaceManager {

    fun isTermuxAccessible(rootPath: String): Boolean {
        val canonical = runCatching {
            File(rootPath).canonicalPath
        }.getOrNull() ?: return false

        return canonical == "/data/data/com.termux/files" ||
            canonical.startsWith("/data/data/com.termux/files/")
    }

    fun getTermuxHome(context: Context): String {
        return context.getSharedPreferences(
            "zipbug.settings",
            Context.MODE_PRIVATE
        ).getString(
            "termuxHome",
            "/data/data/com.termux/files/home"
        )!!
    }

    fun getRecommendedWorkspace(
        context: Context,
        projectName: String
    ): File {
        val home = getTermuxHome(context)
        val safeName = projectName
            .replace(Regex("[^a-zA-Z0-9_-]"), "_")
            .ifBlank { "Project" }
        return File(home, "OpenDots/projects/$safeName")
    }

    /**
     * Android app sandboxing prevents Zip_Bug from directly writing into
     * Termux private storage. Keeping this boundary explicit avoids reporting
     * a transfer that Android cannot actually perform.
     *
     * Export a verified source ZIP from Projects/App Creator and extract it
     * from Termux into the recommended workspace instead.
     */
    suspend fun transferToTermuxWorkspace(
        context: Context,
        project: ProjectEntity,
        targetDir: File = getRecommendedWorkspace(context, project.name)
    ): Result<ProjectEntity> = withContext(Dispatchers.IO) {
        Result.failure(
            UnsupportedOperationException(
                "Direct transfer to Termux private storage is blocked by Android sandboxing. " +
                    "Export a verified source ZIP and extract it in Termux at " +
                    targetDir.absolutePath +
                    "."
            )
        )
    }
}
