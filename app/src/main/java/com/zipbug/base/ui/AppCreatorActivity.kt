package com.zipbug.base.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar
import com.zipbug.base.creator.AppScaffolder
import com.zipbug.base.creator.AppSpec
import com.zipbug.base.creator.ProjectZipWriter
import com.zipbug.base.creator.WebMiniAppScaffolder
import com.zipbug.base.databinding.ActivityAppCreatorBinding
import java.util.Locale

class AppCreatorActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAppCreatorBinding

    private var pendingFiles: Map<String, String>? = null
    private var lastExportUri: Uri? = null

    private val saveZip = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri == null) {
            binding.result.text =
                "PACKAGING • CANCELLED\nNo source ZIP was written."
            return@registerForActivityResult
        }

        val files = pendingFiles
            ?: return@registerForActivityResult

        binding.exportActions.visibility = View.GONE
        binding.result.text =
            "VALIDATION • PASS\n" +
                "PACKAGING • WRITING\n" +
                "Destination: $uri"

        runCatching {
            val writer = ProjectZipWriter(this)
            writer.write(uri, files)

            val validation = writer.validate(
                uri,
                files.keys
            )

            val metadata = queryDocumentMetadata(uri)

            Triple(
                validation,
                metadata.first,
                metadata.second
            )
        }.onSuccess { (validation, displayName, sizeBytes) ->
            lastExportUri = uri
            binding.exportActions.visibility = View.VISIBLE

            binding.result.text = buildString {
                append("VALIDATION • PASS\n")
                append("PACKAGING • PASS\n")
                append("ZIP VERIFY • PASS\n\n")
                append("NAME: ").append(displayName).append("\n")
                append("SIZE: ").append(formatBytes(sizeBytes)).append("\n")
                append("FILES: ").append(validation.entryCount).append("\n")
                append("UNCOMPRESSED: ")
                    .append(formatBytes(validation.totalUncompressedBytes))
                    .append("\n")
                append("LOCATION: ").append(uri).append("\n\n")
                append("Source ZIP was reopened and validated before success was reported.")
            }
        }.onFailure { error ->
            lastExportUri = null
            binding.exportActions.visibility = View.GONE
            binding.result.text = buildString {
                append("VALIDATION / PACKAGING • FAILED\n\n")
                append(error.message ?: "ZIP creation failed")
                append("\n\nSAFE NEXT STEP:\n")
                append("Choose another writable destination and export again. ")
                append("No signing credential or existing project file was overwritten.")
            }

            Snackbar.make(
                binding.root,
                error.message ?: "ZIP creation failed",
                Snackbar.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppCreatorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.createAndroid.setOnClickListener {
            createAndroidProject()
        }

        binding.createWeb.setOnClickListener {
            createWebMiniApp()
        }

        binding.openExport.setOnClickListener {
            lastExportUri?.let(::openZip)
        }

        binding.shareExport.setOnClickListener {
            lastExportUri?.let(::shareZip)
        }
    }

    private fun createAndroidProject() {
        binding.exportActions.visibility = View.GONE
        binding.result.text =
            "INPUT VALIDATION • RUNNING\n" +
                "Preparing Kotlin/XML project source…"

        val spec = AppSpec(
            name = binding.appName.text.toString().trim(),
            packageName = binding.packageName.text.toString().trim(),
            goal = binding.goal.text.toString().trim()
        )

        runCatching {
            AppScaffolder.files(spec)
        }.onSuccess { files ->
            binding.result.text =
                "INPUT VALIDATION • PASS\n" +
                    "SOURCE PLAN • PASS\n" +
                    "Prepared ${files.size} files. Choose a ZIP save location."

            export(
                files,
                safeFilename(spec.name) + "-android.zip"
            )
        }.onFailure {
            showError(
                stage = "INPUT VALIDATION",
                error = it,
                nextStep = "Check app name and package name, then try again."
            )
        }
    }

    private fun createWebMiniApp() {
        binding.exportActions.visibility = View.GONE
        binding.result.text =
            "INPUT VALIDATION • RUNNING\n" +
                "Preparing HTML/JS/JSON mini-app source…"

        val name = binding.appName.text.toString().trim()
        val goal = binding.goal.text.toString().trim()

        runCatching {
            WebMiniAppScaffolder.files(
                name = name,
                goal = goal
            )
        }.onSuccess { files ->
            binding.result.text =
                "INPUT VALIDATION • PASS\n" +
                    "SOURCE PLAN • PASS\n" +
                    "Prepared ${files.size} files. Choose a ZIP save location."

            export(
                files,
                safeFilename(name) + "-mini-app.zip"
            )
        }.onFailure {
            showError(
                stage = "INPUT VALIDATION",
                error = it,
                nextStep = "Enter a valid app name and try again."
            )
        }
    }

    private fun export(
        files: Map<String, String>,
        filename: String
    ) {
        pendingFiles = files
        lastExportUri = null
        saveZip.launch(filename)
    }

    private fun openZip(uri: Uri) {
        runCatching {
            startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(
                        uri,
                        "application/zip"
                    )
                    addFlags(
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
            )
        }.onFailure {
            Snackbar.make(
                binding.root,
                "No app can open this ZIP directly. Use Share or Files instead.",
                Snackbar.LENGTH_LONG
            ).show()
        }
    }

    private fun shareZip(uri: Uri) {
        runCatching {
            startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND).apply {
                        type = "application/zip"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    },
                    "Share source ZIP"
                )
            )
        }.onFailure {
            Snackbar.make(
                binding.root,
                "Unable to share ZIP: ${it.message}",
                Snackbar.LENGTH_LONG
            ).show()
        }
    }

    private fun queryDocumentMetadata(
        uri: Uri
    ): Pair<String, Long> {
        var name = "project.zip"
        var size = -1L

        contentResolver.query(
            uri,
            arrayOf(
                OpenableColumns.DISPLAY_NAME,
                OpenableColumns.SIZE
            ),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex =
                    cursor.getColumnIndex(
                        OpenableColumns.DISPLAY_NAME
                    )
                val sizeIndex =
                    cursor.getColumnIndex(
                        OpenableColumns.SIZE
                    )

                if (nameIndex >= 0) {
                    name = cursor.getString(nameIndex)
                        ?: name
                }

                if (
                    sizeIndex >= 0 &&
                    !cursor.isNull(sizeIndex)
                ) {
                    size = cursor.getLong(sizeIndex)
                }
            }
        }

        if (size < 0) {
            size = contentResolver
                .openAssetFileDescriptor(uri, "r")
                ?.use { it.length }
                ?: -1L
        }

        return name to size
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 0) return "unknown"
        if (bytes < 1024) return "$bytes B"

        val kb = bytes / 1024.0
        if (kb < 1024) {
            return String.format(
                Locale.US,
                "%.1f KB",
                kb
            )
        }

        return String.format(
            Locale.US,
            "%.2f MB",
            kb / 1024.0
        )
    }

    private fun safeFilename(value: String): String =
        value.replace(
            Regex("[^a-zA-Z0-9._-]"),
            "_"
        ).ifBlank { "ZipBugProject" }

    private fun showError(
        stage: String,
        error: Throwable,
        nextStep: String
    ) {
        binding.exportActions.visibility = View.GONE
        binding.result.text = buildString {
            append(stage).append(" • FAILED\n\n")
            append(error.message ?: "Invalid project")
            append("\n\nSAFE NEXT STEP:\n")
            append(nextStep)
        }

        Snackbar.make(
            binding.root,
            error.message ?: "Invalid project",
            Snackbar.LENGTH_LONG
        ).show()
    }
}
