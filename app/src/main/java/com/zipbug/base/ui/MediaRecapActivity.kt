package com.zipbug.base.ui

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.zipbug.base.R
import com.zipbug.base.ZipBugApp
import com.zipbug.base.data.BuildJobEntity
import com.zipbug.base.databinding.ActivityMediaRecapBinding
import com.zipbug.base.termux.TermuxBridge
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

enum class PipelineStage(val title: String) {
    SOURCE("1. SOURCE"),
    DOWNLOAD("2. DOWNLOAD"),
    AUDIO("3. AUDIO (ffmpeg)"),
    TRANSCRIBE("4. TRANSCRIBE"),
    AI_RECAP("5. AI RECAP"),
    TTS("6. TTS (Edge-TTS)"),
    SUBTITLE("7. SUBTITLE"),
    RENDER("8. RENDER")
}

enum class StageState(val label: String) {
    PENDING("PENDING"),
    RUNNING("RUNNING"),
    SUCCESS("SUCCESS"),
    FAILED("FAILED"),
    SKIPPED("SKIPPED")
}

data class StageInfo(
    val stage: PipelineStage,
    var state: StageState = StageState.PENDING,
    var detail: String = ""
)

class MediaRecapActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMediaRecapBinding
    private var observer: Job? = null

    private val pipeline = mutableListOf(
        StageInfo(PipelineStage.SOURCE),
        StageInfo(PipelineStage.DOWNLOAD),
        StageInfo(PipelineStage.AUDIO),
        StageInfo(PipelineStage.TRANSCRIBE),
        StageInfo(PipelineStage.AI_RECAP),
        StageInfo(PipelineStage.TTS),
        StageInfo(PipelineStage.SUBTITLE),
        StageInfo(PipelineStage.RENDER)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMediaRecapBinding.inflate(layoutInflater)
        setContentView(binding.root)

        renderPipeline()

        binding.prepareMedia.setOnClickListener {
            prepareAuthorizedMedia()
        }

        binding.recap.setOnClickListener {
            buildPrompt("Recap")
            updateStage(PipelineStage.AI_RECAP, StageState.SUCCESS, "Recap prompt constructed")
        }
        binding.dialogue.setOnClickListener {
            buildPrompt("Recap + Dialogue")
            updateStage(PipelineStage.AI_RECAP, StageState.SUCCESS, "Dialogue recap prompt constructed")
        }
        binding.dub.setOnClickListener {
            buildPrompt("Dub")
            updateStage(PipelineStage.TTS, StageState.RUNNING, "Dub script prepared")
        }
    }

    private fun updateStage(stage: PipelineStage, state: StageState, detail: String = "") {
        pipeline.find { it.stage == stage }?.let {
            it.state = state
            it.detail = detail
        }
        renderPipeline()
    }

    private fun renderPipeline() {
        binding.pipelineContainer.removeAllViews()

        for (item in pipeline) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = 4
                    bottomMargin = 4
                }
            }

            val titleTv = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                text = item.stage.title
                setTextColor(ContextCompat.getColor(this@MediaRecapActivity, R.color.z_text))
                textSize = 12f
            }
            row.addView(titleTv)

            val badgeTv = TextView(this).apply {
                text = item.state.label
                textSize = 11f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(12, 4, 12, 4)
                when (item.state) {
                    StageState.SUCCESS -> {
                        setTextColor(ContextCompat.getColor(this@MediaRecapActivity, R.color.z_green))
                        setBackgroundColor(0xFF1E3A24.toInt())
                    }
                    StageState.RUNNING -> {
                        setTextColor(ContextCompat.getColor(this@MediaRecapActivity, R.color.z_orange))
                        setBackgroundColor(0xFF3E2D12.toInt())
                    }
                    StageState.FAILED -> {
                        setTextColor(ContextCompat.getColor(this@MediaRecapActivity, R.color.z_red))
                        setBackgroundColor(0xFF3D1818.toInt())
                    }
                    StageState.SKIPPED -> {
                        setTextColor(ContextCompat.getColor(this@MediaRecapActivity, R.color.z_muted))
                        setBackgroundColor(0xFF222222.toInt())
                    }
                    StageState.PENDING -> {
                        setTextColor(ContextCompat.getColor(this@MediaRecapActivity, R.color.z_muted))
                        setBackgroundColor(0xFF1B1B1B.toInt())
                    }
                }
            }
            row.addView(badgeTv)

            binding.pipelineContainer.addView(row)
        }
    }

    private fun prepareAuthorizedMedia() {
        val url = binding.url.text.toString().trim()

        if (!url.startsWith("https://") && !url.startsWith("http://")) {
            Snackbar.make(binding.root, "Enter a valid http(s) URL", Snackbar.LENGTH_SHORT).show()
            updateStage(PipelineStage.SOURCE, StageState.FAILED, "Invalid URL")
            return
        }

        updateStage(PipelineStage.SOURCE, StageState.SUCCESS, url)
        updateStage(PipelineStage.DOWNLOAD, StageState.RUNNING, "Starting yt-dlp")
        updateStage(PipelineStage.AUDIO, StageState.PENDING)

        val prefs = getSharedPreferences("zipbug.settings", MODE_PRIVATE)
        val home = prefs.getString("termuxHome", "/data/data/com.termux/files/home")!!
        val outputTemplate = "/storage/emulated/0/Download/ZipBugRecap_%(id)s.%(ext)s"

        binding.mediaStatus.text = "MEDIA • QUEUING REAL yt-dlp JOB"

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
            ).onSuccess { jobId ->
                observe(jobId)
            }.onFailure {
                binding.mediaStatus.text = "MEDIA • FAILED"
                binding.mediaLog.text = it.message ?: it.javaClass.simpleName
                updateStage(PipelineStage.DOWNLOAD, StageState.FAILED, it.message ?: "Job dispatch failed")
                updateStage(PipelineStage.AUDIO, StageState.SKIPPED)
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
        binding.mediaStatus.text = "MEDIA • " + job.status +
            (job.exitCode?.let { " • exit " + it } ?: "")

        when (job.status) {
            BuildJobEntity.RUNNING -> {
                updateStage(PipelineStage.DOWNLOAD, StageState.RUNNING, "Downloading...")
            }
            BuildJobEntity.SUCCESS -> {
                updateStage(PipelineStage.DOWNLOAD, StageState.SUCCESS, "Downloaded")
                updateStage(PipelineStage.AUDIO, StageState.SUCCESS, "Extracted MP3 via ffmpeg")
                updateStage(PipelineStage.TRANSCRIBE, StageState.PENDING, "Ready for audio transcription")
            }
            BuildJobEntity.FAILED -> {
                updateStage(PipelineStage.DOWNLOAD, StageState.FAILED, "Exit ${job.exitCode}")
                updateStage(PipelineStage.AUDIO, StageState.SKIPPED)
            }
        }

        binding.mediaLog.text = buildString {
            append("tool: ").append(job.tool)
            append("\nstatus: ").append(job.status)

            if (job.stdout.isNotBlank()) {
                append("\n\nSTDOUT\n").append(job.stdout)
            }

            if (job.stderr.isNotBlank()) {
                append("\n\nSTDERR\n").append(job.stderr)
            }

            if (job.errorMessage.isNotBlank()) {
                append("\n\nERROR\n").append(job.errorMessage)
            }

            if (job.status == BuildJobEntity.SUCCESS) {
                append("\n\nAudio extraction completed. Use the Termux log above to confirm file.")
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
