package com.zipbug.base.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.zipbug.base.ZipBugApp
import com.zipbug.base.data.BuildJobEntity
import com.zipbug.base.databinding.ActivityTtsStudioBinding
import com.zipbug.base.termux.TermuxBridge
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

class TtsStudioActivity : AppCompatActivity() {
    private lateinit var binding: ActivityTtsStudioBinding
    private var observer: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTtsStudioBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.femaleVoice.setOnClickListener {
            binding.voice.setText("my-MM-NilarNeural")
        }

        binding.maleVoice.setOnClickListener {
            binding.voice.setText("my-MM-ThihaNeural")
        }

        binding.installEdgeTts.setOnClickListener {
            installEdgeTts()
        }

        binding.generate.setOnClickListener {
            generate()
        }
    }

    private fun termuxHome(): String =
        getSharedPreferences("zipbug.settings", MODE_PRIVATE)
            .getString(
                "termuxHome",
                "/data/data/com.termux/files/home"
            )!!

    private fun installEdgeTts() {
        binding.status.text = "SETUP • QUEUING"

        lifecycleScope.launch {
            TermuxBridge.send(
                this@TtsStudioActivity,
                TermuxBridge.Request(
                    tool = "python",
                    args = listOf(
                        "-m",
                        "pip",
                        "install",
                        "--upgrade",
                        "edge-tts"
                    ),
                    workDir = termuxHome(),
                    label = "Zip_Bug Edge TTS Setup"
                )
            ).onSuccess {
                observe(it)
            }.onFailure {
                showError(it)
            }
        }
    }

    private fun generate() {
        val text = binding.text.text.toString().trim()
        val voice = binding.voice.text.toString().trim()
        val output = binding.outputPath.text.toString().trim()

        if (text.isBlank()) {
            Snackbar.make(
                binding.root,
                "Enter text first",
                Snackbar.LENGTH_SHORT
            ).show()
            return
        }

        if (voice.isBlank() || output.isBlank()) {
            Snackbar.make(
                binding.root,
                "Voice and output path are required",
                Snackbar.LENGTH_SHORT
            ).show()
            return
        }

        binding.status.text = "TTS • QUEUING"

        lifecycleScope.launch {
            TermuxBridge.send(
                this@TtsStudioActivity,
                TermuxBridge.Request(
                    tool = "edge-tts",
                    args = listOf(
                        "--text",
                        text,
                        "--voice",
                        voice,
                        "--write-media",
                        output
                    ),
                    workDir = termuxHome(),
                    label = "Zip_Bug TTS Generate"
                )
            ).onSuccess {
                observe(it)
            }.onFailure {
                showError(it)
            }
        }
    }

    private fun observe(jobId: String) {
        observer?.cancel()

        val app = application as ZipBugApp
        observer = lifecycleScope.launch {
            app.database.buildJobDao()
                .observe(jobId)
                .filterNotNull()
                .collect { job ->
                    render(job)
                }
        }
    }

    private fun render(job: BuildJobEntity) {
        binding.status.text =
            "TTS • " + job.status +
                (job.exitCode?.let { " • exit " + it } ?: "")

        binding.log.text = buildString {
            append("job: ")
            append(job.id)
            append("\ntool: ")
            append(job.tool)
            append("\nstatus: ")
            append(job.status)

            if (job.stdout.isNotBlank()) {
                append("\n\nSTDOUT\n")
                append(job.stdout)
            }

            if (job.stderr.isNotBlank()) {
                append("\n\nSTDERR\n")
                append(job.stderr)
            }

            if (job.errorMessage.isNotBlank()) {
                append("\n\nERROR\n")
                append(job.errorMessage)
            }

            if (
                job.tool == "edge-tts" &&
                job.status == BuildJobEntity.SUCCESS
            ) {
                append("\n\nAUDIO OUTPUT REQUESTED\n")
                append(binding.outputPath.text.toString())
            }
        }
    }

    private fun showError(error: Throwable) {
        binding.status.text = "TTS • FAILED"
        binding.log.text =
            error.message ?: error.javaClass.simpleName
    }

    override fun onDestroy() {
        observer?.cancel()
        super.onDestroy()
    }
}
