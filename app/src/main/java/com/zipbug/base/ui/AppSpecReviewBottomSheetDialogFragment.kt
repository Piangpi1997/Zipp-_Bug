package com.zipbug.base.ui

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.snackbar.Snackbar
import com.zipbug.base.creator.AppCreatorParser
import com.zipbug.base.ZipBugApp
import com.zipbug.base.creator.AppProjectGenerator
import com.zipbug.base.creator.GeneratedProjectTermuxHandoff
import com.zipbug.base.creator.GeneratedProjectSpec
import com.zipbug.base.creator.ProjectZipWriter
import com.zipbug.base.data.BuildJobEntity
import com.zipbug.base.data.ProjectEntity
import com.zipbug.base.databinding.DialogAppSpecReviewBinding
import com.zipbug.base.project.ProjectWorkspaceManager
import com.zipbug.base.termux.TermuxBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

class AppSpecReviewBottomSheetDialogFragment : BottomSheetDialogFragment() {

    private var _binding: DialogAppSpecReviewBinding? = null
    private val binding get() = _binding!!

    private var currentSpec: GeneratedProjectSpec? = null
    private var rawJson: String = ""
    private var generatedProject: ProjectEntity? = null
    private var pendingExportProject: ProjectEntity? = null
    private var handoffObserverJob: Job? = null

    private val exportGeneratedSource = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        val project = pendingExportProject
        pendingExportProject = null

        if (uri == null || project == null) {
            if (_binding != null) {
                Snackbar.make(
                    binding.root,
                    "Source ZIP export cancelled",
                    Snackbar.LENGTH_SHORT
                ).show()
            }
            return@registerForActivityResult
        }

        val appContext = context?.applicationContext
            ?: return@registerForActivityResult

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val root = File(project.rootPath).canonicalFile
                    require(root.isDirectory) {
                        "Generated project folder is no longer available."
                    }

                    val files = linkedMapOf<String, String>()
                    root.walkTopDown()
                        .filter { it.isFile }
                        .forEach { file ->
                            val relative = file
                                .relativeTo(root)
                                .invariantSeparatorsPath
                            require(file.length() <= MAX_EXPORT_SOURCE_FILE_BYTES) {
                                "Source file is too large to export safely: " + relative
                            }
                            files[relative] = file.readText(Charsets.UTF_8)
                        }

                    require(files.isNotEmpty()) {
                        "Generated project contains no source files."
                    }

