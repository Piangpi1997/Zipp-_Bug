package com.zipbug.base.ui

import android.app.AlertDialog
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.zipbug.base.R
import com.zipbug.base.ZipBugApp
import com.zipbug.base.ai.AiProvider
import com.zipbug.base.ai.AiRepository
import com.zipbug.base.data.BuildJobEntity
import com.zipbug.base.data.recap.*
import com.zipbug.base.databinding.ActivityRecapStudioBinding
import com.zipbug.base.recap.*
import com.zipbug.base.security.SecretStore
import com.zipbug.base.termux.LogSanitizer
import com.zipbug.base.termux.TermuxBridge
import com.zipbug.base.tts.EdgeTtsHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class RecapStudioActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRecapStudioBinding
    private val app by lazy { application as ZipBugApp }
    private val db by lazy { app.database }
    private val aiRepository = AiRepository()

    private var activeProject: RecapProjectEntity? = null
    private val clips = mutableListOf<RecapClipEntity>()
    private val scripts = mutableListOf<RecapScriptEntity>()

    private var selectedScriptMode = RecapProjectEntity.MODE_RECAP
    private var selectedLanguage = RecapProjectEntity.LANG_MYANMAR
    private var selectedVoice = EdgeTtsHelper.VOICE_NILAR

    private var renderObserverJob: Job? = null
    private var currentRenderJobId: String? = null
    private var lastRenderOutputVerification: RecapRenderCoordinator.FileVerification? = null

    private val safMediaPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            handleMediaSelected(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRecapStudioBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupPipelineBadges()
        setupListeners()
        loadOrCreateInitialProject()
    }

    private fun setupListeners() {
        binding.btnNewProject.setOnClickListener { promptCreateNewProject() }
        binding.btnRenameProject.setOnClickListener { promptRenameProject() }
        binding.btnSelectMedia.setOnClickListener {
            safMediaPicker.launch(arrayOf("video/*", "audio/*"))
        }

        // Script Mode selection
        binding.btnModeRecap.setOnClickListener { setScriptMode(RecapProjectEntity.MODE_RECAP) }
        binding.btnModeDialogue.setOnClickListener { setScriptMode(RecapProjectEntity.MODE_RECAP_DIALOGUE) }
        binding.btnModeDub.setOnClickListener { setScriptMode(RecapProjectEntity.MODE_DUB) }

        // Language selection
        binding.btnLangMyanmar.setOnClickListener { setScriptLanguage(RecapProjectEntity.LANG_MYANMAR) }
        binding.btnLangEnglish.setOnClickListener { setScriptLanguage(RecapProjectEntity.LANG_ENGLISH) }

        binding.btnGenerateScript.setOnClickListener { generateAiScript() }

        // Timeline actions
        binding.btnResetTimeline.setOnClickListener { resetTimelineClips() }
        binding.btnAddClip.setOnClickListener { addNewClip() }

        // Voiceover actions
        binding.btnVoiceNilar.setOnClickListener { setTtsVoice(EdgeTtsHelper.VOICE_NILAR) }
        binding.btnVoiceThiha.setOnClickListener { setTtsVoice(EdgeTtsHelper.VOICE_THIHA) }
        binding.btnVoiceJenny.setOnClickListener { setTtsVoice(EdgeTtsHelper.VOICE_JENNY) }
        binding.btnPreviewVoice.setOnClickListener { previewTtsVoice() }
        binding.btnGenerateVoiceover.setOnClickListener { generateTtsVoiceover() }

        // Render actions
        binding.btnStartRender.setOnClickListener { startRenderPipeline() }
        binding.btnCancelRender.setOnClickListener { cancelCurrentRender() }
        binding.btnRetryRender.setOnClickListener { startRenderPipeline() }

        binding.btnOpenVideo.setOnClickListener { openRenderedVideo() }
        binding.btnShareVideo.setOnClickListener { shareRenderedVideo() }
    }

    private fun loadOrCreateInitialProject() {
        lifecycleScope.launch {
            val existing = db.recapProjectDao().getAllSync().firstOrNull()
            if (existing != null) {
                bindProject(existing)
            } else {
                val newProject = RecapProjectEntity(
                    id = UUID.randomUUID().toString(),
                    name = "Recap Project 1",
                    language = RecapProjectEntity.LANG_MYANMAR,
                    recapMode = RecapProjectEntity.MODE_RECAP
                )
                db.recapProjectDao().upsert(newProject)
                bindProject(newProject)
            }
        }
    }

    private suspend fun bindProject(project: RecapProjectEntity) {
        activeProject = project
        selectedLanguage = project.language
        selectedScriptMode = project.recapMode

        binding.tvProjectName.text = "Project: ${project.name}"
        binding.tvMediaSourceStatus.text = if (project.sourceLocalPath != null) {
            "Source: ${project.sourceFileName ?: File(project.sourceLocalPath).name} (${project.sourceDurationMs / 1000}s)"
        } else {
            "Source: No media selected"
        }

        updateModeButtonsUi()
        updateLanguageButtonsUi()

        // Load clips
        val loadedClips = db.recapClipDao().getClipsForProject(project.id)
        clips.clear()
        if (loadedClips.isNotEmpty()) {
            clips.addAll(loadedClips)
        } else if (project.sourceLocalPath != null) {
            val initial = RecapTimelineManager.createInitialClips(
                projectId = project.id,
                sourcePath = project.sourceLocalPath,
                totalDurationMs = project.sourceDurationMs
            )
            db.recapClipDao().upsertAll(initial)
            clips.addAll(initial)
        }
        renderClipsUi()
        updateRenderAvailability()

        // Load scripts
        val loadedScripts = db.recapScriptDao().getScriptsForProject(project.id)
        scripts.clear()
        if (loadedScripts.isNotEmpty()) {
            scripts.addAll(loadedScripts)
            bindScriptsUi()
        }
    }

    private fun setScriptMode(mode: String) {
        selectedScriptMode = mode
        updateModeButtonsUi()
        activeProject?.let { p ->
            val updated = p.copy(recapMode = mode, updatedAt = System.currentTimeMillis())
            activeProject = updated
            lifecycleScope.launch { db.recapProjectDao().upsert(updated) }
        }
    }

    private fun updateModeButtonsUi() {
        binding.btnModeRecap.setBackgroundColor(if (selectedScriptMode == RecapProjectEntity.MODE_RECAP) 0xFF4A3412.toInt() else 0xFF2A2A2A.toInt())
        binding.btnModeDialogue.setBackgroundColor(if (selectedScriptMode == RecapProjectEntity.MODE_RECAP_DIALOGUE) 0xFF4A3412.toInt() else 0xFF2A2A2A.toInt())
        binding.btnModeDub.setBackgroundColor(if (selectedScriptMode == RecapProjectEntity.MODE_DUB) 0xFF4A3412.toInt() else 0xFF2A2A2A.toInt())
    }

    private fun setScriptLanguage(lang: String) {
        selectedLanguage = lang
        updateLanguageButtonsUi()
        activeProject?.let { p ->
            val updated = p.copy(language = lang, updatedAt = System.currentTimeMillis())
            activeProject = updated
            lifecycleScope.launch { db.recapProjectDao().upsert(updated) }
        }
    }

    private fun updateLanguageButtonsUi() {
        val isMy = selectedLanguage == RecapProjectEntity.LANG_MYANMAR
        binding.btnLangMyanmar.setBackgroundColor(if (isMy) 0xFF1E3A24.toInt() else 0xFF2A2A2A.toInt())
        binding.btnLangEnglish.setBackgroundColor(if (!isMy) 0xFF1E3A24.toInt() else 0xFF2A2A2A.toInt())
    }

    private fun setTtsVoice(voice: String) {
        selectedVoice = voice
        binding.tvSelectedVoice.text = "Voice: $voice"
        binding.btnVoiceNilar.setBackgroundColor(if (voice == EdgeTtsHelper.VOICE_NILAR) 0xFF1E3A24.toInt() else 0xFF2A2A2A.toInt())
        binding.btnVoiceThiha.setBackgroundColor(if (voice == EdgeTtsHelper.VOICE_THIHA) 0xFF1E3A24.toInt() else 0xFF2A2A2A.toInt())
        binding.btnVoiceJenny.setBackgroundColor(if (voice == EdgeTtsHelper.VOICE_JENNY) 0xFF1E3A24.toInt() else 0xFF2A2A2A.toInt())
    }

    private fun handleMediaSelected(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            val project = activeProject ?: return@launch
            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {
                // Not all SAF pickers grant persistable permission
            }

            val stagedResult = RecapMediaStager.stage(
                context = this@RecapStudioActivity,
                uri = uri,
                projectId = project.id
            )

            if (stagedResult.isFailure) {
                val reason = stagedResult.exceptionOrNull()
                    ?.message
                    ?: "Media staging failed"
                withContext(Dispatchers.Main) {
                    showFailureDiagnosis(
                        RecapFailureDiagnosis.mediaStagingFailed(
                            uri = uri.toString(),
                            reason = reason
                        )
                    )
                }
                return@launch
            }

            val staged = stagedResult.getOrThrow()

            // Extract duration from the shared staged copy that Termux/ffmpeg can read.
            var durationMs = 60_000L
            runCatching {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(staged.localPath)
                val durStr = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_DURATION
                )
                durStr?.toLongOrNull()?.let { durationMs = it }
                retriever.release()
            }

            val updatedProject = project.copy(
                sourceUri = staged.sourceUri,
                sourceFileName = staged.displayName,
                sourceLocalPath = staged.localPath,
                sourceDurationMs = durationMs,
                updatedAt = System.currentTimeMillis()
            )
            db.recapProjectDao().upsert(updatedProject)

            val initialClips = RecapTimelineManager.createInitialClips(
                projectId = project.id,
                sourcePath = staged.localPath,
                totalDurationMs = durationMs
            )
            db.recapClipDao().deleteClipsForProject(project.id)
            db.recapClipDao().upsertAll(initialClips)

            withContext(Dispatchers.Main) {
                bindProject(updatedProject)
                hideFailureDiagnosis()
                Toast.makeText(
                    this@RecapStudioActivity,
                    "Media imported: ${staged.displayName}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun promptCreateNewProject() {
        val input = EditText(this).apply { hint = "Project Name" }
        AlertDialog.Builder(this)
            .setTitle("New Recap Project")
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                val name = input.text.toString().trim().ifBlank { "Recap Project ${System.currentTimeMillis() % 1000}" }
                lifecycleScope.launch {
                    val newProj = RecapProjectEntity(
                        id = UUID.randomUUID().toString(),
                        name = name,
                        language = selectedLanguage,
                        recapMode = selectedScriptMode
                    )
                    db.recapProjectDao().upsert(newProj)
                    bindProject(newProj)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun promptRenameProject() {
        val current = activeProject ?: return
        val input = EditText(this).apply { setText(current.name) }
        AlertDialog.Builder(this)
            .setTitle("Rename Project")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotBlank()) {
                    lifecycleScope.launch {
                        db.recapProjectDao().rename(current.id, newName)
                        activeProject = current.copy(name = newName)
                        binding.tvProjectName.text = "Project: $newName"
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun generateAiScript() {
        val project = activeProject ?: return
        val topic = binding.etTranscriptInput.text.toString().trim()
        if (topic.isBlank()) {
            Toast.makeText(this, "Please enter a transcript summary or topic", Toast.LENGTH_SHORT).show()
            return
        }

        binding.btnGenerateScript.isEnabled = false
        binding.btnGenerateScript.text = "Generating..."

        lifecycleScope.launch {
            val secretStore = SecretStore(this@RecapStudioActivity)
            // Use active provider or fallback to OpenRouter
            val provider = AiProvider.OPENROUTER
            val apiKey = secretStore.get(provider.name)

            val result = RecapScriptGenerator.generateScript(
                aiRepository = aiRepository,
                provider = provider,
                apiKey = apiKey,
                model = provider.defaultModel,
                transcriptOrTopic = topic,
                mode = selectedScriptMode,
                targetLanguage = selectedLanguage,
                projectId = project.id
            )

            binding.btnGenerateScript.isEnabled = true
            binding.btnGenerateScript.text = getString(R.string.btn_generate_script)

            result.fold(
                onSuccess = { generatedParts ->
                    scripts.clear()
                    scripts.addAll(generatedParts)
                    db.recapScriptDao().deleteScriptsForProject(project.id)
                    db.recapScriptDao().upsertAll(generatedParts)
                    bindScriptsUi()
                    hideFailureDiagnosis()
                    Toast.makeText(this@RecapStudioActivity, "AI Script generated (4 parts)", Toast.LENGTH_SHORT).show()
                },
                onFailure = { error ->
                    val diag = if (apiKey.isBlank()) {
                        RecapFailureDiagnosis.missingApiKey(provider.label)
                    } else {
                        RecapFailureDiagnosis(
                            whatFailed = "AI Script Generation",
                            why = error.message ?: "AI request failed",
                            safeNextStep = "Check internet connection and API balance",
                            canRetry = true
                        )
                    }
                    showFailureDiagnosis(diag)
                }
            )
        }
    }

    private fun bindScriptsUi() {
        val p1 = scripts.firstOrNull { it.partNumber == 1 }?.scriptText.orEmpty()
        val p2 = scripts.firstOrNull { it.partNumber == 2 }?.scriptText.orEmpty()
        val p3 = scripts.firstOrNull { it.partNumber == 3 }?.scriptText.orEmpty()
        val p4 = scripts.firstOrNull { it.partNumber == 4 }?.scriptText.orEmpty()

        binding.etScriptPart1.setText(p1)
        binding.etScriptPart2.setText(p2)
        binding.etScriptPart3.setText(p3)
        binding.etScriptPart4.setText(p4)
    }

    private fun persistScriptsFromUi() {
        val project = activeProject ?: return
        val p1 = binding.etScriptPart1.text.toString().trim()
        val p2 = binding.etScriptPart2.text.toString().trim()
        val p3 = binding.etScriptPart3.text.toString().trim()
        val p4 = binding.etScriptPart4.text.toString().trim()

        val list = listOf(
            RecapScriptEntity(UUID.randomUUID().toString(), project.id, 1, "Hook & Introduction", p1, selectedVoice),
            RecapScriptEntity(UUID.randomUUID().toString(), project.id, 2, "Rising Action", p2, selectedVoice),
            RecapScriptEntity(UUID.randomUUID().toString(), project.id, 3, "Climax", p3, selectedVoice),
            RecapScriptEntity(UUID.randomUUID().toString(), project.id, 4, "Resolution & Conclusion", p4, selectedVoice)
        )
        scripts.clear()
        scripts.addAll(list)
        lifecycleScope.launch {
            db.recapScriptDao().upsertAll(list)
        }
    }

    private fun renderClipsUi() {
        binding.clipsContainer.removeAllViews()
        val count = clips.size
        binding.tvClipsHeader.text = "TIMELINE & CLIPS ($count / 18)"

        if (clips.isEmpty()) {
            binding.tvTimelineValidation.text =
                "No clips yet. Select media, then use Auto Split or + Clip."
            binding.tvTimelineValidation.setTextColor(
                ContextCompat.getColor(this, R.color.z_muted)
            )
        } else {
            val validation = RecapTimelineManager.validateTimeline(clips)
            if (validation.isSuccess) {
                binding.tvTimelineValidation.text = "Status: Valid ($count clips)"
                binding.tvTimelineValidation.setTextColor(
                    ContextCompat.getColor(this, R.color.z_green)
                )
            } else {
                val reason = validation.exceptionOrNull()
                    ?.message
                    ?.lineSequence()
                    ?.firstOrNull()
                    ?.take(120)
                    ?: "Timeline needs attention"
                binding.tvTimelineValidation.text = "Status: Needs attention — $reason"
                binding.tvTimelineValidation.setTextColor(
                    ContextCompat.getColor(this, R.color.z_red)
                )
            }
        }

        for ((index, clip) in clips.withIndex()) {
            val clipView = layoutInflater.inflate(R.layout.item_recap_clip, binding.clipsContainer, false)
            val tvNum = clipView.findViewById<TextView>(R.id.tvClipNumber)
            val etStart = clipView.findViewById<EditText>(R.id.etClipStartMs)
            val etEnd = clipView.findViewById<EditText>(R.id.etClipEndMs)
            val btnEffect = clipView.findViewById<Button>(R.id.btnClipEffect)
            val tvDur = clipView.findViewById<TextView>(R.id.tvClipDuration)
            val btnUp = clipView.findViewById<Button>(R.id.btnClipUp)
            val btnDown = clipView.findViewById<Button>(R.id.btnClipDown)
            val btnDelete = clipView.findViewById<Button>(R.id.btnClipDelete)

            tvNum.text = "Clip #${index + 1}"
            etStart.setText(clip.startMs.toString())
            etEnd.setText(clip.endMs.toString())
            btnEffect.text = clip.effectType
            tvDur.text = "Dur: ${(clip.endMs - clip.startMs).coerceAtLeast(0) / 1000.0}s"

            etStart.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) {
                    val s = etStart.text.toString().toLongOrNull() ?: clip.startMs
                    updateClip(index, clip.copy(startMs = s))
                }
            }
            etEnd.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) {
                    val e = etEnd.text.toString().toLongOrNull() ?: clip.endMs
                    updateClip(index, clip.copy(endMs = e))
                }
            }

            btnEffect.setOnClickListener {
                val nextEffect = when (clip.effectType) {
                    RecapClipEntity.EFFECT_NONE -> RecapClipEntity.EFFECT_FREEZE_ZOOM
                    RecapClipEntity.EFFECT_FREEZE_ZOOM -> RecapClipEntity.EFFECT_BLUR
                    RecapClipEntity.EFFECT_BLUR -> RecapClipEntity.EFFECT_SUBTITLES
                    else -> RecapClipEntity.EFFECT_NONE
                }
                updateClip(index, clip.copy(effectType = nextEffect))
            }

            btnUp.setOnClickListener {
                if (index > 0) {
                    val reordered = RecapTimelineManager.reorderClips(clips, index, index - 1)
                    clips.clear()
                    clips.addAll(reordered)
                    persistClips()
                    renderClipsUi()
                }
            }

            btnDown.setOnClickListener {
                if (index < clips.size - 1) {
                    val reordered = RecapTimelineManager.reorderClips(clips, index, index + 1)
                    clips.clear()
                    clips.addAll(reordered)
                    persistClips()
                    renderClipsUi()
                }
            }

            btnDelete.setOnClickListener {
                if (clips.size > 1) {
                    clips.removeAt(index)
                    persistClips()
                    renderClipsUi()
                } else {
                    Toast.makeText(this, "Project must have at least 1 clip", Toast.LENGTH_SHORT).show()
                }
            }

            binding.clipsContainer.addView(clipView)
        }
    }

    private fun updateClip(index: Int, updated: RecapClipEntity) {
        if (index in clips.indices) {
            clips[index] = updated
            persistClips()
            renderClipsUi()
        }
    }

    private fun persistClips() {
        val project = activeProject ?: return
        lifecycleScope.launch {
            db.recapClipDao().deleteClipsForProject(project.id)
            db.recapClipDao().upsertAll(clips)
        }
    }

    private fun resetTimelineClips() {
        val project = activeProject ?: return
        val src = project.sourceLocalPath
        if (src == null) {
            Toast.makeText(this, "Select source media first", Toast.LENGTH_SHORT).show()
            return
        }
        val initial = RecapTimelineManager.createInitialClips(project.id, src, project.sourceDurationMs, 4)
        clips.clear()
        clips.addAll(initial)
        persistClips()
        renderClipsUi()
        updateRenderAvailability()
    }

    private fun addNewClip() {
        val project = activeProject ?: return
        if (clips.size >= RecapTimelineManager.MAX_CLIPS) {
            Toast.makeText(this, "Max 18 clips allowed per project", Toast.LENGTH_SHORT).show()
            return
        }
        val src = project.sourceLocalPath
        if (src.isNullOrBlank()) {
            Toast.makeText(
                this,
                "Select source media before adding timeline clips",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        val lastEnd = clips.lastOrNull()?.endMs ?: 0L
        val newClip = RecapClipEntity(
            id = UUID.randomUUID().toString(),
            projectId = project.id,
            orderIndex = clips.size,
            sourcePath = src,
            startMs = lastEnd,
            endMs = lastEnd + 5000L,
            durationMs = project.sourceDurationMs,
            effectType = RecapClipEntity.EFFECT_NONE
        )
        clips.add(newClip)
        persistClips()
        renderClipsUi()
        updateRenderAvailability()
    }

    private fun updateRenderAvailability() {
        val sourcePath = activeProject?.sourceLocalPath
        val sourceReady =
            !sourcePath.isNullOrBlank() &&
                !sourcePath.startsWith("content://", ignoreCase = true) &&
                File(sourcePath).isFile &&
                File(sourcePath).length() > 0L
        val timelineReady = clips.isNotEmpty()

        binding.btnStartRender.isEnabled = sourceReady && timelineReady
        if (!sourceReady) {
            binding.tvRenderStatus.text =
                "Status: WAITING • Select media to enable render"
        } else if (!timelineReady) {
            binding.tvRenderStatus.text =
                "Status: WAITING • Add at least one clip"
        } else if (binding.tvRenderStatus.text.toString().startsWith("Status: WAITING")) {
            binding.tvRenderStatus.text =
                "Status: IDLE • Ready to render"
        }
    }

    private fun previewTtsVoice() {
        persistScriptsFromUi()
        val firstPart = scripts.firstOrNull { it.scriptText.isNotBlank() }
        if (firstPart == null) {
            Toast.makeText(this, "Enter script text to preview", Toast.LENGTH_SHORT).show()
            return
        }

        val previewPath = "/data/data/com.termux/files/home/recap/preview_${System.currentTimeMillis()}.mp3"
        val requestResult = RecapTtsCoordinator.buildPreviewRequest(firstPart, previewPath)
        if (requestResult.isFailure) {
            Toast.makeText(this, requestResult.exceptionOrNull()?.message, Toast.LENGTH_SHORT).show()
            return
        }

        binding.tvTtsStatus.text = "Voiceover: Generating preview..."
        lifecycleScope.launch {
            TermuxBridge.send(this@RecapStudioActivity, requestResult.getOrThrow()).fold(
                onSuccess = { jobId ->
                    binding.tvTtsStatus.text = "Voiceover: Preview job queued ($jobId)"
                },
                onFailure = { err ->
                    showFailureDiagnosis(RecapFailureDiagnosis.missingVoiceover(firstPart.partNumber, err.message ?: "Failed"))
                }
            )
        }
    }

    private fun generateTtsVoiceover() {
        persistScriptsFromUi()
        val project = activeProject ?: return
        val combinedText = scripts.sortedBy { it.partNumber }.joinToString(" ") { it.scriptText }
        if (combinedText.isBlank()) {
            Toast.makeText(this, "Enter script parts first", Toast.LENGTH_SHORT).show()
            return
        }

        val outAudio = "/data/data/com.termux/files/home/recap/voice_${project.id}.mp3"
        val fullScript = RecapScriptEntity(
            id = UUID.randomUUID().toString(),
            projectId = project.id,
            partNumber = 1,
            title = "Combined",
            scriptText = combinedText,
            ttsVoice = selectedVoice,
            ttsSpeed = binding.etTtsSpeed.text.toString().trim()
        )

        val reqResult = RecapTtsCoordinator.buildVoiceoverRequest(fullScript, outAudio)
        if (reqResult.isFailure) {
            Toast.makeText(this, reqResult.exceptionOrNull()?.message, Toast.LENGTH_SHORT).show()
            return
        }

        binding.tvTtsStatus.text = "Voiceover: Generating full audio..."
        lifecycleScope.launch {
            TermuxBridge.send(this@RecapStudioActivity, reqResult.getOrThrow()).fold(
                onSuccess = { jobId ->
                    binding.tvTtsStatus.text = "Voiceover: Queued ($outAudio)"
                    // Associate audio path with project scripts
                    val updatedScripts = scripts.map { it.copy(ttsAudioPath = outAudio) }
                    scripts.clear()
                    scripts.addAll(updatedScripts)
                    db.recapScriptDao().upsertAll(updatedScripts)
                },
                onFailure = { err ->
                    showFailureDiagnosis(RecapFailureDiagnosis.missingVoiceover(1, err.message ?: "TTS failed"))
                }
            )
        }
    }

    // 8-stage pipeline UI badges
    private fun setupPipelineBadges() {
        binding.pipelineStagesContainer.removeAllViews()
        for (stage in RecapRenderJobEntity.PIPELINE_STAGES) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 3, 0, 3)
            }
            val title = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                text = stage
                setTextColor(ContextCompat.getColor(this@RecapStudioActivity, R.color.z_text))
                textSize = 11f
            }
            val badge = TextView(this).apply {
                tag = "badge_$stage"
                text = "PENDING"
                textSize = 10f
                setTextColor(ContextCompat.getColor(this@RecapStudioActivity, R.color.z_muted))
                setBackgroundColor(0xFF1B1B1B.toInt())
                setPadding(8, 2, 8, 2)
            }
            row.addView(title)
            row.addView(badge)
            binding.pipelineStagesContainer.addView(row)
        }
    }

    private fun updateStageBadge(stage: String, status: String) {
        val badge = binding.pipelineStagesContainer.findViewWithTag<TextView>("badge_$stage") ?: return
        badge.text = status
        when (status) {
            "RUNNING" -> {
                badge.setTextColor(ContextCompat.getColor(this, R.color.z_orange))
                badge.setBackgroundColor(0xFF3E2D12.toInt())
            }
            "SUCCESS" -> {
                badge.setTextColor(ContextCompat.getColor(this, R.color.z_green))
                badge.setBackgroundColor(0xFF1E3A24.toInt())
            }
            "FAILED" -> {
                badge.setTextColor(ContextCompat.getColor(this, R.color.z_red))
                badge.setBackgroundColor(0xFF3D1818.toInt())
            }
            else -> {
                badge.setTextColor(ContextCompat.getColor(this, R.color.z_muted))
                badge.setBackgroundColor(0xFF1B1B1B.toInt())
            }
        }
    }

    private fun startRenderPipeline() {
        persistScriptsFromUi()
        val project = activeProject ?: return

        // 1. STAGE: VALIDATION
        updateStageBadge(RecapRenderJobEntity.STAGE_VALIDATION, "RUNNING")

        val sourcePath = project.sourceLocalPath
        if (
            sourcePath.isNullOrBlank() ||
                sourcePath.startsWith("content://", ignoreCase = true) ||
                !File(sourcePath).isFile ||
                File(sourcePath).length() <= 0L
        ) {
            updateStageBadge(RecapRenderJobEntity.STAGE_VALIDATION, "FAILED")
            showFailureDiagnosis(
                RecapFailureDiagnosis.missingMediaFile(sourcePath)
            )
            updateRenderAvailability()
            return
        }

        val timelineVal = RecapTimelineManager.validateTimeline(clips)
        if (timelineVal.isFailure) {
            updateStageBadge(RecapRenderJobEntity.STAGE_VALIDATION, "FAILED")
            showFailureDiagnosis(RecapFailureDiagnosis.invalidClipCount(clips.size))
            updateRenderAvailability()
            return
        }
        updateStageBadge(RecapRenderJobEntity.STAGE_VALIDATION, "SUCCESS")

        // 2. STAGE: MEDIA PREPARATION
        updateStageBadge(RecapRenderJobEntity.STAGE_MEDIA_PREPARATION, "RUNNING")
        val outputDir = "/storage/emulated/0/Download"
        val planResult = RecapRenderCoordinator.preparePlan(
            project = project,
            clips = clips,
            scripts = scripts,
            outputDirectory = outputDir
        )

        if (planResult.isFailure) {
            updateStageBadge(RecapRenderJobEntity.STAGE_MEDIA_PREPARATION, "FAILED")
            showFailureDiagnosis(RecapFailureDiagnosis.missingMediaFile(project.sourceLocalPath))
            return
        }
        val plan = planResult.getOrThrow()
        updateStageBadge(RecapRenderJobEntity.STAGE_MEDIA_PREPARATION, "SUCCESS")

        // Create Room Render Job
        val renderJob = RecapRenderJobEntity(
            jobId = plan.jobId,
            projectId = project.id,
            stage = RecapRenderJobEntity.STAGE_CLIP_PROCESSING,
            status = RecapRenderJobEntity.STATUS_QUEUED,
            outputReference = plan.outputFilePath
        )

        lifecycleScope.launch {
            db.recapRenderJobDao().upsert(renderJob)
            currentRenderJobId = plan.jobId

            binding.tvRenderStatus.text = "Status: QUEUED • Dispatching to Termux"
            binding.renderProgressBar.progress = 20
            hideFailureDiagnosis()
            binding.layoutOutputDetails.visibility = View.GONE

            updateStageBadge(RecapRenderJobEntity.STAGE_CLIP_PROCESSING, "RUNNING")
            updateStageBadge(RecapRenderJobEntity.STAGE_VOICEOVER, "RUNNING")
            updateStageBadge(RecapRenderJobEntity.STAGE_FINAL_ENCODE, "RUNNING")

            TermuxBridge.send(
                this@RecapStudioActivity,
                TermuxBridge.Request(
                    tool = "ffmpeg",
                    args = plan.ffmpegArgs,
                    workDir = plan.workDir,
                    label = "Recap Render (${project.name})"
                )
            ).fold(
                onSuccess = { termuxJobId ->
                    observeRenderExecution(plan.jobId, termuxJobId, plan.outputFilePath)
                },
                onFailure = { ex ->
                    handleRenderFailure(plan.jobId, ex.message ?: "Failed to dispatch FFmpeg")
                }
            )
        }
    }

    private fun observeRenderExecution(recapJobId: String, termuxJobId: String, outputPath: String) {
        renderObserverJob?.cancel()
        renderObserverJob = lifecycleScope.launch {
            db.buildJobDao().observe(termuxJobId).filterNotNull().collect { job ->
                renderProgressUpdate(recapJobId, job, outputPath)
            }
        }
    }

    private fun renderProgressUpdate(recapJobId: String, job: BuildJobEntity, outputPath: String) {
        binding.tvRenderStatus.text = "Status: ${job.status}" + (job.exitCode?.let { " • Exit $it" } ?: "")
        binding.tvRenderLog.text = LogSanitizer.sanitize(
            buildString {
                append("JOB ID: ").append(recapJobId).append("\n")
                append("TOOL: ").append(job.tool).append("\n")
                append("STATUS: ").append(job.status).append("\n")
                if (job.stdout.isNotBlank()) append("\nSTDOUT:\n").append(job.stdout)
                if (job.stderr.isNotBlank()) append("\nSTDERR:\n").append(job.stderr)
                if (job.errorMessage.isNotBlank()) append("\nERROR:\n").append(job.errorMessage)
            }
        )

        when (job.status) {
            BuildJobEntity.RUNNING -> {
                binding.renderProgressBar.progress = 60
                updateStageBadge(RecapRenderJobEntity.STAGE_FINAL_ENCODE, "RUNNING")
            }
            BuildJobEntity.SUCCESS -> {
                // STAGE: OUTPUT VERIFICATION
                updateStageBadge(RecapRenderJobEntity.STAGE_FINAL_ENCODE, "SUCCESS")
                updateStageBadge(RecapRenderJobEntity.STAGE_OUTPUT_VERIFICATION, "RUNNING")

                val verifyResult = RecapRenderCoordinator.verifyRenderResult(job.exitCode, outputPath)
                if (verifyResult.isSuccess) {
                    val verification = verifyResult.getOrThrow()
                    lastRenderOutputVerification = verification
                    binding.renderProgressBar.progress = 100
                    updateStageBadge(RecapRenderJobEntity.STAGE_OUTPUT_VERIFICATION, "SUCCESS")
                    binding.tvRenderStatus.text = "Status: SUCCESS • Verified Output"
                    binding.tvRenderStatus.setTextColor(ContextCompat.getColor(this, R.color.z_green))

                    binding.layoutOutputDetails.visibility = View.VISIBLE
                    binding.tvOutputFilename.text = "File: ${verification.fileName}"
                    binding.tvOutputSize.text = "Size: ${verification.sizeBytes} bytes"
                    binding.tvOutputPath.text = "Path: ${verification.filePath}"

                    lifecycleScope.launch {
                        db.recapRenderJobDao().markFinished(
                            jobId = recapJobId,
                            status = RecapRenderJobEntity.STATUS_SUCCESS,
                            outputReference = verification.filePath,
                            outputSizeBytes = verification.sizeBytes
                        )
                        activeProject?.let { p ->
                            db.recapProjectDao().updateOutput(p.id, verification.filePath, verification.sizeBytes, null)
                        }
                    }
                } else {
                    val ex = verifyResult.exceptionOrNull()!!
                    handleRenderFailure(recapJobId, ex.message ?: "Output verification failed")
                }
            }
            BuildJobEntity.FAILED -> {
                handleRenderFailure(recapJobId, "Exit ${job.exitCode}: ${job.errorMessage.ifBlank { job.stderr }}")
            }
        }
    }

    private fun handleRenderFailure(jobId: String, reason: String) {
        updateStageBadge(RecapRenderJobEntity.STAGE_FINAL_ENCODE, "FAILED")
        updateStageBadge(RecapRenderJobEntity.STAGE_OUTPUT_VERIFICATION, "FAILED")
        binding.tvRenderStatus.text = "Status: FAILED"
        binding.tvRenderStatus.setTextColor(ContextCompat.getColor(this, R.color.z_red))

        val diag = RecapFailureDiagnosis.ffmpegProcessFailed(null, reason)
        showFailureDiagnosis(diag)

        lifecycleScope.launch {
            db.recapRenderJobDao().markFinished(
                jobId = jobId,
                status = RecapRenderJobEntity.STATUS_FAILED,
                failureDiagnosis = diag.format(),
                errorReason = reason
            )
        }
    }

    private fun cancelCurrentRender() {
        val jobId = currentRenderJobId ?: return
        renderObserverJob?.cancel()
        binding.tvRenderStatus.text = "Status: CANCELLED"
        val diag = RecapFailureDiagnosis.renderCancelled()
        showFailureDiagnosis(diag)

        lifecycleScope.launch {
            db.recapRenderJobDao().markFinished(
                jobId = jobId,
                status = RecapRenderJobEntity.STATUS_CANCELLED,
                failureDiagnosis = diag.format(),
                errorReason = "User cancelled"
            )
        }
    }

    private fun showFailureDiagnosis(diagnosis: RecapFailureDiagnosis) {
        binding.layoutFailureDiagnosis.visibility = View.VISIBLE
        binding.tvDiagnosisText.text = diagnosis.format()
    }

    private fun hideFailureDiagnosis() {
        binding.layoutFailureDiagnosis.visibility = View.GONE
    }

    private fun openRenderedVideo() {
        val path = lastRenderOutputVerification?.filePath ?: return
        val file = File(path)
        if (!file.exists()) {
            Toast.makeText(this, "File not found", Toast.LENGTH_SHORT).show()
            return
        }
        val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "video/mp4")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Open Recap Video"))
    }

    private fun shareRenderedVideo() {
        val path = lastRenderOutputVerification?.filePath ?: return
        val file = File(path)
        if (!file.exists()) {
            Toast.makeText(this, "File not found", Toast.LENGTH_SHORT).show()
            return
        }
        val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Share Recap Video"))
    }

    override fun onDestroy() {
        renderObserverJob?.cancel()
        super.onDestroy()
    }
}
