package com.zipbug.base.ui

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.View
import android.widget.ArrayAdapter
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar
import com.zipbug.base.R
import com.zipbug.base.creator.HtmlWebViewScaffolder
import com.zipbug.base.creator.ProjectZipWriter
import com.zipbug.base.creator.WebAppSpec
import com.zipbug.base.creator.WebHardware
import com.zipbug.base.creator.WebOrientation
import com.zipbug.base.creator.WebTemplate
import com.zipbug.base.creator.WebTemplates
import com.zipbug.base.databinding.ActivityHtmlAppWizardBinding
import java.util.Locale

/**
 * Step-by-step HTML-to-app wizard (Info → Content → Display →
 * Permissions → WebView → Summary), modelled on dedicated HTML-to-APK
 * builder apps. Produces the same source-project ZIP as the quick
 * creator form, through [HtmlWebViewScaffolder].
 */
class HtmlAppWizardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHtmlAppWizardBinding
    private var step = 1
    private var selectedTemplate: WebTemplate? = null
    private var pendingFiles: Map<String, String>? = null
    private var lastExportUri: Uri? = null

    private val saveZip = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri == null) {
            binding.wizResult.text =
                "PACKAGING • CANCELLED\nNo source ZIP was written."
            return@registerForActivityResult
        }
        val files = pendingFiles ?: return@registerForActivityResult
        binding.wizExportActions.visibility = View.GONE
        binding.wizResult.text =
            "VALIDATION • PASS\nPACKAGING • WRITING\nDestination: $uri"
        runCatching {
            val writer = ProjectZipWriter(this)
            writer.write(uri, files)
            val validation = writer.validate(uri, files.keys)
            val metadata = queryDocumentMetadata(uri)
            Triple(validation, metadata.first, metadata.second)
        }.onSuccess { (validation, displayName, sizeBytes) ->
            lastExportUri = uri
            binding.wizExportActions.visibility = View.VISIBLE
            binding.wizResult.text = buildString {
                append("VALIDATION • PASS\n")
                append("PACKAGING • PASS\n")
                append("ZIP VERIFY • PASS\n\n")
                append("NAME: ").append(displayName).append("\n")
                append("SIZE: ").append(formatBytes(sizeBytes)).append("\n")
                append("FILES: ").append(validation.entryCount).append("\n")
                append("UNCOMPRESSED: ")
                    .append(formatBytes(validation.totalUncompressedBytes))
                    .append("\n")
                append("LOCATION: ").append(uri)
            }
        }.onFailure { error ->
            lastExportUri = null
            binding.wizExportActions.visibility = View.GONE
            binding.wizResult.text =
                "VALIDATION / PACKAGING • FAILED\n\n" +
                    (error.message ?: "ZIP creation failed")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHtmlAppWizardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.wizOrientation.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            listOf(
                getString(R.string.wizard_orientation_auto),
                getString(R.string.wizard_orientation_portrait),
                getString(R.string.wizard_orientation_landscape)
            )
        )

        binding.wizSourceGroup.setOnCheckedChangeListener { _, checkedId ->
            updateSourceVisibility(checkedId)
        }
        binding.wizChooseTemplate.setOnClickListener { chooseTemplate() }
        binding.wizBack.setOnClickListener { if (step > 1) showStep(step - 1) }
        binding.wizNext.setOnClickListener {
            if (step == 1 && !validateInfo()) return@setOnClickListener
            if (step < 6) showStep(step + 1)
        }
        binding.wizCreate.setOnClickListener { createProject() }
        binding.wizOpenExport.setOnClickListener {
            val uri = lastExportUri ?: return@setOnClickListener
            startActivity(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, "application/zip")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            )
        }
        binding.wizShareExport.setOnClickListener {
            val uri = lastExportUri ?: return@setOnClickListener
            startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND)
                        .setType("application/zip")
                        .putExtra(Intent.EXTRA_STREAM, uri)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                    "Share ZIP"
                )
            )
        }

        intent.getStringExtra(EXTRA_TEMPLATE_ID)?.let { id ->
            WebTemplates.byId(id)?.let { template ->
                binding.wizSourceTemplate.isChecked = true
                applyTemplate(template)
            }
        }
        showStep(1)
    }

    private fun validateInfo(): Boolean {
        val ok = binding.wizAppName.text.toString().isNotBlank() &&
            binding.wizPackageName.text.toString().isNotBlank()
        if (!ok) {
            Snackbar.make(
                binding.root,
                R.string.wizard_name_package_required,
                Snackbar.LENGTH_LONG
            ).show()
        }
        return ok
    }

    private fun updateSourceVisibility(checkedId: Int) {
        val urlMode = checkedId == R.id.wizSourceUrl
        val templateMode = checkedId == R.id.wizSourceTemplate
        binding.wizUrl.visibility = if (urlMode) View.VISIBLE else View.GONE
        binding.wizHtml.visibility = if (urlMode) View.GONE else View.VISIBLE
        binding.wizChooseTemplate.visibility =
            if (templateMode) View.VISIBLE else View.GONE
        binding.wizTemplateLabel.visibility =
            if (templateMode) View.VISIBLE else View.GONE
    }

    private fun chooseTemplate() {
        val templates = WebTemplates.ALL
        AlertDialog.Builder(this)
            .setTitle(R.string.wizard_choose_template)
            .setItems(templates.map { it.title }.toTypedArray()) { _, which ->
                applyTemplate(templates[which])
            }
            .show()
    }

    private fun applyTemplate(template: WebTemplate) {
        selectedTemplate = template
        binding.wizTemplateLabel.text =
            getString(R.string.wizard_template_selected, template.title)
        binding.wizHtml.setText(template.html)
        binding.wizPermCamera.isChecked =
            WebHardware.CAMERA in template.hardware
        binding.wizPermMicrophone.isChecked =
            WebHardware.MICROPHONE in template.hardware
        binding.wizPermLocation.isChecked =
            WebHardware.LOCATION in template.hardware
        binding.wizPermNotifications.isChecked =
            WebHardware.NOTIFICATIONS in template.hardware
        binding.wizFullscreen.isChecked = template.fullscreen
        binding.wizKeepScreenOn.isChecked = template.keepScreenOn
    }

    private fun showStep(target: Int) {
        step = target
        val steps = listOf(
            binding.stepInfo,
            binding.stepContent,
            binding.stepDisplay,
            binding.stepPermissions,
            binding.stepWebView,
            binding.stepSummary
        )
        steps.forEachIndexed { index, view ->
            view.visibility = if (index == step - 1) View.VISIBLE else View.GONE
        }
        binding.wizardStepLabel.text = getString(R.string.wizard_step_of, step)
        binding.wizardProgress.progress = step
        binding.wizBack.isEnabled = step > 1
        binding.wizNext.visibility = if (step < 6) View.VISIBLE else View.GONE
        binding.wizCreate.visibility = if (step == 6) View.VISIBLE else View.GONE
        if (step == 6) refreshSummary()
    }

    private fun isUrlMode(): Boolean =
        binding.wizSourceGroup.checkedRadioButtonId == R.id.wizSourceUrl

    private fun buildSpec(): WebAppSpec {
        val name = binding.wizAppName.text.toString().trim()
        val hardware = mutableSetOf<WebHardware>()
        if (binding.wizPermCamera.isChecked) hardware += WebHardware.CAMERA
        if (binding.wizPermMicrophone.isChecked) {
            hardware += WebHardware.MICROPHONE
        }
        if (binding.wizPermLocation.isChecked) {
            hardware += WebHardware.LOCATION
        }
        if (binding.wizPermNotifications.isChecked) {
            hardware += WebHardware.NOTIFICATIONS
        }
        val orientation = when (binding.wizOrientation.selectedItemPosition) {
            1 -> WebOrientation.PORTRAIT
            2 -> WebOrientation.LANDSCAPE
            else -> WebOrientation.AUTO
        }
        val urlMode = isUrlMode()
        return WebAppSpec(
            name = name,
            packageName = binding.wizPackageName.text.toString().trim(),
            startUrl = if (urlMode) {
                binding.wizUrl.text.toString().trim()
            } else {
                null
            },
            webFiles = if (urlMode) {
                emptyMap()
            } else {
                mapOf(
                    "index.html" to binding.wizHtml.text.toString().ifBlank {
                        HtmlWebViewScaffolder.starterIndexHtml(name, "")
                    }
                )
            },
            orientation = orientation,
            fullscreen = binding.wizFullscreen.isChecked,
            keepScreenOn = binding.wizKeepScreenOn.isChecked,
            userAgent = binding.wizUserAgent.text.toString()
                .trim()
                .ifBlank { null },
            hardware = hardware,
            pullToRefresh = binding.wizPullToRefresh.isChecked,
            noInternetDialog = binding.wizNoInternetDialog.isChecked,
            exitConfirmation = binding.wizExitConfirmation.isChecked,
            splashEnabled = binding.wizSplash.isChecked,
            splashSeconds = binding.wizSplashSeconds.text.toString()
                .trim()
                .toIntOrNull() ?: 2,
            splashColor = binding.wizSplashColor.text.toString()
                .trim()
                .ifBlank { "#121212" },
            statusBarColor = binding.wizStatusBarColor.text.toString()
                .trim()
                .ifBlank { null },
            urlScheme = binding.wizScheme.text.toString()
                .trim()
                .ifBlank { null }
        )
    }

    private fun refreshSummary() {
        val spec = runCatching { buildSpec() }.getOrNull()
        binding.wizSummary.text = buildString {
            append(
                getString(
                    R.string.wizard_summary_name,
                    binding.wizAppName.text.toString().trim()
                )
            ).append("\n")
            append(
                getString(
                    R.string.wizard_summary_package,
                    binding.wizPackageName.text.toString().trim()
                )
            ).append("\n")
            val source = when {
                isUrlMode() -> binding.wizUrl.text.toString().trim()
                selectedTemplate != null &&
                    binding.wizSourceGroup.checkedRadioButtonId ==
                    R.id.wizSourceTemplate ->
                    getString(
                        R.string.wizard_template_selected,
                        selectedTemplate!!.title
                    )
                else -> getString(R.string.wizard_summary_source_html)
            }
            append(getString(R.string.wizard_summary_source, source))
                .append("\n")
            append(
                getString(
                    R.string.wizard_summary_display,
                    binding.wizOrientation.selectedItem?.toString() ?: ""
                )
            ).append("\n")
            if (spec != null) {
                append(
                    getString(
                        R.string.wizard_summary_permissions,
                        HtmlWebViewScaffolder.permissionsFor(spec).size
                    )
                ).append("\n")
                val webOptions = listOf(
                    spec.pullToRefresh,
                    spec.noInternetDialog,
                    spec.exitConfirmation
                ).count { it }
                append(
                    getString(
                        R.string.wizard_summary_webview_options,
                        webOptions
                    )
                )
            }
        }
    }

    private fun createProject() {
        binding.wizExportActions.visibility = View.GONE
        binding.wizResult.text =
            "INPUT VALIDATION • RUNNING\nPreparing HTML app project source…"
        runCatching {
            val spec = buildSpec()
            spec to HtmlWebViewScaffolder.files(spec)
        }.onSuccess { (spec, files) ->
            pendingFiles = files
            binding.wizResult.text =
                "INPUT VALIDATION • PASS\n" +
                    "SOURCE PLAN • PASS\n" +
                    "Prepared ${files.size} files. Choose a ZIP save location."
            saveZip.launch(safeFilename(spec.name) + "-html-app.zip")
        }.onFailure { error ->
            binding.wizResult.text =
                "INPUT VALIDATION • FAILED\n\n" +
                    (error.message ?: "Invalid project") +
                    "\n\nSAFE NEXT STEP:\n" +
                    "Check the app name, package name and options, then try again."
            Snackbar.make(
                binding.root,
                error.message ?: "Invalid project",
                Snackbar.LENGTH_LONG
            ).show()
        }
    }

    private fun queryDocumentMetadata(uri: Uri): Pair<String, Long> {
        var name = "project.zip"
        var size = -1L
        contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIndex >= 0) {
                    name = cursor.getString(nameIndex) ?: name
                }
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                    size = cursor.getLong(sizeIndex)
                }
            }
        }
        if (size < 0) {
            size = contentResolver.openAssetFileDescriptor(uri, "r")
                ?.use { it.length } ?: -1L
        }
        return name to size
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 0) return "unknown"
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) {
            return String.format(Locale.US, "%.1f KB", kb)
        }
        return String.format(Locale.US, "%.2f MB", kb / 1024.0)
    }

    private fun safeFilename(value: String): String =
        value.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            .ifBlank { "ZipBugProject" }

    companion object {
        const val EXTRA_TEMPLATE_ID = "com.zipbug.base.extra.TEMPLATE_ID"
    }
}
