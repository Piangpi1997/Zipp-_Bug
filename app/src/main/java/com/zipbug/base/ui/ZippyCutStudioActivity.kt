package com.zipbug.base.ui

import android.content.ContentValues
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.zipbug.base.ZipBugApp
import com.zipbug.base.data.BuildJobEntity
import com.zipbug.base.databinding.ActivityZippyCutStudioBinding
import com.zipbug.base.recap.RecapMediaStager
import com.zipbug.base.recap.RecapRenderCoordinator
import com.zipbug.base.termux.LogSanitizer
import com.zipbug.base.termux.TermuxBridge
import com.zipbug.base.zippy.ZippyCutPlan
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/**
 * Zippy Cut Studio — a native (not WebView) video studio modelled on the
 * reference Zippy_Cut UI (dark panels, coral accent, timeline-first layout)
 * but wired to ZIP_BUG's real engines:
 * - metadata/preview: Android MediaMetadataRetriever + VideoView
 * - staged media: RecapMediaStager (shared Downloads copy Termux can read)
 * - render: ffmpeg argument list from ZippyCutPlan, executed by the existing
 *   TermuxBridge job pipeline (queued/running/success/failed are the real
 *   Room job states; no progress percentage is invented)
 * - drafts: edit state JSON in app-private storage, restored on launch.
 *   The original media file is never modified.
 */
class ZippyCutStudioActivity : AppCompatActivity() {

    private lateinit var binding: ActivityZippyCutStudioBinding
    private var state = ZippyCutPlan.EditState()
    private var outputPath: String = ""
    private var observerJob: Job? = null
    private val seekHandler = Handler(Looper.getMainLooper())
    private var destroyed = false

    private fun isMy(): Boolean = AppLanguage.selected(this) == AppLanguage.BURMESE

    private fun L(my: String, en: String): String = if (isMy()) my else en

