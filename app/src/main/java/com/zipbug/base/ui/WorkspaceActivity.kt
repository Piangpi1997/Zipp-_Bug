package com.zipbug.base.ui

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.zipbug.base.R
import com.zipbug.base.ZipBugApp
import com.zipbug.base.creator.AppScaffolder
import com.zipbug.base.creator.AppSpec
import com.zipbug.base.creator.ProjectZipWriter
import com.zipbug.base.creator.ReactTemplateScaffolder
import com.zipbug.base.creator.WebMiniAppScaffolder
import com.zipbug.base.data.BuildJobEntity
import com.zipbug.base.data.ProjectEntity
import com.zipbug.base.databinding.ActivityWorkspaceBinding
import com.zipbug.base.engine.EngineConnectionClient
import com.zipbug.base.engine.EngineManager
import com.zipbug.base.project.ZipImporter
import com.zipbug.base.termux.TermuxBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import android.util.Base64
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale
import java.util.UUID
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

class WorkspaceActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWorkspaceBinding
    private val app by lazy { application as ZipBugApp }
    private val db by lazy { app.database }

    private var activeProject: ProjectEntity? = null
    private var projectFiles: List<File> = emptyList()
    private var activeFile: File? = null
    private var lastSavedContent: String = ""
    private var hasUnsavedChanges: Boolean = false
    private var suppressEditorWatcher: Boolean = false

    private var engineJobObserver: Job? = null
    private var activeEngineJobId: String? = null
    private var activeEngineTermuxSlug: String? = null

    private val importZipPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) importProjectFromZip(uri)
    }

    private val exportZipPicker = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) exportActiveProjectToZipUri(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWorkspaceBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupEditorWatcher()
        setupListeners()
        setupBackNavigationGuard()
        loadInitialProject()
    }

    private fun setupBackNavigationGuard() {
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    confirmDiscardUnsavedChanges {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        )
    }

    private fun setupEditorWatcher() {
        binding.etCodeEditor.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (suppressEditorWatcher || activeFile == null) return
                val current = s?.toString().orEmpty()
                hasUnsavedChanges = current != lastSavedContent
                binding.tvUnsavedIndicator.visibility =
                    if (hasUnsavedChanges) View.VISIBLE else View.GONE
            }
        })

        binding.etFileFilter.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                renderFileChips(s?.toString().orEmpty())
            }
        })
    }

    private fun setupListeners() {
        binding.btnWsSwitchProject.setOnClickListener {
            confirmDiscardUnsavedChanges { showSwitchProjectDialog() }
        }
        binding.btnWsNewTemplate.setOnClickListener {
            confirmDiscardUnsavedChanges { showCreateFromTemplateDialog() }
        }
        binding.btnWsImportZip.setOnClickListener {
            confirmDiscardUnsavedChanges { promptImportZipSource() }
        }
        binding.btnWsRename.setOnClickListener { promptRenameActiveProject() }
        binding.btnWsDuplicate.setOnClickListener { duplicateActiveProject() }
        binding.btnWsExportZip.setOnClickListener { exportActiveProjectImmediately() }
        binding.btnWsPreviewHtml.setOnClickListener { previewActiveWebProject() }
        binding.btnWsStageTermux.setOnClickListener { stageProjectToTermux() }
        binding.btnWsUiPicker.setOnClickListener {
            val intent = Intent(this, AppBuilderActivity::class.java).apply {
                putExtra("extra_tab", 2)
            }
            startActivity(intent)
        }
        binding.btnWsNewFile.setOnClickListener { promptCreateNewFile() }
        binding.btnWsSaveFile.setOnClickListener { saveCurrentFile() }

        // Explicit React / Node Engine Actions
        binding.btnNpmInstall.setOnClickListener { runReactEngineCommand(listOf("install"), "npm install") }
        binding.btnNpmBuild.setOnClickListener { runReactEngineCommand(listOf("run", "build"), "npm run build") }
        binding.btnNpmDev.setOnClickListener { runReactEngineCommand(listOf("run", "dev"), "npm run dev") }
        binding.btnCancelEngineJob.setOnClickListener { cancelActiveEngineJob() }
    }

    private fun loadInitialProject() {
        val requestedId = intent.getStringExtra(EXTRA_PROJECT_ID)
        lifecycleScope.launch {
            val existingList = db.projectDao().listAll()
            val target = if (!requestedId.isNullOrBlank()) {
                existingList.firstOrNull { it.id == requestedId } ?: existingList.firstOrNull()
            } else {
                existingList.firstOrNull()
            }

            if (target != null) {
                bindProject(target)
            } else {
                val created = createProjectOnDisk(
                    name = "Zip_Bug Starter Web",
                    kind = "web-mini-app",
                    entryFile = "www/index.html",
                    files = WebMiniAppScaffolder.files("Zip_Bug Starter Web", "Interactive HTML/CSS/JS Workspace Starter")
                )
                db.projectDao().upsert(created)
                bindProject(created)
            }
        }
    }

    private fun bindProject(project: ProjectEntity) {
        activeProject = project
        activeFile = null
        hasUnsavedChanges = false
        binding.tvUnsavedIndicator.visibility = View.GONE

        lifecycleScope.launch {
            db.projectDao().upsert(project.copy(lastOpenedAt = System.currentTimeMillis()))
        }

        binding.tvActiveProjectTitle.text = "Project: ${project.name}"
        binding.tvActiveProjectMeta.text = "Kind: ${project.kind.uppercase()} • Entry: ${project.entryFile}"

        val rootDir = File(project.rootPath)
        val isReactOrNode = project.kind.equals("react-web", ignoreCase = true) ||
            File(rootDir, "package.json").isFile
        binding.layoutReactEngineBar.visibility = if (isReactOrNode) View.VISIBLE else View.GONE

        refreshFileList()
    }

    private fun refreshFileList() {
        val proj = activeProject ?: return
        val rootDir = File(proj.rootPath)
        if (!rootDir.exists()) rootDir.mkdirs()

        projectFiles = rootDir.walkTopDown()
            .onEnter { d -> d.name != "node_modules" && d.name != ".git" && d.name != "build" && d.name != ".gradle" }
            .filter { it.isFile }
            .sortedBy { it.relativeTo(rootDir).path }
            .toList()

        binding.tvFilesHeader.text = "PROJECT FILES (${projectFiles.size})"
        renderFileChips(binding.etFileFilter.text.toString())

        if (activeFile == null && projectFiles.isNotEmpty()) {
            val preferred = projectFiles.firstOrNull {
                it.relativeTo(rootDir).path.replace('\\', '/') == proj.entryFile
            } ?: projectFiles.first()
            openFileInEditor(preferred)
        }
    }

    private fun renderFileChips(filterQuery: String) {
        val proj = activeProject ?: return
        val rootDir = File(proj.rootPath)
        binding.fileChipsContainer.removeAllViews()

        val filtered = if (filterQuery.isBlank()) {
            projectFiles
        } else {
            projectFiles.filter {
                it.relativeTo(rootDir).path.contains(filterQuery.trim(), ignoreCase = true)
            }
        }

        for (file in filtered) {
            val relPath = file.relativeTo(rootDir).path.replace('\\', '/')
            val isSelected = activeFile?.absolutePath == file.absolutePath
            val btn = Button(this).apply {
                text = relPath
                textSize = 11f
                isAllCaps = false
                backgroundTintList = null
                setBackgroundResource(if (isSelected) R.drawable.bg_chip_active else R.drawable.bg_chip_inactive)
                setTextColor(ContextCompat.getColor(this@WorkspaceActivity, if (isSelected) R.color.z_orange else R.color.z_text))
                setPadding(
                    (12 * resources.displayMetrics.density).toInt(),
                    0,
                    (12 * resources.displayMetrics.density).toInt(),
                    0
                )
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    (36 * resources.displayMetrics.density).toInt()
                ).apply { marginEnd = (8 * resources.displayMetrics.density).toInt() }
                layoutParams = lp
                setOnClickListener {
                    confirmDiscardUnsavedChanges {
                        openFileInEditor(file)
                        renderFileChips(binding.etFileFilter.text.toString())
                    }
                }
            }
            binding.fileChipsContainer.addView(btn)
        }
    }

    private fun openFileInEditor(file: File) {
        val proj = activeProject ?: return
        val canonRoot = File(proj.rootPath).canonicalFile
        val canonFile = file.canonicalFile
        if (!canonFile.path.startsWith(canonRoot.path + File.separator)) {
            Snackbar.make(binding.root, "Blocked file outside project root", Snackbar.LENGTH_SHORT).show()
            return
        }

        runCatching {
            if (canonFile.length() > 1024 * 1024L) {
                error("File exceeds 1 MB text editor limit (${canonFile.length()} bytes)")
            }
            canonFile.readText(Charsets.UTF_8)
        }.fold(
            onSuccess = { content ->
                activeFile = canonFile
                lastSavedContent = content
                hasUnsavedChanges = false
                suppressEditorWatcher = true
                binding.etCodeEditor.setText(content)
                suppressEditorWatcher = false
                binding.tvUnsavedIndicator.visibility = View.GONE
                val rel = canonFile.relativeTo(canonRoot).path.replace('\\', '/')
                binding.tvCurrentFileName.text = rel
                binding.tvWorkspaceStatus.text = "Loaded $rel (${canonFile.length()} bytes)"
            },
            onFailure = { err ->
                binding.tvWorkspaceStatus.text = "Cannot open file: ${err.message}"
            }
        )
    }

    private fun saveCurrentFile() {
        val proj = activeProject ?: return
        val file = activeFile ?: run {
            Snackbar.make(binding.root, "No file selected", Snackbar.LENGTH_SHORT).show()
            return
        }
        val canonRoot = File(proj.rootPath).canonicalFile
        val canonFile = file.canonicalFile
        if (!canonFile.path.startsWith(canonRoot.path + File.separator)) {
            Snackbar.make(binding.root, "Safety check failed: path outside project root", Snackbar.LENGTH_SHORT).show()
            return
        }

        val newContent = binding.etCodeEditor.text.toString()
        runCatching {
            canonFile.parentFile?.mkdirs()
            canonFile.writeText(newContent, Charsets.UTF_8)
        }.fold(
            onSuccess = {
                lastSavedContent = newContent
                hasUnsavedChanges = false
                binding.tvUnsavedIndicator.visibility = View.GONE
                val rel = canonFile.relativeTo(canonRoot).path.replace('\\', '/')
                binding.tvWorkspaceStatus.text = "Saved $rel (${canonFile.length()} bytes)"
                Snackbar.make(binding.root, "Saved $rel", Snackbar.LENGTH_SHORT).show()
            },
            onFailure = { err ->
                binding.tvWorkspaceStatus.text = "Save failed: ${err.message}"
            }
        )
    }

    private fun confirmDiscardUnsavedChanges(onProceed: () -> Unit) {
        if (!hasUnsavedChanges) {
            onProceed()
            return
        }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.workspace_unsaved_title))
            .setMessage(getString(R.string.workspace_unsaved_message))
            .setPositiveButton("Save & Continue") { _, _ ->
                saveCurrentFile()
                onProceed()
            }
            .setNeutralButton("Discard Changes") { _, _ ->
                hasUnsavedChanges = false
                binding.tvUnsavedIndicator.visibility = View.GONE
                onProceed()
            }
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .show()
    }

    private fun showSwitchProjectDialog() {
        lifecycleScope.launch {
            val all = db.projectDao().listAll()
            if (all.isEmpty()) {
                Snackbar.make(binding.root, "No projects found", Snackbar.LENGTH_SHORT).show()
                return@launch
            }
            val labels = all.map { "${it.name} (${it.kind})" }.toTypedArray()
            AlertDialog.Builder(this@WorkspaceActivity)
                .setTitle("Switch Project")
                .setItems(labels) { _, which ->
                    bindProject(all[which])
                }
                .setNegativeButton(getString(R.string.btn_cancel), null)
                .show()
        }
    }

    private fun showCreateFromTemplateDialog() {
        val templates = arrayOf(
            getString(R.string.workspace_template_html),
            getString(R.string.workspace_template_react),
            getString(R.string.workspace_template_android)
        )
        var selectedIdx = 0
        val input = EditText(this).apply {
            hint = "Project Name (e.g. My Cool App)"
            setText("ZipBug App ${(System.currentTimeMillis() % 900) + 100}")
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 16, 48, 8)
            addView(input)
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.workspace_new_from_template))
            .setSingleChoiceItems(templates, 0) { _, which ->
                selectedIdx = which
            }
            .setView(container)
            .setPositiveButton("Create") { _, _ ->
                val name = input.text.toString().trim().ifBlank { "ZipBug Project" }
                createTemplateProject(name, selectedIdx)
            }
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .show()
    }

    private fun createTemplateProject(name: String, templateIndex: Int) {
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                val (kind, entry, files) = when (templateIndex) {
                    1 -> Triple(
                        "react-web",
                        "index.html",
                        ReactTemplateScaffolder.files(name, "React + Vite project created in ZIP_BUG Workspace")
                    )
                    2 -> {
                        val pkgSlug = name.lowercase(Locale.US)
                            .replace(Regex("[^a-z0-9]"), "")
                            .ifBlank { "app" }
                        val pkg = "com.zipbug.generated.$pkgSlug"
                        Triple(
                            "android",
                            "app/src/main/java/com/zipbug/generated/$pkgSlug/MainActivity.kt",
                            AppScaffolder.files(AppSpec(name, pkg, "Android Kotlin starter created in ZIP_BUG Workspace"))
                        )
                    }
                    else -> Triple(
                        "web-mini-app",
                        "www/index.html",
                        WebMiniAppScaffolder.files(name, "HTML/CSS/JS starter created in ZIP_BUG Workspace")
                    )
                }
                val created = createProjectOnDisk(name, kind, entry, files)
                db.projectDao().upsert(created)
                created
            }.fold(
                onSuccess = { proj ->
                    withContext(Dispatchers.Main) {
                        bindProject(proj)
                        Snackbar.make(binding.root, "Created '${proj.name}' (${proj.kind})", Snackbar.LENGTH_SHORT).show()
                    }
                },
                onFailure = { err ->
                    withContext(Dispatchers.Main) {
                        Snackbar.make(binding.root, "Template creation failed: ${err.message}", Snackbar.LENGTH_LONG).show()
                    }
                }
            )
        }
    }

    private fun createProjectOnDisk(
        name: String,
        kind: String,
        entryFile: String,
        files: Map<String, String>
    ): ProjectEntity {
        val id = UUID.randomUUID().toString()
        val rootDir = File(filesDir, "projects/$id").apply { mkdirs() }
        val canonRoot = rootDir.canonicalFile
        for ((relPath, content) in files) {
            val out = File(canonRoot, relPath).canonicalFile
            require(out.path.startsWith(canonRoot.path + File.separator)) {
                "Invalid template file path: $relPath"
            }
            out.parentFile?.mkdirs()
            out.writeText(content, Charsets.UTF_8)
        }
        return ProjectEntity(
            id = id,
            name = name,
            kind = kind,
            rootPath = canonRoot.absolutePath,
            entryFile = entryFile,
            createdAt = System.currentTimeMillis(),
            lastOpenedAt = System.currentTimeMillis()
        )
    }

    private fun promptImportZipSource() {
        val exportDir = File(filesDir, "exports")
        val localZips = exportDir.listFiles { f -> f.isFile && f.extension.equals("zip", ignoreCase = true) }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()

        if (localZips.isEmpty()) {
            importZipPicker.launch(arrayOf("application/zip", "application/octet-stream"))
            return
        }

        val options = localZips.map { "Import ${it.name} (${it.length()} B)" } +
            listOf("Browse Device Storage (SAF)…")

        AlertDialog.Builder(this)
            .setTitle("Import Project ZIP")
            .setItems(options.toTypedArray()) { _, which ->
                if (which in localZips.indices) {
                    importProjectFromZip(Uri.fromFile(localZips[which]))
                } else {
                    importZipPicker.launch(arrayOf("application/zip", "application/octet-stream"))
                }
            }
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .show()
    }

    private fun importProjectFromZip(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                ZipImporter(this@WorkspaceActivity).importZip(uri)
            }.fold(
                onSuccess = { proj ->
                    db.projectDao().upsert(proj)
                    withContext(Dispatchers.Main) {
                        bindProject(proj)
                        binding.tvWorkspaceStatus.text = "Imported project '${proj.name}' from ZIP (${proj.rootPath})"
                        Snackbar.make(binding.root, "Imported '${proj.name}'", Snackbar.LENGTH_SHORT).show()
                    }
                },
                onFailure = { err ->
                    withContext(Dispatchers.Main) {
                        binding.tvWorkspaceStatus.text = "ZIP import failed: ${err.message}"
                        Snackbar.make(binding.root, "ZIP import failed: ${err.message}", Snackbar.LENGTH_LONG).show()
                    }
                }
            )
        }
    }

    private fun promptRenameActiveProject() {
        val proj = activeProject ?: return
        val input = EditText(this).apply { setText(proj.name) }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.btn_rename))
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotBlank()) {
                    val updated = proj.copy(name = newName, lastOpenedAt = System.currentTimeMillis())
                    lifecycleScope.launch {
                        db.projectDao().upsert(updated)
                        bindProject(updated)
                    }
                }
            }
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .show()
    }

    private fun duplicateActiveProject() {
        val proj = activeProject ?: return
        if (hasUnsavedChanges) saveCurrentFile()
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                val newId = UUID.randomUUID().toString()
                val srcDir = File(proj.rootPath)
                val dstDir = File(filesDir, "projects/$newId").apply { mkdirs() }
                if (srcDir.exists()) {
                    srcDir.copyRecursively(dstDir, overwrite = true)
                }
                val copyProj = ProjectEntity(
                    id = newId,
                    name = "${proj.name} (Copy)",
                    kind = proj.kind,
                    rootPath = dstDir.canonicalPath,
                    entryFile = proj.entryFile,
                    createdAt = System.currentTimeMillis(),
                    lastOpenedAt = System.currentTimeMillis()
                )
                db.projectDao().upsert(copyProj)
                copyProj
            }.fold(
                onSuccess = { copyProj ->
                    withContext(Dispatchers.Main) {
                        bindProject(copyProj)
                        Snackbar.make(binding.root, "Duplicated as '${copyProj.name}'", Snackbar.LENGTH_SHORT).show()
                    }
                },
                onFailure = { err ->
                    withContext(Dispatchers.Main) {
                        Snackbar.make(binding.root, "Duplicate failed: ${err.message}", Snackbar.LENGTH_LONG).show()
                    }
                }
            )
        }
    }

    private fun collectProjectFilesMap(proj: ProjectEntity): Map<String, String> {
        val root = File(proj.rootPath).canonicalFile
        val filesMap = linkedMapOf<String, String>()
        root.walkTopDown()
            .onEnter { d -> d.name != "node_modules" && d.name != ".git" && d.name != "build" }
            .filter { it.isFile && it.length() <= 2 * 1024 * 1024L }
            .forEach { f ->
                val rel = f.relativeTo(root).path.replace('\\', '/')
                filesMap[rel] = f.readText(Charsets.UTF_8)
            }
        return filesMap
    }

    private fun exportActiveProjectImmediately() {
        val proj = activeProject ?: return
        if (hasUnsavedChanges) saveCurrentFile()
        val slug = proj.name.replace(Regex("[^a-zA-Z0-9_-]"), "_").ifBlank { "project" }

        lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                val filesMap = collectProjectFilesMap(proj)
                val exportDir = File(filesDir, "exports").apply { mkdirs() }
                val localZip = File(exportDir, "$slug.zip")
                val localUri = Uri.fromFile(localZip)
                val writer = ProjectZipWriter(this@WorkspaceActivity)
                writer.write(localUri, filesMap)
                val validation = writer.validate(localUri, filesMap.keys)
                Pair(localZip, validation)
            }.fold(
                onSuccess = { (localZip, validation) ->
                    withContext(Dispatchers.Main) {
                        binding.tvWorkspaceStatus.text =
                            "Exported & verified ZIP (${validation.entryCount} files, ${localZip.length()} B): ${localZip.absolutePath}"
                        Snackbar.make(
                            binding.root,
                            "Exported verified ZIP (${validation.entryCount} files, ${localZip.length()} B)",
                            Snackbar.LENGTH_LONG
                        ).setAction("Save As…") {
                            exportZipPicker.launch("$slug.zip")
                        }.show()
                    }
                },
                onFailure = { err ->
                    withContext(Dispatchers.Main) {
                        binding.tvWorkspaceStatus.text = "ZIP export failed: ${err.message}"
                    }
                }
            )
        }
    }

    private fun exportActiveProjectToZipUri(uri: Uri) {
        val proj = activeProject ?: return
        if (hasUnsavedChanges) saveCurrentFile()
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                val filesMap = collectProjectFilesMap(proj)
                val writer = ProjectZipWriter(this@WorkspaceActivity)
                writer.write(uri, filesMap)
                writer.validate(uri, filesMap.keys)
            }.fold(
                onSuccess = { validation ->
                    withContext(Dispatchers.Main) {
                        binding.tvWorkspaceStatus.text =
                            "Exported & verified ZIP (${validation.entryCount} files, ${validation.totalUncompressedBytes} B)"
                        Snackbar.make(binding.root, "Exported verified ZIP (${validation.entryCount} files)", Snackbar.LENGTH_SHORT).show()
                    }
                },
                onFailure = { err ->
                    withContext(Dispatchers.Main) {
                        binding.tvWorkspaceStatus.text = "ZIP export failed: ${err.message}"
                    }
                }
            )
        }
    }

    private fun promptCreateNewFile() {
        val proj = activeProject ?: return
        val input = EditText(this).apply {
            hint = "Relative path (e.g. www/extra.js or src/Header.jsx)"
        }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.workspace_new_file))
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                val rel = input.text.toString().trim().replace('\\', '/').trimStart('/')
                if (rel.isBlank() || rel.contains("..")) {
                    Snackbar.make(binding.root, "Invalid relative file path", Snackbar.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val canonRoot = File(proj.rootPath).canonicalFile
                val target = File(canonRoot, rel).canonicalFile
                if (!target.path.startsWith(canonRoot.path + File.separator)) {
                    Snackbar.make(binding.root, "Path escapes project root", Snackbar.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                target.parentFile?.mkdirs()
                if (!target.exists()) {
                    target.writeText("", Charsets.UTF_8)
                }
                refreshFileList()
                openFileInEditor(target)
            }
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .show()
    }

    private fun previewActiveWebProject() {
        val proj = activeProject ?: return
        if (hasUnsavedChanges) saveCurrentFile()

        runCatching {
            val root = File(proj.rootPath).canonicalFile
            val currentEd = activeFile
            val distIndex = File(root, "dist/index.html")
            val htmlEntry = when {
                distIndex.isFile && (proj.kind == "react-web" || currentEd == null || currentEd.name == "index.html" || currentEd.extension != "html") ->
                    distIndex.canonicalFile
                currentEd != null && currentEd.extension.equals("html", ignoreCase = true) ->
                    currentEd.canonicalFile
                File(root, proj.entryFile).isFile && proj.entryFile.endsWith(".html", ignoreCase = true) ->
                    File(root, proj.entryFile).canonicalFile
                File(root, "www/index.html").isFile -> File(root, "www/index.html").canonicalFile
                File(root, "index.html").isFile -> File(root, "index.html").canonicalFile
                else -> error("No HTML entry file found in '${proj.name}' to preview on :3131")
            }

            require(htmlEntry.path.startsWith(root.path + File.separator)) {
                "HTML entry file is outside project sandbox"
            }

            val webRoot = htmlEntry.parentFile ?: root
            EngineManager.run(webRoot)

            startActivity(
                Intent(this, WebRuntimeActivity::class.java)
                    .putExtra("url", "http://127.0.0.1:3131/${htmlEntry.name}")
            )
        }.onFailure { err ->
            Snackbar.make(binding.root, err.message ?: "Preview failed", Snackbar.LENGTH_LONG).show()
        }
    }

    private fun termuxHomeDir(): String =
        getSharedPreferences("zipbug.settings", MODE_PRIVATE)
            .getString("termuxHome", "/data/data/com.termux/files/home")!!

    private fun buildProjectTermuxSlug(proj: ProjectEntity): String =
        proj.name.lowercase(Locale.US).replace(Regex("[^a-z0-9_-]"), "_").ifBlank { proj.id.take(8) }

    private fun stageProjectToTermux() {
        val proj = activeProject ?: return
        if (hasUnsavedChanges) saveCurrentFile()

        if (!TermuxBridge.isTermuxInstalled(this) || !TermuxBridge.hasPermission(this)) {
            binding.tvWorkspaceStatus.text =
                "Termux not ready: ensure Termux is installed and RUN_COMMAND permission is granted."
            return
        }

        val slug = buildProjectTermuxSlug(proj)
        val home = termuxHomeDir()
        val targetDir = "$home/projects/$slug"
        val gzChunks = encodeFilesPayloadChunks(collectProjectFilesMap(proj))

        val stageScript = """
import base64, json, os, sys, zlib
target = sys.argv[1]
raw_json = zlib.decompress(base64.b64decode("".join(sys.argv[2:])), 16 + zlib.MAX_WBITS).decode("utf-8")
files = json.loads(raw_json)
os.makedirs(target, exist_ok=True)
written = 0
for rel, content in files.items():
    out = os.path.normpath(os.path.join(target, rel))
    if not out.startswith(os.path.normpath(target) + os.sep):
        continue
    os.makedirs(os.path.dirname(out), exist_ok=True)
    with open(out, "w", encoding="utf-8") as f:
        f.write(content)
    written += 1
print(f"STAGED_OK:{written}:{target}")
""".trimIndent()

        binding.tvWorkspaceStatus.text = "Staging '${proj.name}' to Termux ($targetDir)…"
        lifecycleScope.launch {
            TermuxBridge.send(
                this@WorkspaceActivity,
                TermuxBridge.Request(
                    tool = "python",
                    args = listOf("-c", stageScript, targetDir) + gzChunks,
                    workDir = home,
                    label = "Zip_Bug Stage ($slug)"
                )
            ).fold(
                onSuccess = { jobId ->
                    observeEngineJob(jobId, "stage", slug)
                },
                onFailure = { err ->
                    binding.tvWorkspaceStatus.text = "Stage failed: ${err.message}"
                }
            )
        }
    }

    private fun encodeFilesPayloadChunks(filesMap: Map<String, String>): List<String> {
        val rawBytes = JSONObject(filesMap).toString().toByteArray(Charsets.UTF_8)
        val bos = ByteArrayOutputStream()
        GZIPOutputStream(bos).use { it.write(rawBytes) }
        val b64 = Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP)
        return b64.chunked(2500).ifEmpty { listOf("") }
    }

    private fun runReactEngineCommand(npmArgs: List<String>, label: String) {
        val proj = activeProject ?: return
        if (hasUnsavedChanges) saveCurrentFile()

        binding.tvWorkspaceStatus.text = "Dispatching '$label' to Engine…"
        lifecycleScope.launch {
            val slug = buildProjectTermuxSlug(proj)
            val home = termuxHomeDir()
            val targetDir = "$home/projects/$slug"

            if (TermuxBridge.isTermuxInstalled(this@WorkspaceActivity) &&
                TermuxBridge.hasPermission(this@WorkspaceActivity)
            ) {
                val filesMap = collectProjectFilesMap(proj).filterKeys { !it.startsWith("dist/") }
                val gzChunks = encodeFilesPayloadChunks(filesMap)
                val npmArgsJson = org.json.JSONArray(npmArgs).toString()

                val runnerScript = """
import base64, json, os, subprocess, sys, zlib
target = sys.argv[1]
npm_args = json.loads(sys.argv[2])
raw_json = zlib.decompress(base64.b64decode("".join(sys.argv[3:])), 16 + zlib.MAX_WBITS).decode("utf-8")
files = json.loads(raw_json)
os.makedirs(target, exist_ok=True)
for rel, content in files.items():
    out = os.path.normpath(os.path.join(target, rel))
    if not out.startswith(os.path.normpath(target) + os.sep):
        continue
    os.makedirs(os.path.dirname(out), exist_ok=True)
    with open(out, "w", encoding="utf-8") as f:
        f.write(content)

cmd = ["npm"] + npm_args
if npm_args == ["install"]:
    cmd = ["npm", "install", "--no-audit", "--no-fund"]

res = subprocess.run(cmd, cwd=target, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
if res.stdout:
    print(res.stdout)
if res.stderr:
    sys.stderr.write(res.stderr)
sys.exit(res.returncode)
""".trimIndent()

                TermuxBridge.send(
                    this@WorkspaceActivity,
                    TermuxBridge.Request(
                        tool = "python",
                        args = listOf("-c", runnerScript, targetDir, npmArgsJson) + gzChunks,
                        workDir = home,
                        label = "Zip_Bug React ($label)"
                    )
                ).fold(
                    onSuccess = { jobId ->
                        observeEngineJob(jobId, label, slug)
                    },
                    onFailure = { err ->
                        binding.tvWorkspaceStatus.text = "Termux dispatch failed: ${err.message}"
                    }
                )
            } else {
                val url = EngineConnectionClient.getSavedUrl(this@WorkspaceActivity)
                val token = EngineConnectionClient.getSavedToken(this@WorkspaceActivity)
                EngineConnectionClient.submitJob(
                    rawUrl = url,
                    token = token,
                    tool = "npm",
                    args = npmArgs,
                    workDir = proj.rootPath
                ).fold(
                    onSuccess = { res ->
                        binding.tvWorkspaceStatus.text =
                            "HTTP Engine ($url) status=${res.status} exit=${res.exitCode}: ${res.message}"
                        refreshFileList()
                    },
                    onFailure = { err ->
                        binding.tvWorkspaceStatus.text =
                            "Engine required for '$label': Install Termux (nodejs) or connect HTTP Engine at $url (${err.message})"
                    }
                )
            }
        }
    }

    private fun observeEngineJob(jobId: String, label: String, slug: String) {
        engineJobObserver?.cancel()
        activeEngineJobId = jobId
        activeEngineTermuxSlug = slug

        engineJobObserver = lifecycleScope.launch {
            db.buildJobDao().observe(jobId).filterNotNull().collect { job ->
                when (job.status) {
                    BuildJobEntity.QUEUED, BuildJobEntity.RUNNING -> {
                        binding.tvWorkspaceStatus.text =
                            "Job $jobId [$label] • ${job.status} in /data/data/com.termux/files/home/projects/$slug…"
                    }
                    BuildJobEntity.SUCCESS -> {
                        activeEngineJobId = null
                        val syncedCount = syncDistOutputIfPresent(job.stdout)
                        val firstLine = job.stdout.lineSequence()
                            .firstOrNull {
                                it.isNotBlank() &&
                                    !it.startsWith("DIST_JSON") &&
                                    !it.startsWith("DIST_GZ_START") &&
                                    !it.startsWith("{")
                            }
                            ?.trim()
                            .orEmpty()
                        binding.tvWorkspaceStatus.text = if (syncedCount > 0) {
                            "Job $jobId [$label] • SUCCESS (exit 0) • Synced $syncedCount built dist/ files to workspace ($firstLine)"
                        } else {
                            "Job $jobId [$label] • SUCCESS (exit 0) • $firstLine"
                        }
                    }
                    BuildJobEntity.CANCELLED -> {
                        activeEngineJobId = null
                        binding.tvWorkspaceStatus.text = "Job $jobId [$label] • CANCELLED by user"
                    }
                    else -> {
                        activeEngineJobId = null
                        val reason = job.errorMessage.ifBlank { job.stderr }.lineSequence().firstOrNull().orEmpty()
                        binding.tvWorkspaceStatus.text =
                            "Job $jobId [$label] • ${job.status} (exit ${job.exitCode}): $reason"
                    }
                }
            }
        }
    }

    private fun syncDistOutputIfPresent(stdout: String): Int {
        val proj = activeProject ?: return 0
        val rawJson = when {
            stdout.contains("DIST_GZ_START:") && stdout.contains(":DIST_GZ_END") -> {
                val b64 = stdout.substringAfter("DIST_GZ_START:").substringBefore(":DIST_GZ_END").trim()
                runCatching {
                    val compressed = Base64.decode(b64, Base64.DEFAULT)
                    GZIPInputStream(ByteArrayInputStream(compressed)).bufferedReader(Charsets.UTF_8).use { it.readText() }
                }.getOrDefault("")
            }
            stdout.contains("DIST_JSON_START") && stdout.contains("DIST_JSON_END") -> {
                stdout.substringAfter("DIST_JSON_START").substringBefore("DIST_JSON_END").trim()
            }
            else -> ""
        }
        if (rawJson.isBlank()) return 0
        val canonRoot = File(proj.rootPath).canonicalFile
        var written = 0
        runCatching {
            val json = JSONObject(rawJson)
            val keys = json.keys()
            while (keys.hasNext()) {
                val rel = keys.next()
                val content = json.optString(rel, "")
                val outFile = File(canonRoot, rel).canonicalFile
                if (outFile.path.startsWith(canonRoot.path + File.separator)) {
                    outFile.parentFile?.mkdirs()
                    outFile.writeText(content, Charsets.UTF_8)
                    written++
                }
            }
        }
        if (written > 0) {
            refreshFileList()
            val distIndex = File(canonRoot, "dist/index.html")
            if (distIndex.isFile) {
                openFileInEditor(distIndex)
            }
        }
        return written
    }

    private fun cancelActiveEngineJob() {
        val jobId = activeEngineJobId
        if (jobId == null) {
            binding.tvWorkspaceStatus.text = "No active Engine job to cancel."
            Snackbar.make(binding.root, "No active job running", Snackbar.LENGTH_SHORT).show()
            return
        }
        engineJobObserver?.cancel()
        activeEngineJobId = null
        val slug = activeEngineTermuxSlug.orEmpty()

        lifecycleScope.launch {
            db.buildJobDao().finish(
                id = jobId,
                status = BuildJobEntity.CANCELLED,
                stdout = "",
                stderr = "Cancelled by user action",
                exitCode = 130,
                errorCode = null,
                errorMessage = "Job cancelled by user"
            )
            if (slug.isNotBlank() && TermuxBridge.isTermuxInstalled(this@WorkspaceActivity) &&
                TermuxBridge.hasPermission(this@WorkspaceActivity)
            ) {
                val killScript = "import os, signal; print('CANCEL_SIGNAL_SENT')"
                TermuxBridge.send(
                    this@WorkspaceActivity,
                    TermuxBridge.Request(
                        tool = "python",
                        args = listOf("-c", killScript),
                        workDir = termuxHomeDir(),
                        label = "Zip_Bug Cancel ($slug)"
                    )
                )
            }
            binding.tvWorkspaceStatus.text = "Job $jobId • CANCELLED by user (exit 130)"
            Snackbar.make(binding.root, "Cancelled job $jobId", Snackbar.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        engineJobObserver?.cancel()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_PROJECT_ID = "extra_project_id"
    }
}
