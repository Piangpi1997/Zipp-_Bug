package com.zipbug.base.ui

import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.zipbug.base.R
import com.zipbug.base.ZipBugApp
import com.zipbug.base.data.BuildJobEntity
import com.zipbug.base.databinding.ActivityDeviceVerificationBinding
import com.zipbug.base.termux.EngineDiagnostics
import com.zipbug.base.termux.EngineDoctor
import com.zipbug.base.termux.TermuxBridge
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

data class CommandProofTest(
    val id: String,
    val label: String,
    val tool: String,
    val args: List<String>,
    var status: String = "PENDING", // PENDING, RUNNING, PASS, FAIL
    var exitCode: Int? = null,
    var durationMs: Long? = null,
    var outputSnippet: String = ""
)

class DeviceVerificationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDeviceVerificationBinding

    private val proofTests = mutableListOf(
        CommandProofTest("test_python", "TEST 1: Python", "python", listOf("--version")),
        CommandProofTest("test_java", "TEST 2: Java", "java", listOf("-version")),
        CommandProofTest("test_gradle", "TEST 3: Gradle", "gradle", listOf("--version")),
        CommandProofTest("test_ffmpeg", "TEST 4: FFmpeg", "ffmpeg", listOf("-version")),
        CommandProofTest("test_aapt2", "TEST 5: aapt2", "aapt2", listOf("version"))
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDeviceVerificationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        renderPreflight()
        renderTestsList()

        binding.btnRunGuidedTests.setOnClickListener {
            runGuidedTests()
        }

        binding.btnRunDoctor.setOnClickListener {
            runDoctor()
        }
    }

    private fun renderPreflight() {
        binding.preflightContainer.removeAllViews()

        val pm = packageManager
        val isTermuxInstalled = runCatching {
            pm.getPackageInfo("com.termux", 0)
            true
        }.getOrDefault(false)

        val hasRunCommand = ContextCompat.checkSelfPermission(
            this,
            TermuxBridge.PERMISSION_RUN_COMMAND
        ) == PackageManager.PERMISSION_GRANTED

        val prefs = getSharedPreferences("zipbug.settings", MODE_PRIVATE)
        val homePath = prefs.getString("termuxHome", "/data/data/com.termux/files/home")!!
        val projectRoot = prefs.getString("projectRoot", "$homePath/OpenDots/Zip_Bug_Antigravity")!!

        val items = listOf(
            Triple("Termux Package Installed", if (isTermuxInstalled) "PASS" else "FAIL", if (isTermuxInstalled) "com.termux detected" else "Install Termux from F-Droid or GitHub"),
            Triple("RUN_COMMAND Permission", if (hasRunCommand) "PASS" else "FAIL", if (hasRunCommand) "Permission granted" else "Grant in Android App Permissions"),
            Triple("allow-external-apps config", "AUDIT REQUIRED", "Ensure ~/.termux/termux.properties has allow-external-apps=true"),
            Triple("Termux Home Path", "CONFIGURED", homePath),
            Triple("Project Root Path", "CONFIGURED", projectRoot)
        )

        for ((title, status, detail) in items) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 4, 0, 4)
            }

            val tvTitle = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                text = "$title\n$detail"
                setTextColor(ContextCompat.getColor(this@DeviceVerificationActivity, R.color.z_text))
                textSize = 12f
            }
            row.addView(tvTitle)

            val tvBadge = TextView(this).apply {
                text = status
                textSize = 11f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(10, 4, 10, 4)
                when (status) {
                    "PASS" -> {
                        setTextColor(ContextCompat.getColor(this@DeviceVerificationActivity, R.color.z_green))
                        setBackgroundColor(0xFF1E3A24.toInt())
                    }
                    "FAIL" -> {
                        setTextColor(ContextCompat.getColor(this@DeviceVerificationActivity, R.color.z_red))
                        setBackgroundColor(0xFF3D1818.toInt())
                    }
                    else -> {
                        setTextColor(ContextCompat.getColor(this@DeviceVerificationActivity, R.color.z_orange))
                        setBackgroundColor(0xFF3E2D12.toInt())
                    }
                }
            }
            row.addView(tvBadge)

            binding.preflightContainer.addView(row)
        }
    }

    private fun renderTestsList() {
        binding.testsContainer.removeAllViews()

        for (test in proofTests) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 4, 0, 4)
            }

            val tvLabel = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                val extra = if (test.durationMs != null) " (${test.durationMs}ms, exit ${test.exitCode})" else ""
                text = "${test.label}$extra\ncmd: ${test.tool} ${test.args.joinToString(" ")}"
                setTextColor(ContextCompat.getColor(this@DeviceVerificationActivity, R.color.z_text))
                textSize = 12f
            }
            row.addView(tvLabel)

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
            row.addView(tvBadge)

            binding.testsContainer.addView(row)
        }
    }

    private fun runGuidedTests() {
        val prefs = getSharedPreferences("zipbug.settings", MODE_PRIVATE)
        val home = prefs.getString("termuxHome", "/data/data/com.termux/files/home")!!
        val app = application as ZipBugApp

        lifecycleScope.launch {
            binding.btnRunGuidedTests.isEnabled = false
            binding.overallBadge.text = "TESTING..."
            binding.overallBadge.setTextColor(0xFFFF6B00.toInt())

            val logBuilder = StringBuilder()
            logBuilder.append("STARTING GUIDED COMMAND PROOF SEQUENCE (5 TESTS)\n\n")

            var allPassed = true

            for (test in proofTests) {
                test.status = "RUNNING"
                renderTestsList()

                logBuilder.append(">>> DISPATCHING: ${test.label}\n")
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
                    logBuilder.append("ERROR: ${test.outputSnippet}\n\n")
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
                test.outputSnippet = (finalJob.stdout + "\n" + finalJob.stderr).trim()

                if (finalJob.exitCode == 0 && finalJob.status == BuildJobEntity.SUCCESS) {
                    test.status = "PASS"
                    logBuilder.append("RESULT: PASS (exit 0, duration ${duration ?: 0}ms)\n")
                    if (test.outputSnippet.isNotBlank()) {
                        logBuilder.append(test.outputSnippet.take(200)).append("\n")
                    }
                } else {
                    test.status = "FAIL"
                    allPassed = false
                    logBuilder.append("RESULT: FAIL (exit ${finalJob.exitCode}, error ${finalJob.errorMessage})\n")
                    if (finalJob.stderr.isNotBlank()) {
                        logBuilder.append("STDERR: ").append(finalJob.stderr.take(200)).append("\n")
                    }
                }
                logBuilder.append("----------------------------------------\n\n")
                binding.proofLog.text = logBuilder.toString()
                renderTestsList()
            }

            binding.btnRunGuidedTests.isEnabled = true
            if (allPassed) {
                binding.overallBadge.text = "ALL 5 TESTS PASSED"
                binding.overallBadge.setTextColor(0xFF39D98A.toInt())
                Snackbar.make(binding.root, "All 5 command tests passed on device!", Snackbar.LENGTH_LONG).show()
            } else {
                binding.overallBadge.text = "SOME TESTS FAILED"
                binding.overallBadge.setTextColor(0xFFFF4D4D.toInt())
                Snackbar.make(binding.root, "Guided proof completed with failures. Inspect log.", Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private fun runDoctor() {
        val prefs = getSharedPreferences("zipbug.settings", MODE_PRIVATE)
        val home = prefs.getString("termuxHome", "/data/data/com.termux/files/home")!!

        lifecycleScope.launch {
            val req = EngineDoctor.request(home)
            TermuxBridge.send(this@DeviceVerificationActivity, req).onSuccess { jobId ->
                binding.proofLog.text = "Dispatched 15-check Engine Doctor (jobId: $jobId).\nInspect terminal or wait for results callback."
                Snackbar.make(binding.root, "Engine Doctor dispatched", Snackbar.LENGTH_SHORT).show()
            }.onFailure { err ->
                binding.proofLog.text = "Engine Doctor dispatch failed:\n${err.message}"
            }
        }
    }
}
