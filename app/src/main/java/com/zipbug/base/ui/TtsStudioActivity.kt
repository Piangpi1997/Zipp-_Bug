package com.zipbug.base.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.zipbug.base.ZipBugApp
import com.zipbug.base.data.BuildJobEntity
import com.zipbug.base.databinding.ActivityTtsStudioBinding
import com.zipbug.base.termux.TermuxBridge
import com.zipbug.base.tts.EdgeTtsHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import java.io.File

class TtsStudioActivity : AppCompatActivity() {
    private lateinit var binding: ActivityTtsStudioBinding
    private var observer: Job? = null
    private var currentAudioTarget: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTtsStudioBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.femaleVoice.setOnClickListener {
            binding.voice.setText(EdgeTtsHelper.VOICE_NILAR)
        }

        binding.maleVoice.setOnClickListener {
            binding.voice.setText(EdgeTtsHelper.VOICE_THIHA)
        }

        binding.installEdgeTts.setOnClickListener {
            installEdgeTts()
        }

        binding.btnPreview.setOnClickListener {
            val fullText = binding.text.text.toString().trim()
            if (fullText.isBlank()) {
                Snackbar.make(binding.root, "Enter text first", Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val previewSnippet = EdgeTtsHelper.createPreviewText(fullText)
            val previewOutput = "/storage/emulated/0/Download/Zip_Bug_TTS_Preview.mp3"
            generateTts(previewSnippet, previewOutput, isPreview = true)
        }

        binding.generate.setOnClickListener {
            val text = binding.text.text.toString().trim()
            val output = binding.outputPath.text.toString().trim()
            generateTts(text, output, isPreview = false)
        }

        binding.btnPlayAudio.setOnClickListener {
            playAudio()
        }

        binding.btnShareAudio.setOnClickListener {
            shareAudio()
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

    private fun generateTts(text: String, output: String, isPreview: Boolean = false) {
        val voice = binding.voice.text.toString().trim()
        val rate = binding.rate.text.toString().trim()
        val pitch = binding.pitch.text.toString().trim()

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

        currentAudioTarget = File(output)
        binding.actionRow.visibility = View.GONE
        binding.status.text = if (isPreview) "PREVIEW • QUEUING" else "TTS • QUEUING"

        val request = EdgeTtsHelper.buildRequest(
            text = text,
            outputFilePath = output,
            voice = voice,
            rate = rate.takeIf { it.isNotBlank() },
            pitch = pitch.takeIf { it.isNotBlank() },
            workDir = termuxHome()
        )

        lifecycleScope.launch {
            TermuxBridge.send(this@TtsStudioActivity, request)
                .onSuccess { observe(it) }
                .onFailure { showError(it) }
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
        val exit = job.exitCode?.let { " • exit $it" } ?: ""

        val isTts = job.tool == "edge-tts"
        val isSuccess = job.status == BuildJobEntity.SUCCESS

        val target = currentAudioTarget ?: File(binding.outputPath.text.toString())
        val artifactValid = isSuccess && isTts && target.exists() && target.length() > 0

        binding.status.text = when {
            artifactValid -> "TTS • SUCCESS (${target.length()} bytes)"
            isSuccess && isTts -> "TTS • ARTIFACT MISSING (0 bytes or unreadable)"
            else -> "TTS • ${job.status}$exit"
        }

        if (artifactValid) {
            binding.actionRow.visibility = View.VISIBLE
        } else {
            binding.actionRow.visibility = View.GONE
        }

        binding.log.text = buildString {
            append("job: ${job.id}\n")
            append("tool: ${job.tool}\n")
            append("status: ${job.status}")
            if (job.exitCode != null) append("\nexitCode: ${job.exitCode}")

            if (job.stdout.isNotBlank()) {
                append("\n\nSTDOUT\n${job.stdout}")
            }
            if (job.stderr.isNotBlank()) {
                append("\n\nSTDERR\n${job.stderr}")
            }
            if (job.errorMessage.isNotBlank()) {
                append("\n\nERROR\n${job.errorMessage}")
            }

            if (isTts && isSuccess) {
                if (artifactValid) {
                    append("\n\nVERIFIED AUDIO ARTIFACT:\n${target.absolutePath} (${target.length()} bytes)")
                } else {
                    append("\n\nWARNING: Exit 0 but file not found on storage:\n${target.absolutePath}")
                }
            }
        }
    }

    private fun playAudio() {
        val file = currentAudioTarget ?: File(binding.outputPath.text.toString())
        if (!file.exists() || file.length() == 0L) {
            Snackbar.make(binding.root, "Audio file does not exist", Snackbar.LENGTH_SHORT).show()
            return
        }
        val uri = getFileUri(file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "audio/mpeg")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(Intent.createChooser(intent, "Play TTS Audio"))
        } catch (e: Exception) {
            Snackbar.make(binding.root, "Cannot open audio player: ${e.message}", Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun shareAudio() {
        val file = currentAudioTarget ?: File(binding.outputPath.text.toString())
        if (!file.exists() || file.length() == 0L) {
            Snackbar.make(binding.root, "Audio file does not exist", Snackbar.LENGTH_SHORT).show()
            return
        }
        val uri = getFileUri(file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "audio/mpeg"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(Intent.createChooser(intent, "Share TTS Audio"))
        } catch (e: Exception) {
            Snackbar.make(binding.root, "Share failed: ${e.message}", Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun getFileUri(file: File): Uri =
        try {
            FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        } catch (_: Exception) {
            Uri.fromFile(file)
        }

    private fun showError(error: Throwable) {
        binding.status.text = "TTS • FAILED"
        binding.log.text = error.message ?: error.javaClass.simpleName
        binding.actionRow.visibility = View.GONE
    }

    override fun onDestroy() {
        observer?.cancel()
        super.onDestroy()
    }
}