    private val pickVideo = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) onVideoPicked(uri)
    }

    private val seekTicker = object : Runnable {
        override fun run() {
            if (destroyed) return
            runCatching {
                if (binding.videoPreview.isPlaying) {
                    binding.seekPreview.progress = binding.videoPreview.currentPosition
                }
            }
            seekHandler.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityZippyCutStudioBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.tvZippySub.text = L(
            "ဗီဒီယို တည်းဖြတ်ခန်း • Termux ffmpeg engine",
            "Video editor • Termux ffmpeg engine"
        )
        binding.tvAutoCaption.text = L(
            "Auto Caption: ပိတ်ထားသည် — transcription provider / API key လိုအပ်သည်။ Manual စာတန်း အပြည့်အဝ သုံးနိုင်သည်။",
            "Auto Caption: DISABLED — a transcription provider / API key is required. Manual subtitles work fully."
        )

        restoreDraft()
        bindActions()
        seekHandler.post(seekTicker)
    }

    private fun bindActions() {
        binding.btnPickVideo.setOnClickListener {
            pickVideo.launch(arrayOf("video/*"))
        }
        binding.btnPlayPause.setOnClickListener {
            val vv = binding.videoPreview
            if (vv.isPlaying) {
                vv.pause()
                binding.btnPlayPause.text = "▶ Play"
            } else {
                vv.start()
                binding.btnPlayPause.text = "⏸ Pause"
            }
        }
        binding.seekPreview.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) binding.videoPreview.seekTo(progress)
            }
            override fun onStartTrackingTouch(bar: SeekBar?) = Unit
            override fun onStopTrackingTouch(bar: SeekBar?) = Unit
        })
        binding.btnAddSubtitle.setOnClickListener { addSubtitleFromInputs() }
        binding.btnExport.setOnClickListener { startExport() }
        binding.btnPreviewOutput.setOnClickListener {
            if (outputPath.isNotBlank()) {
                binding.videoPreview.setVideoPath(outputPath)
                binding.videoPreview.start()
            }
        }
        binding.btnShareOutput.setOnClickListener { shareOutput() }
    }

    // -------- import / metadata --------

    private fun onVideoPicked(uri: Uri) {
        runCatching {
            contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        val meta = readMetadata(uri)
        state = state.copy(
            sourceUri = uri.toString(),
            sourceName = meta.name,
            durationMs = meta.durationMs,
            width = meta.width,
            height = meta.height,
            trimStartMs = 0L,
            trimEndMs = meta.durationMs,
            splitMs = null,
            stagedPath = ""
        )
        binding.videoPreview.setVideoURI(uri)
        binding.seekPreview.max = meta.durationMs.toInt().coerceAtLeast(0)
        binding.etTrimStart.setText("0")
        binding.etTrimEnd.setText(String.format(Locale.US, "%.3f", meta.durationMs / 1000.0))
        binding.etSplit.setText("")
        updateMetaText()
        refreshSegmentsText()
        saveDraft()
        // Stage a Termux-readable copy, exactly like Recap Studio does.
        binding.tvExportStatus.text = L(
            "အခြေအနေ: ဗီဒီယိုကို Termux ဖတ်နိုင်အောင် ကူးနေသည်…",
            "Status: staging a Termux-readable copy…"
        )
        lifecycleScope.launch {
            val staged = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                RecapMediaStager.stage(this@ZippyCutStudioActivity, uri, "zippycut")
            }
            staged.fold(
                onSuccess = { media ->
                    state = state.copy(stagedPath = media.localPath)
                    binding.tvExportStatus.text = L(
                        "အခြေအနေ: အဆင်သင့်ဖြစ်ပြီ (staged copy ရပြီး)",
                        "Status: ready (staged copy available)"
                    )
                    saveDraft()
                },
                onFailure = { err ->
                    binding.tvExportStatus.text = L(
                        "အခြေအနေ: staging မအောင်မြင်ပါ — ${err.message}",
                        "Status: staging failed — ${err.message}"
                    )
                }
            )
        }
    }

    private data class Meta(val name: String, val durationMs: Long, val width: Int, val height: Int)

    private fun readMetadata(uri: Uri): Meta {
        var name = "video"
        runCatching {
            contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) name = c.getString(0) ?: name }
        }
        var duration = 0L
        var width = 0
        var height = 0
        runCatching {
            val r = MediaMetadataRetriever()
            r.setDataSource(this, uri)
            duration = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            width = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            height = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            r.release()
        }
        return Meta(name, duration, width, height)
    }

    private fun updateMetaText() {
        binding.tvMeta.text = if (state.durationMs > 0) {
            "${state.sourceName} • ${String.format(Locale.US, "%.1fs", state.durationMs / 1000.0)}" +
                if (state.width > 0) " • ${state.width}×${state.height}" else ""
        } else {
            L("ဗီဒီယို မရွေးရသေးပါ", "No video selected")
        }
    }

    // -------- state <-> fields --------

    private fun secondsToMs(text: String): Long? =
        text.trim().toDoubleOrNull()?.let { (it * 1000).toLong() }

    private fun collectState() {
        val subStart = state.trimStartMs
        val start = secondsToMs(binding.etTrimStart.text.toString()) ?: subStart
        val end = secondsToMs(binding.etTrimEnd.text.toString()) ?: state.trimEndMs
        val splitText = binding.etSplit.text.toString().trim()
        val split = if (splitText.isEmpty()) null else secondsToMs(splitText)
        state = state.copy(
            trimStartMs = start,
            trimEndMs = end,
            splitMs = split,
            title = binding.etTitle.text.toString(),
            keepAudio = binding.cbKeepAudio.isChecked
        )
    }

    private fun refreshSegmentsText() {
        val err = ZippyCutPlan.validate(state)
        binding.tvSegments.text = if (err != null) {
            "Segments: $err"
        } else {
            "Segments: " + ZippyCutPlan.segments(state).joinToString("  +  ") {
                "${ZippyCutPlan.formatSeconds(it.startMs)}s–${ZippyCutPlan.formatSeconds(it.endMs)}s"
            }
        }
    }

    private fun addSubtitleFromInputs() {
        val start = secondsToMs(binding.etSubStart.text.toString())
        val end = secondsToMs(binding.etSubEnd.text.toString())
        val text = binding.etSubText.text.toString().trim()
        if (start == null || end == null || text.isEmpty()) {
            Toast.makeText(this, L("စာတန်း အစ/အဆုံး/စာသား အပြည့်ဖြည့်ပါ", "Fill subtitle start, end and text"), Toast.LENGTH_SHORT).show()
            return
        }
        val line = ZippyCutPlan.SubtitleLine(start, end, text)
        state = state.copy(subtitles = (state.subtitles + line).sortedBy { it.startMs })
        binding.etSubStart.setText("")
        binding.etSubEnd.setText("")
        binding.etSubText.setText("")
        renderSubtitleRows()
        saveDraft()
    }

    private fun renderSubtitleRows() {
        val box = binding.subtitleRows
        box.removeAllViews()
        state.subtitles.forEachIndexed { idx, line ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            val label = TextView(this).apply {
                text = "${ZippyCutPlan.formatSeconds(line.startMs)}s–${ZippyCutPlan.formatSeconds(line.endMs)}s  ${line.text}"
                setTextColor(0xFFEEF3F6.toInt())
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val del = Button(this).apply {
                text = "✕"
                setOnClickListener {
                    state = state.copy(subtitles = state.subtitles.filterIndexed { i, _ -> i != idx })
                    renderSubtitleRows()
                    saveDraft()
                }
            }
            row.addView(label)
            row.addView(del)
            box.addView(row)
        }
    }

    // -------- draft persistence --------

    private fun draftFile(): File =
        File(File(filesDir, "zippycut").apply { mkdirs() }, "draft.json")

    private fun saveDraft() {
        if (!::binding.isInitialized) return
        collectState()
        runCatching { draftFile().writeText(ZippyCutPlan.toJson(state), Charsets.UTF_8) }
    }

    private fun restoreDraft() {
        val file = draftFile()
        if (!file.exists()) {
            updateMetaText()
            return
        }
        runCatching {
            state = ZippyCutPlan.fromJson(file.readText(Charsets.UTF_8))
            binding.etTitle.setText(state.title)
            binding.cbKeepAudio.isChecked = state.keepAudio
            binding.etTrimStart.setText(String.format(Locale.US, "%.3f", state.trimStartMs / 1000.0))
            binding.etTrimEnd.setText(String.format(Locale.US, "%.3f", state.trimEndMs / 1000.0))
            binding.etSplit.setText(state.splitMs?.let { String.format(Locale.US, "%.3f", it / 1000.0) } ?: "")
            renderSubtitleRows()
            if (state.sourceUri.isNotBlank()) {
                val uri = Uri.parse(state.sourceUri)
                binding.videoPreview.setVideoURI(uri)
                binding.seekPreview.max = state.durationMs.toInt().coerceAtLeast(0)
            }
        }
        updateMetaText()
        refreshSegmentsText()
    }

    // -------- export via Termux engine (same path as Recap Studio) --------

    private fun startExport() {
        collectState()
        refreshSegmentsText()
        val validation = ZippyCutPlan.validate(state)
        if (validation != null) {
            binding.tvExportStatus.text = "Export status: FAILED — $validation"
            return
        }
        if (state.stagedPath.isBlank()) {
            binding.tvExportStatus.text = L(
                "Export အခြေအနေ: FAILED — staged copy မရှိသေးပါ (ဗီဒီယို ပြန်ရွေးပြီး staging စောင့်ပါ)",
                "Export status: FAILED — no staged copy yet (re-pick the video and wait for staging)"
            )
            return
        }
        val inputPath = state.stagedPath
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val safeName = state.sourceName.replace(Regex("[^a-zA-Z0-9_-]"), "_").ifBlank { "zippy" }
        outputPath = "/storage/emulated/0/Download/ZippyCut_${safeName}_$ts.mp4"

        var srtPath: String? = null
        if (state.subtitles.isNotEmpty()) {
            val srt = ZippyCutPlan.subtitlesToSrt(state.subtitles)
            runCatching { File(File(filesDir, "zippycut"), "draft.srt").writeText(srt, Charsets.UTF_8) }
            srtPath = stageSrtToShared(srt, "ZippyCut_$ts.srt")
            if (srtPath == null) {
                binding.tvExportStatus.text = L(
                    "Export အခြေအနေ: FAILED — စာတန်း .srt ကို shared storage သို့ မတင်နိုင်ပါ",
                    "Export status: FAILED — could not place the .srt file in shared storage"
                )
                return
            }
        }
        val font = listOf(
            "/system/fonts/Roboto-Regular.ttf",
            "/system/fonts/NotoSans-Regular.ttf",
            "/system/fonts/DroidSans.ttf"
        ).firstOrNull { File(it).exists() }

        val argsResult = ZippyCutPlan.buildFfmpegArgs(
            state = state,
            inputPath = inputPath,
            outputPath = outputPath,
            subtitlesPath = srtPath,
            fontFile = font
        )
        if (argsResult.isFailure) {
            binding.tvExportStatus.text = "Export status: FAILED — ${argsResult.exceptionOrNull()?.message}"
            return
        }
        binding.btnExport.isEnabled = false
        binding.tvExportStatus.text = L(
            "Export အခြေအနေ: QUEUED • Termux သို့ ပို့နေသည်… (ရာခိုင်နှုန်း မပြပါ — တကယ့် အခြေအနေကိုသာ ပြသည်)",
            "Export status: QUEUED • dispatching to Termux… (no fake percentage — real status only)"
        )
        lifecycleScope.launch {
            TermuxBridge.send(
                this@ZippyCutStudioActivity,
                TermuxBridge.Request(
                    tool = "ffmpeg",
                    args = argsResult.getOrThrow(),
                    label = "Zippy Cut Export"
                )
            ).fold(
                onSuccess = { termuxJobId -> observeExport(termuxJobId) },
                onFailure = { ex ->
                    binding.btnExport.isEnabled = true
                    binding.tvExportStatus.text = "Export status: FAILED — ${ex.message}"
                }
            )
        }
        saveDraft()
    }

    /** Places the SRT where the Termux ffmpeg process can read it (shared Downloads). */
    private fun stageSrtToShared(srt: String, name: String): String? {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/x-subrip")
                    put(
                        MediaStore.MediaColumns.RELATIVE_PATH,
                        Environment.DIRECTORY_DOWNLOADS + "/Zip_Bug/Recap"
                    )
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: error("insert failed")
                contentResolver.openOutputStream(uri, "w")?.use {
                    it.write(srt.toByteArray(Charsets.UTF_8))
                } ?: error("open failed")
                contentResolver.update(
                    uri,
                    ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                    null, null
                )
                "/storage/emulated/0/Download/Zip_Bug/Recap/$name"
            } else {
                val dir = File(getExternalFilesDir(null), "zippycut").apply { mkdirs() }
                val f = File(dir, name)
                f.writeText(srt, Charsets.UTF_8)
                f.absolutePath
            }
        }.getOrNull()
    }

    private fun observeExport(termuxJobId: String) {
        observerJob?.cancel()
        val db = (application as ZipBugApp).database
        observerJob = lifecycleScope.launch {
            db.buildJobDao().observe(termuxJobId).filterNotNull().collect { job ->
                onExportJobUpdate(job)
            }
        }
    }

    private fun onExportJobUpdate(job: BuildJobEntity) {
        when (job.status) {
            BuildJobEntity.QUEUED, BuildJobEntity.RUNNING -> {
                binding.tvExportStatus.text = "Export status: ${job.status} • processing in Termux (ffmpeg)…"
            }
            BuildJobEntity.SUCCESS -> {
                val verify = RecapRenderCoordinator.verifyRenderResult(job.exitCode, outputPath)
                binding.btnExport.isEnabled = true
                if (verify.isSuccess) {
                    val info = verify.getOrThrow()
                    binding.tvExportStatus.text =
                        "Export status: SUCCESS • ${info.fileName} • ${info.sizeBytes} bytes • $outputPath"
                    binding.btnPreviewOutput.isEnabled = true
                    binding.btnShareOutput.isEnabled = true
                } else {
                    binding.tvExportStatus.text =
                        "Export status: FAILED — output verification failed: ${verify.exceptionOrNull()?.message}"
                }
            }
            else -> {
                binding.btnExport.isEnabled = true
                val detail = LogSanitizer.sanitize(
                    job.errorMessage.ifBlank { job.stderr.take(300) }
                )
                binding.tvExportStatus.text = "Export status: FAILED • exit ${job.exitCode} • $detail"
            }
        }
    }

    private fun shareOutput() {
        val file = File(outputPath)
        if (!file.exists() || file.length() <= 0L) {
            Toast.makeText(this, L("Output file မရှိသေးပါ", "No output file yet"), Toast.LENGTH_SHORT).show()
            return
        }
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Share video"))
    }

    override fun onPause() {
        super.onPause()
        runCatching { saveDraft() }
    }

    override fun onDestroy() {
        destroyed = true
        seekHandler.removeCallbacksAndMessages(null)
        observerJob?.cancel()
        super.onDestroy()
    }
}
