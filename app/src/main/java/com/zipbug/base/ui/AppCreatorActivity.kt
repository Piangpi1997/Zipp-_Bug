package com.zipbug.base.ui

import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar
import com.zipbug.base.creator.AppScaffolder
import com.zipbug.base.creator.AppSpec
import com.zipbug.base.creator.ProjectZipWriter
import com.zipbug.base.creator.WebMiniAppScaffolder
import com.zipbug.base.databinding.ActivityAppCreatorBinding

class AppCreatorActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAppCreatorBinding
    private var pendingFiles: Map<String, String>? = null

    private val saveZip = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri == null) return@registerForActivityResult

        val files = pendingFiles
            ?: return@registerForActivityResult

        runCatching {
            ProjectZipWriter(this).write(uri, files)
        }.onSuccess {
            binding.result.text =
                "SOURCE ZIP CREATED\n" +
                    uri.toString() +
                    "\n\nFiles: " +
                    files.size +
                    "\nThis is real source output, not a fake APK."
        }.onFailure {
            Snackbar.make(
                binding.root,
                it.message ?: "ZIP creation failed",
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
    }

    private fun createAndroidProject() {
        val spec = AppSpec(
            name = binding.appName.text.toString().trim(),
            packageName = binding.packageName.text.toString().trim(),
            goal = binding.goal.text.toString().trim()
        )

        runCatching {
            AppScaffolder.files(spec)
        }.onSuccess { files ->
            export(
                files,
                safeFilename(spec.name) + "-android.zip"
            )
        }.onFailure {
            showError(it)
        }
    }

    private fun createWebMiniApp() {
        val name = binding.appName.text.toString().trim()
        val goal = binding.goal.text.toString().trim()

        runCatching {
            WebMiniAppScaffolder.files(
                name = name,
                goal = goal
            )
        }.onSuccess { files ->
            export(
                files,
                safeFilename(name) + "-mini-app.zip"
            )
        }.onFailure {
            showError(it)
        }
    }

    private fun export(
        files: Map<String, String>,
        filename: String
    ) {
        pendingFiles = files
        binding.result.text =
            "Prepared " + files.size +
                " source files. Choose a save location."
        saveZip.launch(filename)
    }

    private fun safeFilename(value: String): String =
        value.replace(
            Regex("[^a-zA-Z0-9._-]"),
            "_"
        ).ifBlank { "ZipBugProject" }

    private fun showError(error: Throwable) {
        Snackbar.make(
            binding.root,
            error.message ?: "Invalid project",
            Snackbar.LENGTH_LONG
        ).show()
    }
}
