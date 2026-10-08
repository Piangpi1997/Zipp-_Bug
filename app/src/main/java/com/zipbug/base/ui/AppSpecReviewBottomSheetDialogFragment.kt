package com.zipbug.base.ui

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.snackbar.Snackbar
import com.zipbug.base.creator.AppCreatorParser
import com.zipbug.base.creator.AppProjectGenerator
import com.zipbug.base.creator.GeneratedProjectSpec
import com.zipbug.base.data.ProjectEntity
import com.zipbug.base.databinding.DialogAppSpecReviewBinding
import com.zipbug.base.termux.TermuxBridge
import kotlinx.coroutines.launch
import java.util.Locale

class AppSpecReviewBottomSheetDialogFragment : BottomSheetDialogFragment() {

    private var _binding: DialogAppSpecReviewBinding? = null
    private val binding get() = _binding!!

    private var currentSpec: GeneratedProjectSpec? = null
    private var rawJson: String = ""
    private var generatedProject: ProjectEntity? = null

    var onRegenerateRequested: (() -> Unit)? = null
    var onSpecEdited: ((String) -> Unit)? = null

    companion object {
        const val TAG = "AppSpecReviewBottomSheet"
        private const val ARG_SPEC_JSON = "arg_spec_json"

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
                    binding.btnBuildProject.isEnabled = true
                    Snackbar.make(
                        binding.root,
                        "Generated project '${proj.name}' in Projects",
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
            val proj = generatedProject ?: return@setOnClickListener
            viewLifecycleOwner.lifecycleScope.launch {
                val req = TermuxBridge.Request(
                    tool = "gradle",
                    args = listOf("--no-daemon", "assembleDebug"),
                    workDir = proj.rootPath,
                    label = "Build ${proj.name}"
                )
                TermuxBridge.send(requireContext(), req).onSuccess { jobId ->
                    Snackbar.make(
                        binding.root,
                        "Queued build job: $jobId",
                        Snackbar.LENGTH_LONG
                    ).show()
                    dismiss()
                }.onFailure { err ->
                    Snackbar.make(
                        binding.root,
                        "Build failed: ${err.message}",
                        Snackbar.LENGTH_LONG
                    ).show()
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
        super.onDestroyView()
        _binding = null
    }
}
