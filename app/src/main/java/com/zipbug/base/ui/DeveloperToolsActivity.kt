package com.zipbug.base.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.zipbug.base.R
import com.zipbug.base.ZipBugApp
import com.zipbug.base.databinding.ActivityDeveloperToolsBinding
import com.zipbug.base.engine.EngineConnectionClient
import com.zipbug.base.termux.TermuxBridge
import com.zipbug.base.tools.DevToolsEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

class DeveloperToolsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDeveloperToolsBinding
    private val app by lazy { application as ZipBugApp }
    private val db by lazy { app.database }
    private var lastComputedSha256: String = ""

    private val hashFilePicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) computeHashForUri(uri)
    }

    private val zipInspectPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) inspectZipUri(uri)
    }

    private val backupCreateLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) exportBackupToUri(uri)
    }

    private val backupRestorePicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) restoreBackupFromUri(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDeveloperToolsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Icon-grid navigator: tap a tile to jump to that tool section.
        fun jumpTo(section: android.view.View) {
            binding.devToolsScroll.post {
                binding.devToolsScroll.smoothScrollTo(0, section.top)
            }
        }
        binding.navJson.setOnClickListener { jumpTo(binding.sectionJson) }
        binding.navHash.setOnClickListener { jumpTo(binding.sectionHash) }
        binding.navZip.setOnClickListener { jumpTo(binding.sectionZip) }
        binding.navSearch.setOnClickListener {
            jumpTo(binding.sectionSearch)
        }
        binding.navBackup.setOnClickListener {
            jumpTo(binding.sectionBackup)
        }

        setupJsonTool()
        setupHashTool()
        setupZipInspectTool()
        setupWorkspaceSearchTool()
        setupBackupAndDiagnosticsTool()
    }

    private fun setupJsonTool() {
        if (binding.etJsonInput.text.isNullOrBlank()) {
            binding.etJsonInput.setText("{\"app\":\"Zip_Bug\",\"version\":2,\"features\":[\"workspace\",\"termux\",\"http_engine\",\"media\"]}")
        }
        binding.btnFormatJson.setOnClickListener {
            val raw = binding.etJsonInput.text.toString()
            DevToolsEngine.validateAndFormatJson(raw, indentSpaces = 2).fold(
                onSuccess = { formatted ->
                    binding.etJsonInput.setText(formatted)
                    binding.tvJsonStatus.text = "VALID JSON • Formatted (${formatted.length} chars)"
                    binding.tvJsonStatus.setTextColor(ContextCompat.getColor(this, R.color.z_green))
                },
                onFailure = { err ->
                    binding.tvJsonStatus.text = "INVALID JSON • ${err.message}"
                    binding.tvJsonStatus.setTextColor(ContextCompat.getColor(this, R.color.z_red))
                }
            )
        }

        binding.btnMinifyJson.setOnClickListener {
            val raw = binding.etJsonInput.text.toString()
            DevToolsEngine.minifyJson(raw).fold(
                onSuccess = { minified ->
                    binding.etJsonInput.setText(minified)
                    binding.tvJsonStatus.text = "VALID JSON • Minified (${minified.length} chars)"
                    binding.tvJsonStatus.setTextColor(ContextCompat.getColor(this, R.color.z_green))
                },
                onFailure = { err ->
                    binding.tvJsonStatus.text = "INVALID JSON • ${err.message}"
                    binding.tvJsonStatus.setTextColor(ContextCompat.getColor(this, R.color.z_red))
                }
            )
        }

        binding.btnCopyJson.setOnClickListener {
            val content = binding.etJsonInput.text.toString()
            if (content.isNotBlank()) {
                copyToClipboard("JSON", content)
            }
        }
    }

    private fun setupHashTool() {
        binding.btnPickHashFile.setOnClickListener {
            val exportZips = File(filesDir, "exports")
                .listFiles { f -> f.isFile }
                ?.sortedByDescending { it.lastModified() }
                .orEmpty()
            if (exportZips.isNotEmpty()) {
                computeHashForUri(Uri.fromFile(exportZips.first()))
            } else {
                hashFilePicker.launch(arrayOf("*/*"))
            }
        }

        binding.btnCopyHash.setOnClickListener {
            if (lastComputedSha256.isNotBlank()) {
                copyToClipboard("SHA-256", lastComputedSha256)
            } else {
                Snackbar.make(binding.root, "Pick a file first to compute SHA-256", Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    private fun computeHashForUri(uri: Uri) {
        binding.tvHashOutput.text = "Computing hashes..."
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                contentResolver.openInputStream(uri)?.use { stream ->
                    DevToolsEngine.computeHashes(stream)
                } ?: error("Cannot open input stream for selected file")
            }.fold(
                onSuccess = { res ->
                    lastComputedSha256 = res.sha256
                    withContext(Dispatchers.Main) {
                        binding.tvHashOutput.text = buildString {
                            appendLine("URI: $uri")
                            appendLine("Size: ${DevToolsEngine.formatBytes(res.sizeBytes)} (${res.sizeBytes} bytes)")
                            appendLine("SHA-256: ${res.sha256}")
                            appendLine("SHA-1:   ${res.sha1}")
                            append("MD5:     ${res.md5}")
                        }
                    }
                },
                onFailure = { err ->
                    withContext(Dispatchers.Main) {
                        binding.tvHashOutput.text = "Hash calculation failed: ${err.message}"
                    }
                }
            )
        }
    }

    private fun setupZipInspectTool() {
        binding.btnInspectZip.setOnClickListener {
            val exportZips = File(filesDir, "exports")
                .listFiles { f -> f.isFile && f.extension.equals("zip", ignoreCase = true) }
                ?.sortedByDescending { it.lastModified() }
                .orEmpty()
            if (exportZips.isNotEmpty()) {
                inspectZipUri(Uri.fromFile(exportZips.first()))
            } else {
                zipInspectPicker.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
            }
        }
    }

    private fun inspectZipUri(uri: Uri) {
        binding.tvZipInspectOutput.text = "Inspecting archive entries..."
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                contentResolver.openInputStream(uri)?.use { stream ->
                    DevToolsEngine.inspectZip(stream).getOrThrow()
                } ?: error("Cannot open selected archive")
            }.fold(
                onSuccess = { report ->
                    withContext(Dispatchers.Main) {
                        binding.tvZipInspectOutput.text = buildString {
                            appendLine("Entries: ${report.entryCount} (${report.fileCount} files, ${report.dirCount} dirs)")
                            appendLine("Compressed:   ${DevToolsEngine.formatBytes(report.totalCompressedBytes)}")
                            appendLine("Uncompressed: ${DevToolsEngine.formatBytes(report.totalUncompressedBytes)}")
                            appendLine("Path Safety:  ${if (report.hasUnsafeEntries) "UNSAFE (Traversal Risk Detected)" else "SAFE (Verified)"}")
                            appendLine("--------------------------------------------")
                            report.entries.take(40).forEach { e ->
                                val tag = if (e.isDirectory) "[DIR] " else "[FILE]"
                                val warn = e.warning?.let { " !! $it" } ?: ""
                                appendLine("$tag ${e.name} (${e.uncompressedBytes} B)$warn")
                            }
                            if (report.entries.size > 40) {
                                append("... and ${report.entries.size - 40} more entries")
                            }
                        }
                    }
                },
                onFailure = { err ->
                    withContext(Dispatchers.Main) {
                        binding.tvZipInspectOutput.text = "Archive inspection failed: ${err.message}"
                    }
                }
            )
        }
    }

    private fun setupWorkspaceSearchTool() {
        binding.btnRunSearch.setOnClickListener {
            val query = binding.etSearchQuery.text.toString().trim()
            if (query.isBlank()) {
                Snackbar.make(binding.root, "Enter a search query first", Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            binding.tvSearchOutput.text = "Searching workspace..."
            lifecycleScope.launch(Dispatchers.IO) {
                val projectsRoot = File(filesDir, "projects")
                val matches = DevToolsEngine.searchInDirectory(projectsRoot, query, maxResults = 60)
                withContext(Dispatchers.Main) {
                    if (matches.isEmpty()) {
                        binding.tvSearchOutput.text = "No matches found for \"$query\" in ${projectsRoot.absolutePath}"
                    } else {
                        binding.tvSearchOutput.text = buildString {
                            appendLine("Found ${matches.size} match(es) for \"$query\":")
                            appendLine("--------------------------------------------")
                            matches.forEach { m ->
                                appendLine("[${m.projectName}] ${m.relativePath}:${m.lineNumber}")
                                appendLine("  ${m.linePreview}")
                            }
                        }
                    }
                }
            }
        }
    }

    private fun setupBackupAndDiagnosticsTool() {
        binding.btnExportBackup.setOnClickListener {
            val backupDir = File(filesDir, "backups").apply { mkdirs() }
            val localBackup = File(backupDir, "zipbug-projects-backup-v1.zip")
            exportBackupToUri(Uri.fromFile(localBackup))
        }

        binding.btnRestoreBackup.setOnClickListener {
            val localBackup = File(filesDir, "backups/zipbug-projects-backup-v1.zip")
            if (localBackup.isFile && localBackup.length() > 0L) {
                restoreBackupFromUri(Uri.fromFile(localBackup))
            } else {
                backupRestorePicker.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
            }
        }

        binding.btnGenerateDiagnostics.setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO) {
                val pCount = db.projectDao().listAll().size + db.recapProjectDao().getAllSync().size
                val jCount = db.buildJobDao().listAll().size
                val report = DevToolsEngine.buildDiagnosticReport(
                    context = this@DeveloperToolsActivity,
                    projectCount = pCount,
                    jobCount = jCount,
                    engineUrl = EngineConnectionClient.getSavedUrl(this@DeveloperToolsActivity),
                    termuxInstalled = TermuxBridge.isTermuxInstalled(this@DeveloperToolsActivity),
                    termuxPermitted = TermuxBridge.hasPermission(this@DeveloperToolsActivity)
                )
                withContext(Dispatchers.Main) {
                    binding.tvBackupDiagOutput.text = report
                    copyToClipboard("Zip_Bug Diagnostic Report", report)
                }
            }
        }
    }

    private fun exportBackupToUri(uri: Uri) {
        binding.tvBackupDiagOutput.text = "Creating versioned backup archive..."
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                val allProjects = db.projectDao().listAll()
                contentResolver.openOutputStream(uri)?.use { out ->
                    DevToolsEngine.createBackupZip(allProjects, out, packageName).getOrThrow()
                } ?: error("Cannot open output stream for backup")
            }.fold(
                onSuccess = { manifest ->
                    withContext(Dispatchers.Main) {
                        binding.tvBackupDiagOutput.text = buildString {
                            appendLine("BACKUP EXPORTED SUCCESSFULLY")
                            appendLine("Format Version: ${manifest.formatVersion}")
                            appendLine("Projects Included: ${manifest.projectCount}")
                            appendLine("Total Files Archived: ${manifest.totalFiles}")
                            append("Destination: $uri")
                        }
                    }
                },
                onFailure = { err ->
                    withContext(Dispatchers.Main) {
                        binding.tvBackupDiagOutput.text = "Backup export failed: ${err.message}"
                    }
                }
            )
        }
    }

    private fun restoreBackupFromUri(uri: Uri) {
        binding.tvBackupDiagOutput.text = "Validating backup manifest and restoring..."
        lifecycleScope.launch(Dispatchers.IO) {
            val projectsBaseDir = File(filesDir, "projects")
            runCatching {
                contentResolver.openInputStream(uri)?.use { input ->
                    DevToolsEngine.validateAndRestoreBackupZip(projectsBaseDir, input).getOrThrow()
                } ?: error("Cannot open selected backup archive")
            }.fold(
                onSuccess = { summary ->
                    summary.restoredProjects.forEach { proj ->
                        db.projectDao().upsert(proj)
                    }
                    withContext(Dispatchers.Main) {
                        binding.tvBackupDiagOutput.text = buildString {
                            appendLine("BACKUP RESTORED SUCCESSFULLY")
                            appendLine("Verified Format Version: ${summary.formatVersion}")
                            appendLine("Restored Projects: ${summary.restoredProjects.size}")
                            appendLine("Restored Files: ${summary.restoredFilesCount}")
                            append("Total Bytes: ${DevToolsEngine.formatBytes(summary.totalBytesRestored)}")
                        }
                    }
                },
                onFailure = { err ->
                    withContext(Dispatchers.Main) {
                        binding.tvBackupDiagOutput.text = "Restore rejected: ${err.message}"
                    }
                }
            )
        }
    }

    private fun copyToClipboard(label: String, text: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
        Snackbar.make(binding.root, "$label copied to clipboard", Snackbar.LENGTH_SHORT).show()
    }
}
