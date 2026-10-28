package com.zipbug.base.ui

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.zipbug.base.R
import com.zipbug.base.ai.*
import com.zipbug.base.command.ZippyCommandAction
import com.zipbug.base.command.ZippyCommandRouter
import com.zipbug.base.data.ChatMessageEntity
import com.zipbug.base.data.ChatSessionEntity
import com.zipbug.base.data.ZipBugDatabase
import com.zipbug.base.databinding.ActivityMainBinding
import com.zipbug.base.databinding.BottomSheetModelPickerBinding
import com.zipbug.base.databinding.BottomSheetToolsBinding
import com.zipbug.base.engine.EngineConnectionClient
import com.zipbug.base.security.SecretStore
import com.zipbug.base.termux.TermuxBridge
import com.zipbug.base.ui.chat.ChatAdapter
import com.zipbug.base.ui.chat.ChatHistoryAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var db: ZipBugDatabase
    private val repository = AiRepository()

    private var currentSessionId: String = UUID.randomUUID().toString()
    private var currentProvider: AiProvider = AiProvider.OPENROUTER
    private var currentModel: String = "openrouter/free"
    private var activeJob: Job? = null
    private var attachedContext: String? = null

    private lateinit var chatAdapter: ChatAdapter
    private lateinit var historyAdapter: ChatHistoryAdapter
    private var allHistorySessions = listOf<ChatSessionEntity>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = ZipBugDatabase.get(this)

        applySystemBarsTheme()
        setupChatRecycler()
        setupSidebarDrawer()
        setupHeaderActions()
        setupEmptyStateSuggestions()
        setupComposer()
        loadOrCreateInitialSession()
        refreshHeaderEngineBadge()
    }

    override fun onResume() {
        super.onResume()
        applySystemBarsTheme()
        refreshHeaderEngineBadge()
        refreshHistoryList()
    }

    private fun applySystemBarsTheme() {
        val isDark = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = !isDark
            isAppearanceLightNavigationBars = !isDark
        }
    }

    private fun setupChatRecycler() {
        chatAdapter = ChatAdapter(
            onOpenWorkspaceWithSnippet = { snippet, _ ->
                val intent = Intent(this, WorkspaceActivity::class.java)
                startActivity(intent)
                Toast.makeText(this, getString(R.string.chat_toast_opening_workspace), Toast.LENGTH_SHORT).show()
            },
            onConfigureKey = {
                startActivity(Intent(this, SettingsActivity::class.java))
            },
            onRetry = { failedMsg ->
                retryMessage(failedMsg)
            }
        )

        val llm = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
        binding.rvChatMessages.layoutManager = llm
        binding.rvChatMessages.adapter = chatAdapter
    }

    private fun setupSidebarDrawer() {
        historyAdapter = ChatHistoryAdapter(
            onSelectSession = { session ->
                currentSessionId = session.id
                currentModel = session.model
                val matchedProvider = AiProvider.values().firstOrNull { it.name.equals(session.provider, ignoreCase = true) }
                if (matchedProvider != null) currentProvider = matchedProvider
                updateModelPillLabel()
                loadMessagesForSession(session.id)
                binding.drawerLayout.closeDrawer(GravityCompat.START)
            },
            onRenameSession = { session ->
                promptRenameSession(session)
            },
            onTogglePin = { session ->
                lifecycleScope.launch(Dispatchers.IO) {
                    db.chatDao().togglePinSession(session.id, !session.isPinned)
                    refreshHistoryList()
                }
            },
            onDeleteSession = { session ->
                promptDeleteSession(session)
            }
        )

        binding.rvChatHistory.layoutManager = LinearLayoutManager(this)
        binding.rvChatHistory.adapter = historyAdapter

        binding.btnDrawerNewChat.setOnClickListener {
            startNewChat()
            binding.drawerLayout.closeDrawer(GravityCompat.START)
        }

        binding.etSearchChats.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val q = s?.toString().orEmpty().trim().lowercase()
                val filtered = if (q.isBlank()) allHistorySessions else allHistorySessions.filter { it.title.lowercase().contains(q) }
                historyAdapter.submitList(filtered)
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Studio & Workspace drawer shortcuts
        binding.drawerItemNewApp.setOnClickListener {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            startActivity(Intent(this, AppBuilderActivity::class.java))
        }
        binding.drawerItemProjects.setOnClickListener {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            startActivity(Intent(this, WorkspaceActivity::class.java))
        }
        binding.drawerItemStudio.setOnClickListener {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            showStudioChoice()
        }
        binding.drawerItemReact.setOnClickListener {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            startActivity(Intent(this, WorkspaceActivity::class.java))
        }
        binding.drawerItemBuild.setOnClickListener {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            startActivity(Intent(this, TerminalActivity::class.java))
        }
        binding.drawerItemAudio.setOnClickListener {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            startActivity(Intent(this, TtsStudioActivity::class.java))
        }
        binding.drawerItemVideo.setOnClickListener {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            showVideoChoice()
        }
        binding.drawerItemDevTools.setOnClickListener {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            startActivity(Intent(this, DeveloperToolsActivity::class.java))
        }

        binding.btnDrawerSettings.setOnClickListener {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        binding.btnDrawerEngineStatus.setOnClickListener {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            startActivity(
                Intent(this, SettingsActivity::class.java)
                    .putExtra(SettingsActivity.EXTRA_SECTION, SettingsActivity.SECTION_ENGINE)
            )
        }
    }

    private fun setupHeaderActions() {
        binding.btnNavDrawer.setOnClickListener {
            binding.drawerLayout.openDrawer(GravityCompat.START)
        }

        binding.btnModelSelector.setOnClickListener {
            showModelPickerSheet()
        }

        binding.btnNewChat.setOnClickListener {
            startNewChat()
        }

        binding.btnHeaderOverflow.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        updateModelPillLabel()
    }

    private fun updateModelPillLabel() {
        binding.tvCurrentModelLabel.text = when (currentProvider) {
            AiProvider.OPENROUTER -> "OpenRouter Free"
            AiProvider.GEMINI -> "Gemini 2.5 Flash"
            AiProvider.OPENAI -> "GPT-4.1 Mini"
            AiProvider.ANTHROPIC -> "Claude Sonnet"
        }
    }

    private fun setupEmptyStateSuggestions() {
        binding.chipSuggestApp.setOnClickListener {
            binding.etChatMessage.setText(R.string.chat_prompt_app)
            binding.etChatMessage.requestFocus()
        }
        binding.chipSuggestCode.setOnClickListener {
            binding.etChatMessage.setText(R.string.chat_prompt_code)
            binding.etChatMessage.requestFocus()
        }
        binding.chipSuggestWeb.setOnClickListener {
            binding.etChatMessage.setText(R.string.chat_prompt_web)
            binding.etChatMessage.requestFocus()
        }
        binding.chipSuggestFile.setOnClickListener {
            binding.etChatMessage.setText(R.string.chat_prompt_file)
            binding.etChatMessage.requestFocus()
        }
    }

    private fun setupComposer() {
        binding.btnAttachTool.setOnClickListener {
            showToolsBottomSheet()
        }

        binding.btnRemoveAttachment.setOnClickListener {
            attachedContext = null
            binding.layoutAttachmentBar.visibility = View.GONE
        }

        binding.btnSendStop.setOnClickListener {
            if (activeJob != null && activeJob?.isActive == true) {
                cancelGeneration()
            } else {
                sendMessage()
            }
        }
    }

    private fun loadOrCreateInitialSession() {
        lifecycleScope.launch(Dispatchers.IO) {
            val sessions = db.chatDao().listSessions()
            withContext(Dispatchers.Main) {
                allHistorySessions = sessions
                historyAdapter.submitList(sessions)
                if (sessions.isNotEmpty()) {
                    val first = sessions.first()
                    currentSessionId = first.id
                    currentModel = first.model
                    val matchedProvider = AiProvider.values().firstOrNull { it.name.equals(first.provider, ignoreCase = true) }
                    if (matchedProvider != null) currentProvider = matchedProvider
                    updateModelPillLabel()
                    loadMessagesForSession(first.id)
                } else {
                    startNewChat()
                }
            }
        }
    }

    private fun refreshHistoryList() {
        lifecycleScope.launch(Dispatchers.IO) {
            val list = db.chatDao().listSessions()
            withContext(Dispatchers.Main) {
                allHistorySessions = list
                historyAdapter.submitList(list)
            }
        }
    }

    private fun loadMessagesForSession(sessionId: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            val messages = db.chatDao().getMessages(sessionId)
            withContext(Dispatchers.Main) {
                chatAdapter.submitList(messages)
                if (messages.isEmpty()) {
                    binding.layoutChatEmptyState.visibility = View.VISIBLE
                    binding.rvChatMessages.visibility = View.GONE
                } else {
                    binding.layoutChatEmptyState.visibility = View.GONE
                    binding.rvChatMessages.visibility = View.VISIBLE
                    binding.rvChatMessages.scrollToPosition(messages.size - 1)
                }
            }
        }
    }

    private fun startNewChat() {
        currentSessionId = UUID.randomUUID().toString()
        chatAdapter.submitList(emptyList())
        binding.layoutChatEmptyState.visibility = View.VISIBLE
        binding.rvChatMessages.visibility = View.GONE
        binding.etChatMessage.setText("")
        attachedContext = null
        binding.layoutAttachmentBar.visibility = View.GONE
    }

    private fun retryMessage(failedMsg: ChatMessageEntity) {
        lifecycleScope.launch(Dispatchers.IO) {
            val messages = db.chatDao().getMessages(currentSessionId)
            val lastUserMsg = messages.lastOrNull { it.sender == "user" }
            withContext(Dispatchers.Main) {
                if (lastUserMsg != null && activeJob?.isActive != true) {
                    binding.etChatMessage.setText(lastUserMsg.content)
                    binding.etChatMessage.setSelection(lastUserMsg.content.length)
                    sendMessage()
                }
            }
        }
    }

    private fun sendMessage() {
        if (activeJob != null && activeJob?.isActive == true) return
        val text = binding.etChatMessage.text.toString().trim()
        if (text.isBlank()) return
        if (text.startsWith("/") || text.startsWith("fix:", ignoreCase = true) ||
            ZippyCommandRouter.route(text) is ZippyCommandAction.Create) {
            val action = ZippyCommandRouter.route(text)
            if (action !is ZippyCommandAction.Ask) {
                binding.etChatMessage.setText("")
                when (action) {
                    is ZippyCommandAction.Create -> startActivity(AiModeActivity.createIntent(this, action.prompt, AiMode.APP_CREATOR))
                    is ZippyCommandAction.Fix -> startActivity(AiModeActivity.createIntent(this, action.prompt, AiMode.FIX_ERROR))
                    ZippyCommandAction.BuildDebug -> navigateToTerminal("/apkbuilder --debug")
                    ZippyCommandAction.OpenStudio -> navigateToStudio()
                    ZippyCommandAction.OpenProjects -> navigateToProjects()
                    ZippyCommandAction.OpenAppCreator -> startActivity(Intent(this, AppCreatorActivity::class.java))
                    ZippyCommandAction.UndoLastChange -> {
                        val target = getSharedPreferences("zipbug.settings", 0).getString("lastReviewedPatchTarget", "").orEmpty()
                        if (target.isBlank()) Toast.makeText(this, "No reviewed change to restore", Toast.LENGTH_SHORT).show()
                        else navigateToTerminal("/restorepatch $target")
                    }
                    else -> Unit
                }
                return
            }
        }

        val userMsgId = UUID.randomUUID().toString()
        val userMsg = ChatMessageEntity(
            id = userMsgId,
            sessionId = currentSessionId,
            sender = "user",
            content = text,
            toolAttachment = attachedContext
        )

        binding.etChatMessage.setText("")
        attachedContext = null
        binding.layoutAttachmentBar.visibility = View.GONE

        // Hide keyboard
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(binding.etChatMessage.windowToken, 0)

        binding.layoutChatEmptyState.visibility = View.GONE
        binding.rvChatMessages.visibility = View.VISIBLE

        chatAdapter.addMessage(userMsg)
        binding.rvChatMessages.scrollToPosition(chatAdapter.itemCount - 1)

        // Save session if new
        lifecycleScope.launch(Dispatchers.IO) {
            val existing = db.chatDao().getSession(currentSessionId)
            if (existing == null) {
                val title = if (text.length > 36) text.take(36) + "…" else text
                db.chatDao().upsertSession(
                    ChatSessionEntity(
                        id = currentSessionId,
                        title = title,
                        model = currentModel,
                        provider = currentProvider.name
                    )
                )
            } else {
                db.chatDao().updateSessionTitle(currentSessionId, existing.title, System.currentTimeMillis())
            }
            db.chatDao().upsertMessage(userMsg)
            refreshHistoryList()
        }

        // Add assistant placeholder with thinking state
        val asstMsgId = UUID.randomUUID().toString()
        val asstPlaceholder = ChatMessageEntity(
            id = asstMsgId,
            sessionId = currentSessionId,
            sender = "assistant",
            content = "",
            status = "generating"
        )
        chatAdapter.addMessage(asstPlaceholder)
        binding.rvChatMessages.scrollToPosition(chatAdapter.itemCount - 1)

        // Set stop button
        binding.btnSendStop.setImageResource(R.drawable.ic_stop)
        binding.btnSendStop.imageTintList = ContextCompat.getColorStateList(this, R.color.z_red)

        val keySecretName = when (currentProvider) {
            AiProvider.OPENROUTER -> "api_key_openrouter"
            AiProvider.OPENAI -> "api_key_openai"
            AiProvider.GEMINI -> "api_key_gemini"
            AiProvider.ANTHROPIC -> "api_key_anthropic"
        }
        val apiKey = runCatching { SecretStore(this).get(keySecretName).orEmpty() }.getOrDefault("")

        if (apiKey.isBlank()) {
            val missingMsg = getString(R.string.chat_keys_needed, currentProvider.label)
            chatAdapter.updateLastMessage(content = missingMsg, status = "error")
            lifecycleScope.launch(Dispatchers.IO) {
                db.chatDao().upsertMessage(asstPlaceholder.copy(content = missingMsg, status = "error"))
            }
            resetSendButton()
            return
        }

        activeJob = lifecycleScope.launch {
            val pastMessages = withContext(Dispatchers.IO) {
                db.chatDao().getMessages(currentSessionId)
            }
            val historyTurns = pastMessages
                .filter { it.status == "sent" && it.content.isNotBlank() && it.id != asstMsgId }
                .takeLast(8)
                .map { ChatHistoryTurn(role = if (it.sender == "user") "user" else "assistant", content = it.content) }

            val req = AiRequest(
                provider = currentProvider,
                apiKey = apiKey,
                model = currentModel,
                system = "You are ZIP_BUG AI, an expert mobile and web developer assistant. Keep answers concise, highly capable, and provide code blocks when requested.",
                prompt = text,
                mode = AiMode.CHAT,
                history = historyTurns
            )

            val result = withContext(Dispatchers.IO) {
                repository.complete(req)
            }

            result.fold(
                onSuccess = { reply ->
                    chatAdapter.updateLastMessage(content = reply, status = "sent")
                    withContext(Dispatchers.IO) {
                        db.chatDao().upsertMessage(asstPlaceholder.copy(content = reply, status = "sent"))
                    }
                },
                onFailure = { err ->
                    val errMsg = getString(R.string.chat_error_prefix, currentProvider.label, err.message ?: "")
                    chatAdapter.updateLastMessage(content = errMsg, status = "error")
                    withContext(Dispatchers.IO) {
                        db.chatDao().upsertMessage(asstPlaceholder.copy(content = errMsg, status = "error"))
                    }
                }
            )

            resetSendButton()
        }
    }

    private fun cancelGeneration() {
        activeJob?.cancel()
        activeJob = null
        chatAdapter.updateLastMessage(content = getString(R.string.chat_generation_cancelled), status = "cancelled")
        resetSendButton()
    }

    private fun resetSendButton() {
        binding.btnSendStop.setImageResource(R.drawable.ic_send)
        binding.btnSendStop.imageTintList = ContextCompat.getColorStateList(this, R.color.z_orange)
        activeJob = null
    }

    private fun showToolsBottomSheet() {
        val dialog = BottomSheetDialog(this)
        val sheetBinding = BottomSheetToolsBinding.inflate(layoutInflater)
        dialog.setContentView(sheetBinding.root)

        sheetBinding.toolTileNewApp.setOnClickListener {
            dialog.dismiss()
            startActivity(Intent(this, AppBuilderActivity::class.java))
        }
        sheetBinding.toolTileUiPicker.setOnClickListener {
            dialog.dismiss()
            val intent = Intent(this, AppBuilderActivity::class.java).apply {
                putExtra("extra_tab", 2)
            }
            startActivity(intent)
        }
        sheetBinding.toolTileWorkspace.setOnClickListener {
            dialog.dismiss()
            startActivity(Intent(this, WorkspaceActivity::class.java))
        }
        sheetBinding.toolTileStudio.setOnClickListener {
            dialog.dismiss()
            showStudioChoice()
        }
        sheetBinding.toolTileReact.setOnClickListener {
            dialog.dismiss()
            startActivity(Intent(this, WorkspaceActivity::class.java))
        }
        sheetBinding.toolTileApkBuilder.setOnClickListener {
            dialog.dismiss()
            startActivity(Intent(this, TerminalActivity::class.java))
        }
        sheetBinding.toolTileAudio.setOnClickListener {
            dialog.dismiss()
            startActivity(Intent(this, TtsStudioActivity::class.java))
        }
        sheetBinding.toolTileVideo.setOnClickListener {
            dialog.dismiss()
            showVideoChoice()
        }
        sheetBinding.toolTileDevTools.setOnClickListener {
            dialog.dismiss()
            startActivity(Intent(this, DeveloperToolsActivity::class.java))
        }
        sheetBinding.toolTileAppCreator.setOnClickListener {
            dialog.dismiss()
            startActivity(Intent(this, AppCreatorActivity::class.java))
        }
        sheetBinding.toolTileAttachFile.setOnClickListener {
            dialog.dismiss()
            attachedContext = getString(R.string.chat_attachment_root)
            binding.layoutAttachmentBar.visibility = View.VISIBLE
            binding.tvAttachmentChip.text = attachedContext
        }

        dialog.show()
    }

    private fun showModelPickerSheet() {
        val dialog = BottomSheetDialog(this)
        val sheetBinding = BottomSheetModelPickerBinding.inflate(layoutInflater)
        dialog.setContentView(sheetBinding.root)

        val highlightOption = { selected: AiProvider ->
            sheetBinding.modelOptionOpenRouter.setBackgroundResource(if (selected == AiProvider.OPENROUTER) R.drawable.bg_chip_active else R.drawable.bg_chip_inactive)
            sheetBinding.modelOptionGemini.setBackgroundResource(if (selected == AiProvider.GEMINI) R.drawable.bg_chip_active else R.drawable.bg_chip_inactive)
            sheetBinding.modelOptionOpenAi.setBackgroundResource(if (selected == AiProvider.OPENAI) R.drawable.bg_chip_active else R.drawable.bg_chip_inactive)
            sheetBinding.modelOptionClaude.setBackgroundResource(if (selected == AiProvider.ANTHROPIC) R.drawable.bg_chip_active else R.drawable.bg_chip_inactive)
        }

        highlightOption(currentProvider)

        sheetBinding.modelOptionOpenRouter.setOnClickListener {
            currentProvider = AiProvider.OPENROUTER
            currentModel = "openrouter/free"
            updateModelPillLabel()
            dialog.dismiss()
        }
        sheetBinding.modelOptionGemini.setOnClickListener {
            currentProvider = AiProvider.GEMINI
            currentModel = "gemini-2.5-flash"
            updateModelPillLabel()
            dialog.dismiss()
        }
        sheetBinding.modelOptionOpenAi.setOnClickListener {
            currentProvider = AiProvider.OPENAI
            currentModel = "gpt-4.1-mini"
            updateModelPillLabel()
            dialog.dismiss()
        }
        sheetBinding.modelOptionClaude.setOnClickListener {
            currentProvider = AiProvider.ANTHROPIC
            currentModel = "claude-sonnet-4-5"
            updateModelPillLabel()
            dialog.dismiss()
        }

        sheetBinding.btnSheetConfigureKeys.setOnClickListener {
            dialog.dismiss()
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        dialog.show()
    }

    private fun promptRenameSession(session: ChatSessionEntity) {
        val input = EditText(this).apply {
            setText(session.title)
            setSelection(session.title.length)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.btn_rename)
            .setView(input)
            .setPositiveButton(R.string.btn_ok) { _, _ ->
                val newTitle = input.text.toString().trim()
                if (newTitle.isNotBlank()) {
                    lifecycleScope.launch(Dispatchers.IO) {
                        db.chatDao().updateSessionTitle(session.id, newTitle)
                        refreshHistoryList()
                    }
                }
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun promptDeleteSession(session: ChatSessionEntity) {
        AlertDialog.Builder(this)
            .setTitle(R.string.chat_delete_dialog_title)
            .setMessage(getString(R.string.chat_delete_dialog_message, session.title))
            .setPositiveButton(R.string.btn_delete) { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) {
                    db.chatDao().deleteMessagesForSession(session.id)
                    db.chatDao().deleteSession(session.id)
                    withContext(Dispatchers.Main) {
                        if (currentSessionId == session.id) {
                            startNewChat()
                        }
                        refreshHistoryList()
                    }
                }
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun refreshHeaderEngineBadge() {
        val installed = TermuxBridge.isTermuxInstalled(this)
        val permitted = TermuxBridge.hasPermission(this)
        val savedUrl = EngineConnectionClient.getSavedUrl(this)
        val savedToken = EngineConnectionClient.getSavedToken(this)

        lifecycleScope.launch {
            val httpHealth = withContext(Dispatchers.IO) {
                EngineConnectionClient.checkHealth(savedUrl, savedToken, timeoutMs = 1200L)
            }

            if (isFinishing || isDestroyed) return@launch

            if (httpHealth.connected) {
                binding.tvDrawerEngineState.text = getString(R.string.engine_badge_ready)
                binding.tvDrawerEngineDot.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.z_green))
                return@launch
            }

            val termuxReady = withContext(Dispatchers.IO) {
                TermuxBridge.isEnvironmentReady(this@MainActivity)
            }

            if (isFinishing || isDestroyed) return@launch

            when {
                termuxReady -> {
                    binding.tvDrawerEngineState.setText(R.string.engine_badge_ready)
                    binding.tvDrawerEngineDot.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.z_green))
                }
                installed -> {
                    binding.tvDrawerEngineState.setText(R.string.engine_badge_setup)
                    binding.tvDrawerEngineDot.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.z_orange))
                }
                else -> {
                    binding.tvDrawerEngineState.setText(R.string.engine_badge_local)
                    binding.tvDrawerEngineDot.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.z_blue))
                }
            }
        }
    }

    fun navigateToAiWithPrompt(
        prompt: String,
        mode: AiMode = AiMode.CHAT
    ) {
        if (mode != AiMode.CHAT) {
            startActivity(AiModeActivity.createIntent(this, prompt, mode))
        } else {
            binding.etChatMessage.setText(prompt)
            if (prompt.isNotBlank()) sendMessage()
        }
    }

    private fun showVideoChoice() {
        AlertDialog.Builder(this).setTitle("Video tools / ဗီဒီယို ကိရိယာများ")
            .setItems(arrayOf("Video & Recap Studio", "Myanmar Drama Editor / မြန်မာဒရာမာ")) { _, index ->
                val screen = if (index == 0) RecapStudioActivity::class.java else MyanmarDramaEditorActivity::class.java
                startActivity(Intent(this, screen))
            }.setNegativeButton(R.string.btn_cancel, null).show()
    }

    private fun showStudioChoice() {
        AlertDialog.Builder(this).setTitle("Choose Studio / Studio ရွေးပါ")
            .setItems(arrayOf("Visual Studio", "Advanced Studio", "LikeFigma Studio • မြန်မာ / English")) { _, index ->
                startActivity(Intent(this, StudioActivity::class.java).putExtra("variant", when (index) { 1 -> "advanced"; 2 -> "likefigma"; else -> "visual" }))
            }.setNegativeButton(R.string.btn_cancel, null).show()
    }

    fun navigateToStudio() {
        showStudioChoice()
    }

    fun navigateToHome() {
        binding.drawerLayout.closeDrawer(GravityCompat.START)
    }

    fun navigateToProjects() {
        startActivity(Intent(this, WorkspaceActivity::class.java))
    }

    fun navigateToTerminal(command: String = "") {
        val intent = Intent(this, TerminalActivity::class.java).apply {
            putExtra(TerminalActivity.EXTRA_COMMAND, command)
        }
        startActivity(intent)
    }
}
