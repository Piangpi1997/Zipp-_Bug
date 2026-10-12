package com.zipbug.base.creator

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.zipbug.base.data.ProjectEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class GeneratedProjectHandoffPlan(
    val stagedZipPath: String,
    val termuxRootPath: String,
    val entryRelativePath: String,
    val validation: DirectoryZipValidation
)

object GeneratedProjectTermuxHandoff {

    suspend fun stage(
        context: Context,
        project: ProjectEntity
    ): Result<GeneratedProjectHandoffPlan> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    "Automatic Termux handoff requires Android 10 or newer. " +
                        "Use Export Source ZIP on older Android versions."
                }

                val generatedRoot = File(project.rootPath).canonicalFile
                val allowedProjectsRoot =
                    File(context.filesDir, "projects").canonicalFile

                require(
                    generatedRoot.path.startsWith(
                        allowedProjectsRoot.path + File.separator
                    )
                ) {
                    "Only projects generated inside Zip_Bug app storage " +
                        "can use automatic Termux handoff."
                }
                require(generatedRoot.isDirectory) {
                    "Generated source directory is missing."
                }

                val entryFile = File(project.entryFile).canonicalFile
                require(
                    entryFile.path.startsWith(
                        generatedRoot.path + File.separator
                    )
                ) {
                    "Generated project entry file escaped its source root."
                }
                val entryRelative = entryFile
                    .relativeTo(generatedRoot)
                    .invariantSeparatorsPath

                val safeName = project.name
                    .replace(Regex("[^a-zA-Z0-9._-]"), "_")
                    .ifBlank { "ZipBugGeneratedApp" }
                val safeId = project.id
                    .replace(Regex("[^a-zA-Z0-9_-]"), "_")
                    .takeLast(24)
                val zipName = "${safeName}_${safeId}.zip"
                val relativeDir =
                    Environment.DIRECTORY_DOWNLOADS +
                        "/Zip_Bug/Generated"

                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, zipName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/zip")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relativeDir)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }

                val uri = resolver.insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    values
                ) ?: error("Unable to create shared handoff ZIP.")

                try {
                    val validation = ProjectDirectoryZipWriter(context)
                        .writeAndValidate(uri, generatedRoot)

                    resolver.update(
                        uri,
                        ContentValues().apply {
                            put(MediaStore.MediaColumns.IS_PENDING, 0)
                        },
                        null,
                        null
                    )

                    val sharedRoot =
                        Environment.getExternalStorageDirectory().absolutePath
                    val stagedPath =
                        "$sharedRoot/$relativeDir/$zipName"

                    val destinationName =
                        "${safeName}_${safeId}"
                            .replace(Regex("[^a-zA-Z0-9._-]"), "_")
                    val termuxRoot =
                        "/data/data/com.termux/files/home/" +
                            "Zip_Bug/Generated/$destinationName"

                    GeneratedProjectHandoffPlan(
                        stagedZipPath = stagedPath,
                        termuxRootPath = termuxRoot,
                        entryRelativePath = entryRelative,
                        validation = validation
                    )
                } catch (error: Throwable) {
                    resolver.delete(uri, null, null)
                    throw error
                }
            }
        }
}
