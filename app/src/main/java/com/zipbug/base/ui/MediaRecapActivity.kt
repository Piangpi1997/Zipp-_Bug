package com.zipbug.base.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.zipbug.base.ZipBugApp
import com.zipbug.base.data.BuildJobEntity
import com.zipbug.base.databinding.ActivityMediaRecapBinding
import com.zipbug.base.termux.TermuxBridge
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

class MediaRecapActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMediaRecapBinding
    private var observer: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMediaRecapBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.prepareMedia.setOnClickListener {
            prepareAuthorizedMedia()
        }

        binding.recap.setOnClickListener {
            buildPrompt("Recap")
        }
        binding.dialogue.setOnClickListener {
            buildPrompt("Recap + Dialogue")
        }
        binding.dub.setOnClickListener {
            buildPrompt("Dub")
        }
    }

    private fun prepareAuthorizedMedia() {
        val url = binding.url.text.toString().trim()

        if (
            !url.startsWith("https://") &&
            !url.startsWith("http://")
        ) {
            Snackbar.make(
                binding.root,
                "Enter a valid http(s) URL",
                Snackbar.LENGTH_SHORT
            ).show()
            return
        }

        val prefs = getSharedPreferences(
            "zipbug.settings",
            MODE_PRIVATE
        )
        val home = prefs.getString(
            "termuxHome",
            "/data/data/com.termux/files/home"
        )!!

        val outputTemplate =
            "/storage/emulated/0/Download/" +
                "ZipBugRecap_%(id)s.%(ext)s"

        binding.mediaStatus.text =
            "MEDIA • QUEUING REAL yt-dlp JOB"

        lifecycleScope.launch {
            TermuxBridge.send(
                this@MediaRecapActivity,
                TermuxBridge.Request(
                    tool = "yt-dlp",
                    args = listOf(
                        "--no-playlist",
                        "-x",
                        "--audio-format",
                        "mp3",
                        "--audio-quality",
                        "0",
                        "-o",
                        outputTemplate,
                        url
                    ),
                    workDir = home,
                    label = "Zip_Bug Authorized Media Prepare"
                )
            ).onSuccess {
                observe(it)
            }.onFailure {
                binding.mediaStatus.text = "MEDIA • FAILED"
                binding.mediaLog.text =
                    it.message ?: it.javaClass.simpleName
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
        binding.mediaStatus.text =
            "MEDIA • " + job.status +
                (job.exitCode?.let { " • exit " + it } ?: "")

        binding.mediaLog.text = buildString {
            append("tool: ")
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

            if (job.status == BuildJobEntity.SUCCESS) {
                append(
                    "\n\nAudio extraction command completed. " +
                        "Use the Termux log above to confirm the exact file."
                )
            }
        }
    }

    private fun buildPrompt(mode: String) {
        val url = binding.url.text.toString().trim()
        val language = binding.language.text.toString()

        binding.output.setText(
            mode + " workflow\n" +
                "1. Prepare media only when you are authorized to process it.\n" +
                "2. Transcribe the extracted audio.\n" +
                "3. Paste the transcript into the AI Provider tab.\n\n" +
                "AI prompt:\n" +
                "Create a concise social recap in " +
                language +
                ". Preserve facts, mark uncertainty, and add dialogue " +
                "only when present in the transcript.\n" +
                "Source reference: " +
                url
        )
    }

    override fun onDestroy() {
        observer?.cancel()
        super.onDestroy()
    }
}
