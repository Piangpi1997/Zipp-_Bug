package com.zipbug.base.ui.fragments

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.zipbug.base.ZipBugApp
import com.zipbug.base.ai.AiMode
import com.zipbug.base.command.CommandRegistry
import com.zipbug.base.build.BuildArtifactProof
import com.zipbug.base.build.BuildWorkflowParser
import com.zipbug.base.data.BuildJobEntity
import com.zipbug.base.databinding.FragmentTerminalBinding
import com.zipbug.base.termux.CommandLineParser
import com.zipbug.base.termux.DiagnosticStatus
import com.zipbug.base.termux.EngineDiagnostics
import com.zipbug.base.termux.EngineDoctor
import com.zipbug.base.termux.TermuxBridge
import com.zipbug.base.ui.AppCreatorActivity
import com.zipbug.base.ui.CommandHelpActivity
import com.zipbug.base.ui.MainActivity
import com.zipbug.base.ui.MediaRecapActivity
import com.zipbug.base.ui.TtsStudioActivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import java.util.UUID

class TerminalFragment : Fragment() {
    companion object {
        private const val ARG_INITIAL_COMMAND = "arg_initial_command"

        fun newInstance(command: String = ""): TerminalFragment =
            TerminalFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_INITIAL_COMMAND, command)
                }
            }
    }

    private var _binding: FragmentTerminalBinding? = null
    private val binding get() = _binding!!
    private var pendingPermissionAction: (() -> Unit)? = null
    private var lastFailedJob: BuildJobEntity? = null
    private var activeBuildJobId: String? = null
    private var activeBuildCancelPath: String? = null
    private var activeBuildWorkDir: String? = null
    private var buildObserver: Job? = null
    private var lastArtifact: BuildArtifactProof? = null

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
            runCommand(binding.command.text.toString())
        }

        arguments?.getString(ARG_INITIAL_COMMAND)
            ?.takeIf { it.isNotBlank() }
            ?.let { binding.command.setText(it) }

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
            binding.command.setText("/apkbuilder --debug")
            runCommand("/apkbuilder --debug")
        }

        binding.releaseApk.setOnClickListener {
            showReleaseSigningBoundary()
        }

        binding.cancelBuild.setOnClickListener {
            requestCancelBuild()
        }

        binding.openArtifact.setOnClickListener {
            lastArtifact?.let { openApk(it) }
        }

        binding.shareArtifact.setOnClickListener {
            lastArtifact?.let { shareApk(it) }
        }

        binding.btnDeviceProof.setOnClickListener {
            startActivity(android.content.Intent(requireContext(), com.zipbug.base.ui.DeviceVerificationActivity::class.java))
        }

        binding.btnFixErrorWithAi.setOnClickListener {
            val job = lastFailedJob ?: return@setOnClickListener
            val rawLog = (job.stderr + "\n" + job.stdout + "\n" + job.errorMessage).trim()
            val errors = com.zipbug.base.repair.CompilerErrorParser.parseErrors(rawLog)
            val projectName = java.io.File(job.workDir).name.ifBlank { "Project" }
            val prompt = if (errors.isNotEmpty()) {
                com.zipbug.base.repair.CompilerErrorParser.buildAiRepairPrompt(projectName, errors, rawLog.take(2000))
            } else {
                "Build job '${job.tool}' failed with exitCode ${job.exitCode}.\n\nFailure Log:\n${rawLog.take(2000)}\n\nPlease analyze the root cause and propose a targeted patch."
            }
            (requireActivity() as? com.zipbug.base.ui.MainActivity)?.navigateToAiWithPrompt(
                prompt,
                com.zipbug.base.ai.AiMode.FIX_ERROR
            )
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
                        if (job == null || _binding == null) {
                            return@collect
                        }

                        val isCancelTokenJob =
                            job.tool == "cp" &&
                                job.argsJson.contains(
                                    ".zipbug-cancel-"
                                )

                        val activeId = activeBuildJobId
                        if (
                            !isCancelTokenJob &&
                            (
                                activeId == null ||
                                    activeId == job.id
                                )
                        ) {
                            render(job)
                        }

                        if (
                            job.status == BuildJobEntity.SUCCESS &&
                            (
                                isControlledBuildJob(job) ||
                                    job.tool == "gradle" ||
                                    job.argsJson.contains("assemble")
                                )
                        ) {
                            launch {
                                runCatching {
                                    com.zipbug.base.artifact.ApkArtifactScanner
                                        .scanAndPersist(
                                            requireContext()
                                        )
                                }
                            }
                        }
                    }
            }
        }
    }

    private fun observeBuildJob(jobId: String) {
        buildObserver?.cancel()
        val app = requireActivity().application as ZipBugApp

        buildObserver = viewLifecycleOwner.lifecycleScope.launch {
            app.database.buildJobDao()
                .observe(jobId)
                .collect { job ->
                    if (job == null || _binding == null) {
                        return@collect
                    }

                    render(job)

                    if (
                        job.status != BuildJobEntity.QUEUED &&
                        job.status != BuildJobEntity.RUNNING
                    ) {
                        activeBuildJobId = null
                        activeBuildCancelPath = null
                        activeBuildWorkDir = null
                        binding.cancelBuild.visibility = View.GONE
                        binding.cancelBuild.isEnabled = true

                        if (
                            job.status == BuildJobEntity.SUCCESS
                        ) {
                            launch {
                                runCatching {
                                    com.zipbug.base.artifact.ApkArtifactScanner
                                        .scanAndPersist(
                                            requireContext()
                                        )
                                }
                            }
                        }
                    }
                }
        }
    }

    private fun render(job: BuildJobEntity) {
        if (isControlledBuildJob(job)) {
            renderControlledBuild(job)
            return
        }

        binding.cancelBuild.visibility = View.GONE
        binding.artifactActions.visibility = View.GONE

        val isFailed =
            job.status == BuildJobEntity.FAILED ||
                (
                    job.exitCode != null &&
                        job.exitCode != 0
                    )

        if (isFailed) {
            lastFailedJob = job
            binding.btnFixErrorWithAi.visibility =
                View.VISIBLE
        } else {
            binding.btnFixErrorWithAi.visibility =
                View.GONE
        }

        val exit = job.exitCode?.let {
            "\nexitCode: $it"
        }.orEmpty()

        val err = job.errorCode?.let {
            "\ntermuxError: $it"
        }.orEmpty()

        val duration =
            if (
                job.startedAt != null &&
                job.finishedAt != null
            ) {
                "\nduration: ${job.finishedAt - job.startedAt}ms"
            } else {
                ""
            }

        binding.engineState.text =
            if (
                job.tool == "cp" &&
                job.status == BuildJobEntity.SUCCESS
            ) {
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

    private fun renderControlledBuild(
        job: BuildJobEntity
    ) {
        val snapshot =
            BuildWorkflowParser.parse(job.stdout)

        val running =
            job.status == BuildJobEntity.QUEUED ||
                job.status == BuildJobEntity.RUNNING

        binding.cancelBuild.visibility =
            if (running) View.VISIBLE else View.GONE

        binding.btnFixErrorWithAi.visibility =
            if (
                job.status == BuildJobEntity.FAILED ||
                (
                    job.exitCode != null &&
                        job.exitCode != 0
                    )
            ) {
                lastFailedJob = job
                View.VISIBLE
            } else {
                View.GONE
            }

        val validation =
            snapshot.stage("VALIDATION")
        val dependencies =
            snapshot.stage("DEPENDENCIES")
        val build =
            snapshot.stage("BUILD")
        val packaging =
            snapshot.stage("PACKAGING")
        val signing =
            snapshot.stage("SIGNING")

        binding.buildStages.text = buildString {
            append("BUILD WORKFLOW • DEBUG\n")
            appendStageLine(
                "Validation",
                validation,
                running
            )
            appendStageLine(
                "Dependencies",
                dependencies,
                running
            )
            appendStageLine(
                "Build",
                build,
                running
            )
            appendStageLine(
                "Packaging",
                packaging,
                running
            )

            if (signing != null) {
                append("Release signing: ")
                append(signing.state)
                append(" — ")
                append(signing.detail)
                append("\n")
            } else {
                append(
                    "Release signing: NOT TOUCHED " +
                        "(explicit configuration required)\n"
                )
            }
        }

        val timedOut =
            job.exitCode == 124 ||
                build?.state == "TIMEOUT"
        val cancelled =
            job.exitCode == 130 ||
                build?.state == "CANCELLED"

        val artifact = snapshot.artifact
        val proofComplete =
            job.status == BuildJobEntity.SUCCESS &&
                job.exitCode == 0 &&
                snapshot.result == "SUCCESS" &&
                packaging?.state == "PASS" &&
                artifact != null

        binding.engineState.text = when {
            running ->
                "BUILD • RUNNING • DEBUG"

            timedOut ->
                "BUILD • TIMEOUT"

            cancelled ->
                "BUILD • CANCELLED"

            proofComplete ->
                "BUILD • SUCCESS • APK VALIDATED"

            job.status == BuildJobEntity.SUCCESS ->
                "BUILD • INCOMPLETE ARTIFACT PROOF"

            else ->
                "BUILD • FAILED • exit ${job.exitCode ?: "?"}"
        }

        if (proofComplete && artifact != null) {
            lastArtifact = artifact
            verifyArtifactForActions(artifact)
        } else {
            lastArtifact = null
            binding.artifactActions.visibility =
                View.GONE
        }

        binding.output.text = buildString {
            append("job: ${job.id}\n")
            append("mode: DEBUG\n")
            append("workdir: ${job.workDir}\n")
            append("status: ${job.status}\n")
            append("exitCode: ${job.exitCode ?: "pending"}\n")

            if (artifact != null) {
                append("\nARTIFACT PROOF\n")
                append("name: ")
                    .append(File(artifact.path).name)
                    .append("\n")
                append("size: ")
                    .append(formatBytes(artifact.sizeBytes))
                    .append(" (")
                    .append(artifact.sizeBytes)
                    .append(" bytes)\n")
                append("path: ")
                    .append(artifact.path)
                    .append("\n")
                append("sha256: ")
                    .append(artifact.sha256)
                    .append("\n")
                append("signature verify: ")
                    .append(
                        artifact.signatureVerification
                    )
                    .append("\n")
            }

            if (
                job.status == BuildJobEntity.FAILED ||
                timedOut ||
                cancelled
            ) {
                append("\nSAFE NEXT STEP\n")
                append(
                    buildSafeNextStep(
                        validation,
                        dependencies,
                        build,
                        packaging,
                        timedOut,
                        cancelled
                    )
                )
                append("\n")
            }

            if (job.stdout.isNotBlank()) {
                append("\nSTDOUT\n")
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

    private fun StringBuilder.appendStageLine(
        label: String,
        stage: com.zipbug.base.build.BuildStageStatus?,
        running: Boolean
    ) {
        append(label)
        append(": ")

        if (stage != null) {
            append(stage.state)
            if (stage.detail.isNotBlank()) {
                append(" — ")
                append(stage.detail)
            }
        } else if (running) {
            append("WAITING / RUNNING")
        } else {
            append("NOT REPORTED")
        }

        append("\n")
    }

    private fun buildSafeNextStep(
        validation: com.zipbug.base.build.BuildStageStatus?,
        dependencies: com.zipbug.base.build.BuildStageStatus?,
        build: com.zipbug.base.build.BuildStageStatus?,
        packaging: com.zipbug.base.build.BuildStageStatus?,
        timedOut: Boolean,
        cancelled: Boolean
    ): String = when {
        cancelled ->
            "Build cancellation was requested. Review the log before starting another build."

        timedOut ->
            "The controlled 20-minute timeout was reached. Check for a stalled Gradle dependency or reduce the project workload before retrying."

        validation?.state == "FAIL" ->
            "Check the configured project root and required Gradle files, then retry."

        dependencies?.state == "FAIL" ->
            "Run Device Proof / Engine Doctor and fix the reported Java, SDK, Gradle, or aapt2 dependency."

        build?.state == "FAIL" ->
            "Use Fix Error with AI with this real stderr/stdout, or correct the Gradle error manually."

        packaging?.state == "FAIL" ->
            "Check shared-storage access, APK output path, and APK validation tools before retrying."

        else ->
            "Inspect stdout/stderr and retry only after the reported cause is fixed."
    }

    private fun isControlledBuildJob(
        job: BuildJobEntity
    ): Boolean =
        job.tool == "bash" &&
            job.argsJson.contains(
                "scripts/build-termux.sh"
            )

    private fun showReleaseSigningBoundary() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Release signing is separate")
            .setMessage(
                "Debug APK builds use /apkbuilder --debug.\n\n" +
                    "Zip_Bug will not create, overwrite, or guess release keystores or passwords. " +
                    "A release build must use signing credentials that you explicitly configure and review in the Android project first."
            )
            .setPositiveButton("Close", null)
            .show()

        binding.buildStages.text =
            "BUILD WORKFLOW • RELEASE\n" +
                "Validation: NOT STARTED\n" +
                "Dependencies: NOT STARTED\n" +
                "Build: NOT STARTED\n" +
                "Packaging: NOT STARTED\n" +
                "Release signing: BLOCKED until explicitly configured"

        binding.output.text =
            "RELEASE SIGNING • NOT EXECUTED\n\n" +
                "No keystore was created or overwritten.\n" +
                "Configure signingConfig explicitly in the project before adding a controlled release workflow."
    }

    private fun requestCancelBuild() {
        val cancelPath = activeBuildCancelPath
        val workDir = activeBuildWorkDir

        if (
            activeBuildJobId == null ||
            cancelPath.isNullOrBlank() ||
            workDir.isNullOrBlank()
        ) {
            Snackbar.make(
                binding.root,
                "No controlled build is currently running.",
                Snackbar.LENGTH_SHORT
            ).show()
            return
        }

        binding.cancelBuild.isEnabled = false
        binding.engineState.text =
            "BUILD • CANCELLATION REQUESTED"

        viewLifecycleOwner.lifecycleScope.launch {
            TermuxBridge.send(
                requireContext(),
                TermuxBridge.Request(
                    tool = "cp",
                    args = listOf(
                        "/dev/null",
                        cancelPath
                    ),
                    workDir = workDir,
                    label = "Zip_Bug Build Cancel Token"
                )
            ).onSuccess {
                Snackbar.make(
                    binding.root,
                    "Cancellation token sent. Waiting for the controlled build script to stop Gradle.",
                    Snackbar.LENGTH_LONG
                ).show()
            }.onFailure { error ->
                binding.cancelBuild.isEnabled = true
                Snackbar.make(
                    binding.root,
                    "Cancel request failed: ${error.message}",
                    Snackbar.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun verifyArtifactForActions(
        artifact: BuildArtifactProof
    ) {
        val file = File(artifact.path)

        if (!file.isFile) {
            binding.artifactActions.visibility =
                View.GONE
            return
        }

        val inspected =
            com.zipbug.base.artifact.ApkArtifactInspector
                .inspect(
                    requireContext(),
                    file
                )

        inspected.onSuccess { metadata ->
            val matchesProof =
                metadata.sizeBytes == artifact.sizeBytes &&
                    metadata.sha256.equals(
                        artifact.sha256,
                        ignoreCase = true
                    )

            binding.artifactActions.visibility =
                if (matchesProof) {
                    View.VISIBLE
                } else {
                    View.GONE
                }

            if (!matchesProof) {
                binding.engineState.text =
                    "BUILD • ARTIFACT VERIFY FAILED"
            }
        }.onFailure {
            binding.artifactActions.visibility =
                View.GONE
        }
    }

    private fun openApk(
        artifact: BuildArtifactProof
    ) {
        val file = File(artifact.path)

        runCatching {
            require(file.isFile) {
                "APK is no longer accessible at ${artifact.path}"
            }

            val uri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                file
            )

            startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(
                        uri,
                        "application/vnd.android.package-archive"
                    )
                    addFlags(
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
            )
        }.onFailure {
            Snackbar.make(
                binding.root,
                "Unable to open APK: ${it.message}",
                Snackbar.LENGTH_LONG
            ).show()
        }
    }

    private fun shareApk(
        artifact: BuildArtifactProof
    ) {
        val file = File(artifact.path)

        runCatching {
            require(file.isFile) {
                "APK is no longer accessible at ${artifact.path}"
            }

            val uri: Uri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                file
            )

            startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND).apply {
                        type =
                            "application/vnd.android.package-archive"
                        putExtra(
                            Intent.EXTRA_STREAM,
                            uri
                        )
                        addFlags(
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    },
                    "Share APK"
                )
            )
        }.onFailure {
            Snackbar.make(
                binding.root,
                "Unable to share APK: ${it.message}",
                Snackbar.LENGTH_LONG
            ).show()
        }
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 1024) {
            return "$bytes B"
        }

        val kb = bytes / 1024.0
        if (kb < 1024) {
            return String.format(
                Locale.US,
                "%.1f KB",
                kb
            )
        }

        return String.format(
            Locale.US,
            "%.2f MB",
            kb / 1024.0
        )
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

        if (parts.isEmpty()) {
            binding.output.text = "Enter a slash command. Use /help to browse commands."
            return
        }

        val spec = CommandRegistry.resolve(parts[0])
        if (spec == null) {
            val query = parts[0]
                .removePrefix("/")
                .trim()
            val suggestions = CommandRegistry.search(query)
                .filter { it.available }
                .take(3)
                .joinToString(", ") { it.command }

            binding.output.text = buildString {
                append("Unknown command: ")
                append(parts[0])
                append("\n\nUse /help to search supported commands.")
                if (suggestions.isNotBlank()) {
                    append("\nPossible matches: ")
                    append(suggestions)
                }
            }
            return
        }

        if (!spec.available) {
            binding.output.text = buildString {
                append(spec.command)
                append(" is currently unavailable.\n\n")
                append(spec.unavailableReason)
                append("\n\nUse /help for working alternatives.")
            }
            return
        }

        when (spec.command) {
            "/help" -> {
                startActivity(
                    Intent(
                        requireContext(),
                        CommandHelpActivity::class.java
                    )
                )
            }

            "/appcreator" -> {
                startActivity(
                    Intent(
                        requireContext(),
                        AppCreatorActivity::class.java
                    )
                )
            }

            "/likefigma" -> {
                (requireActivity() as? MainActivity)
                    ?.navigateToStudio()
            }

            "/aicreator" -> {
                (requireActivity() as? MainActivity)
                    ?.navigateToAiWithPrompt(
                        "",
                        AiMode.APP_CREATOR
                    )
            }

            "/tts" -> {
                startActivity(
                    Intent(
                        requireContext(),
                        TtsStudioActivity::class.java
                    )
                )
            }

            "/socialrecap" -> {
                startActivity(
                    Intent(
                        requireContext(),
                        MediaRecapActivity::class.java
                    )
                )
            }

            "/projects" -> {
                (requireActivity() as? MainActivity)
                    ?.navigateToProjects()
            }

            "/termux",
            "/apkbuilder" -> {
                withTermuxPermission {
                    runTermuxCommand(
                        parts,
                        spec.command
                    )
                }
            }

            else -> {
                binding.output.text =
                    "Command is registered but has no executable action. Use /help."
            }
        }
    }

    private fun runTermuxCommand(
        parts: List<String>,
        canonicalCommand: String
    ) {
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

        var buildCancelPath: String? = null
        var buildWorkDir: String? = null

        val request = when (canonicalCommand) {
            "/termux" -> {
                if (parts.size < 2) {
                    binding.output.text =
                        "Usage: /termux <approved-tool> [args]\n" +
                            "Example: /termux python --version"
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
                val options = parts.drop(1)

                if (
                    options.any {
                        it.equals(
                            "--release",
                            ignoreCase = true
                        )
                    }
                ) {
                    showReleaseSigningBoundary()
                    return
                }

                val unsupported = options
                    .filter {
                        it.startsWith("--") &&
                            it != "--debug"
                    }

                if (unsupported.isNotEmpty()) {
                    binding.output.text =
                        "Unsupported /apkbuilder option: " +
                            unsupported.joinToString() +
                            "\n\nUsage: /apkbuilder --debug [absolute-project-root]\n" +
                            "Release signing is a separate explicit workflow."
                    return
                }

                val workDir = options
                    .firstOrNull {
                        it.startsWith("/")
                    }
                    ?: projectRoot

                val exportPath = prefs.getString(
                    "artifactExportPath",
                    "/storage/emulated/0/Download/Zip_Bug-debug.apk"
                )!!

                val cancelPath =
                    "$workDir/.zipbug-cancel-" +
                        UUID.randomUUID()
                            .toString()
                            .replace("-", "")

                buildCancelPath = cancelPath
                buildWorkDir = workDir

                lastArtifact = null
                binding.artifactActions.visibility =
                    View.GONE
                binding.cancelBuild.visibility =
                    View.VISIBLE
                binding.cancelBuild.isEnabled = true
                binding.buildStages.text =
                    "BUILD WORKFLOW • DEBUG\n" +
                        "Validation: QUEUED\n" +
                        "Dependencies: QUEUED\n" +
                        "Build: QUEUED\n" +
                        "Packaging: QUEUED\n" +
                        "Release signing: NOT TOUCHED"

                TermuxBridge.Request(
                    tool = "bash",
                    args = listOf(
                        "$workDir/scripts/build-termux.sh",
                        "--debug",
                        "--export",
                        exportPath,
                        "--cancel-file",
                        cancelPath,
                        "--timeout-seconds",
                        "1200"
                    ),
                    workDir = workDir,
                    label = "Zip_Bug Controlled Debug APK Build"
                )
            }

            else -> return
        }

        binding.engineState.text = "ENGINE • QUEUING"

        viewLifecycleOwner.lifecycleScope.launch {
            val result = TermuxBridge.send(
                requireContext(),
                request
            )

            result.onSuccess { id ->
                if (canonicalCommand == "/apkbuilder") {
                    activeBuildJobId = id
                    activeBuildCancelPath =
                        buildCancelPath
                    activeBuildWorkDir =
                        buildWorkDir
                    observeBuildJob(id)

                    binding.output.text =
                        "Queued controlled debug build:\n$id\n\n" +
                            "Timeout: 1200 seconds\n" +
                            "Release signing: untouched\n" +
                            "Waiting for the Termux result callback…"
                } else {
                    binding.output.text =
                        "Queued real Termux job:\n$id\n\n" +
                            "Waiting for Termux result callback…"
                }
            }.onFailure {
                if (canonicalCommand == "/apkbuilder") {
                    activeBuildJobId = null
                    activeBuildCancelPath = null
                    activeBuildWorkDir = null
                    binding.cancelBuild.visibility =
                        View.GONE
                }

                binding.output.text =
                    "Command failed before execution:\n${it.message}"
            }
        }
    }

    override fun onDestroyView() {
        buildObserver?.cancel()
        buildObserver = null
        pendingPermissionAction = null
        super.onDestroyView()
        _binding = null
    }
}
