package com.zipbug.base.ui

import android.content.Intent
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.zipbug.base.ZipBugApp
import com.zipbug.base.data.BuildJobEntity
import com.zipbug.base.databinding.ActivityTtsStudioBinding
import com.zipbug.base.recap.SubtitleExporter
import com.zipbug.base.termux.TermuxBridge
import com.zipbug.base.tts.EdgeTtsHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

class TtsStudioActivity : AppCompatActivity() {
    private lateinit var binding: ActivityTtsStudioBinding
    private var observer: Job? = null
    private var currentAudioTarget: File? = null
    private var mediaPlayer: MediaPlayer? = null
    private var pendingExportFormat = SubtitleExporter.FORMAT_SRT
    private var lastSyncedTtsJobId: String? = null

    private val safExporter = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null) {
            exportScriptToUri(uri, pendingExportFormat)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTtsStudioBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fun selectVoicePill(activeBtn: android.widget.Button, voiceId: String) {
            binding.voice.setText(voiceId)
            val allBtns = listOf(
                binding.femaleVoice,
                binding.maleVoice,
                binding.englishVoice,
                binding.thaiVoice,
                binding.chineseVoice
            )
            allBtns.forEach { btn ->
                val isSelected = btn == activeBtn
                btn.backgroundTintList = null
                btn.setBackgroundResource(
                    if (isSelected) com.zipbug.base.R.drawable.bg_chip_active
                    else com.zipbug.base.R.drawable.bg_chip_inactive
                )
                btn.setTextColor(
                    androidx.core.content.ContextCompat.getColor(
                        this,
                        if (isSelected) com.zipbug.base.R.color.z_white else com.zipbug.base.R.color.z_text
                    )
                )
            }
        }

        binding.femaleVoice.setOnClickListener {
            selectVoicePill(binding.femaleVoice, EdgeTtsHelper.VOICE_NILAR)
        }
        if (binding.text.text.isNullOrBlank()) {
            binding.text.setText("မင်္ဂလာပါ၊ Zip_Bug Studio မှ ကြိုဆိုပါတယ်။ Audio and subtitle export is verified.")
        }

        binding.maleVoice.setOnClickListener {
            selectVoicePill(binding.maleVoice, EdgeTtsHelper.VOICE_THIHA)
        }

        binding.englishVoice.setOnClickListener {
            selectVoicePill(binding.englishVoice, EdgeTtsHelper.VOICE_JENNY)
        }

        binding.thaiVoice.setOnClickListener {
            selectVoicePill(binding.thaiVoice, EdgeTtsHelper.VOICE_PREMWADEE)
        }

        binding.chineseVoice.setOnClickListener {
            selectVoicePill(binding.chineseVoice, EdgeTtsHelper.VOICE_XIAOXIAO)
        }
        selectVoicePill(binding.femaleVoice, EdgeTtsHelper.VOICE_NILAR)

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

        binding.btnTtsExportTxt.setOnClickListener {
            triggerExport(SubtitleExporter.FORMAT_TXT, "zipbug_narration.txt")
        }
        binding.btnTtsExportSrt.setOnClickListener {
            triggerExport(SubtitleExporter.FORMAT_SRT, "zipbug_narration.srt")
        }
        binding.btnTtsExportVtt.setOnClickListener {
            triggerExport(SubtitleExporter.FORMAT_VTT, "zipbug_narration.vtt")
        }

        binding.btnPlayInApp.setOnClickListener {
            toggleInAppPlayback()
        }

        binding.btnPlayAudio.setOnClickListener {
            playAudio()
        }

        binding.btnShareAudio.setOnClickListener {
            shareAudio()
        }
    }

    private fun triggerExport(format: String, defaultFilename: String) {
        val rawText = binding.text.text.toString().trim()
        if (rawText.isBlank()) {
            Snackbar.make(binding.root, "Enter narration text before exporting", Snackbar.LENGTH_SHORT).show()
            return
        }
        pendingExportFormat = format
        val cues = SubtitleExporter.buildCuesFromPlainText(rawText)
        val content = when (format) {
            SubtitleExporter.FORMAT_SRT -> SubtitleExporter.cuesToSrt(cues)
            SubtitleExporter.FORMAT_VTT -> SubtitleExporter.cuesToVtt(cues)
            else -> "# NOTE: ${SubtitleExporter.TIMING_NOTE}\n\n$rawText\n"
        }
        val localDir = File(filesDir, "subtitles").apply { mkdirs() }
        val localFile = File(localDir, defaultFilename)
        localFile.writeText(content, Charsets.UTF_8)
        binding.status.text =
            "EXPORT • VERIFIED $format (${localFile.length()} bytes • estimated timing from text length)"
        Snackbar.make(
            binding.root,
            "Verified $format (${localFile.length()} B, estimated timing)",
            Snackbar.LENGTH_LONG
        ).setAction("Save As…") {
            runCatching { safExporter.launch(defaultFilename) }
        }.show()
    }

    private fun exportScriptToUri(uri: Uri, format: String) {
        val rawText = binding.text.text.toString().trim()
        val cues = SubtitleExporter.buildCuesFromPlainText(rawText)
        val content = when (format) {
            SubtitleExporter.FORMAT_SRT -> SubtitleExporter.cuesToSrt(cues)
            SubtitleExporter.FORMAT_VTT -> SubtitleExporter.cuesToVtt(cues)
            else -> "# NOTE: ${SubtitleExporter.TIMING_NOTE}\n\n$rawText\n"
        }
        runCatching {
            contentResolver.openOutputStream(uri)?.use { out ->
                val bytes = content.toByteArray(Charsets.UTF_8)
                out.write(bytes)
                out.flush()
                bytes.size
            } ?: error("Cannot open destination URI")
        }.onSuccess { bytes ->
            Snackbar.make(binding.root, "Exported $format ($bytes bytes)", Snackbar.LENGTH_SHORT).show()
        }.onFailure { err ->
            Snackbar.make(binding.root, "Export failed: ${err.message}", Snackbar.LENGTH_SHORT).show()
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

        val target = runCatching { validatedSharedAudioTarget(output) }
            .getOrElse { error ->
                Snackbar.make(
                    binding.root,
                    error.message ?: "Choose a valid shared audio output path",
                    Snackbar.LENGTH_LONG
                ).show()
                return
            }

        stopInAppPlayer()
        currentAudioTarget = target
        binding.actionRow.visibility = View.GONE
        binding.status.text = if (isPreview) "PREVIEW • QUEUING" else "TTS • QUEUING"

        val request = EdgeTtsHelper.buildRequest(
            text = text,
            outputFilePath = target.absolutePath,
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
        val isSuccess = job.status == BuildJobEntity.SUCCESS

        if (isSuccess && job.tool == "python" && job.stdout.contains("AUDIO_B64_START:")) {
            val b64 = job.stdout.substringAfter("AUDIO_B64_START:").substringBefore(":AUDIO_B64_END").trim()
            if (b64.isNotEmpty()) {
                val origTarget = currentAudioTarget ?: File(binding.outputPath.text.toString())
                val localDir = File(filesDir, "tts").apply { mkdirs() }
                val localCopy = File(localDir, origTarget.name.ifBlank { "Zip_Bug_TTS.mp3" })
                runCatching {
                    localCopy.writeBytes(Base64.decode(b64, Base64.DEFAULT))
                }
                if (localCopy.exists() && localCopy.length() > 0L) {
                    currentAudioTarget = localCopy
                }
            }
        }

        val isTts = job.tool == "edge-tts" || (job.tool == "python" && job.stdout.contains("AUDIO_B64_START:"))
        val target = currentAudioTarget ?: File(binding.outputPath.text.toString())
        val isDirectlyReadable = target.exists() && target.length() > 0 &&
            runCatching { target.inputStream().use { it.read() >= 0 } }.getOrDefault(false)
        val artifactValid = isSuccess && isTts && isDirectlyReadable

        if (isSuccess && job.tool == "edge-tts" && !isDirectlyReadable && lastSyncedTtsJobId != job.id) {
            lastSyncedTtsJobId = job.id
            syncAudioFromTermux(target)
            return
        }

        val durInfo = if (artifactValid) {
            val dMs = extractAudioDurationMs(target)
            if (dMs > 0L) ", ${String.format(Locale.US, "%.1fs", dMs / 1000.0)}" else ""
        } else ""

        binding.status.text = when {
            artifactValid -> "TTS • SUCCESS (${target.length()} bytes$durInfo)"
            isSuccess && isTts -> "TTS • ARTIFACT MISSING (0 bytes or unreadable)"
            else -> "TTS • ${job.status}$exit"
        }

        if (artifactValid) {
            binding.actionRow.visibility = View.VISIBLE
        } else {
            binding.actionRow.visibility = View.GONE
        }

        val cleanedStdout = job.stdout.replace(Regex("AUDIO_B64_START:[A-Za-z0-9+/=\\s]+:AUDIO_B64_END"), "[AUDIO_SYNCED_TO_APP_STORAGE]")

        binding.log.text = buildString {
            append("job: ${job.id}\n")
            append("tool: ${job.tool}\n")
            append("status: ${job.status}")
            if (job.exitCode != null) append("\nexitCode: ${job.exitCode}")

            if (cleanedStdout.isNotBlank()) {
                append("\n\nSTDOUT\n$cleanedStdout")
            }
            if (job.stderr.isNotBlank()) {
                append("\n\nSTDERR\n${job.stderr}")
            }
            if (job.errorMessage.isNotBlank()) {
                append("\n\nERROR\n${job.errorMessage}")
            }

            if (isTts && isSuccess) {
                if (artifactValid) {
                    append("\n\nVERIFIED AUDIO ARTIFACT:\n${target.absolutePath} (${target.length()} bytes$durInfo)")
                } else {
                    append("\n\nWARNING: Exit 0 but file not found on storage:\n${target.absolutePath}")
                }
            }
        }
    }

    private fun syncAudioFromTermux(remoteFile: File) {
        binding.status.text = "TTS • SYNCING AUDIO ARTIFACT..."
        val pyScript = """
            import base64, os, sys
            p = sys.argv[1]
            if not os.path.exists(p) or os.path.getsize(p) == 0:
                sys.stderr.write("Missing or empty file: " + p + "\n")
                sys.exit(2)
            with open(p, "rb") as f:
                data = f.read()
            sys.stdout.write("AUDIO_SIZE:" + str(len(data)) + "\n")
            sys.stdout.write("AUDIO_B64_START:" + base64.b64encode(data).decode("ascii") + ":AUDIO_B64_END\n")
        """.trimIndent()
        lifecycleScope.launch {
            TermuxBridge.send(
                this@TtsStudioActivity,
                TermuxBridge.Request(
                    tool = "python",
                    args = listOf("-c", pyScript, remoteFile.absolutePath),
                    workDir = termuxHome(),
                    label = "Sync TTS Audio (${remoteFile.name})"
                )
            ).onSuccess { observe(it) }
                .onFailure { showError(it) }
        }
    }

    private fun extractAudioDurationMs(file: File): Long {
        var duration = 0L
        runCatching {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(file.absolutePath)
            duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            retriever.release()
        }
        return duration
    }

    private fun toggleInAppPlayback() {
        val existing = mediaPlayer
        if (existing != null && existing.isPlaying) {
            stopInAppPlayer()
            return
        }
        val file = currentAudioTarget ?: File(binding.outputPath.text.toString())
        if (!file.exists() || file.length() == 0L) {
            Snackbar.make(binding.root, "Audio file does not exist", Snackbar.LENGTH_SHORT).show()
            return
        }
        runCatching {
            stopInAppPlayer()
            val mp = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
                setOnCompletionListener {
                    binding.btnPlayInApp.text = "▶ Play In-App"
                }
                start()
            }
            mediaPlayer = mp
            val durSec = (mp.duration / 1000.0)
            binding.btnPlayInApp.text = "■ Stop (${String.format(Locale.US, "%.1fs", durSec)})"
            binding.status.text = "TTS • PLAYING (${file.name}, ${String.format(Locale.US, "%.1fs", durSec)})"
        }.onFailure { err ->
            Snackbar.make(binding.root, "In-app playback failed: ${err.message}", Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun stopInAppPlayer() {
        runCatching {
            mediaPlayer?.stop()
        }
        runCatching {
            mediaPlayer?.release()
        }
        mediaPlayer = null
        binding.btnPlayInApp.text = "▶ Play In-App"
    }

    private fun playAudio() {
        val file = currentAudioTarget ?: File(binding.outputPath.text.toString())
        if (!file.exists() || file.length() == 0L) {
            Snackbar.make(binding.root, "Audio file does not exist", Snackbar.LENGTH_SHORT).show()
            return
        }

        runCatching {
            val uri = getFileUri(file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "audio/mpeg")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Play TTS Audio"))
        }.onFailure { error ->
            Snackbar.make(
                binding.root,
                "Cannot open audio player: ${error.message}",
                Snackbar.LENGTH_LONG
            ).show()
        }
    }

    private fun shareAudio() {
        val file = currentAudioTarget ?: File(binding.outputPath.text.toString())
        if (!file.exists() || file.length() == 0L) {
            Snackbar.make(binding.root, "Audio file does not exist", Snackbar.LENGTH_SHORT).show()
            return
        }

        runCatching {
            val uri = getFileUri(file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "audio/mpeg"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Share TTS Audio"))
        }.onFailure { error ->
            Snackbar.make(
                binding.root,
                "Share failed: ${error.message}",
                Snackbar.LENGTH_LONG
            ).show()
        }
    }

    private fun getFileUri(file: File): Uri =
        FileProvider.getUriForFile(
            this,
            "$packageName.fileprovider",
            file
        )

    private fun validatedSharedAudioTarget(path: String): File {
        val file = File(path)
        val canonical = file.canonicalPath
        require(
            canonical.startsWith("/storage/emulated/0/Download/") ||
                canonical.startsWith("/storage/emulated/0/Music/")
        ) {
            "Save audio under shared Download or Music so Play/Share can access it safely."
        }
        require(file.name.endsWith(".mp3", ignoreCase = true)) {
            "Audio output must use an .mp3 filename."
        }
        return file
    }

    private fun showError(error: Throwable) {
        binding.status.text = "TTS • FAILED"
        binding.log.text = error.message ?: error.javaClass.simpleName
        binding.actionRow.visibility = View.GONE
    }

    override fun onDestroy() {
        stopInAppPlayer()
        observer?.cancel()
        super.onDestroy()
    }
}