                    val writer = ProjectZipWriter(appContext)
                    writer.write(uri, files)
                    writer.validate(uri, files.keys)
                }
            }

            if (_binding == null) return@launch

            result.onSuccess { validation ->
                Snackbar.make(
                    binding.root,
                    "Source ZIP verified: " + validation.entryCount +
                        " files. Save/unzip it in your Termux workspace before building.",
                    Snackbar.LENGTH_LONG
                ).show()
            }.onFailure { error ->
                Snackbar.make(
                    binding.root,
                    "Source ZIP export failed: " +
                        (error.message ?: "unknown error"),
                    Snackbar.LENGTH_LONG
                ).show()
            }
        }
    }

    var onRegenerateRequested: (() -> Unit)? = null
    var onSpecEdited: ((String) -> Unit)? = null

    companion object {
        const val TAG = "AppSpecReviewBottomSheet"
        private const val ARG_SPEC_JSON = "arg_spec_json"
        private const val MAX_EXPORT_SOURCE_FILE_BYTES = 4L * 1024 * 1024

        fun newInstance(specJson: String): AppSpecReviewBottomSheetDialogFragment {
            return AppSpecReviewBottomSheetDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_SPEC_JSON, specJson)
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogAppSpecReviewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        rawJson = arguments?.getString(ARG_SPEC_JSON).orEmpty()
        val parsed = AppCreatorParser.parse(rawJson)

        parsed.onSuccess { spec ->
            currentSpec = spec
            bindSpec(spec)
        }.onFailure { err ->
            binding.projectSummary.text = "Failed to parse spec JSON:\n${err.message}"
            binding.btnGenerateProject.isEnabled = false
        }

        binding.btnEditSpec.setOnClickListener {
            showEditDialog()
        }

        binding.btnRegenerate.setOnClickListener {
            dismiss()
            onRegenerateRequested?.invoke()
        }

        binding.btnGenerateProject.setOnClickListener {
            val spec = currentSpec ?: return@setOnClickListener
            viewLifecycleOwner.lifecycleScope.launch {
                binding.btnGenerateProject.isEnabled = false
                val result = AppProjectGenerator.generate(requireContext(), spec)
                result.onSuccess { proj ->
                    generatedProject = proj

                    val termuxAccessible =
                        ProjectWorkspaceManager.isTermuxAccessible(proj.rootPath)

                    binding.btnBuildProject.isEnabled = true

                    binding.btnBuildProject.text =
                        if (termuxAccessible) {
                            "Build Debug APK"
                        } else {
                            "Move to Termux & Build"
                        }

                    Snackbar.make(
                        binding.root,
                        if (termuxAccessible) {
                            "Generated '${proj.name}'. Ready for controlled debug build."
                        } else {
                            "Generated '${proj.name}' safely in app storage. Export/move the source to a Termux workspace before building."
                        },
                        Snackbar.LENGTH_LONG
                    ).show()
                }.onFailure { e ->
                    binding.btnGenerateProject.isEnabled = true
                    Snackbar.make(
                        binding.root,
                        "Generation failed: ${e.message}",
                        Snackbar.LENGTH_LONG
                    ).show()
                }
            }
        }

        binding.btnBuildProject.setOnClickListener {
            val proj =
                generatedProject
                    ?: return@setOnClickListener

            val termuxAccessible =
                ProjectWorkspaceManager.isTermuxAccessible(proj.rootPath)

            if (!termuxAccessible) {
                showHandoffOptions(proj)
                return@setOnClickListener
            }

            dismiss()
            (activity as? MainActivity)?.navigateToTerminal(
                "/apkbuilder --debug \"" + proj.rootPath + "\""
            )
        }
    }

    private fun showHandoffOptions(project: ProjectEntity) {
        AlertDialog.Builder(requireContext())
            .setTitle("Move generated project")
            .setMessage(
                "Zip_Bug generated this project in private app storage. " +
                    "Choose Move & Build to stage a verified ZIP in Downloads, " +
                    "extract it into a unique Termux workspace, then start the " +
                    "controlled debug APK workflow. Imported ZIPs still cannot " +
                    "trigger shell commands."
            )
            .setNegativeButton("Cancel", null)
            .setNeutralButton("Export ZIP") { _, _ ->
                exportProjectSource(project)
            }
            .setPositiveButton("Move & Build") { _, _ ->
                moveProjectToTermuxAndBuild(project)
            }
            .show()
    }

    private fun exportProjectSource(project: ProjectEntity) {
        pendingExportProject = project
        val filename = project.name
            .replace(Regex("[^a-zA-Z0-9._-]"), "_")
            .ifBlank { "ZipBugGeneratedApp" } +
            "-source.zip"
        exportGeneratedSource.launch(filename)
    }

    private fun moveProjectToTermuxAndBuild(project: ProjectEntity) {
        binding.btnBuildProject.isEnabled = false
        binding.btnBuildProject.text = "Staging Source…"

        viewLifecycleOwner.lifecycleScope.launch {
            val staged = GeneratedProjectTermuxHandoff.stage(
                requireContext(),
                project
            )

            staged.onFailure { error ->
                if (_binding == null) return@onFailure
                binding.btnBuildProject.isEnabled = true
                binding.btnBuildProject.text = "Move to Termux & Build"
                Snackbar.make(
                    binding.root,
                    "Handoff staging failed: " +
                        (error.message ?: "unknown error"),
                    Snackbar.LENGTH_LONG
                ).show()
            }

            val plan = staged.getOrNull() ?: return@launch

            binding.btnBuildProject.text = "Moving to Termux…"

            TermuxBridge.send(
                requireContext(),
                TermuxBridge.Request(
                    tool = "python",
                    args = listOf(
                        "-m",
                        "zipfile",
                        "-e",
                        plan.stagedZipPath,
                        plan.termuxRootPath
                    ),
                    workDir = "/data/data/com.termux/files/home",
                    label = "Generated Project Handoff"
                )
            ).onSuccess { jobId ->
                observeHandoffJob(
                    project = project,
                    plan = plan,
                    jobId = jobId
                )
            }.onFailure { error ->
                if (_binding == null) return@onFailure
                binding.btnBuildProject.isEnabled = true
                binding.btnBuildProject.text = "Move to Termux & Build"
                Snackbar.make(
                    binding.root,
                    "Termux handoff could not start: " +
                        (error.message ?: "unknown error"),
                    Snackbar.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun observeHandoffJob(
        project: ProjectEntity,
        plan: com.zipbug.base.creator.GeneratedProjectHandoffPlan,
        jobId: String
    ) {
        handoffObserverJob?.cancel()
        val database =
            (requireActivity().application as ZipBugApp).database

        handoffObserverJob = viewLifecycleOwner.lifecycleScope.launch {
            database.buildJobDao()
                .observe(jobId)
                .filterNotNull()
                .collect { job ->
                    when (job.status) {
                        BuildJobEntity.SUCCESS -> {
                            handoffObserverJob?.cancel()

                            val moved = project.copy(
                                rootPath = plan.termuxRootPath,
                                entryFile = File(
                                    plan.termuxRootPath,
                                    plan.entryRelativePath
                                ).absolutePath,
                                lastOpenedAt = System.currentTimeMillis()
                            )
                            database.projectDao().upsert(moved)
                            generatedProject = moved

                            if (_binding == null) return@collect

                            binding.btnBuildProject.isEnabled = true
                            binding.btnBuildProject.text = "Build Debug APK"

                            Snackbar.make(
                                binding.root,
                                "Moved and verified " +
                                    plan.validation.entryCount +
                                    " files in Termux. Starting debug build…",
                                Snackbar.LENGTH_LONG
                            ).show()

                            dismiss()
                            (activity as? MainActivity)?.navigateToTerminal(
                                "/apkbuilder --debug \"" +
                                    moved.rootPath +
                                    "\""
                            )
                        }

                        BuildJobEntity.FAILED -> {
                            handoffObserverJob?.cancel()
                            if (_binding == null) return@collect

                            binding.btnBuildProject.isEnabled = true
                            binding.btnBuildProject.text =
                                "Move to Termux & Build"

                            val detail = sequenceOf(
                                job.errorMessage,
                                job.stderr,
                                job.stdout
                            ).firstOrNull { it.isNotBlank() }
                                ?.lineSequence()
                                ?.firstOrNull()
                                ?.take(180)
                                ?: "Termux could not extract the staged source."

                            Snackbar.make(
                                binding.root,
                                "Handoff failed: $detail",
                                Snackbar.LENGTH_LONG
                            ).show()
                        }
                    }
                }
        }
    }

    private fun bindSpec(spec: GeneratedProjectSpec) {
        binding.projectSummary.text = buildString {
            append("Name: ").append(spec.name).append("\n")
            append("Package: ").append(spec.packageName).append("\n")
            append("Target: ").append(spec.target).append(" (min ").append(spec.minSdk)
                .append(" / target ").append(spec.targetSdk).append(")")
        }

        binding.permissionsList.text = if (spec.permissions.isEmpty()) {
            "(None requested)"
        } else {
            spec.permissions.joinToString("\n") { "• $it" }
        }

        binding.dependenciesList.text = if (spec.dependencies.isEmpty()) {
            "(Default Android dependencies)"
        } else {
            spec.dependencies.joinToString("\n") { "• $it" }
        }

        binding.filePlan.text = if (spec.files.isEmpty()) {
            "(Standard baseline template files will be scaffolded)"
        } else {
            spec.files.joinToString("\n") { gf ->
                val size = gf.content.toByteArray(Charsets.UTF_8).size
                val sizeStr = if (size < 1024) "$size B" else String.format(Locale.US, "%.1f KB", size / 1024f)
                "• ${gf.path} ($sizeStr)"
            }
        }
    }

    private fun showEditDialog() {
        val input = EditText(requireContext()).apply {
            setText(rawJson)
            setPadding(16, 16, 16, 16)
            textSize = 12f
            setSelectAllOnFocus(true)
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Edit Spec JSON")
            .setView(input)
            .setPositiveButton("Apply") { _, _ ->
                val newJson = input.text.toString().trim()
                val parsed = AppCreatorParser.parse(newJson)
                parsed.onSuccess {
                    rawJson = newJson
                    currentSpec = it
                    bindSpec(it)
                    onSpecEdited?.invoke(newJson)
                }.onFailure {
                    Snackbar.make(binding.root, "Invalid JSON: ${it.message}", Snackbar.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        handoffObserverJob?.cancel()
        handoffObserverJob = null
        pendingExportProject = null
        super.onDestroyView()
        _binding = null
    }
}
