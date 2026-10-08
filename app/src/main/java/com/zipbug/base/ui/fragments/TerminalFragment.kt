package com.zipbug.base.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.zipbug.base.ZipBugApp
import com.zipbug.base.data.BuildJobEntity
import com.zipbug.base.databinding.FragmentTerminalBinding
import com.zipbug.base.termux.CommandLineParser
import com.zipbug.base.termux.DiagnosticStatus
import com.zipbug.base.termux.EngineDiagnostics
import com.zipbug.base.termux.EngineDoctor
import com.zipbug.base.termux.TermuxBridge
import kotlinx.coroutines.launch

class TerminalFragment : Fragment() {
    private var _binding: FragmentTerminalBinding? = null
    private val binding get() = _binding!!
    private var pendingPermissionAction: (() -> Unit)? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val action = pendingPermissionAction
        pendingPermissionAction = null

        if (granted) {
            action?.invoke()
        } else if (_binding != null) {
            binding.engineState.text = "ENGINE • PERMISSION REQUIRED"
            binding.output.text =
                "Zip_Bug needs Android permission:\n" +
                    "Run commands in Termux environment\n\n" +
                    "Open Android Settings → Apps → Zip_Bug Studio → " +
                    "Permissions → Additional permissions → " +
                    "Run commands in Termux environment → Allow."
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTerminalBinding.inflate(
            inflater,
            container,
            false
        )
        return binding.root
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        binding.run.setOnClickListener {
            withTermuxPermission {
                runCommand(binding.command.text.toString())
            }
        }

        // Test Panel
        binding.testPython.setOnClickListener {
            binding.command.setText("/termux python --version")
            withTermuxPermission { runCommand("/termux python --version") }
        }

        binding.testJava.setOnClickListener {
            binding.command.setText("/termux java -version")
            withTermuxPermission { runCommand("/termux java -version") }
        }

        binding.testGradle.setOnClickListener {
            binding.command.setText("/termux gradle --version")
            withTermuxPermission { runCommand("/termux gradle --version") }
        }

        binding.testFfmpeg.setOnClickListener {
            binding.command.setText("/termux ffmpeg -version")
            withTermuxPermission { runCommand("/termux ffmpeg -version") }
        }

        binding.testAapt2.setOnClickListener {
            binding.command.setText("/termux aapt2 version")
            withTermuxPermission { runCommand("/termux aapt2 version") }
        }

        binding.buildApk.setOnClickListener {
            binding.command.setText("/apkbuilder")
            withTermuxPermission {
                runCommand("/apkbuilder")
            }
        }

        binding.doctor.setOnClickListener {
            withTermuxPermission {
                runDoctor()
            }
        }

        observeLatestJob()
    }

    private fun withTermuxPermission(
        action: () -> Unit
    ) {
        if (TermuxBridge.hasPermission(requireContext())) {
            action()
        } else {
            pendingPermissionAction = action
            permissionLauncher.launch(
                TermuxBridge.PERMISSION_RUN_COMMAND
            )
        }
    }

