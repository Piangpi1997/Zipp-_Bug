package com.zipbug.base.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PersistableBundle
import android.text.InputType
import android.text.method.PasswordTransformationMethod
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.zipbug.base.R
import com.zipbug.base.databinding.ActivitySettingsBinding
import com.zipbug.base.engine.EngineConnectionClient
import com.zipbug.base.security.SecretBackup
import com.zipbug.base.security.SecretStore
import com.zipbug.base.termux.TermuxBridge
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding
    private lateinit var secrets: SecretStore

    private var activeSection: String = SECTION_HUB
    private var pendingBackupPassphrase: CharArray? = null

    private val createBackupFile =
        registerForActivityResult(
            ActivityResultContracts.CreateDocument("application/octet-stream")
        ) { uri ->
            val passphrase = pendingBackupPassphrase
            pendingBackupPassphrase = null

            if (uri == null || passphrase == null) {
                passphrase?.fill('\u0000')
                return@registerForActivityResult
            }

            try {
                val encrypted = SecretBackup.encrypt(
                    currentKeys(),
                    passphrase
                )
                contentResolver.openOutputStream(uri, "w")?.use {
                    it.write(encrypted)
                    it.flush()
                } ?: error("Could not open the selected backup file.")

                Snackbar.make(
                    binding.root,
                    "Encrypted API key backup saved",
                    Snackbar.LENGTH_LONG
                ).show()
            } catch (error: Exception) {
                Snackbar.make(
                    binding.root,
                    "Backup failed: ${safeMessage(error)}",
                    Snackbar.LENGTH_LONG
                ).show()
            } finally {
                passphrase.fill('\u0000')
            }
        }

    private val openBackupFile =
        registerForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri ->
            if (uri != null) {
                promptRestorePassphrase(uri)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        secrets = SecretStore(this)
        val prefs = getSharedPreferences(
            "zipbug.settings",
            MODE_PRIVATE
        )

        setupGroupedNavigation()
        setupLanguageAndThemeButtons()
        setupEngineHttpControls()

        // One-time convenience migration for users who pasted an
        // OpenRouter key into any old provider field.
        val savedOpenRouter = secrets.get("openrouter")
        if (savedOpenRouter.isBlank()) {
            listOf("openai", "gemini", "anthropic")
                .firstOrNull {
                    secrets.get(it).startsWith("sk-or-")
                }
                ?.let { oldField ->
                    secrets.put(
                        "openrouter",
                        secrets.get(oldField)
                    )
                    secrets.put(oldField, "")
                }
        }

        loadSecretFields()

        bindSecretControls(
            binding.openrouter,
            binding.openrouterReveal,
            binding.openrouterCopy,
            "OpenRouter"
        )
        bindSecretControls(
            binding.openai,
            binding.openaiReveal,
            binding.openaiCopy,
            "OpenAI"
        )
        bindSecretControls(
            binding.gemini,
            binding.geminiReveal,
            binding.geminiCopy,
            "Gemini"
        )
        bindSecretControls(
            binding.anthropic,
            binding.anthropicReveal,
            binding.anthropicCopy,
            "Claude"
        )

        setupProviderConfigureToggles()

        binding.backupKeys.setOnClickListener {
            promptBackupPassphrase()
        }

        binding.restoreKeys.setOnClickListener {
            openBackupFile.launch(arrayOf("*/*"))
        }

        binding.btnOpenWorkspaceBackup.setOnClickListener {
            startActivity(Intent(this, DeveloperToolsActivity::class.java))
        }

        val home = prefs.getString(
            "termuxHome",
            "/data/data/com.termux/files/home"
        )!!

        binding.termuxHome.setText(home)

        val savedProjectRoot = prefs.getString(
            "projectRoot",
            "$home/OpenDots/Zip_Bug_Antigravity"
        )!!

        val migratedProjectRoot =
            if (savedProjectRoot == "$home/OpenDots/Zip_Bug") {
                "$home/OpenDots/Zip_Bug_Antigravity"
            } else {
                savedProjectRoot
            }

        binding.projectRoot.setText(migratedProjectRoot)
        binding.artifactExportPath.setText(
            prefs.getString(
                "artifactExportPath",
                "/storage/emulated/0/Download/Zip_Bug-debug.apk"
            )
        )

        binding.save.setOnClickListener {
            saveSecrets()
            EngineConnectionClient.saveUrl(this, binding.etEngineHttpUrl.text.toString())
            EngineConnectionClient.saveToken(this, binding.etEngineToken.text.toString())

            prefs.edit()
                .putString(
                    "termuxHome",
                    binding.termuxHome.text.toString()
                )
                .putString(
                    "projectRoot",
                    binding.projectRoot.text.toString()
                )
                .putString(
                    "artifactExportPath",
                    binding.artifactExportPath.text.toString()
                )
                .apply()

            refreshGroupSummaries()
            Snackbar.make(
                binding.root,
                "Encrypted settings saved",
                Snackbar.LENGTH_SHORT
            ).show()
        }

        refreshGroupSummaries()

        val initialSection = savedInstanceState?.getString(KEY_ACTIVE_SECTION)
            ?: intent.getStringExtra(EXTRA_SECTION)
            ?: SECTION_HUB
        showSection(initialSection)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_ACTIVE_SECTION, activeSection)
    }

    private fun setupGroupedNavigation() {
        binding.cardSettingsAppearance.setOnClickListener {
            showSection(SECTION_APPEARANCE)
        }
        binding.cardSettingsEngine.setOnClickListener {
            showSection(SECTION_ENGINE)
        }
        binding.cardSettingsAiProviders.setOnClickListener {
            showSection(SECTION_AI)
        }
        binding.cardSettingsStorageBackup.setOnClickListener {
            showSection(SECTION_STORAGE)
        }
        binding.cardSettingsPrivacyAbout.setOnClickListener {
            showSection(SECTION_PRIVACY)
        }

        binding.btnSettingsBack.setOnClickListener {
            if (activeSection != SECTION_HUB) {
                showSection(SECTION_HUB)
            } else {
                finish()
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (activeSection != SECTION_HUB) {
                    showSection(SECTION_HUB)
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    private fun showSection(section: String) {
        activeSection = section
        binding.layoutSettingsHub.visibility = if (section == SECTION_HUB) View.VISIBLE else View.GONE
        binding.panelSettingsAppearance.visibility = if (section == SECTION_APPEARANCE) View.VISIBLE else View.GONE
        binding.panelSettingsEngine.visibility = if (section == SECTION_ENGINE) View.VISIBLE else View.GONE
        binding.panelSettingsAiProviders.visibility = if (section == SECTION_AI) View.VISIBLE else View.GONE
        binding.panelSettingsStorageBackup.visibility = if (section == SECTION_STORAGE) View.VISIBLE else View.GONE
        binding.panelSettingsPrivacyAbout.visibility = if (section == SECTION_PRIVACY) View.VISIBLE else View.GONE

        binding.tvSettingsHeaderTitle.text = when (section) {
            SECTION_APPEARANCE -> getString(R.string.settings_group_appearance_title)
            SECTION_ENGINE -> getString(R.string.settings_group_engine_title)
            SECTION_AI -> getString(R.string.settings_group_ai_title)
            SECTION_STORAGE -> getString(R.string.settings_group_storage_title)
            SECTION_PRIVACY -> getString(R.string.settings_group_privacy_title)
            else -> getString(R.string.settings_title)
        }
        refreshGroupSummaries()
    }

    private fun setupProviderConfigureToggles() {
        fun bindToggle(btn: Button, editor: View) {
            btn.setOnClickListener {
                val visible = editor.visibility == View.VISIBLE
                editor.visibility = if (visible) View.GONE else View.VISIBLE
                btn.text = if (visible) "Configure" else "Done"
            }
        }
        bindToggle(binding.btnConfigOpenRouter, binding.editorOpenRouter)
        bindToggle(binding.btnConfigOpenAi, binding.editorOpenAi)
        bindToggle(binding.btnConfigGemini, binding.editorGemini)
        bindToggle(binding.btnConfigAnthropic, binding.editorAnthropic)
    }

    private fun refreshGroupSummaries() {
        val lang = LocaleThemeHelper.getSavedLanguage(this)
        val theme = LocaleThemeHelper.getSavedTheme(this)
        val langLabel = when (lang) {
            LocaleThemeHelper.LANG_MYANMAR -> "မြန်မာ"
            LocaleThemeHelper.LANG_ENGLISH -> "English"
            else -> "System"
        }
        val themeLabel = when (theme) {
            LocaleThemeHelper.THEME_LIGHT -> "Light"
            LocaleThemeHelper.THEME_SYSTEM -> "System"
            else -> "Dark"
        }
        binding.tvSummaryAppearance.text = "$langLabel • $themeLabel Theme"

        val termuxState = when {
            TermuxBridge.isTermuxInstalled(this) && TermuxBridge.hasPermission(this) -> "Termux Ready"
            TermuxBridge.isTermuxInstalled(this) -> "Termux Perm Needed"
            else -> "Termux Not Installed"
        }
        val engineUrl = EngineConnectionClient.getSavedUrl(this)
        binding.tvSummaryEngine.text = "$termuxState • $engineUrl"

        val green = ContextCompat.getColor(this, R.color.z_green)
        val muted = ContextCompat.getColor(this, R.color.z_muted)

        fun updateProviderBadge(field: EditText, badge: TextView): Boolean {
            val configured = field.text.toString().isNotBlank()
            if (configured) {
                badge.text = "● Configured (Encrypted)"
                badge.setTextColor(green)
            } else {
                badge.text = "○ Not configured"
                badge.setTextColor(muted)
            }
            return configured
        }

        var count = 0
        if (updateProviderBadge(binding.openrouter, binding.tvStatusOpenRouter)) count++
        if (updateProviderBadge(binding.openai, binding.tvStatusOpenAi)) count++
        if (updateProviderBadge(binding.gemini, binding.tvStatusGemini)) count++
        if (updateProviderBadge(binding.anthropic, binding.tvStatusAnthropic)) count++
        binding.tvSummaryAiProviders.text = "$count of 4 providers configured • Encrypted"
    }

    private fun setupLanguageAndThemeButtons() {
        val currentLang = LocaleThemeHelper.getSavedLanguage(this)
        val currentTheme = LocaleThemeHelper.getSavedTheme(this)

        binding.btnSetLangSystem.alpha = if (currentLang == LocaleThemeHelper.LANG_SYSTEM) 1.0f else 0.65f
        binding.btnSetLangEnglish.alpha = if (currentLang == LocaleThemeHelper.LANG_ENGLISH) 1.0f else 0.65f
        binding.btnSetLangMyanmar.alpha = if (currentLang == LocaleThemeHelper.LANG_MYANMAR) 1.0f else 0.65f

        binding.btnSetThemeDark.alpha = if (currentTheme == LocaleThemeHelper.THEME_DARK) 1.0f else 0.65f
        binding.btnSetThemeLight.alpha = if (currentTheme == LocaleThemeHelper.THEME_LIGHT) 1.0f else 0.65f
        binding.btnSetThemeSystem.alpha = if (currentTheme == LocaleThemeHelper.THEME_SYSTEM) 1.0f else 0.65f

        binding.btnSetLangSystem.setOnClickListener {
            LocaleThemeHelper.setLanguage(this, LocaleThemeHelper.LANG_SYSTEM)
            recreate()
        }
        binding.btnSetLangEnglish.setOnClickListener {
            LocaleThemeHelper.setLanguage(this, LocaleThemeHelper.LANG_ENGLISH)
            recreate()
        }
        binding.btnSetLangMyanmar.setOnClickListener {
            LocaleThemeHelper.setLanguage(this, LocaleThemeHelper.LANG_MYANMAR)
            recreate()
        }

        binding.btnSetThemeDark.setOnClickListener {
            LocaleThemeHelper.setTheme(this, LocaleThemeHelper.THEME_DARK)
            recreate()
        }
        binding.btnSetThemeLight.setOnClickListener {
            LocaleThemeHelper.setTheme(this, LocaleThemeHelper.THEME_LIGHT)
            recreate()
        }
        binding.btnSetThemeSystem.setOnClickListener {
            LocaleThemeHelper.setTheme(this, LocaleThemeHelper.THEME_SYSTEM)
            recreate()
        }
    }

    private fun setupEngineHttpControls() {
        binding.etEngineHttpUrl.setText(EngineConnectionClient.getSavedUrl(this))
        binding.etEngineToken.setText(EngineConnectionClient.getSavedToken(this))

        binding.btnTestEngineConnection.setOnClickListener {
            val url = binding.etEngineHttpUrl.text.toString().trim()
            val token = binding.etEngineToken.text.toString().trim()
            EngineConnectionClient.saveUrl(this, url)
            EngineConnectionClient.saveToken(this, token)
            refreshGroupSummaries()

            binding.tvEngineTestResult.text = "Testing connection to $url/health ..."
            binding.tvEngineTestResult.setTextColor(ContextCompat.getColor(this, R.color.z_muted))

            lifecycleScope.launch {
                val res = EngineConnectionClient.checkHealth(url, token)
                binding.tvEngineTestResult.text = buildString {
                    appendLine("STATUS: ${if (res.connected) "CONNECTED (PASS)" else "UNREACHABLE / NOT RUNNING"}")
                    appendLine("ENDPOINT: ${res.endpointUrl}")
                    if (res.connected) {
                        appendLine("VERSION: ${res.version}")
                        appendLine("CAPABILITIES: ${if (res.capabilities.isEmpty()) "default" else res.capabilities.joinToString(", ")}")
                        appendLine("LATENCY: ${res.latencyMs} ms")
                    }
                    append("DIAGNOSIS: ${res.diagnosticMessage}")
                }
                binding.tvEngineTestResult.setTextColor(
                    ContextCompat.getColor(
                        this@SettingsActivity,
                        if (res.connected) R.color.z_green else R.color.z_orange
                    )
                )
            }
        }

        binding.btnClearEngineToken.setOnClickListener {
            binding.etEngineToken.setText("")
            EngineConnectionClient.clearToken(this)
            val remaining = secrets.contains("engine_token")
            binding.tvEngineTestResult.text =
                "Engine Bearer token cleared from encrypted store (contains=$remaining)."
            binding.tvEngineTestResult.setTextColor(ContextCompat.getColor(this, R.color.z_orange))
            Snackbar.make(binding.root, "Engine token removed", Snackbar.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        pendingBackupPassphrase?.fill('\u0000')
        pendingBackupPassphrase = null
        super.onDestroy()
    }

    private fun loadSecretFields() {
        binding.openrouter.setText(secrets.get("openrouter"))
        binding.openai.setText(secrets.get("openai"))
        binding.gemini.setText(secrets.get("gemini"))
        binding.anthropic.setText(secrets.get("anthropic"))
    }

    private fun saveSecrets() {
        currentKeysIncludingBlank().forEach { (name, value) ->
            secrets.put(name, value)
        }
    }

    private fun currentKeys(): Map<String, String> =
        currentKeysIncludingBlank().filterValues { it.isNotBlank() }

    private fun currentKeysIncludingBlank(): Map<String, String> =
        linkedMapOf(
            "openrouter" to binding.openrouter.text.toString().trim(),
            "openai" to binding.openai.text.toString().trim(),
            "gemini" to binding.gemini.text.toString().trim(),
            "anthropic" to binding.anthropic.text.toString().trim()
        )

    private fun bindSecretControls(
        field: EditText,
        revealButton: Button,
        copyButton: Button,
        provider: String
    ) {
        field.inputType =
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        field.transformationMethod = PasswordTransformationMethod.getInstance()

        revealButton.setOnClickListener {
            val revealing = revealButton.tag as? Boolean ?: false
            val nextRevealing = !revealing

            field.transformationMethod =
                if (nextRevealing) null
                else PasswordTransformationMethod.getInstance()

            revealButton.text =
                if (nextRevealing) "Hide"
                else "Reveal"

            revealButton.tag = nextRevealing
            field.setSelection(field.text.length)
        }

        copyButton.setOnClickListener {
            val value = field.text.toString()
            if (value.isBlank()) {
                Snackbar.make(
                    binding.root,
                    "$provider API key is empty",
                    Snackbar.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            val clipboard =
                getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText(
                "$provider API key",
                value
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                clip.description.extras = PersistableBundle().apply {
                    putBoolean("android.content.extra.IS_SENSITIVE", true)
                }
            }

            clipboard.setPrimaryClip(clip)

            Snackbar.make(
                binding.root,
                "$provider key copied. Clear the clipboard after use.",
                Snackbar.LENGTH_LONG
            ).show()
        }
    }

    private fun promptBackupPassphrase() {
        if (currentKeys().isEmpty()) {
            Snackbar.make(
                binding.root,
                "There are no API keys to back up",
                Snackbar.LENGTH_SHORT
            ).show()
            return
        }

        val passphrase = passwordInput("Backup passphrase")
        val confirm = passwordInput("Confirm passphrase")

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (20 * resources.displayMetrics.density).toInt()
            setPadding(padding, 0, padding, 0)
            addView(passphrase)
            addView(confirm)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Encrypted API key backup")
            .setMessage(
                "Choose a passphrase of at least 8 characters. " +
                    "Zip_Bug does not store it, so keep it somewhere safe."
            )
            .setView(container)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Choose file") { _, _ ->
                val first = passphrase.text.toString().toCharArray()
                val second = confirm.text.toString().toCharArray()

                try {
                    when {
                        first.size < 8 -> {
                            Snackbar.make(
                                binding.root,
                                "Passphrase must be at least 8 characters",
                                Snackbar.LENGTH_LONG
                            ).show()
                        }

                        !first.contentEquals(second) -> {
                            Snackbar.make(
                                binding.root,
                                "Passphrases do not match",
                                Snackbar.LENGTH_LONG
                            ).show()
                        }

                        else -> {
                            pendingBackupPassphrase?.fill('\u0000')
                            pendingBackupPassphrase = first.copyOf()
                            createBackupFile.launch(
                                "Zip_Bug-API-Keys.zipbugkeys"
                            )
                        }
                    }
                } finally {
                    first.fill('\u0000')
                    second.fill('\u0000')
                }
            }
            .show()
    }

    private fun promptRestorePassphrase(uri: Uri) {
        val passphrase = passwordInput("Backup passphrase")

        MaterialAlertDialogBuilder(this)
            .setTitle("Restore API keys")
            .setMessage(
                "Enter the passphrase used when this backup was created. " +
                    "Restored provider keys replace matching saved keys."
            )
            .setView(passphrase)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Restore") { _, _ ->
                val password = passphrase.text.toString().toCharArray()
                try {
                    val encrypted = readLimited(uri, MAX_BACKUP_BYTES)
                    val restored = SecretBackup.decrypt(
                        encrypted,
                        password
                    )

                    if (restored.isEmpty()) {
                        error("The backup contains no supported API keys.")
                    }

                    restored.forEach { (name, value) ->
                        secrets.put(name, value)
                    }
                    loadSecretFields()
                    refreshGroupSummaries()

                    Snackbar.make(
                        binding.root,
                        "API keys restored and encrypted locally",
                        Snackbar.LENGTH_LONG
                    ).show()
                } catch (error: Exception) {
                    Snackbar.make(
                        binding.root,
                        "Restore failed: wrong passphrase or invalid backup",
                        Snackbar.LENGTH_LONG
                    ).show()
                } finally {
                    password.fill('\u0000')
                }
            }
            .show()
    }

    private fun passwordInput(hint: String): EditText =
        EditText(this).apply {
            this.hint = hint
            inputType =
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            transformationMethod = PasswordTransformationMethod.getInstance()
            isSingleLine = true
        }

    private fun readLimited(uri: Uri, maxBytes: Int): ByteArray {
        val input = contentResolver.openInputStream(uri)
            ?: error("Could not open the selected backup.")

        input.use {
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8 * 1024)
            var total = 0

            while (true) {
                val read = it.read(buffer)
                if (read < 0) break

                total += read
                require(total <= maxBytes) { "Backup file is too large." }
                output.write(buffer, 0, read)
            }

            return output.toByteArray()
        }
    }

    private fun safeMessage(error: Exception): String =
        error.message
            ?.take(120)
            ?.replace('\n', ' ')
            ?: "unknown error"

    companion object {
        private const val MAX_BACKUP_BYTES = 1024 * 1024
        private const val KEY_ACTIVE_SECTION = "active_section"
        const val EXTRA_SECTION = "extra_section"
        const val SECTION_HUB = "hub"
        const val SECTION_APPEARANCE = "appearance"
        const val SECTION_ENGINE = "engine"
        const val SECTION_AI = "ai"
        const val SECTION_STORAGE = "storage"
        const val SECTION_PRIVACY = "privacy"
    }
}
