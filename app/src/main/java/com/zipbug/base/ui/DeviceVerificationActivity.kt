package com.zipbug.base.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.zipbug.base.R
import com.zipbug.base.ZipBugApp
import com.zipbug.base.data.BuildJobEntity
import com.zipbug.base.databinding.ActivityDeviceVerificationBinding
import com.zipbug.base.termux.DiagnosticCheck
import com.zipbug.base.termux.DiagnosticStatus
import com.zipbug.base.termux.EngineDiagnostics
import com.zipbug.base.termux.EngineDoctor
import com.zipbug.base.termux.TermuxBridge
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class CommandProofTest(
    val id: String,
    val label: String,
    val tool: String,
    val args: List<String>,
    var status: String = "UNKNOWN", // UNKNOWN, RUNNING, PASS, FAIL
    val command: String = "$tool ${args.joinToString(" ")}",
    var exitCode: Int? = null,
    var durationMs: Long? = null,
    var timestamp: Long? = null,
    var stdout: String = "",
    var stderr: String = "",
    var outputSnippet: String = ""
)

class DeviceVerificationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDeviceVerificationBinding

    private val diagnosticChecks = EngineDiagnostics.createInitialChecks().toMutableList()

    private val proofTests = mutableListOf(
        CommandProofTest("test_python", "TEST 1: Python", "python", listOf("--version")),
        CommandProofTest("test_java", "TEST 2: Java", "java", listOf("-version")),
        CommandProofTest("test_gradle", "TEST 3: Gradle", "gradle", listOf("--version")),
        CommandProofTest("test_ffmpeg", "TEST 4: FFmpeg", "ffmpeg", listOf("-version")),
        CommandProofTest("test_aapt2", "TEST 5: aapt2", "aapt2", listOf("version"))
    )

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDeviceVerificationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        EngineDiagnostics.evaluateLocalChecks(this, diagnosticChecks)
        renderPreflight()
        renderTestsList()

        binding.btnRunGuidedTests.setOnClickListener {
            runGuidedTests()
        }

        binding.btnRunDoctor.setOnClickListener {
            runDoctor()
        }

        binding.btnCopyLog.setOnClickListener {
            copyLogToClipboard()
        }

        binding.btnRetry.setOnClickListener {
            retryTests()
        }

        binding.btnOpenTermuxHelp.setOnClickListener {
            showTermuxHelpDialog()
        }
    }

    private fun updateOverallBadge() {
        val mandatoryChecks = diagnosticChecks.filter { it.isMandatory }
        val allMandatoryPassed = mandatoryChecks.all { it.status == DiagnosticStatus.PASS }
        val failedMandatoryCount = mandatoryChecks.count { it.status == DiagnosticStatus.FAIL }
        val unknownMandatoryCount = mandatoryChecks.count { it.status == DiagnosticStatus.UNKNOWN }

        if (allMandatoryPassed) {
            binding.overallBadge.text = "READY"
            binding.overallBadge.setTextColor(0xFF39D98A.toInt())
            binding.overallBadge.setBackgroundColor(0xFF1E3A24.toInt())
        } else if (failedMandatoryCount > 0) {
            binding.overallBadge.text = "NOT READY ($failedMandatoryCount FAILS)"
            binding.overallBadge.setTextColor(0xFFFF4D4D.toInt())
            binding.overallBadge.setBackgroundColor(0xFF3D1818.toInt())
        } else {
            binding.overallBadge.text = "UNVERIFIED ($unknownMandatoryCount PENDING)"
            binding.overallBadge.setTextColor(0xFFFF6B00.toInt())
            binding.overallBadge.setBackgroundColor(0xFF2E2010.toInt())
        }
    }

    private fun renderPreflight() {
        binding.preflightContainer.removeAllViews()

        for (check in diagnosticChecks) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 6, 0, 6)
            }

            val tvTitle = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                val mandText = if (check.isMandatory) "[Mandatory]" else "[Optional]"
                text = "${check.name} $mandText\n${check.detail.ifBlank { check.fixInstruction }}"
                setTextColor(ContextCompat.getColor(this@DeviceVerificationActivity, R.color.z_text))
                textSize = 12f
            }
            row.addView(tvTitle)

            val tvBadge = TextView(this).apply {
                text = check.status.name
                textSize = 11f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(10, 4, 10, 4)
                when (check.status) {
                    DiagnosticStatus.PASS -> {
                        setTextColor(ContextCompat.getColor(this@DeviceVerificationActivity, R.color.z_green))
                        setBackgroundColor(0xFF1E3A24.toInt())
                    }
                    DiagnosticStatus.FAIL -> {
                        setTextColor(ContextCompat.getColor(this@DeviceVerificationActivity, R.color.z_red))
                        setBackgroundColor(0xFF3D1818.toInt())
                    }
                    DiagnosticStatus.CHECKING -> {
                        setTextColor(ContextCompat.getColor(this@DeviceVerificationActivity, R.color.z_orange))
                        setBackgroundColor(0xFF3E2D12.toInt())
                    }
                    else -> {
                        setTextColor(ContextCompat.getColor(this@DeviceVerificationActivity, R.color.z_muted))
                        setBackgroundColor(0xFF222222.toInt())
                    }
                }
            }
            row.addView(tvBadge)

            binding.preflightContainer.addView(row)
        }

        updateOverallBadge()
    }

    private fun renderTestsList() {
        binding.testsContainer.removeAllViews()

        for (test in proofTests) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 6, 0, 6)
            }

            val headerRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
            }

            val timeInfo = if (test.timestamp != null) {
                " • ${dateFormat.format(Date(test.timestamp!!))}"
            } else ""

            val runInfo = if (test.durationMs != null) {
                " (${test.durationMs}ms, exit ${test.exitCode})"
            } else ""

            val tvLabel = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                text = "${test.label}$runInfo$timeInfo\nCommand: ${test.command}"
                setTextColor(ContextCompat.getColor(this@DeviceVerificationActivity, R.color.z_text))
                textSize = 12f
            }
            headerRow.addView(tvLabel)

            val tvBadge = TextView(this).apply {
                text = test.status
                textSize = 11f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(10, 4, 10, 4)
                when (test.status) {
                    "PASS" -> {
                        setTextColor(ContextCompat.getColor(this@DeviceVerificationActivity, R.color.z_green))
                        setBackgroundColor(0xFF1E3A24.toInt())
                    }
                    "RUNNING" -> {
                        setTextColor(ContextCompat.getColor(this@DeviceVerificationActivity, R.color.z_orange))
                        setBackgroundColor(0xFF3E2D12.toInt())
                    }
                    "FAIL" -> {
                        setTextColor(ContextCompat.getColor(this@DeviceVerificationActivity, R.color.z_red))
                        setBackgroundColor(0xFF3D1818.toInt())
                    }
                    else -> {
                        setTextColor(ContextCompat.getColor(this@DeviceVerificationActivity, R.color.z_muted))
                        setBackgroundColor(0xFF222222.toInt())
                    }
                }
            }
            headerRow.addView(tvBadge)
            row.addView(headerRow)

            if (test.outputSnippet.isNotBlank()) {
                val tvOutput = TextView(this).apply {
                    text = test.outputSnippet.take(240)
                    setTextColor(ContextCompat.getColor(this@DeviceVerificationActivity, R.color.z_muted))
                    textSize = 11f
                    setPadding(0, 2, 0, 0)
                }
                row.addView(tvOutput)
            }

            binding.testsContainer.addView(row)
        }
    }

    private fun runGuidedTests() {
        val prefs = getSharedPreferences("zipbug.settings", MODE_PRIVATE)
        val home = prefs.getString("termuxHome", "/data/data/com.termux/files/home")!!
        val app = application as ZipBugApp

        lifecycleScope.launch {
            binding.btnRunGuidedTests.isEnabled = false
            binding.btnRetry.isEnabled = false

            val logBuilder = StringBuilder()
            logBuilder.append("STARTING GUIDED COMMAND PROOF SEQUENCE (5 TESTS)\n")
            logBuilder.append("Timestamp: ${dateFormat.format(Date())}\n\n")

            var allPassed = true

            for (test in proofTests) {
                test.status = "RUNNING"
                test.timestamp = System.currentTimeMillis()
                renderTestsList()

                logBuilder.append(">>> DISPATCHING: ${test.label}\n")
                logBuilder.append("Tool: ${test.tool} | Command: ${test.command}\n")
                binding.proofLog.text = logBuilder.toString()

                val req = TermuxBridge.Request(
                    tool = test.tool,
                    args = test.args,
                    workDir = home,
                    label = "Proof ${test.tool}"
                )

                val sendResult = TermuxBridge.send(this@DeviceVerificationActivity, req)
                if (sendResult.isFailure) {
                    test.status = "FAIL"
                    test.outputSnippet = sendResult.exceptionOrNull()?.message ?: "Failed to dispatch"
                    logBuilder.append("DISPATCH ERROR: ${test.outputSnippet}\n\n")
                    allPassed = false
                    renderTestsList()
                    continue
                }

                val jobId = sendResult.getOrThrow()
                logBuilder.append("Dispatched jobId: $jobId. Waiting for result callback…\n")
                binding.proofLog.text = logBuilder.toString()

                // Await completion in Room DB
                val finalJob = app.database.buildJobDao().observe(jobId)
                    .filterNotNull()
                    .first { it.status != BuildJobEntity.RUNNING }

                val duration = if (finalJob.startedAt != null && finalJob.finishedAt != null) {
                    finalJob.finishedAt - finalJob.startedAt
                } else null

                test.exitCode = finalJob.exitCode
                test.durationMs = duration
                test.stdout = finalJob.stdout
                test.stderr = finalJob.stderr
                test.outputSnippet = (finalJob.stdout + "\n" + finalJob.stderr).trim()

                if (finalJob.exitCode == 0 && finalJob.status == BuildJobEntity.SUCCESS) {
                    test.status = "PASS"
                    logBuilder.append("RESULT: PASS (exit 0, duration ${duration ?: 0}ms)\n")
                    if (test.outputSnippet.isNotBlank()) {
                        logBuilder.append("STDOUT: ").append(test.outputSnippet.take(300)).append("\n")
                    }
                    syncDiagnosticFromProof(test, true)
                } else {
                    test.status = "FAIL"
                    allPassed = false
                    logBuilder.append("RESULT: FAIL (exit ${finalJob.exitCode}, error: ${finalJob.errorMessage})\n")
                    if (finalJob.stderr.isNotBlank()) {
                        logBuilder.append("STDERR: ").append(finalJob.stderr.take(300)).append("\n")
                    }
                    syncDiagnosticFromProof(test, false)
                }
                logBuilder.append("----------------------------------------\n\n")
                binding.proofLog.text = logBuilder.toString()
                renderTestsList()
                renderPreflight()
            }

            binding.btnRunGuidedTests.isEnabled = true
            binding.btnRetry.isEnabled = true
            if (allPassed) {
                Snackbar.make(binding.root, "All 5 command tests passed on device!", Snackbar.LENGTH_LONG).show()
            } else {
                Snackbar.make(binding.root, "Guided proof completed with failures. Inspect log.", Snackbar.LENGTH_LONG).show()
            }
            updateOverallBadge()
        }
    }

    private fun syncDiagnosticFromProof(test: CommandProofTest, passed: Boolean) {
        val checkId = when (test.tool) {
            "python" -> "python_available"
            "java" -> "java_available"
            "gradle" -> "gradle_available"
            "ffmpeg" -> "ffmpeg_available"
            "aapt2" -> "aapt2_available"
            else -> null
        } ?: return

        diagnosticChecks.find { it.id == checkId }?.apply {
            status = if (passed) DiagnosticStatus.PASS else DiagnosticStatus.FAIL
            detail = test.outputSnippet.lines().firstOrNull() ?: (if (passed) "Verified" else "Failed")
        }

        if (test.tool == "java" && passed) {
            diagnosticChecks.find { it.id == "java_version" }?.apply {
                val has17 = test.outputSnippet.contains("17") || test.outputSnippet.contains("18") ||
                        test.outputSnippet.contains("19") || test.outputSnippet.contains("21")
                status = if (has17) DiagnosticStatus.PASS else DiagnosticStatus.FAIL
                detail = test.outputSnippet.lines().firstOrNull() ?: ""
            }
        }
        if (test.tool == "gradle" && passed) {
            diagnosticChecks.find { it.id == "gradle_version" }?.apply {
                status = DiagnosticStatus.PASS
                detail = test.outputSnippet.lines().firstOrNull() ?: ""
            }
        }
    }

    private fun runDoctor() {
        val prefs = getSharedPreferences("zipbug.settings", MODE_PRIVATE)
        val home = prefs.getString("termuxHome", "/data/data/com.termux/files/home")!!
        val app = application as ZipBugApp

        lifecycleScope.launch {
            binding.btnRunDoctor.isEnabled = false
            binding.proofLog.text = "Dispatching 15-check Engine Doctor…\n"

            val req = EngineDoctor.request(home)
            val sendResult = TermuxBridge.send(this@DeviceVerificationActivity, req)
            if (sendResult.isFailure) {
                binding.btnRunDoctor.isEnabled = true
                binding.proofLog.text = "Engine Doctor dispatch failed:\n${sendResult.exceptionOrNull()?.message}"
                return@launch
            }

            val jobId = sendResult.getOrThrow()
            binding.proofLog.text = "Dispatched Engine Doctor (jobId: $jobId). Waiting for Python diagnostics callback…\n"

            val finalJob = app.database.buildJobDao().observe(jobId)
                .filterNotNull()
                .first { it.status != BuildJobEntity.RUNNING }

            binding.btnRunDoctor.isEnabled = true

            if (finalJob.status == BuildJobEntity.SUCCESS && finalJob.exitCode == 0) {
                val report = EngineDiagnostics.parseDiagnosticOutput(finalJob.stdout, diagnosticChecks)
                renderPreflight()
                binding.proofLog.text = buildString {
                    append("ENGINE DOCTOR REPORT:\n")
                    append(report.summary).append("\n\n")
                    append("Raw stdout:\n").append(finalJob.stdout)
                }
                Snackbar.make(binding.root, report.summary, Snackbar.LENGTH_LONG).show()
            } else {
                binding.proofLog.text = buildString {
                    append("ENGINE DOCTOR FAILED (exit ${finalJob.exitCode})\n")
                    append("stderr:\n").append(finalJob.stderr).append("\n")
                    append("error:\n").append(finalJob.errorMessage)
                }
                Snackbar.make(binding.root, "Engine Doctor failed: ${finalJob.errorMessage}", Snackbar.LENGTH_LONG).show()
            }
            updateOverallBadge()
        }
    }

    private fun copyLogToClipboard() {
        val text = buildString {
            append("=== ZIP_BUG ANTIGRAVITY DEVICE VERIFICATION LOG ===\n")
            append("Overall: ").append(binding.overallBadge.text).append("\n")
            append("Timestamp: ").append(dateFormat.format(Date())).append("\n\n")
            append("--- PREFLIGHT CHECKS ---\n")
            diagnosticChecks.forEach {
                append("[${it.status}] ${it.name} - ${it.detail}\n")
            }
            append("\n--- COMMAND PROOFS ---\n")
            proofTests.forEach {
                append("[${it.status}] ${it.label} (cmd: ${it.command}, exit: ${it.exitCode}, dur: ${it.durationMs}ms)\n")
                if (it.outputSnippet.isNotBlank()) {
                    append("Output: ").append(it.outputSnippet.take(200)).append("\n")
                }
            }
            append("\n--- LOG CONSOLE ---\n")
            append(binding.proofLog.text)
        }

        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("ZipBug Device Verification Log", text))
        Snackbar.make(binding.root, "Verification log copied to clipboard", Snackbar.LENGTH_SHORT).show()
    }

    private fun retryTests() {
        proofTests.forEach {
            it.status = "UNKNOWN"
            it.exitCode = null
            it.durationMs = null
            it.outputSnippet = ""
            it.stdout = ""
            it.stderr = ""
        }
        renderTestsList()
        runGuidedTests()
    }

    private fun showTermuxHelpDialog() {
        val message = """
        TERMUX CONFIGURATION CHECKLIST:

        1. Enable External App Commands:
           mkdir -p ~/.termux
           echo "allow-external-apps=true" >> ~/.termux/termux.properties
           termux-reload-settings

        2. Android SDK Setup (~/.bashrc):
           export ANDROID_HOME=${'$'}HOME/android-sdk
           export ANDROID_SDK_ROOT=${'$'}ANDROID_HOME
           export PATH=${'$'}PATH:${'$'}ANDROID_HOME/platform-tools

        3. Install Required Native Tools:
           pkg update -y
           pkg install -y python openjdk-17 gradle ffmpeg aapt2

        4. Install Required Python Packages:
           pip install --upgrade yt-dlp edge-tts

        5. Grant Storage Permission:
           termux-setup-storage

        Project Clone Location:
        ~/OpenDots/Zip_Bug_Antigravity
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle("Termux Environment Setup Guide")
            .setMessage(message)
            .setPositiveButton("Copy Setup Script") { _, _ ->
                val script = """
                mkdir -p ~/.termux && echo "allow-external-apps=true" >> ~/.termux/termux.properties && termux-reload-settings
                pkg update -y && pkg install -y python openjdk-17 gradle ffmpeg aapt2
                pip install --upgrade yt-dlp edge-tts
                termux-setup-storage
                """.trimIndent()
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("Termux Setup Commands", script))
                Snackbar.make(binding.root, "Setup commands copied to clipboard", Snackbar.LENGTH_SHORT).show()
            }
            .setNegativeButton("Close", null)
            .show()
    }
}
