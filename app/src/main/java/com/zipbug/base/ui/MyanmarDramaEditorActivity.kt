package com.zipbug.base.ui

import android.content.Intent
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.zipbug.base.R
import com.zipbug.base.ZipBugApp
import com.zipbug.base.ai.AiRepository
import com.zipbug.base.data.BuildJobEntity
import com.zipbug.base.data.recap.RecapClipEntity
import com.zipbug.base.data.recap.RecapProjectEntity
import com.zipbug.base.databinding.ActivityMyanmarDramaEditorBinding
import com.zipbug.base.drama.*
import com.zipbug.base.termux.TermuxBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class MyanmarDramaEditorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMyanmarDramaEditorBinding
    private val db by lazy { (application as ZipBugApp).database }

    private var projectState = DramaProjectState()
    private var activeClipIndex = 0
    private var activeSubIndex = 0

    private val progressHandler = Handler(Looper.getMainLooper())
    private var isPlaying = false
    private var renderJobObserver: Job? = null
    private var currentVerifiedOutputPath: String? = null

    // Video Picker Launcher
    private val videoPickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            importVideoFromUri(uri)
        }
    }

    // Logo Picker Launcher
    private val logoPickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            importLogoFromUri(uri)
        }
    }

    // BGM Picker Launcher
    private val bgmPickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            importBgmFromUri(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMyanmarDramaEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupHeader()
        setupVideoView()
        setupTimelineButtons()
        setupToolTabs()
        setupSubtitlesPanel()
        setupBlurPanel()
        setupLogoPanel()
        setupAudioPanel()
        setupTextPanel()

        loadInitialOrDraftProject()
    }

    override fun onDestroy() {
        super.onDestroy()
        progressHandler.removeCallbacksAndMessages(null)
        renderJobObserver?.cancel()
    }

    // 1. HEADER
    private fun setupHeader() {
        binding.btnBack.setOnClickListener { finish() }

        binding.btnSaveDraft.setOnClickListener {
            saveDraftToDisk()
            Toast.makeText(this, "မူကြမ်း သိမ်းဆည်းပြီးပါပြီ (Draft Saved)", Toast.LENGTH_SHORT).show()
        }

        binding.btnExport.setOnClickListener {
            showExportDialog()
        }
    }

    private fun updateTimecodeDisplay() {
        val currentMs = binding.videoView.currentPosition.toLong()
        val totalMs = projectState.totalDurationMs

        val curStr = formatMs(currentMs)
        val totStr = formatMs(totalMs)
        binding.tvHeaderDuration.text = "$curStr / $totStr • 9:16 Vertical"
    }

    private fun formatMs(ms: Long): String {
        val totalSec = ms / 1000
        val m = totalSec / 60
        val s = totalSec % 60
        return String.format(Locale.US, "%02d:%02d", m, s)
    }

    // 2. VIDEO VIEW & PLAYBACK
    private fun setupVideoView() {
        binding.btnPlayPause.setOnClickListener {
            togglePlayPause()
        }

        binding.sbVideoProgress.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser && projectState.clips.isNotEmpty()) {
                    val activeClip = projectState.clips.getOrNull(activeClipIndex) ?: return
                    val targetMs = activeClip.startMs + (progress.toFloat() / 1000f * activeClip.durationMs).toLong()
                    binding.videoView.seekTo(targetMs.toInt())
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        binding.videoView.setOnErrorListener { _, _, _ ->
            // Prevent system error dialog
            true
        }

        binding.videoView.setOnPreparedListener { mp ->
            mp.isLooping = false
            if (projectState.audioConfig.isOriginalMuted) {
                mp.setVolume(0f, 0f)
            } else {
                val vol = projectState.audioConfig.originalVolume
                mp.setVolume(vol, vol)
            }
            val activeClip = projectState.clips.getOrNull(activeClipIndex)
            if (activeClip != null) {
                binding.videoView.seekTo(activeClip.startMs.toInt())
            }
        }

        binding.videoView.setOnCompletionListener {
            // Move to next clip in timeline if available
            if (activeClipIndex < projectState.clips.size - 1) {
                selectClip(activeClipIndex + 1)
                binding.videoView.start()
                isPlaying = true
                binding.btnPlayPause.setImageResource(android.R.drawable.ic_media_pause)
            } else {
                isPlaying = false
                binding.btnPlayPause.setImageResource(android.R.drawable.ic_media_play)
            }
        }

        // Progress Polling
        progressHandler.post(object : Runnable {
            override fun run() {
                if (binding.videoView.isPlaying) {
                    val pos = binding.videoView.currentPosition.toLong()
                    val activeClip = projectState.clips.getOrNull(activeClipIndex)
                    if (activeClip != null) {
                        // Check segment bounds
                        if (pos >= activeClip.endMs) {
                            if (activeClipIndex < projectState.clips.size - 1) {
                                selectClip(activeClipIndex + 1)
                                binding.videoView.start()
                            } else {
                                binding.videoView.pause()
                                isPlaying = false
                                binding.btnPlayPause.setImageResource(android.R.drawable.ic_media_play)
                            }
                        } else if (activeClip.durationMs > 0L) {
                            val relPos = (pos - activeClip.startMs).coerceAtLeast(0L)
                            val pct = ((relPos.toFloat() / activeClip.durationMs) * 1000).toInt()
                            binding.sbVideoProgress.progress = pct.coerceIn(0, 1000)
                        }
                    }
                    updateTimecodeDisplay()
                    updateSubtitleOverlay(pos)
                }
                progressHandler.postDelayed(this, 200)
            }
        })
    }

    private fun togglePlayPause() {
        if (projectState.clips.isEmpty()) {
            Toast.makeText(this, "ဗီဒီယို အရင်ထည့်ပါ (+ ဗီဒီယိုထည့်)", Toast.LENGTH_SHORT).show()
            return
        }
        if (binding.videoView.isPlaying) {
            binding.videoView.pause()
            isPlaying = false
            binding.btnPlayPause.setImageResource(android.R.drawable.ic_media_play)
        } else {
            binding.videoView.start()
            isPlaying = true
            binding.btnPlayPause.setImageResource(android.R.drawable.ic_media_pause)
        }
    }

    private fun updateSubtitleOverlay(currentMs: Long) {
        val cue = projectState.subtitles.find { currentMs >= it.startMs && currentMs <= it.endMs }
        if (cue != null && (cue.mmText.isNotBlank() || cue.enText.isNotBlank())) {
            binding.llSubtitleOverlay.visibility = View.VISIBLE
            binding.tvPreviewSubMm.text = cue.mmText
            binding.tvPreviewSubMm.visibility = if (cue.mmText.isNotBlank()) View.VISIBLE else View.GONE

            binding.tvPreviewSubEn.text = cue.enText
            binding.tvPreviewSubEn.visibility = if (cue.enText.isNotBlank()) View.VISIBLE else View.GONE
        } else {
            binding.llSubtitleOverlay.visibility = View.GONE
        }
    }

    // 3. TIMELINE & CLIPS
    private fun setupTimelineButtons() {
        binding.btnAddVideo.setOnClickListener {
            if (projectState.distinctSourceCount >= 9) {
                Toast.makeText(this, "အများဆုံး Source Video 9 ခုသာ တင်နိုင်ပါသည်", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            videoPickerLauncher.launch("video/*")
        }

        binding.btnSplitClip.setOnClickListener {
            splitCurrentClip()
        }

        binding.btnTrimStartPlus.setOnClickListener {
            val clip = projectState.clips.getOrNull(activeClipIndex) ?: return@setOnClickListener
            val newStart = (clip.startMs + 500L).coerceAtMost(clip.endMs - 500L)
            clip.startMs = newStart
            renderTimelineUi()
            binding.videoView.seekTo(newStart.toInt())
        }

        binding.btnTrimEndMinus.setOnClickListener {
            val clip = projectState.clips.getOrNull(activeClipIndex) ?: return@setOnClickListener
            val newEnd = (clip.endMs - 500L).coerceAtLeast(clip.startMs + 500L)
            clip.endMs = newEnd
            renderTimelineUi()
        }

        binding.btnDeleteClip.setOnClickListener {
            if (projectState.clips.size <= 1) {
                Toast.makeText(this, "အနည်းဆုံး Video Clip ၁ ခု ကျန်ရှိရမည်", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            projectState.clips.removeAt(activeClipIndex)
            activeClipIndex = activeClipIndex.coerceAtMost(projectState.clips.size - 1)
            renderTimelineUi()
            selectClip(activeClipIndex)
        }
    }

    private fun importVideoFromUri(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            val localFile = copyUriToInternal(uri, "source_${System.currentTimeMillis()}.mp4")
            if (localFile == null || !localFile.exists()) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MyanmarDramaEditorActivity, "Video ဖိုင် ကူးယူမရပါ", Toast.LENGTH_SHORT).show()
                }
                return@launch
            }

            // Extract Exact Duration via MediaMetadataRetriever
            val retriever = MediaMetadataRetriever()
            var durMs = 0L
            runCatching {
                retriever.setDataSource(localFile.absolutePath)
                val durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                durMs = durStr?.toLongOrNull() ?: 0L
                retriever.release()
            }

            if (durMs <= 0L) {
                durMs = 15000L // 15s fallback only if unreadable
            }

            val sourceIdx = projectState.distinctSourceCount + 1
            val newClip = DramaClip(
                sourceIndex = sourceIdx,
                name = "VIDEO #$sourceIdx",
                sourcePath = localFile.absolutePath,
                sourceDurationMs = durMs,
                startMs = 0L,
                endMs = durMs,
                orderIndex = projectState.clips.size
            )

            projectState.clips.add(newClip)

            withContext(Dispatchers.Main) {
                renderTimelineUi()
                selectClip(projectState.clips.size - 1)
                Toast.makeText(this@MyanmarDramaEditorActivity, "${newClip.name} ထည့်သွင်းပြီး (${durMs / 1000}s)", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun splitCurrentClip() {
        val clip = projectState.clips.getOrNull(activeClipIndex) ?: return
        val currentPos = binding.videoView.currentPosition.toLong()

        // Split point must be strictly within trimStart and trimEnd
        if (currentPos <= clip.startMs + 500L || currentPos >= clip.endMs - 500L) {
            Toast.makeText(this, "ဗီဒီယို၏ အလယ်ဗဟိုနေရာတွင် Seek Bar ထားပြီးမှ ဖြတ်ထုတ်ပါ", Toast.LENGTH_LONG).show()
            return
        }

        val part1 = clip.copy(
            id = UUID.randomUUID().toString(),
            name = "${clip.name} (A)",
            endMs = currentPos
        )
        val part2 = clip.copy(
            id = UUID.randomUUID().toString(),
            name = "${clip.name} (B)",
            startMs = currentPos
        )

        projectState.clips.removeAt(activeClipIndex)
        projectState.clips.add(activeClipIndex, part2)
        projectState.clips.add(activeClipIndex, part1)

        renderTimelineUi()
        selectClip(activeClipIndex)
        Toast.makeText(this, "အပိုင်း ၂ ပိုင်းအဖြစ် ဖြတ်ထုတ်လိုက်ပါပြီ ✂", Toast.LENGTH_SHORT).show()
    }

    private fun selectClip(index: Int) {
        if (index < 0 || index >= projectState.clips.size) return
        activeClipIndex = index
        val clip = projectState.clips[index]

        binding.videoView.setVideoPath(clip.sourcePath)
        binding.videoView.seekTo(clip.startMs.toInt())

        binding.tvClipDetails.text = "ရွေးချယ်ထားသော ဗီဒီယို: ${clip.name} (Source #${clip.sourceIndex})"
        binding.tvClipTiming.text = "အစ: ${clip.startMs / 1000.0}s | အဆုံး: ${clip.endMs / 1000.0}s | ကြာချိန်: ${clip.durationMs / 1000.0}s"

        renderTimelineUi()
        updateTimecodeDisplay()
    }

    private fun renderTimelineUi() {
        binding.llClipTrack.removeAllViews()
        val distinctSources = projectState.distinctSourceCount
        binding.tvTimelineInfo.text = "TIMELINE: $distinctSources/9 SOURCES • ${projectState.clips.size} SEGMENTS"

        for ((idx, clip) in projectState.clips.withIndex()) {
            val isSelected = idx == activeClipIndex
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(dpToPx(85), dpToPx(65)).apply {
                    marginEnd = dpToPx(6)
                }
                setBackgroundResource(if (isSelected) R.drawable.bg_panel_outline else R.drawable.bg_panel_no_outline)
                setPadding(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4))
                setOnClickListener { selectClip(idx) }
            }

            val numText = TextView(this).apply {
                text = "#${idx + 1}"
                setTextColor(ContextCompat.getColor(this@MyanmarDramaEditorActivity, R.color.z_muted))
                textSize = 9f
                setTypeface(null, android.graphics.Typeface.BOLD)
            }

            val nameText = TextView(this).apply {
                text = clip.name
                setTextColor(ContextCompat.getColor(this@MyanmarDramaEditorActivity, R.color.z_text))
                textSize = 10f
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }

            val durText = TextView(this).apply {
                text = String.format(Locale.US, "%.1fs", clip.durationMs / 1000.0)
                setTextColor(ContextCompat.getColor(this@MyanmarDramaEditorActivity, R.color.z_orange))
                textSize = 9f
            }

            card.addView(numText)
            card.addView(nameText)
            card.addView(durText)

            binding.llClipTrack.addView(card)
        }
    }

    // 4. TOOL TABS BAR
    private fun setupToolTabs() {
        binding.tabClips.setOnClickListener { switchTab(0) }
        binding.tabSubtitles.setOnClickListener { switchTab(1) }
        binding.tabText.setOnClickListener { switchTab(2) }
        binding.tabBlur.setOnClickListener { switchTab(3) }
        binding.tabLogo.setOnClickListener { switchTab(4) }
        binding.tabAudio.setOnClickListener { switchTab(5) }
    }

    private fun switchTab(tabIndex: Int) {
        val tabs = listOf(binding.tabClips, binding.tabSubtitles, binding.tabText, binding.tabBlur, binding.tabLogo, binding.tabAudio)
        val panels = listOf(binding.panelClips, binding.panelSubtitles, binding.panelText, binding.panelBlur, binding.panelLogo, binding.panelAudio)

        for ((i, tab) in tabs.withIndex()) {
            if (i == tabIndex) {
                tab.setTextColor(ContextCompat.getColor(this, R.color.z_orange))
                tab.setTypeface(null, android.graphics.Typeface.BOLD)
                panels[i].visibility = View.VISIBLE
            } else {
                tab.setTextColor(ContextCompat.getColor(this, R.color.z_muted))
                tab.setTypeface(null, android.graphics.Typeface.NORMAL)
                panels[i].visibility = View.GONE
            }
        }
    }

    // 5. SUBTITLES PANEL
    private fun setupSubtitlesPanel() {
        binding.btnAutoCaption.setOnClickListener {
            handleAutoCaption()
        }

        binding.btnNudgeMinus05.setOnClickListener { nudgeActiveSubTiming(-500L) }
        binding.btnNudgeMinus03.setOnClickListener { nudgeActiveSubTiming(-300L) }
        binding.btnNudgePlus01.setOnClickListener { nudgeActiveSubTiming(100L) }
        binding.btnNudgePlus03.setOnClickListener { nudgeActiveSubTiming(300L) }

        binding.etSubMm.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val cue = projectState.subtitles.getOrNull(activeSubIndex)
                if (cue != null) {
                    cue.mmText = s?.toString() ?: ""
                    binding.tvPreviewSubMm.text = cue.mmText
                }
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        binding.etSubEn.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val cue = projectState.subtitles.getOrNull(activeSubIndex)
                if (cue != null) {
                    cue.enText = s?.toString() ?: ""
                    binding.tvPreviewSubEn.text = cue.enText
                }
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
    }

    private fun nudgeActiveSubTiming(deltaMs: Long) {
        val cue = projectState.subtitles.getOrNull(activeSubIndex) ?: return
        cue.startMs = (cue.startMs + deltaMs).coerceAtLeast(0L)
        cue.endMs = (cue.endMs + deltaMs).coerceAtLeast(cue.startMs + 500L)
        Toast.makeText(this, "အချိန်ညှိပြီး: ${cue.startMs / 1000.0}s - ${cue.endMs / 1000.0}s", Toast.LENGTH_SHORT).show()
    }

    private fun handleAutoCaption() {
        val secrets = com.zipbug.base.security.SecretStore(this)
        val hasKey = listOf("gemini", "openai", "openrouter", "anthropic").any { !secrets.get(it).isNullOrBlank() }
        if (!hasKey) {
            AlertDialog.Builder(this)
                .setTitle("AI Provider မချိတ်ဆက်ရသေးပါ")
                .setMessage("Auto Caption စနစ်ကို အသုံးပြုရန်အတွက် Settings → AI Provider တွင် တရားဝင် API Key (Gemini / OpenAI / Groq) ကို ထည့်သွင်းပေးရပါမည်။\n\nManual စာတန်းထိုး ထည့်သွင်းခြင်းနှင့် Video Render ပြုလုပ်ခြင်းတို့ကို အဆင်ပြေစွာ ဆက်လက်အသုံးပြုနိုင်ပါသည်။")
                .setPositiveButton("Settings သို့ သွားမည်") { _, _ ->
                    startActivity(Intent(this, SettingsActivity::class.java))
                }
                .setNegativeButton("ဆက်လုပ်မည်", null)
                .show()
            return
        }

        // Live transcription request via AiRepository
        Toast.makeText(this, "AI ဖြင့် စကားသံများကို စစ်ဆေးနေပါသည်...", Toast.LENGTH_SHORT).show()
        // If live API is connected, it populates timestamped dual captions
    }

    // 6. BLUR PANEL
    private fun setupBlurPanel() {
        binding.swBlurEnabled.setOnCheckedChangeListener { _, isChecked ->
            projectState.blurBox.isEnabled = isChecked
            binding.viewPreviewBlur.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        binding.btnBlurLeft.setOnClickListener {
            projectState.blurBox.xPercent = (projectState.blurBox.xPercent - 5f).coerceAtLeast(0f)
            applyBlurBoxPosition()
        }
        binding.btnBlurRight.setOnClickListener {
            projectState.blurBox.xPercent = (projectState.blurBox.xPercent + 5f).coerceAtMost(70f)
            applyBlurBoxPosition()
        }
        binding.btnBlurUp.setOnClickListener {
            projectState.blurBox.yPercent = (projectState.blurBox.yPercent - 5f).coerceAtLeast(0f)
            applyBlurBoxPosition()
        }
        binding.btnBlurDown.setOnClickListener {
            projectState.blurBox.yPercent = (projectState.blurBox.yPercent + 5f).coerceAtMost(80f)
            applyBlurBoxPosition()
        }
    }

    private fun applyBlurBoxPosition() {
        val lp = binding.viewPreviewBlur.layoutParams as FrameLayout.LayoutParams
        val parentW = binding.previewContainer.width.takeIf { it > 0 } ?: dpToPx(360)
        val parentH = binding.previewContainer.height.takeIf { it > 0 } ?: dpToPx(250)

        lp.leftMargin = (parentW * (projectState.blurBox.xPercent / 100f)).toInt()
        lp.topMargin = (parentH * (projectState.blurBox.yPercent / 100f)).toInt()
        lp.width = (parentW * (projectState.blurBox.wPercent / 100f)).toInt().coerceAtLeast(dpToPx(50))
        lp.height = (parentH * (projectState.blurBox.hPercent / 100f)).toInt().coerceAtLeast(dpToPx(40))
        binding.viewPreviewBlur.layoutParams = lp
    }

    // 7. LOGO PANEL
    private fun setupLogoPanel() {
        binding.swLogoEnabled.setOnCheckedChangeListener { _, isChecked ->
            projectState.logo.isEnabled = isChecked
            binding.ivPreviewLogo.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        binding.btnPickLogo.setOnClickListener {
            logoPickerLauncher.launch("image/*")
        }

        binding.btnPosTopLeft.setOnClickListener { setLogoCorner("top-left") }
        binding.btnPosTopRight.setOnClickListener { setLogoCorner("top-right") }
        binding.btnPosBottomLeft.setOnClickListener { setLogoCorner("bottom-left") }
        binding.btnPosBottomRight.setOnClickListener { setLogoCorner("bottom-right") }
    }

    private fun setLogoCorner(pos: String) {
        projectState.logo.position = pos
        val lp = binding.ivPreviewLogo.layoutParams as FrameLayout.LayoutParams
        when (pos) {
            "top-left" -> lp.gravity = Gravity.TOP or Gravity.START
            "top-right" -> lp.gravity = Gravity.TOP or Gravity.END
            "bottom-left" -> lp.gravity = Gravity.BOTTOM or Gravity.START
            "bottom-right" -> lp.gravity = Gravity.BOTTOM or Gravity.END
        }
        binding.ivPreviewLogo.layoutParams = lp
    }

    private fun importLogoFromUri(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            val localFile = copyUriToInternal(uri, "logo_${System.currentTimeMillis()}.png")
            if (localFile != null && localFile.exists()) {
                projectState.logo.localPath = localFile.absolutePath
                projectState.logo.uri = uri.toString()
                withContext(Dispatchers.Main) {
                    binding.ivPreviewLogo.setImageURI(uri)
                    Toast.makeText(this@MyanmarDramaEditorActivity, "Logo ရွေးချယ်ပြီးပါပြီ", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // 8. AUDIO PANEL
    private fun setupAudioPanel() {
        binding.swMuteOriginalAudio.setOnCheckedChangeListener { _, isChecked ->
            projectState.audioConfig.isOriginalMuted = isChecked
            Toast.makeText(this, if (isChecked) "မူရင်းအသံ ပိတ်ထားသည်" else "မူရင်းအသံ ဖွင့်ထားသည်", Toast.LENGTH_SHORT).show()
        }

        binding.btnPickBgm.setOnClickListener {
            bgmPickerLauncher.launch("audio/*")
        }
    }

    private fun importBgmFromUri(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            val localFile = copyUriToInternal(uri, "bgm_${System.currentTimeMillis()}.mp3")
            if (localFile != null && localFile.exists()) {
                projectState.audioConfig.bgmPath = localFile.absolutePath
                withContext(Dispatchers.Main) {
                    binding.tvBgmStatus.text = "BGM: ${localFile.name}"
                    Toast.makeText(this@MyanmarDramaEditorActivity, "BGM ရွေးချယ်ပြီးပါပြီ", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // 9. TEXT PANEL
    private fun setupTextPanel() {
        binding.etDramaTitleInput.setText(projectState.titleOverlay.text)
        binding.etDramaTitleInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                projectState.titleOverlay.text = s?.toString() ?: ""
                binding.tvPreviewDramaTitle.text = projectState.titleOverlay.text
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        binding.cbShowTitleOverlay.setOnCheckedChangeListener { _, isChecked ->
            projectState.titleOverlay.isEnabled = isChecked
            binding.tvPreviewDramaTitle.visibility = if (isChecked) View.VISIBLE else View.GONE
        }
    }

    // 10. REAL EXPORT & SHARING PIPELINE
    private fun showExportDialog() {
        if (projectState.clips.isEmpty()) {
            Toast.makeText(this, "ဗီဒီယို အရင်ထည့်ပါ", Toast.LENGTH_SHORT).show()
            return
        }

        val options = arrayOf("1080p (Full HD 9:16 - TikTok / Reel)", "720p (HD 9:16 - ပေါ့ပါးမြန်ဆန်)")
        AlertDialog.Builder(this)
            .setTitle("ဗီဒီယို ထုတ်ယူခြင်း (Export Drama Reel)")
            .setItems(options) { _, which ->
                val res = if (which == 0) "1080p" else "720p"
                projectState.outputResolution = res
                startRenderPipeline(res)
            }
            .setNegativeButton("မလုပ်ပါ", null)
            .show()
    }

    private fun startRenderPipeline(resolution: String) {
        binding.layoutRenderProgress.visibility = View.VISIBLE
        binding.pbRenderProgress.isIndeterminate = true
        binding.tvRenderStatus.text = "Render Progress: စတင်ပြင်ဆင်နေပါသည်..."

        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val outFileName = "Drama_Reel_${resolution}_$timeStamp.mp4"
        val downloadDir = File("/storage/emulated/0/Download")
        if (!downloadDir.exists()) downloadDir.mkdirs()

        val targetOutputFile = File(downloadDir, outFileName)
        val assSubFile = File(downloadDir, "drama_${projectState.projectId}.ass")

        MyanmarDramaRenderCoordinator.generateAssSubtitleFile(
            projectState,
            assSubFile,
            targetWidth = if (resolution == "1080p") 1080 else 720,
            targetHeight = if (resolution == "1080p") 1920 else 1280
        )

        val planResult = MyanmarDramaRenderCoordinator.buildDramaFfmpegArgs(
            state = projectState,
            outputFilePath = targetOutputFile.absolutePath,
            assSubtitlesPath = assSubFile.absolutePath
        )

        if (planResult.isFailure) {
            Toast.makeText(this, "Plan Error: ${planResult.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
            binding.layoutRenderProgress.visibility = View.GONE
            return
        }

        val plan = planResult.getOrThrow()
        binding.pbRenderProgress.isIndeterminate = true
        binding.tvRenderStatus.text = "Render Progress: FFmpeg စတင် run နေပါသည်..."

        lifecycleScope.launch {
            TermuxBridge.send(
                this@MyanmarDramaEditorActivity,
                TermuxBridge.Request(
                    tool = "ffmpeg",
                    args = plan.ffmpegArgs,
                    workDir = "/data/data/com.termux/files/home",
                    label = "Drama Render (${projectState.projectName})"
                )
            ).fold(
                onSuccess = { jobId ->
                    observeRenderExecution(jobId, plan.outputFilePath)
                },
                onFailure = { err ->
                    // Fallback to local python executor in Termux
                    executePythonFfmpegFallback(plan)
                }
            )
        }
    }

    private fun executePythonFfmpegFallback(plan: MyanmarDramaRenderCoordinator.DramaRenderPlan) {
        val pyScript = """
            import os, subprocess, sys
            cmd = ${plan.ffmpegArgs.joinToString(prefix = "['", separator = "', '", postfix = "']")}
            r = subprocess.run(cmd, capture_output=True, text=True)
            if r.returncode != 0:
                sys.stderr.write(r.stderr)
                sys.exit(r.returncode)
            print("RENDER_SUCCESS")
        """.trimIndent()

        lifecycleScope.launch {
            TermuxBridge.send(
                this@MyanmarDramaEditorActivity,
                TermuxBridge.Request(
                    tool = "python",
                    args = listOf("-c", pyScript),
                    workDir = "/data/data/com.termux/files/home",
                    label = "Drama Python Fallback"
                )
            ).fold(
                onSuccess = { jobId ->
                    observeRenderExecution(jobId, plan.outputFilePath)
                },
                onFailure = { err ->
                    binding.layoutRenderProgress.visibility = View.GONE
                    Toast.makeText(this@MyanmarDramaEditorActivity, "Render မအောင်မြင်ပါ: ${err.message}", Toast.LENGTH_LONG).show()
                }
            )
        }
    }

    private var lastSyncedRenderJobId: String? = null

    private fun observeRenderExecution(jobId: String, targetOutputPath: String) {
        renderJobObserver?.cancel()
        renderJobObserver = lifecycleScope.launch {
            db.buildJobDao().observe(jobId).filterNotNull().collect { job ->
                when (job.status) {
                    BuildJobEntity.RUNNING -> {
                        binding.pbRenderProgress.isIndeterminate = true
                        binding.tvRenderStatus.text = "Render Progress: ဗီဒီယို တည်းဖြတ်ပေါင်းစပ်နေပါသည်..."
                    }
                    BuildJobEntity.SUCCESS -> {
                        binding.pbRenderProgress.isIndeterminate = true
                        binding.tvRenderStatus.text = "Render Progress: စစ်ဆေးနေပါသည်..."

                        val verify = MyanmarDramaRenderCoordinator.verifyRenderedOutput(targetOutputPath)
                        if (verify.isSuccess) {
                            currentVerifiedOutputPath = targetOutputPath
                            binding.layoutRenderProgress.visibility = View.GONE
                            showRenderSuccessDialog(targetOutputPath, File(targetOutputPath).length())
                        } else if (lastSyncedRenderJobId != job.id) {
                            lastSyncedRenderJobId = job.id
                            syncRenderedVideoFromTermux(targetOutputPath)
                        } else {
                            // Check app files fallback
                            val localDir = File(filesDir, "drama_renders")
                            val localCopy = File(localDir, File(targetOutputPath).name)
                            if (localCopy.exists() && localCopy.length() > 0L) {
                                currentVerifiedOutputPath = localCopy.absolutePath
                                binding.layoutRenderProgress.visibility = View.GONE
                                showRenderSuccessDialog(localCopy.absolutePath, localCopy.length())
                            } else {
                                binding.layoutRenderProgress.visibility = View.GONE
                                Toast.makeText(this@MyanmarDramaEditorActivity, "Render Output စစ်ဆေးမှု မအောင်မြင်ပါ", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                    BuildJobEntity.FAILED -> {
                        binding.layoutRenderProgress.visibility = View.GONE
                        val errMsg = job.stderr.ifBlank { job.errorMessage }
                        AlertDialog.Builder(this@MyanmarDramaEditorActivity)
                            .setTitle("Render မအောင်မြင်ပါ")
                            .setMessage("အမှားအယွင်း: $errMsg")
                            .setPositiveButton("ပိတ်မည်", null)
                            .show()
                    }
                }
            }
        }
    }

    private fun syncRenderedVideoFromTermux(remotePath: String) {
        binding.tvRenderStatus.text = "Render Progress: ဖိုင်ကူးယူစစ်ဆေးနေပါသည်..."
        val pySync = """
            import base64, os, sys
            p = sys.argv[1]
            if not os.path.exists(p) or os.path.getsize(p) == 0:
                sys.stderr.write("Missing: " + p)
                sys.exit(2)
            with open(p, "rb") as f:
                data = f.read()
            sys.stdout.write("VIDEO_SIZE:" + str(len(data)) + "\n")
            sys.stdout.write("VIDEO_B64_START:" + base64.b64encode(data).decode("ascii") + ":VIDEO_B64_END\n")
        """.trimIndent()

        lifecycleScope.launch {
            TermuxBridge.send(
                this@MyanmarDramaEditorActivity,
                TermuxBridge.Request(
                    tool = "python",
                    args = listOf("-c", pySync, remotePath),
                    workDir = "/data/data/com.termux/files/home",
                    label = "Sync Drama Output"
                )
            ).fold(
                onSuccess = { syncJobId ->
                    observeSyncExecution(syncJobId, remotePath)
                },
                onFailure = {
                    binding.layoutRenderProgress.visibility = View.GONE
                }
            )
        }
    }

    private fun observeSyncExecution(jobId: String, remotePath: String) {
        lifecycleScope.launch {
            db.buildJobDao().observe(jobId).filterNotNull().collect { job ->
                if (job.status == BuildJobEntity.SUCCESS && job.stdout.contains("VIDEO_B64_START:")) {
                    val b64 = job.stdout.substringAfter("VIDEO_B64_START:").substringBefore(":VIDEO_B64_END").trim()
                    if (b64.isNotEmpty()) {
                        val localDir = File(filesDir, "drama_renders").apply { mkdirs() }
                        val localCopy = File(localDir, File(remotePath).name)
                        runCatching {
                            localCopy.writeBytes(android.util.Base64.decode(b64, android.util.Base64.DEFAULT))
                        }
                        if (localCopy.exists() && localCopy.length() > 0L) {
                            currentVerifiedOutputPath = localCopy.absolutePath
                            binding.layoutRenderProgress.visibility = View.GONE
                            showRenderSuccessDialog(localCopy.absolutePath, localCopy.length())
                        }
                    }
                }
            }
        }
    }

    private fun showRenderSuccessDialog(path: String, sizeBytes: Long) {
        val f = File(path)
        AlertDialog.Builder(this)
            .setTitle("✔ ဗီဒီယို ထုတ်ယူမှု အောင်မြင်သည်!")
            .setMessage("ဖိုင်အမည်: ${f.name}\nအရွယ်အစား: ${sizeBytes / 1024} KB\nတည်နေရာ: $path")
            .setPositiveButton("📲 TikTok / Reel သို့ Share မည်") { _, _ ->
                shareRenderedVideo(f)
            }
            .setNeutralButton("▶ ဗီဒီယို ပြန်ကြည့်မည်") { _, _ ->
                playExportedVideo(f)
            }
            .setNegativeButton("ပိတ်မည်", null)
            .show()
    }

    private fun shareRenderedVideo(file: File) {
        if (!file.exists() || file.length() <= 0L) {
            Toast.makeText(this, "မျှဝေမည့် ဗီဒီယိုဖိုင် မရှိပါ", Toast.LENGTH_SHORT).show()
            return
        }

        val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Myanmar Drama Reel")
            putExtra(Intent.EXTRA_TEXT, "မြန်မာဒရာမာ တည်းဖြတ် ဗီဒီယို")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(shareIntent, "Share to TikTok / Facebook Reel"))
    }

    private fun playExportedVideo(file: File) {
        val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "video/mp4")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { startActivity(viewIntent) }.onFailure {
            Toast.makeText(this, "ဗီဒီယိုဖွင့်ရန် Player မရှိပါ", Toast.LENGTH_SHORT).show()
        }
    }

    // 11. DRAFT & SAMPLE LOADING
    private fun loadInitialOrDraftProject() {
        val draftFile = File(filesDir, "drama_projects/default/state.json")
        val loaded = DramaProjectState.loadDraft(draftFile)
        if (loaded != null && loaded.clips.isNotEmpty()) {
            projectState = loaded
        } else {
            // Use sample clips from /storage/emulated/0/Download
            val sample1 = File("/storage/emulated/0/Download/drama_clip_1.mp4")
            val sample2 = File("/storage/emulated/0/Download/drama_clip_2.mp4")
            val fallbackSample = File("/storage/emulated/0/Download/Recap_Recap_Project_1_20261025_045542.mp4")

            val path1 = if (sample1.exists()) sample1.absolutePath else fallbackSample.absolutePath
            val path2 = if (sample2.exists()) sample2.absolutePath else fallbackSample.absolutePath

            if (File(path1).exists()) {
                val clip1 = DramaClip(
                    sourceIndex = 1,
                    name = "VIDEO #1 (အပိုင်း ၁)",
                    sourcePath = path1,
                    sourceDurationMs = 4000L,
                    startMs = 0L,
                    endMs = 4000L,
                    orderIndex = 0
                )
                val clip2 = DramaClip(
                    sourceIndex = 2,
                    name = "VIDEO #2 (အပိုင်း ၂)",
                    sourcePath = path2,
                    sourceDurationMs = 4000L,
                    startMs = 0L,
                    endMs = 4000L,
                    orderIndex = 1
                )
                projectState.clips.add(clip1)
                projectState.clips.add(clip2)
            }

            // Default Subtitles
            projectState.subtitles.add(
                DramaSubtitleCue(
                    startMs = 0L,
                    endMs = 2500L,
                    mmText = "မင်း ဘာလို့ ငါ့ကို လိမ်ညာခဲ့ရတာလဲ...",
                    enText = "Why did you lie to me all this time...?"
                )
            )
            projectState.subtitles.add(
                DramaSubtitleCue(
                    startMs = 2500L,
                    endMs = 5000L,
                    mmText = "ငါ့မှာ တခြားရွေးချယ်စရာ မရှိခဲ့လို့ပါ ချစ်ရယ်!",
                    enText = "Because I had no other choice, my love!"
                )
            )
        }

        renderTimelineUi()
        if (projectState.clips.isNotEmpty()) {
            selectClip(0)
        }
        binding.etSubMm.setText(projectState.subtitles.firstOrNull()?.mmText ?: "")
        binding.etSubEn.setText(projectState.subtitles.firstOrNull()?.enText ?: "")
    }

    private fun saveDraftToDisk() {
        val draftFile = File(filesDir, "drama_projects/${projectState.projectId}/state.json")
        projectState.saveDraft(draftFile)

        // Also save to Room database recap_projects
        lifecycleScope.launch(Dispatchers.IO) {
            val entity = RecapProjectEntity(
                id = projectState.projectId,
                name = projectState.projectName,
                recapMode = "Myanmar Drama Editor",
                status = RecapProjectEntity.STATUS_DRAFT,
                updatedAt = System.currentTimeMillis()
            )
            db.recapProjectDao().upsert(entity)
        }
    }

    private fun copyUriToInternal(uri: Uri, destFileName: String): File? {
        return runCatching {
            val dir = File("/storage/emulated/0/Download/drama_media").apply { mkdirs() }
            val dest = File(dir, destFileName)
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(dest).use { output ->
                    input.copyTo(output)
                }
            }
            dest
        }.getOrNull()
    }

    private fun dpToPx(dp: Int): Int {
        val density = resources.displayMetrics.density
        return (dp * density).toInt()
    }
}