    private fun observeLatestJob() {
        val app = requireActivity().application as ZipBugApp

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(
                Lifecycle.State.STARTED
            ) {
                app.database.buildJobDao()
                    .observeLatest()
                    .collect { job ->
                        if (job != null && _binding != null) {
                            render(job)
                        }
                    }
            }
        }
    }

    private fun render(job: BuildJobEntity) {
        val exit = job.exitCode?.let {
            "\nexitCode: $it"
        }.orEmpty()

        val err = job.errorCode?.let {
            "\ntermuxError: $it"
        }.orEmpty()

        val duration = if (job.startedAt != null && job.finishedAt != null) {
            "\nduration: ${job.finishedAt - job.startedAt}ms"
        } else {
            ""
        }

        binding.engineState.text =
            if (job.tool == "cp" && job.status == BuildJobEntity.SUCCESS) {
                "APK EXPORTED • COPY EXIT 0"
            } else {
                "ENGINE • ${job.status} • ${job.tool}"
            }

        binding.output.text = buildString {
            append("job: ${job.id}\n")
            append("tool: ${job.tool}\n")
            append("workdir: ${job.workDir}\n")
            append("status: ${job.status}")
            append(exit)
            append(err)
            append(duration)
            append("\ntimestamp: ${job.createdAt}")

            if (job.stdout.isNotBlank()) {
                append("\n\nSTDOUT\n")
                append(job.stdout)
            }

            if (job.stderr.isNotBlank()) {
                append("\n\nSTDERR\n")
                append(job.stderr)
            }

            if (job.errorMessage.isNotBlank()) {
                append("\n\nERROR\n")
                append(job.errorMessage)
            }
        }
    }

    private fun runDoctor() {
        val home = requireContext()
            .getSharedPreferences("zipbug.settings", 0)
            .getString(
                "termuxHome",
                "/data/data/com.termux/files/home"
            )!!

        val checks = EngineDiagnostics.createInitialChecks()
        EngineDiagnostics.evaluateLocalChecks(requireContext(), checks)

        binding.engineState.text = "ENGINE • DIAGNOSTICS DISPATCHING"
        binding.output.text = "Evaluating local checks & dispatching Termux diagnostics…"

        viewLifecycleOwner.lifecycleScope.launch {
            val req = EngineDoctor.request(home)
            TermuxBridge.send(requireContext(), req).onSuccess { jobId ->
                binding.output.text = buildString {
                    append("Dispatched Diagnostics Job: $jobId\n\n")
                    append("LOCAL PRE-FLIGHT CHECKS:\n")
                    checks.take(2).forEach { c ->
                        append("[${c.status}] ${c.name}: ${c.detail}\n")
                        if (c.status == DiagnosticStatus.FAIL) {
                            append("  Fix: ${c.fixInstruction}\n")
                        }
                    }
                    append("\nWaiting for Termux job callback to evaluate remaining 13 checks…")
                }
            }.onFailure { err ->
                binding.engineState.text = "ENGINE • DIAGNOSTICS FAILED"
                binding.output.text = "Diagnostics dispatch failed:\n${err.message}"
            }
        }
    }

    private fun runCommand(raw: String) {
        val parts = runCatching {
            CommandLineParser.parse(raw)
        }.getOrElse {
            binding.output.text = "Parse error: ${it.message}"
            return
        }

        if (parts.isEmpty()) return

        val prefs = requireContext()
            .getSharedPreferences("zipbug.settings", 0)

        val home = prefs.getString(
            "termuxHome",
            "/data/data/com.termux/files/home"
        )!!

        val savedProjectRoot = prefs.getString(
            "projectRoot",
            "$home/OpenDots/Zip_Bug_Antigravity"
        )!!

        val projectRoot =
            if (savedProjectRoot == "$home/OpenDots/Zip_Bug") {
                "$home/OpenDots/Zip_Bug_Antigravity"
            } else {
                savedProjectRoot
            }

        val request = when (parts[0]) {
            "/termux" -> {
                if (parts.size < 2) {
                    binding.output.text =
                        "Use /termux <approved-tool> [args]"
                    return
                }

                TermuxBridge.Request(
                    tool = parts[1],
                    args = parts.drop(2),
                    workDir = home,
                    label = "Zip_Bug /termux ${parts[1]}"
                )
            }

            "/apkbuilder" -> {
                val workDir = parts.getOrNull(1)
                    ?.takeIf { it.startsWith("/") }
                    ?: projectRoot

                TermuxBridge.Request(
                    tool = "gradle",
                    args = listOf(
                        "--no-daemon",
                        "assembleDebug"
                    ),
                    workDir = workDir,
                    label = "Zip_Bug APK Builder"
                )
            }

            else -> {
                binding.output.text =
                    "Supported now: /termux, /apkbuilder"
                return
            }
        }

        binding.engineState.text = "ENGINE • QUEUING"

        viewLifecycleOwner.lifecycleScope.launch {
            val result = TermuxBridge.send(
                requireContext(),
                request
            )

            result.onSuccess { id ->
                binding.output.text =
                    "Queued real Termux job:\n$id\n\n" +
                    "Waiting for Termux result callback…"
            }.onFailure {
                binding.output.text =
                    "Command failed before execution:\n${it.message}"
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
