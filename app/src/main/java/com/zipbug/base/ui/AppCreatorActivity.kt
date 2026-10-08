package com.zipbug.base.ui

import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar
import com.zipbug.base.creator.AppScaffolder
import com.zipbug.base.creator.AppSpec
import com.zipbug.base.creator.ProjectZipWriter
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
                "PROJECT ZIP CREATED\n" +
                    uri.toString() +
                    "\n\nThis is source output, not a fake APK. " +
                    "Extract it in Termux and run /apkbuilder."
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

        binding.create.setOnClickListener {
            createProject()
        }
    }

    private fun createProject() {
        val spec = AppSpec(
            name = binding.appName.text.toString().trim(),
            packageName = binding.packageName.text.toString().trim(),
            goal = binding.goal.text.toString().trim()
        )

        runCatching {
            AppScaffolder.files(spec)
        }.onSuccess { files ->
            pendingFiles = files

            val filename = spec.name
                .replace(
                    Regex("[^a-zA-Z0-9._-]"),
                    "_"
                )
                .ifBlank { "ZipBugProject" } + ".zip"

            saveZip.launch(filename)
        }.onFailure {
            Snackbar.make(
                binding.root,
                it.message ?: "Invalid project",
                Snackbar.LENGTH_LONG
            ).show()
        }
    }
}
