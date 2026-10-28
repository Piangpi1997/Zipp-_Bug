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
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.zipbug.base.data.BuildJobEntity
import com.zipbug.base.data.ZipBugDatabase
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
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Zippy Cut Studio — a native (not WebView) multi-clip video studio modelled
 * on the reference Zippy_Cut UI (dark panels, coral accent, timeline-first
 * layout) but wired to ZIP_BUG's real engines:
 * - metadata/preview: MediaMetadataRetriever + VideoView
 * - staged media: RecapMediaStager (shared Downloads copy Termux can read)
 * - edit model/render plan: ZippyCutPlan (pure, unit-tested) — multi-clip
 *   reorder, per-clip trim, split, manual subtitles, title + text overlay,
 *   logo overlay, audio keep/mute/replace
 * - render: ffmpeg argument list executed by the existing TermuxBridge job
 *   pipeline (QUEUED/RUNNING/SUCCESS/FAILED are the real Room job states;
 *   no progress percentage is invented)
 * - drafts: edit-state JSON in app-private storage, restored on launch.
 *   Original media is never modified.
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
    ) { uri -> if (uri != null) onClipPicked(uri) }

    private val pickLogo = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) onLogoPicked(uri) }

    private val pickAudio = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) onAudioPicked(uri) }

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
        refreshAll()
        seekHandler.post(seekTicker)
    }

    private fun bindActions() {
        binding.btnPickVideo.setOnClickListener { pickVideo.launch(arrayOf("video/*")) }
        binding.btnPickLogo.setOnClickListener { pickLogo.launch(arrayOf("image/*")) }
        binding.btnPickAudio.setOnClickListener { pickAudio.launch(arrayOf("audio/*")) }
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
        binding.btnApplySplit.setOnClickListener { applySplit() }
        binding.btnAddSubtitle.setOnClickListener { addSubtitleFromInputs() }
        binding.btnExport.setOnClickListener { startExport() }
        binding.btnPreviewOutput.setOnClickListener {
            if (outputPath.isNotBlank()) {
                binding.videoPreview.setVideoPath(outputPath)
                binding.videoPreview.start()
            }
        }
        binding.btnShareOutput.setOnClickListener { shareOutput() }
        binding.rgAudioMode.setOnCheckedChangeListener { _, checkedId ->
            state = state.copy(
                audioMode = when (checkedId) {
                    binding.rbAudioMute.id -> ZippyCutPlan.AudioMode.MUTE
                    binding.rbAudioReplace.id -> ZippyCutPlan.AudioMode.REPLACE
                    else -> ZippyCutPlan.AudioMode.KEEP
                }
            )
            saveDraft()
        }
    }

    // -------- clip import / staging --------

    private data class Meta(val name: String, val durationMs: Long, val width: Int, val height: Int)

    private fun readMetadata(uri: Uri): Meta {
        var name = "video"
        runCatching {
            contentResolver.query(
                uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null
            )?.use { c -> if (c.moveToFirst()) name = c.getString(0) ?: name }
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

    private fun displayNameOf(uri: Uri): String {
        var name = "file"
        runCatching {
            contentResolver.query(
                uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null
            )?.use { c -> if (c.moveToFirst()) name = c.getString(0) ?: name }
        }
        return name
    }

    private fun takeReadPermission(uri: Uri) {
        runCatching {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun onClipPicked(uri: Uri) {
        takeReadPermission(uri)
        val meta = readMetadata(uri)
        val clip = ZippyCutPlan.Clip(
            id = UUID.randomUUID().toString().take(8),
            sourceUri = uri.toString(),
            sourceName = meta.name,
            durationMs = meta.durationMs,
            width = meta.width,
            height = meta.height,
            trimStartMs = 0L,
            trimEndMs = meta.durationMs
        )
        state = state.copy(
            clips = state.clips + clip,
            selectedClip = state.clips.size
        )
        binding.videoPreview.setVideoURI(uri)
        binding.seekPreview.max = meta.durationMs.toInt().coerceAtLeast(0)
        refreshAll()
        saveDraft()
        stageClip(clip.id, uri)
    }

    private fun stageClip(clipId: String, uri: Uri) {
        setStatus(L("အခြေအနေ: clip ကို Termux ဖတ်နိုင်အောင် ကူးနေသည်…", "Status: staging a Termux-readable copy…"))
        lifecycleScope.launch {
            val staged = withContext(Dispatchers.IO) {
                RecapMediaStager.stage(this@ZippyCutStudioActivity, uri, "zippycut")
            }
            staged.fold(
                onSuccess = { media ->
                    val idx = state.clips.indexOfFirst { it.id == clipId }
                    if (idx >= 0) {
                        val updated = state.clips.toMutableList()
                        updated[idx] = updated[idx].copy(stagedPath = media.localPath)
                        state = state.copy(clips = updated)
                        saveDraft()
                    }
                    setStatus(L("အခြေအနေ: အဆင်သင့်ဖြစ်ပြီ (staged copy ရပြီး)", "Status: ready (staged copy available)"))
                },
                onFailure = { err ->
                    setStatus(L("အခြေအနေ: staging မအောင်မြင်ပါ — ${err.message}", "Status: staging failed — ${err.message}"))
                }
            )
        }
    }

    private fun onLogoPicked(uri: Uri) {
        takeReadPermission(uri)
        val name = displayNameOf(uri)
        state = state.copy(logoUri = uri.toString(), logoPath = "")
        binding.tvLogoName.text = name
        saveDraft()
        lifecycleScope.launch {
            val staged = withContext(Dispatchers.IO) {
                RecapMediaStager.stage(this@ZippyCutStudioActivity, uri, "zippycut-logo")
            }
            staged.fold(
                onSuccess = { media ->
                    state = state.copy(logoPath = media.localPath)
                    binding.tvLogoName.text = "$name ✓"
                    saveDraft()
                },
                onFailure = { err ->
                    binding.tvLogoName.text = L("Logo staging မအောင်မြင်: ${err.message}", "Logo staging failed: ${err.message}")
                }
            )
        }
    }

    private fun onAudioPicked(uri: Uri) {
        takeReadPermission(uri)
        val name = displayNameOf(uri)
        state = state.copy(
            replaceAudioUri = uri.toString(),
            replaceAudioPath = "",
            replaceAudioName = name,
            audioMode = ZippyCutPlan.AudioMode.REPLACE
        )
        binding.rbAudioReplace.isChecked = true
        binding.tvReplaceAudioName.text = name
        saveDraft()
        lifecycleScope.launch {
            val staged = withContext(Dispatchers.IO) {
                RecapMediaStager.stage(this@ZippyCutStudioActivity, uri, "zippycut-audio")
            }
            staged.fold(
                onSuccess = { media ->
                    state = state.copy(replaceAudioPath = media.localPath)
                    binding.tvReplaceAudioName.text = "$name ✓"
                    saveDraft()
                },
                onFailure = { err ->
                    binding.tvReplaceAudioName.text = L("အသံ staging မအောင်မြင်: ${err.message}", "Audio staging failed: ${err.message}")
                }
            )
        }
    }

    // -------- clip list UI --------

    private fun selectedClip(): ZippyCutPlan.Clip? = state.clips.getOrNull(state.selectedClip)

    private fun refreshAll() {
        renderClipRows()
        renderSubtitleRows()
        updateMetaText()
        refreshSegmentsText()
        val sel = selectedClip()
        if (sel != null) {
            binding.etTrimStart.setText(String.format(Locale.US, "%.3f", sel.trimStartMs / 1000.0))
            binding.etTrimEnd.setText(String.format(Locale.US, "%.3f", sel.trimEndMs / 1000.0))
        }
        binding.etTitle.setText(state.title)
        binding.etTextOverlay.setText(state.textOverlay)
        binding.tvLogoName.text = state.logoUri.substringAfterLast('/').ifBlank {
            L("Logo မရှိပါ", "No logo")
        }
        binding.tvReplaceAudioName.text = state.replaceAudioName.ifBlank {
            L("အသံဖိုင် မရှိပါ", "No audio file")
        }
        when (state.audioMode) {
            ZippyCutPlan.AudioMode.KEEP -> binding.rbAudioKeep.isChecked = true
            ZippyCutPlan.AudioMode.MUTE -> binding.rbAudioMute.isChecked = true
            ZippyCutPlan.AudioMode.REPLACE -> binding.rbAudioReplace.isChecked = true
        }
    }

    private fun renderClipRows() {
        val box = binding.clipsRows
        box.removeAllViews()
        state.clips.forEachIndexed { idx, clip ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            val label = TextView(this).apply {
                text = "${idx + 1}. ${clip.sourceName} • " +
                    String.format(Locale.US, "%.1fs", clip.trimmedDurationMs / 1000.0) +
                    if (clip.stagedPath.isBlank()) " (staging…)" else ""
                setTextColor(
                    if (idx == state.selectedClip) 0xFFD97757.toInt() else 0xFFEEF3F6.toInt()
                )
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setOnClickListener { selectClip(idx) }
            }
            val up = Button(this).apply {
                text = "↑"
                isEnabled = idx > 0
                setOnClickListener {
                    collectState()
                    state = ZippyCutPlan.moveClip(state, idx, idx - 1)
                    refreshAll()
                    saveDraft()
                }
            }
            val down = Button(this).apply {
                text = "↓"
                isEnabled = idx < state.clips.lastIndex
                setOnClickListener {
                    collectState()
                    state = ZippyCutPlan.moveClip(state, idx, idx + 1)
                    refreshAll()
                    saveDraft()
                }
            }
            val del = Button(this).apply {
                text = "✕"
                setOnClickListener {
                    val updated = state.clips.toMutableList()
                    updated.removeAt(idx)
                    state = state.copy(
                        clips = updated,
                        selectedClip = state.selectedClip.coerceIn(0, maxOf(0, updated.size - 1))
                    )
                    refreshAll()
                    saveDraft()
                }
            }
            row.addView(label)
            row.addView(up)
            row.addView(down)
            row.addView(del)
            box.addView(row)
        }
    }

    private fun selectClip(idx: Int) {
        collectState()
        val clip = state.clips.getOrNull(idx) ?: return
        state = state.copy(selectedClip = idx)
        if (clip.sourceUri.isNotBlank()) {
            binding.videoPreview.setVideoURI(Uri.parse(clip.sourceUri))
            binding.seekPreview.max = clip.durationMs.toInt().coerceAtLeast(0)
        }
        refreshAll()
        saveDraft()
    }

    private fun updateMetaText() {
        val sel = selectedClip()
        binding.tvMeta.text = when {
            sel == null -> L("ဗီဒီယို မရွေးရသေးပါ", "No video selected")
            else -> "${sel.sourceName} • ${String.format(Locale.US, "%.1fs", sel.durationMs / 1000.0)}" +
                if (sel.width > 0) " • ${sel.width}×${sel.height}" else ""
        }
    }

    // -------- state <-> fields --------

    private fun secondsToMs(text: String): Long? =
        text.trim().toDoubleOrNull()?.let { (it * 1000).toLong() }

    /** Writes the on-screen fields back into the edit state. */
    private fun collectState() {
        val sel = selectedClip()
        var clips = state.clips
        if (sel != null) {
            val start = secondsToMs(binding.etTrimStart.text.toString()) ?: sel.trimStartMs
            val end = secondsToMs(binding.etTrimEnd.text.toString()) ?: sel.trimEndMs
            val updated = clips.toMutableList()
            updated[state.selectedClip] = sel.copy(trimStartMs = start, trimEndMs = end)
            clips = updated
        }
        state = state.copy(
            clips = clips,
            title = binding.etTitle.text.toString(),
            textOverlay = binding.etTextOverlay.text.toString()
        )
    }

    private fun refreshSegmentsText() {
        val err = ZippyCutPlan.validate(state)
        binding.tvSegments.text = if (err != null) {
            "Segments: $err"
        } else {
            "Segments: " + ZippyCutPlan.segments(state).joinToString("  +  ") {
                "${ZippyCutPlan.formatSeconds(it.startMs)}s–${ZippyCutPlan.formatSeconds(it.endMs)}s"
            } + "  •  total " + String.format(
                Locale.US, "%.1fs", ZippyCutPlan.totalDurationMs(state) / 1000.0
            )
        }
    }

    private fun applySplit() {
        collectState()
        val idx = state.selectedClip
        if (selectedClip() == null) {
            Toast.makeText(this, L("အရင် clip ရွေးပါ", "Select a clip first"), Toast.LENGTH_SHORT).show()
            return
        }
        val at = secondsToMs(binding.etSplit.text.toString())
        if (at == null) {
            Toast.makeText(this, L("Split အချိန် (စက္ကန့်) ထည့်ပါ", "Enter a split time in seconds"), Toast.LENGTH_SHORT).show()
            return
        }
        ZippyCutPlan.splitClip(state, idx, at).fold(
            onSuccess = { newState ->
                state = newState
                binding.etSplit.setText("")
                refreshAll()
                saveDraft()
            },
            onFailure = { err ->
                Toast.makeText(this, err.message, Toast.LENGTH_SHORT).show()
            }
        )
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
        if (!file.exists()) return
        runCatching {
            state = ZippyCutPlan.fromJson(file.readText(Charsets.UTF_8))
            val sel = selectedClip()
            if (sel != null && sel.sourceUri.isNotBlank()) {
                binding.videoPreview.setVideoURI(Uri.parse(sel.sourceUri))
                binding.seekPreview.max = sel.durationMs.toInt().coerceAtLeast(0)
            }
        }
    }

    // -------- export via Termux engine (same path as Recap Studio) --------

    private fun setStatus(text: String) {
        binding.tvExportStatus.text = text
    }

    private fun startExport() {
        collectState()
        refreshSegmentsText()
        val validation = ZippyCutPlan.validate(state)
        if (validation != null) {
            setStatus("Export status: FAILED — $validation")
            return
        }
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val firstName = state.clips.firstOrNull()?.sourceName?.substringBeforeLast('.') ?: "zippy"
        val safeName = firstName.replace(Regex("[^a-zA-Z0-9_-]"), "_").ifBlank { "zippy" }
        outputPath = "/storage/emulated/0/Download/ZippyCut_${safeName}_$ts.mp4"

        var srtPath: String? = null
        if (state.subtitles.isNotEmpty()) {
            val srt = ZippyCutPlan.subtitlesToSrt(state.subtitles)
            runCatching { File(File(filesDir, "zippycut"), "draft.srt").writeText(srt, Charsets.UTF_8) }
            srtPath = stageSrtToShared(srt, "ZippyCut_$ts.srt")
            if (srtPath == null) {
                setStatus(L(
                    "Export အခြေအနေ: FAILED — စာတန်း .srt ကို shared storage သို့ မတင်နိုင်ပါ",
                    "Export status: FAILED — could not place the .srt file in shared storage"
                ))
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
            outputPath = outputPath,
            subtitlesPath = srtPath,
            fontFile = font
        )
        if (argsResult.isFailure) {
            setStatus("Export status: FAILED — ${argsResult.exceptionOrNull()?.message}")
            return
        }
        binding.btnExport.isEnabled = false
        setStatus(L(
            "Export အခြေအနေ: QUEUED • Termux သို့ ပို့နေသည်… (ရာခိုင်နှုန်း မပြပါ — တကယ့် အခြေအနေကိုသာ ပြသည်)",
            "Export status: QUEUED • dispatching to Termux… (no fake percentage — real status only)"
        ))
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
                    setStatus("Export status: FAILED — ${ex.message}")
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
        val dao = ZipBugDatabase.get(this).buildJobDao()
        observerJob = lifecycleScope.launch {
            dao.observe(termuxJobId).filterNotNull().collect { job ->
                onExportJobUpdate(job)
            }
        }
    }

    private fun onExportJobUpdate(job: BuildJobEntity) {
        when (job.status) {
            BuildJobEntity.QUEUED, BuildJobEntity.RUNNING -> {
                setStatus("Export status: ${job.status} • processing in Termux (ffmpeg)…")
            }
            BuildJobEntity.SUCCESS -> {
                val verify = RecapRenderCoordinator.verifyRenderResult(job.exitCode, outputPath)
                binding.btnExport.isEnabled = true
                if (verify.isSuccess) {
                    val info = verify.getOrThrow()
                    setStatus("Export status: SUCCESS • ${info.fileName} • ${info.sizeBytes} bytes • $outputPath")
                    binding.btnPreviewOutput.isEnabled = true
                    binding.btnShareOutput.isEnabled = true
                } else {
                    setStatus("Export status: FAILED — output verification failed: ${verify.exceptionOrNull()?.message}")
                }
            }
            else -> {
                binding.btnExport.isEnabled = true
                val detail = LogSanitizer.sanitize(
                    job.errorMessage.ifBlank { job.stderr.take(300) }
                )
                setStatus("Export status: FAILED • exit ${job.exitCode} • $detail")
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
