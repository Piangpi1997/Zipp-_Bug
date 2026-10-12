package com.zipbug.base.recap

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import java.io.File

object RecapMediaStager {
    data class StagedMedia(
        val sourceUri: String,
        val displayName: String,
        val localPath: String,
        val sizeBytes: Long
    )

    fun stage(
        context: Context,
        uri: Uri,
        projectId: String
    ): Result<StagedMedia> = runCatching {
        val resolver = context.contentResolver
        val originalName = queryDisplayName(context, uri)
            ?: "source_media"
        val extension = extensionFor(
            originalName,
            resolver.getType(uri)
        )
        val safeProject = projectId.replace(
            Regex("[^a-zA-Z0-9_-]"),
            "_"
        )
        val stagedName =
            "source_${safeProject.take(24)}_${System.currentTimeMillis()}" +
                extension

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            stageToPublicDownloads(
                context = context,
                uri = uri,
                displayName = originalName,
                stagedName = stagedName
            )
        } else {
            stageToAppExternalFiles(
                context = context,
                uri = uri,
                displayName = originalName,
                stagedName = stagedName
            )
        }
    }

    private fun stageToPublicDownloads(
        context: Context,
        uri: Uri,
        displayName: String,
        stagedName: String
    ): StagedMedia {
        val resolver = context.contentResolver
        val relativePath =
            Environment.DIRECTORY_DOWNLOADS + "/Zip_Bug/Recap"

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, stagedName)
            put(
                MediaStore.MediaColumns.MIME_TYPE,
                resolver.getType(uri) ?: "application/octet-stream"
            )
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }

        val targetUri = resolver.insert(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            values
        ) ?: error("Could not create shared recap staging file.")

        try {
            val copied = resolver.openInputStream(uri)?.use { input ->
                resolver.openOutputStream(targetUri, "w")?.use { output ->
                    input.copyTo(output)
                } ?: error("Could not open shared staging output.")
            } ?: error("Selected media URI cannot be opened.")

            require(copied > 0L) {
                "Selected media copied zero bytes."
            }

            resolver.update(
                targetUri,
                ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                },
                null,
                null
            )

            val root = Environment.getExternalStorageDirectory().absolutePath
            val path =
                "$root/${Environment.DIRECTORY_DOWNLOADS}/Zip_Bug/Recap/$stagedName"

            return StagedMedia(
                sourceUri = uri.toString(),
                displayName = displayName,
                localPath = path,
                sizeBytes = copied
            )
        } catch (error: Throwable) {
            resolver.delete(targetUri, null, null)
            throw error
        }
    }

    private fun stageToAppExternalFiles(
        context: Context,
        uri: Uri,
        displayName: String,
        stagedName: String
    ): StagedMedia {
        val base = context.getExternalFilesDir(null)
            ?: error("External app storage is unavailable.")
        val dir = File(base, "recap")
        require(dir.exists() || dir.mkdirs()) {
            "Could not create recap staging directory."
        }

        val target = File(dir, stagedName)
        val copied = context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output ->
                input.copyTo(output)
            }
        } ?: error("Selected media URI cannot be opened.")

        require(copied > 0L) {
            "Selected media copied zero bytes."
        }

        return StagedMedia(
            sourceUri = uri.toString(),
            displayName = displayName,
            localPath = target.absolutePath,
            sizeBytes = copied
        )
    }

    private fun queryDisplayName(
        context: Context,
        uri: Uri
    ): String? {
        return runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (!cursor.moveToFirst()) {
                    null
                } else {
                    val index = cursor.getColumnIndex(
                        OpenableColumns.DISPLAY_NAME
                    )
                    if (index >= 0) cursor.getString(index) else null
                }
            }
        }.getOrNull()
    }

    internal fun extensionFor(
        fileName: String,
        mimeType: String?
    ): String {
        val fromName = fileName.substringAfterLast(
            '.',
            missingDelimiterValue = ""
        ).lowercase()
        val extension = if (
            fromName.matches(Regex("[a-z0-9]{1,8}"))
        ) {
            fromName
        } else {
            MimeTypeMap.getSingleton()
                .getExtensionFromMimeType(mimeType)
                .orEmpty()
        }

        return if (extension.isBlank()) "" else ".$extension"
    }
}
