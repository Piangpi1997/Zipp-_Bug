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
        var count = 0
        var total = 0L

        context.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(BufferedInputStream(input)).use { zin ->
                while (true) {
                    val entry = zin.nextEntry ?: break
                    if (++count > 5000) error("ZIP contains too many files")

                    val out = File(root, entry.name).canonicalFile
                    if (!out.path.startsWith(canonicalRoot.path + File.separator)) {
                        error("Unsafe ZIP path: ${entry.name}")
                    }

                    if (entry.isDirectory) {
                        out.mkdirs()
                    } else {
                        out.parentFile?.mkdirs()
                        FileOutputStream(out).use { fos ->
                            val buffer = ByteArray(8192)
                            while (true) {
                                val n = zin.read(buffer)
                                if (n <= 0) break
                                total += n
                                if (total > 512L * 1024 * 1024) {
                                    error("ZIP exceeds 512 MB")
                                }
                                fos.write(buffer, 0, n)
                            }
                        }
                    }
                }
            }
        } ?: error("Unable to open ZIP")

        val manifestFile = File(root, "app.json")
        val manifest = if (manifestFile.exists()) {
            JSONObject(manifestFile.readText())
        } else {
            JSONObject()
        }

        val entry = manifest.optString("entry", "www/index.html")
        if (!File(root, entry).exists()) error("Missing entry file: $entry")

        return ProjectEntity(
            id = id,
            name = manifest.optString("name", "Imported project"),
            kind = manifest.optString("kind", "web-mini-app"),
            rootPath = root.absolutePath,
            entryFile = entry
        )
    }
}
