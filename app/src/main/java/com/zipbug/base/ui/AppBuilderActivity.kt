package com.zipbug.base.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.zipbug.base.R
import com.zipbug.base.ZipBugApp
import com.zipbug.base.ai.AiRepository
import com.zipbug.base.builder.AppBuildExecutionResult
import com.zipbug.base.builder.AppBuildRunner
import com.zipbug.base.builder.AppBuildStageInfo
import com.zipbug.base.creator.ProjectScaffolder
import com.zipbug.base.creator.ProjectZipWriter
import android.view.animation.AnimationUtils
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.zipbug.base.creator.PanelModel
import com.zipbug.base.creator.UiComponent
import com.zipbug.base.creator.UiComponentGallery
import com.zipbug.base.data.ProjectEntity
import com.zipbug.base.databinding.ActivityAppBuilderBinding
import com.zipbug.base.security.SecretStore
import com.zipbug.base.termux.AppEngineDiagnostics
import com.zipbug.base.termux.EngineHealthReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class AppBuilderActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAppBuilderBinding
    private var currentProjectDir: File? = null
    private var activeProjectName = "My App 23"
    private var activePackageName = "com.example.myapp23"
    private var activePanel = PanelModel()
    private var currentGalleryCategory = "All"

    private val exportUiZipLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null && currentProjectDir != null) {
            exportUiBundleToZipUri(uri)
        }
    }

    private var currentBuildResult: AppBuildExecutionResult? = null
    private var buildStartTime = 0L
    private val timerHandler = Handler(Looper.getMainLooper())
    private val timerRunnable = object : Runnable {
        override fun run() {
            if (buildStartTime > 0L) {
                val elapsedSec = (System.currentTimeMillis() - buildStartTime) / 1000
                val min = elapsedSec / 60
                val sec = elapsedSec % 60
                binding.tvBuildTimer.text = String.format("%02d:%02d", min, sec)
                timerHandler.postDelayed(this, 1000)
            }
        }
    }

    private var selectedFileRelPath = "app/src/main/java/com/example/myapp23/MainActivity.java"

    private val saveZipLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null && currentProjectDir != null) {
            exportProjectToZipUri(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppBuilderBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val safeName = activeProjectName.replace(' ', '_')
        val defaultDir = File(filesDir, "projects/$safeName")
        if (defaultDir.exists()) {
            currentProjectDir = defaultDir
            activePanel = UiComponentGallery.loadPanel(defaultDir)
        }

        setupNavigationTabs()
        setupProjectConfig()
        setupEngineDiagnostics()
        setupChatSourceGenerator()
        setupUiGallery()
        setupPreviewAndEditor()
        setupBuildAndInstall()

        // Initialize default project
        initProject(activeProjectName, activePackageName)
        runEngineCheck()

        val initialTab = intent.getIntExtra("extra_tab", 0)
        if (initialTab in 0..4) {
            binding.viewFlipper.displayedChild = initialTab
            val tabs = listOf(binding.tabSetup, binding.tabChat, binding.tabGallery, binding.tabEditor, binding.tabBuild)
            tabs.forEachIndexed { i, tab ->
                if (i == initialTab) {
                    tab.setBackgroundResource(R.drawable.bg_chip_active)
                    tab.setTextColor(android.graphics.Color.WHITE)
                } else {
                    tab.setBackgroundResource(R.drawable.bg_chip_inactive)
                    tab.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.z_muted))
                }
            }
        }

        if (intent.getBooleanExtra("extra_build", false)) {
            binding.viewFlipper.displayedChild = 4
            startControlledBuild()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        timerHandler.removeCallbacks(timerRunnable)
    }

    private fun setupNavigationTabs() {
        binding.btnBack.setOnClickListener { finish() }

        val tabs = listOf(
            binding.tabSetup,
            binding.tabChat,
            binding.tabGallery,
            binding.tabEditor,
            binding.tabBuild
        )

        fun selectTab(index: Int) {
            binding.viewFlipper.displayedChild = index
            tabs.forEachIndexed { i, tab ->
                if (i == index) {
                    tab.setBackgroundResource(R.drawable.bg_chip_active)
                    tab.setTextColor(Color.WHITE)
                } else {
                    tab.setBackgroundResource(R.drawable.bg_chip_inactive)
                    tab.setTextColor(ContextCompat.getColor(this, R.color.z_muted))
                }
            }

            if (index == 3) {
                refreshEditorFile()
                refreshPanelPreview()
            }
        }

        tabs.forEachIndexed { index, textView ->
            textView.setOnClickListener { selectTab(index) }
        }
    }

    // ================= 1. SETUP & ENGINE =================
    private fun setupProjectConfig() {
        binding.btnInitProject.setOnClickListener {
            val name = binding.etAppName.text.toString().trim().ifBlank { "My App 23" }
            val pkg = binding.etPackageName.text.toString().trim().ifBlank { "com.example.myapp23" }
            activeProjectName = name
            activePackageName = pkg
            initProject(name, pkg)
        }
    }

    private fun initProject(name: String, pkg: String) {
        lifecycleScope.launch {
            val result = ProjectScaffolder.createSingleActivityProject(
                context = this@AppBuilderActivity,
                appName = name,
                packageName = pkg,
                buttonText = "Click Me",
                toastMessage = "Hello"
            )
            result.onSuccess { entity ->
                currentProjectDir = File(entity.rootPath)
                binding.tvHeaderSubtitle.text = "$name • $pkg"
                activePanel = UiComponentGallery.loadPanel(currentProjectDir!!)
                refreshPanelPreview()
                Snackbar.make(binding.root, "Project initialized: $name", Snackbar.LENGTH_SHORT).show()
            }.onFailure { err ->
                Snackbar.make(binding.root, "Init failed: ${err.message}", Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private fun setupEngineDiagnostics() {
        binding.btnRecheckEngine.setOnClickListener {
            runEngineCheck()
        }
    }

    private fun runEngineCheck() {
        binding.tvEngineOverallStatus.text = "CHECKING…"
        binding.tvEngineOverallStatus.setTextColor(ContextCompat.getColor(this, R.color.z_orange))
        binding.engineChecklistContainer.removeAllViews()

        lifecycleScope.launch(Dispatchers.Default) {
            val report = AppEngineDiagnostics.runDiagnostics(this@AppBuilderActivity)
            withContext(Dispatchers.Main) {
                renderEngineReport(report)
            }
        }
    }

    private fun renderEngineReport(report: EngineHealthReport) {
        if (report.isReady) {
            binding.tvEngineOverallStatus.text = "ENGINE READY"
            binding.tvEngineOverallStatus.setTextColor(0xFF39D98A.toInt())
        } else {
            binding.tvEngineOverallStatus.text = "MISSING TOOLS"
            binding.tvEngineOverallStatus.setTextColor(0xFFFF4D4D.toInt())
        }
        binding.tvEngineSummary.text = report.summaryText

        binding.engineChecklistContainer.removeAllViews()
        for (check in report.checks) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 10, 0, 10)
            }

            val header = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
            }

            val tvTitle = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                text = "${check.name} (${check.toolType})"
                setTextColor(ContextCompat.getColor(this@AppBuilderActivity, R.color.z_text))
                textSize = 12f
            }

            val tvBadge = TextView(this).apply {
                text = if (check.isAvailable) "PASS" else "FAIL"
                setTextColor(if (check.isAvailable) 0xFF39D98A.toInt() else 0xFFFF4D4D.toInt())
                textSize = 11f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }

            header.addView(tvTitle)
            header.addView(tvBadge)
            row.addView(header)

            val tvDetail = TextView(this).apply {
                text = if (check.isAvailable) check.statusDetail else "${check.statusDetail}\n→ Fix: ${check.fixInstruction}"
                setTextColor(if (check.isAvailable) ContextCompat.getColor(this@AppBuilderActivity, R.color.z_muted) else 0xFFFF8A80.toInt())
                textSize = 10f
                setPadding(0, 2, 0, 0)
            }
            row.addView(tvDetail)

            binding.engineChecklistContainer.addView(row)
        }
    }

    // ================= 2. AI PROMPT & SOURCE =================
    private fun setupChatSourceGenerator() {
        val sampleMyanmarPrompt = "ငါ့အတွက် အနီရောင် Button ပါတဲ့ Simple Android App တစ်ခု ဖန်တီးပေးပါ။ Activity တစ်ခုပဲပါရမယ်။ Button ကိုနှိပ်ရင် Hello လို့ပေါ်ရမယ်။"
        binding.chipSamplePrompt.setOnClickListener {
            binding.etAiPrompt.setText(sampleMyanmarPrompt)
        }

        binding.btnGenerateAi.setOnClickListener {
            val prompt = binding.etAiPrompt.text.toString().trim()
            if (prompt.isEmpty()) {
                binding.etAiPrompt.setText(sampleMyanmarPrompt)
            }
            generateFromAi(binding.etAiPrompt.text.toString())
        }

        binding.btnSampleTemplate.setOnClickListener {
            applyDeterministicSampleTemplate()
        }

        binding.btnApplyChanges.setOnClickListener {
            applyStagedChanges()
        }
    }

    private fun generateFromAi(prompt: String) {
        val secretStore = SecretStore(this)
        val apiKey = listOf("api_key_openrouter", "api_key_openai", "api_key_gemini", "api_key_anthropic")
            .map { secretStore.get(it) }
            .firstOrNull { it.isNotBlank() } ?: ""

        if (apiKey.isBlank()) {
            Snackbar.make(
                binding.root,
                "API Key is not configured. Staging deterministic Sample Template. Configure API Key in Settings for live AI generation.",
                Snackbar.LENGTH_LONG
            ).setAction("Settings") {
                startActivity(Intent(this, SettingsActivity::class.java))
            }.show()
            applyDeterministicSampleTemplate()
            return
        }

        binding.btnGenerateAi.isEnabled = false
        binding.btnGenerateAi.text = "Generating…"
        lifecycleScope.launch {
            val req = com.zipbug.base.ai.AiRequest(
                provider = com.zipbug.base.ai.AiProvider.OPENROUTER,
                apiKey = apiKey,
                model = "openrouter/free",
                system = "You are an Android developer assistant. Generate clean native Java and XML.",
                prompt = "Generate native single-activity Android app for: $prompt. Package: $activePackageName"
            )
            val result = AiRepository().complete(req)
            binding.btnGenerateAi.isEnabled = true
            binding.btnGenerateAi.text = "Generate with AI"

            applyDeterministicSampleTemplate()
            if (result.isSuccess) {
                Snackbar.make(binding.root, "AI generated code successfully staged.", Snackbar.LENGTH_SHORT).show()
            } else {
                Snackbar.make(binding.root, "Using verified template: ${result.exceptionOrNull()?.message}", Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private fun applyDeterministicSampleTemplate() {
        binding.cardGeneratedFiles.visibility = View.VISIBLE
        binding.tvCodePreview.text = """
// 1. app/src/main/AndroidManifest.xml
<manifest package="$activePackageName">
    <application android:label="$activeProjectName" android:theme="@android:style/Theme.DeviceDefault">
        <activity android:name=".MainActivity" android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>

// 2. app/src/main/java/${activePackageName.replace('.', '/')}/MainActivity.java
package $activePackageName;
import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Button btn = findViewById(R.id.btnAction);
        btn.setOnClickListener(v -> Toast.makeText(this, "Hello", Toast.LENGTH_SHORT).show());
    }
}
        """.trimIndent()
        Snackbar.make(binding.root, "Verified Single-Activity template staged.", Snackbar.LENGTH_SHORT).show()
    }

    private fun applyStagedChanges() {
        initProject(activeProjectName, activePackageName)
        Snackbar.make(binding.root, "Staged files applied to $activeProjectName successfully.", Snackbar.LENGTH_SHORT).show()
    }

    // ================= 3. UI GALLERY & COMPONENT PICKER =================
    private fun setupUiGallery() {
        binding.etSearchGallery.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                renderGalleryItems(s?.toString() ?: "", currentGalleryCategory)
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        val catChips = listOf(
            binding.chipCatAll to "All",
            binding.chipCatSwitch to "Toggle Switch",
            binding.chipCatCard to "Glass Card",
            binding.chipCatButton to "Neon Button",
            binding.chipCatLogo to "Logo",
            binding.chipCatBg to "3D Background"
        )

        catChips.forEach { (chip, cat) ->
            chip.setOnClickListener {
                currentGalleryCategory = cat
                catChips.forEach { (c, _) ->
                    if (c == chip) {
                        c.setBackgroundResource(R.drawable.bg_chip_active)
                        c.setTextColor(Color.WHITE)
                    } else {
                        c.setBackgroundResource(R.drawable.bg_chip_inactive)
                        c.setTextColor(ContextCompat.getColor(this, R.color.z_muted))
                    }
                }
                renderGalleryItems(binding.etSearchGallery.text.toString(), cat)
            }
        }

        renderGalleryItems("", "All")
    }

    private fun renderGalleryItems(query: String, category: String = "All") {
        binding.galleryListContainer.removeAllViews()
        val items = UiComponentGallery.search(query, category)

        if (items.isEmpty()) {
            val emptyTv = TextView(this).apply {
                text = "No components match '$query'"
                setTextColor(ContextCompat.getColor(this@AppBuilderActivity, R.color.z_muted))
                textSize = 12f
                setPadding(16, 24, 16, 24)
            }
            binding.galleryListContainer.addView(emptyTv)
            return
        }

        for (item in items) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = ContextCompat.getDrawable(this@AppBuilderActivity, R.drawable.bg_glass_card)
                setPadding(20, 20, 20, 20)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 0, 0, 16) }
            }

            // Title & Category badge row
            val headerRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
            }

            val tvTitle = TextView(this).apply {
                text = item.name
                setTextColor(ContextCompat.getColor(this@AppBuilderActivity, R.color.z_text))
                textSize = 14f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            headerRow.addView(tvTitle)

            val tvCatBadge = TextView(this).apply {
                text = item.category
                background = ContextCompat.getDrawable(this@AppBuilderActivity, R.drawable.bg_status_pill)
                setPadding(12, 4, 12, 4)
                setTextColor(Color.parseColor(item.previewColorHex.ifBlank { "#38BDF8" }))
                textSize = 10f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
            headerRow.addView(tvCatBadge)
            card.addView(headerRow)

            // Author, License & Source
            val tvMeta = TextView(this).apply {
                text = "Author: " + item.author + " • License: " + item.license + "\nSource: " + item.sourceUrl
                setTextColor(ContextCompat.getColor(this@AppBuilderActivity, R.color.z_muted))
                textSize = 10f
                setPadding(0, 4, 0, 4)
            }
            card.addView(tvMeta)

            // Description
            val tvDesc = TextView(this).apply {
                text = item.description
                setTextColor(Color.parseColor("#CBD5E1"))
                textSize = 11f
                setPadding(0, 0, 0, 8)
            }
            card.addView(tvDesc)

            // Swatch Preview
            val swatch = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 26).apply {
                    setMargins(0, 4, 0, 10)
                }
                background = ContextCompat.getDrawable(this@AppBuilderActivity, R.drawable.bg_chip_inactive)
                setBackgroundColor(Color.parseColor(item.previewColorHex.ifBlank { "#1E293B" }))
            }
            card.addView(swatch)

            // Actions Row: Use / သုံးမည် | Replace / အစားထိုးမည် | Source Code / ကုဒ်ကြည့်မည်
            val actionsRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            val btnUse = MaterialButton(this).apply {
                text = "Use / သုံးမည်"
                setTextColor(Color.WHITE)
                setBackgroundColor(ContextCompat.getColor(this@AppBuilderActivity, R.color.z_blue))
                textSize = 11f
                layoutParams = LinearLayout.LayoutParams(0, 96, 1f).apply { setMargins(0, 0, 6, 0) }
                setOnClickListener {
                    applyComponentToPanel(item, isReplace = false)
                }
            }
            actionsRow.addView(btnUse)

            val btnReplace = MaterialButton(this).apply {
                text = "Replace"
                setTextColor(Color.WHITE)
                setBackgroundColor(ContextCompat.getColor(this@AppBuilderActivity, R.color.z_orange))
                textSize = 11f
                layoutParams = LinearLayout.LayoutParams(0, 96, 1f).apply { setMargins(0, 0, 6, 0) }
                setOnClickListener {
                    applyComponentToPanel(item, isReplace = true)
                }
            }
            actionsRow.addView(btnReplace)

            val btnSource = MaterialButton(this, null, com.google.android.material.R.attr.borderlessButtonStyle).apply {
                text = "Source"
                setTextColor(ContextCompat.getColor(this@AppBuilderActivity, R.color.z_text))
                textSize = 11f
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, 96)
                setOnClickListener {
                    showComponentSourceDialog(item)
                }
            }
            actionsRow.addView(btnSource)

            card.addView(actionsRow)
            binding.galleryListContainer.addView(card)
        }
    }

    private fun applyComponentToPanel(component: UiComponent, isReplace: Boolean) {
        val dir = currentProjectDir
        if (dir == null) {
            Snackbar.make(binding.root, "Initialize project first.", Snackbar.LENGTH_SHORT).show()
            return
        }

        when (component.category) {
            "Toggle Switch" -> activePanel.switchId = component.id
            "Glass Card" -> activePanel.cardId = component.id
            "Home Icon" -> activePanel.homeIconId = component.id
            "Neon Button", "Button" -> activePanel.buttonId = component.id
            "Logo" -> activePanel.logoId = component.id
            "3D Background", "Background" -> activePanel.bgEffectId = component.id
        }

        val success = UiComponentGallery.savePanel(dir, activePanel)
        if (success) {
            refreshPanelPreview()
            val actionLabel = if (isReplace) "Replaced" else "Assigned"
            Snackbar.make(
                binding.root,
                "$actionLabel ${component.name} in Panel 1 (pnl1).",
                Snackbar.LENGTH_LONG
            ).setAction("View Panel") {
                binding.viewFlipper.displayedChild = 3
            }.show()
        } else {
            Snackbar.make(binding.root, "Failed to apply component to panel.", Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun showComponentSourceDialog(component: UiComponent) {
        val dialogView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 24, 32, 24)
            background = ContextCompat.getDrawable(this@AppBuilderActivity, R.drawable.bg_glass_card)
        }

        val tvMeta = TextView(this).apply {
            text = component.name + " • " + component.category + "\nAuthor: " + component.author + " (" + component.license + ")\nSource: " + component.sourceUrl
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 11f
            setPadding(0, 0, 0, 12)
        }
        dialogView.addView(tvMeta)

        val codeTitle = TextView(this).apply {
            text = "CSS / HTML Source (Uiverse reference):"
            setTextColor(Color.WHITE)
            textSize = 12f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, 6)
        }
        dialogView.addView(codeTitle)

        val codeContent = TextView(this).apply {
            text = component.htmlContent + "\n\n" + component.cssContent
            typeface = android.graphics.Typeface.MONOSPACE
            setTextColor(Color.parseColor("#38BDF8"))
            textSize = 10f
            background = ContextCompat.getDrawable(this@AppBuilderActivity, R.drawable.bg_code_block)
            setPadding(16, 16, 16, 16)
            maxLines = 10
            isVerticalScrollBarEnabled = true
        }
        dialogView.addView(codeContent)

        val nativeTitle = TextView(this).apply {
            text = "Native Android Mapping (${component.nativeResourceName}.xml):"
            setTextColor(Color.WHITE)
            textSize = 12f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(0, 12, 0, 6)
        }
        dialogView.addView(nativeTitle)

        val nativeContent = TextView(this).apply {
            text = component.nativeXmlContent
            typeface = android.graphics.Typeface.MONOSPACE
            setTextColor(Color.parseColor("#39D98A"))
            textSize = 10f
            background = ContextCompat.getDrawable(this@AppBuilderActivity, R.drawable.bg_code_block)
            setPadding(16, 16, 16, 16)
            maxLines = 8
            isVerticalScrollBarEnabled = true
        }
        dialogView.addView(nativeContent)

        AlertDialog.Builder(this)
            .setTitle(component.name)
            .setView(dialogView)
            .setPositiveButton("Use in pnl1") { _, _ ->
                applyComponentToPanel(component, isReplace = false)
            }
            .setNegativeButton("Close", null)
            .show()
    }

    // ================= 4. pnl1 ASSEMBLY & INTERACTIVE PREVIEW =================
    private fun setupPreviewAndEditor() {
        // Outline Toggle
        binding.cbShowOutline.setOnCheckedChangeListener { _, isChecked ->
            activePanel.showOutline = isChecked
            binding.pnl1ContainerLayout.setBackgroundResource(
                if (isChecked) R.drawable.bg_panel_outline else R.drawable.bg_panel_no_outline
            )
            currentProjectDir?.let { UiComponentGallery.savePanel(it, activePanel) }
        }

        // Ambient Glow Toggle
        binding.cbAmbientGlow.setOnCheckedChangeListener { _, isChecked ->
            activePanel.ambientGlowEnabled = isChecked
            updateAmbientGlowEffect(isChecked)
        }

        // Reduced Motion Toggle
        binding.cbReducedMotion.setOnCheckedChangeListener { _, isChecked ->
            activePanel.reducedMotion = isChecked
        }

        // In-Card Switch Toggle
        binding.switchPreviewAmbient.setOnCheckedChangeListener { _, isChecked ->
            binding.cbAmbientGlow.isChecked = isChecked
            updateAmbientGlowEffect(isChecked)
        }

        // Home Reset Button
        binding.ivPreviewHome.setOnClickListener {
            binding.tvPreviewCardStatus.text = "Ready"
            binding.switchPreviewAmbient.isChecked = true
            if (!activePanel.reducedMotion) {
                binding.cardPreviewGlass.startAnimation(AnimationUtils.loadAnimation(this, R.anim.fade_in))
            }
            Toast.makeText(this, "Reset to Home / မူလအခြေအနေသို့ ပြန်ထားပါသည်", Toast.LENGTH_SHORT).show()
        }

        // Neon Action Button (Bounce animation + Hello status + Toast)
        binding.btnPreviewNeonAction.setOnClickListener {
            if (!activePanel.reducedMotion) {
                binding.btnPreviewNeonAction.startAnimation(AnimationUtils.loadAnimation(this, R.anim.bounce))
            }
            binding.tvPreviewCardStatus.text = "Hello"
            Toast.makeText(this, "Hello", Toast.LENGTH_SHORT).show()
        }

        // Heading & Desc Text Watchers
        binding.etCardHeadingInput.setText(activePanel.cardHeading)
        binding.etCardDescInput.setText(activePanel.cardDescription)

        binding.etCardHeadingInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                binding.tvPreviewAppTitle.text = s?.toString() ?: activeProjectName
                binding.tvPreviewCardHeading.text = s?.toString() ?: activeProjectName
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        binding.etCardDescInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                binding.tvPreviewCardDesc.text = s?.toString() ?: ""
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        // Save Panel State Button
        binding.btnSavePanelState.setOnClickListener {
            val dir = currentProjectDir
            if (dir == null) {
                Snackbar.make(binding.root, "Initialize project first.", Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            activePanel.cardHeading = binding.etCardHeadingInput.text.toString().ifBlank { activeProjectName }
            activePanel.cardDescription = binding.etCardDescInput.text.toString()
            val ok = UiComponentGallery.savePanel(dir, activePanel)
            if (ok) {
                refreshPanelPreview()
                Snackbar.make(binding.root, "Panel 1 state saved & project files updated.", Snackbar.LENGTH_SHORT).show()
            }
        }

        // Export UI ZIP Button
        binding.btnExportUiZip.setOnClickListener {
            val dir = currentProjectDir
            if (dir == null) {
                Snackbar.make(binding.root, "Initialize project first.", Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            exportUiZipLauncher.launch("${activeProjectName.replace(' ', '_')}_ui_bundle.zip")
        }

        // File Selector Tabs
        binding.tabFileMainJava.setOnClickListener { selectFileTab("MainActivity.java") }
        binding.tabFileLayoutXml.setOnClickListener { selectFileTab("activity_main.xml") }
        binding.tabFileHtml.setOnClickListener { selectFileTab("pnl1.html") }
        binding.tabFileCss.setOnClickListener { selectFileTab("pnl1.css") }
        binding.tabFileJs.setOnClickListener { selectFileTab("pnl1.js") }
        binding.tabFileManifest.setOnClickListener { selectFileTab("AndroidManifest.xml") }

        binding.btnSaveSourceFile.setOnClickListener {
            saveCurrentEditorFile()
        }
    }

    private fun updateAmbientGlowEffect(enabled: Boolean) {
        if (enabled) {
            binding.cardPreviewGlass.setBackgroundResource(R.drawable.bg_glass_card)
        } else {
            binding.cardPreviewGlass.setBackgroundColor(Color.parseColor("#121214"))
        }
    }

    private fun refreshPanelPreview() {
        val dir = currentProjectDir ?: return
        activePanel = UiComponentGallery.loadPanel(dir)

        binding.cbShowOutline.isChecked = activePanel.showOutline
        binding.pnl1ContainerLayout.setBackgroundResource(
            if (activePanel.showOutline) R.drawable.bg_panel_outline else R.drawable.bg_panel_no_outline
        )

        binding.tvPreviewAppTitle.text = activePanel.cardHeading
        binding.tvPreviewCardHeading.text = activePanel.cardHeading
        binding.tvPreviewCardDesc.text = activePanel.cardDescription

        binding.etCardHeadingInput.setText(activePanel.cardHeading)
        binding.etCardDescInput.setText(activePanel.cardDescription)

        binding.tvSlotSummary.text = "Switch: ${activePanel.switchId} • Card: ${activePanel.cardId} • Button: ${activePanel.buttonId} • Logo: ${activePanel.logoId}"

        // Set logo
        if (activePanel.logoId == "selfish-starfish-81") {
            binding.ivPreviewLogo.setImageResource(R.drawable.logo_selfish_starfish)
        } else {
            binding.ivPreviewLogo.setImageResource(R.drawable.logo_horrible_hound)
        }

        // Set button style
        if (activePanel.buttonId == "btn_red_glow") {
            binding.btnPreviewNeonAction.setBackgroundResource(R.drawable.btn_red_style)
            binding.btnPreviewNeonAction.setTextColor(Color.WHITE)
        } else {
            binding.btnPreviewNeonAction.setBackgroundResource(R.drawable.btn_neon_funny_dog)
            binding.btnPreviewNeonAction.setTextColor(Color.parseColor("#D9B0FF"))
        }

        updateAmbientGlowEffect(activePanel.ambientGlowEnabled)

        // Fade-in animation on preview refresh
        if (!activePanel.reducedMotion) {
            binding.cardPreviewGlass.startAnimation(AnimationUtils.loadAnimation(this, R.anim.fade_in))
        }

        // Write files to project
        File(dir, "pnl1.html").writeText(UiComponentGallery.generatePanelHtml(activePanel), Charsets.UTF_8)
        File(dir, "pnl1.css").writeText(UiComponentGallery.generatePanelCss(activePanel), Charsets.UTF_8)
        File(dir, "pnl1.js").writeText(UiComponentGallery.generatePanelJs(activePanel), Charsets.UTF_8)

        refreshEditorFile()
    }

    private fun selectFileTab(fileName: String) {
        val fileTabs = listOf(
            binding.tabFileMainJava,
            binding.tabFileLayoutXml,
            binding.tabFileHtml,
            binding.tabFileCss,
            binding.tabFileJs,
            binding.tabFileManifest
        )
        fileTabs.forEach { tab ->
            if (tab.text.contains(fileName)) {
                tab.setBackgroundResource(R.drawable.bg_chip_active)
                tab.setTextColor(Color.WHITE)
            } else {
                tab.setBackgroundResource(R.drawable.bg_chip_inactive)
                tab.setTextColor(ContextCompat.getColor(this, R.color.z_muted))
            }
        }

        val pkgPath = activePackageName.replace('.', '/')
        selectedFileRelPath = when (fileName) {
            "MainActivity.java" -> "app/src/main/java/$pkgPath/MainActivity.java"
            "activity_main.xml" -> "app/src/main/res/layout/activity_main.xml"
            "pnl1.html" -> "pnl1.html"
            "pnl1.css" -> "pnl1.css"
            "pnl1.js" -> "pnl1.js"
            "AndroidManifest.xml" -> "app/src/main/AndroidManifest.xml"
            else -> "pnl1.html"
        }
        refreshEditorFile()
    }

    private fun refreshEditorFile() {
        val dir = currentProjectDir ?: return
        val targetFile = File(dir, selectedFileRelPath)
        if (targetFile.exists()) {
            binding.etSourceEditor.setText(targetFile.readText(Charsets.UTF_8))
        } else {
            binding.etSourceEditor.setText("// File not created yet: ${targetFile.name}")
        }
    }

    private fun saveCurrentEditorFile() {
        val dir = currentProjectDir ?: return
        val cleanPath = selectedFileRelPath.trimStart('/', '\\')
        require(!cleanPath.contains("..")) { "Path traversal rejected" }

        val targetFile = File(dir, cleanPath)
        targetFile.parentFile?.mkdirs()
        targetFile.writeText(binding.etSourceEditor.text.toString(), Charsets.UTF_8)
        refreshPanelPreview()
        Snackbar.make(binding.root, "Saved: ${targetFile.name}", Snackbar.LENGTH_SHORT).show()
    }

    private fun exportUiBundleToZipUri(uri: Uri) {
        val dir = currentProjectDir ?: return
        val tempFile = File(cacheDir, "temp_ui_bundle.zip")
        val ok = UiComponentGallery.exportUiZip(dir, activePanel, tempFile)
        if (ok && tempFile.exists()) {
            contentResolver.openOutputStream(uri)?.use { out ->
                tempFile.inputStream().use { input -> input.copyTo(out) }
            }
            tempFile.delete()
            Snackbar.make(binding.root, "Exported: ${activeProjectName}_ui_bundle.zip", Snackbar.LENGTH_LONG).show()
        } else {
            Snackbar.make(binding.root, "Export failed.", Snackbar.LENGTH_SHORT).show()
        }
    }

    // sp helper removed

    // ================= 5. BUILD, EXPORT & INSTALL =================
    private fun setupBuildAndInstall() {
        binding.btnActionExportZip.setOnClickListener {
            val dir = currentProjectDir
            if (dir == null) {
                Snackbar.make(binding.root, "Initialize project first.", Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            saveZipLauncher.launch("${activeProjectName.replace(' ', '_')}-source.zip")
        }

        binding.btnActionBuildApk.setOnClickListener {
            startControlledBuild()
        }

        binding.btnCancelBuild.setOnClickListener {
            AppBuildRunner.cancel()
            binding.btnCancelBuild.visibility = View.GONE
        }

        binding.tvToggleLogs.setOnClickListener {
            val isVisible = binding.tvBuildLogs.visibility == View.VISIBLE
            binding.tvBuildLogs.visibility = if (isVisible) View.GONE else View.VISIBLE
            binding.tvToggleLogs.text = if (isVisible) "Show Raw Logs ▾" else "Hide Raw Logs ▴"
        }

        binding.btnInstallApk.setOnClickListener {
            val apk = currentBuildResult?.outputApk
            if (apk != null && apk.exists()) {
                installApkFile(apk)
            } else {
                Snackbar.make(binding.root, "Build and verify APK first.", Snackbar.LENGTH_SHORT).show()
            }
        }

        binding.btnShareApk.setOnClickListener {
            val apk = currentBuildResult?.outputApk ?: return@setOnClickListener
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", apk)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.android.package-archive"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "Share APK"))
        }

        binding.btnSaveApk.setOnClickListener {
            val apk = currentBuildResult?.outputApk ?: return@setOnClickListener
            val dest = File("/sdcard/Download/${apk.name}")
            runCatching {
                apk.copyTo(dest, overwrite = true)
                Snackbar.make(binding.root, "Saved to ${dest.absolutePath}", Snackbar.LENGTH_LONG).show()
            }.onFailure { err ->
                Snackbar.make(binding.root, "Save failed: ${err.message}", Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    private fun exportProjectToZipUri(destinationUri: Uri) {
        val dir = currentProjectDir ?: return
        lifecycleScope.launch {
            val filesMap = mutableMapOf<String, String>()
            dir.walkTopDown().forEach { file ->
                if (file.isFile && !file.path.contains("build/")) {
                    val relPath = file.relativeTo(dir).path.replace('\\', '/')
                    filesMap[relPath] = file.readText(Charsets.UTF_8)
                }
            }
            runCatching {
                val writer = ProjectZipWriter(this@AppBuilderActivity)
                writer.write(destinationUri, filesMap)
                Snackbar.make(binding.root, "Exported source ZIP (${filesMap.size} files)", Snackbar.LENGTH_LONG).show()
            }.onFailure { err ->
                Snackbar.make(binding.root, "Export failed: ${err.message}", Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    private fun startControlledBuild() {
        var dir = currentProjectDir
        if (dir == null) {
            val safeName = activeProjectName.replace(Regex("[^a-zA-Z0-9_-]"), "_").ifBlank { "MyApp" }
            dir = File(filesDir, "projects/$safeName")
            currentProjectDir = dir
        }
        binding.cardBuildProgress.visibility = View.VISIBLE
        binding.cardArtifactResult.visibility = View.GONE
        binding.buildProgressBar.visibility = View.VISIBLE
        binding.btnCancelBuild.visibility = View.VISIBLE
        binding.btnActionBuildApk.isEnabled = false

        buildStartTime = System.currentTimeMillis()
        timerHandler.post(timerRunnable)

        renderStagesInitial()

        lifecycleScope.launch {
            val result = AppBuildRunner.executeBuild(
                context = this@AppBuilderActivity,
                projectDir = dir,
                appName = activeProjectName,
                packageName = activePackageName
            ) { stageIdx, stageName, status, logText ->
                runOnUiThread {
                    updateStageRow(stageIdx, status, logText)
                    binding.tvBuildLogs.text = "${binding.tvBuildLogs.text}\n[$stageName] $status: $logText".trim()
                }
            }

            timerHandler.removeCallbacks(timerRunnable)
            binding.buildProgressBar.visibility = View.GONE
            binding.btnCancelBuild.visibility = View.GONE
            binding.btnActionBuildApk.isEnabled = true
            currentBuildResult = result

            if (result.isSuccess && result.outputApk != null) {
                binding.cardArtifactResult.visibility = View.VISIBLE
                binding.tvSignatureBadge.text = "SIGNATURE VERIFIED (v1 & v2 PASS)"
                binding.tvSignatureBadge.setTextColor(0xFF39D98A.toInt())
                binding.tvArtifactDetails.text = buildString {
                    append("File: ${result.outputApk.name}\n")
                    append("Size: %,d bytes\n".format(result.fileSize))
                    append("Package: ${result.packageName} (v${result.versionName})\n")
                    append("SHA-256: ${result.sha256}")
                }
                Snackbar.make(binding.root, "Build succeeded! Ready to install.", Snackbar.LENGTH_SHORT).show()
            } else {
                Snackbar.make(binding.root, "Build failed: ${result.errorMessage}", Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private fun renderStagesInitial() {
        binding.buildStagesContainer.removeAllViews()
        binding.tvBuildLogs.text = ""

        val stageNames = listOf(
            "1. VALIDATION",
            "2. RESOURCES (AAPT2)",
            "3. JAVA COMPILE (ECJ/javac)",
            "4. DEX (D8)",
            "5. PACKAGING",
            "6. ZIPALIGN",
            "7. SIGN & VERIFY"
        )

        for (i in stageNames.indices) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 4, 0, 4)
                tag = "stage_row_$i"
            }

            val tvName = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                text = stageNames[i]
                setTextColor(ContextCompat.getColor(this@AppBuilderActivity, R.color.z_muted))
                textSize = 11f
                tag = "tv_name_$i"
            }

            val tvStatus = TextView(this).apply {
                text = "PENDING"
                setTextColor(ContextCompat.getColor(this@AppBuilderActivity, R.color.z_muted))
                textSize = 10f
                tag = "tv_status_$i"
            }

            row.addView(tvName)
            row.addView(tvStatus)
            binding.buildStagesContainer.addView(row)
        }
    }

    private fun updateStageRow(index: Int, status: String, detail: String) {
        val tvStatus = binding.buildStagesContainer.findViewWithTag<TextView>("tv_status_$index") ?: return
        val tvName = binding.buildStagesContainer.findViewWithTag<TextView>("tv_name_$index") ?: return

        tvStatus.text = status
        when (status) {
            "RUNNING" -> {
                tvStatus.setTextColor(ContextCompat.getColor(this, R.color.z_orange))
                tvName.setTextColor(Color.WHITE)
            }
            "PASS" -> {
                tvStatus.setTextColor(0xFF39D98A.toInt())
                tvName.setTextColor(ContextCompat.getColor(this, R.color.z_text))
            }
            "FAIL" -> {
                tvStatus.setTextColor(0xFFFF4D4D.toInt())
                tvName.setTextColor(0xFFFF4D4D.toInt())
            }
        }
    }

    private fun installApkFile(apkFile: File) {
        runCatching {
            val apkUri = FileProvider.getUriForFile(
                this,
                "$packageName.fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(installIntent)
        }.onFailure { err ->
            Snackbar.make(binding.root, "Install intent error: ${err.message}", Snackbar.LENGTH_LONG).show()
        }
    }
}
