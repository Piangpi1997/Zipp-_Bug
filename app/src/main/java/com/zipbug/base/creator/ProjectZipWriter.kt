package com.zipbug.base.creator

import android.content.Context
import android.net.Uri
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ProjectZipWriter(
    private val context: Context
) {
    fun write(
        uri: Uri,
        files: Map<String, String>
    ) {
        context.contentResolver
            .openOutputStream(uri, "w")
            ?.use { output ->
                ZipOutputStream(output.buffered()).use { zip ->
                    files.forEach { (path, content) ->
                        require(!path.startsWith("/"))
                        require(!path.contains("../"))

                        zip.putNextEntry(ZipEntry(path))
                        zip.write(
                            content.toByteArray(Charsets.UTF_8)
                        )
                        zip.closeEntry()
                    }
                }
            }
            ?: error("Unable to open destination")
    }
}
