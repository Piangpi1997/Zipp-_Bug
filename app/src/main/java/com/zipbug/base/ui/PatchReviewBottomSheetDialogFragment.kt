package com.zipbug.base.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.snackbar.Snackbar
import com.zipbug.base.databinding.DialogPatchReviewBinding
import com.zipbug.base.repair.CompilerErrorParser
import com.zipbug.base.repair.PatchProposal
import com.zipbug.base.termux.TermuxBridge
import kotlinx.coroutines.launch
import java.io.File

class PatchReviewBottomSheetDialogFragment : BottomSheetDialogFragment() {

    private var _binding: DialogPatchReviewBinding? = null
    private val binding get() = _binding!!

    private var currentPatch: PatchProposal? = null
    private var projectRootPath: String = ""

    var onPatchApplied: (() -> Unit)? = null

    companion object {
        const val TAG = "PatchReviewBottomSheet"
        private const val ARG_PATCH_JSON = "arg_patch_json"
        private const val ARG_PROJECT_ROOT = "arg_project_root"

        fun newInstance(patchJson: String, projectRoot: String): PatchReviewBottomSheetDialogFragment {
            return PatchReviewBottomSheetDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PATCH_JSON, patchJson)
                    putString(ARG_PROJECT_ROOT, projectRoot)
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogPatchReviewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val patchJson = arguments?.getString(ARG_PATCH_JSON).orEmpty()
        projectRootPath = arguments?.getString(ARG_PROJECT_ROOT).orEmpty()

        val parseResult = CompilerErrorParser.parsePatchProposal(patchJson)
        parseResult.onSuccess { patch ->
            currentPatch = patch
            binding.targetFileInfo.text = "Target: ${patch.targetFile}"
            binding.patchExplanation.text = patch.explanation.ifBlank { "No explanation provided." }
            binding.originalSnippet.text = patch.originalSnippet
            binding.replacementSnippet.text = patch.replacementSnippet
        }.onFailure { err ->
            binding.targetFileInfo.text = "Failed to parse patch proposal:\n${err.message}"
            binding.btnApplyPatch.isEnabled = false
        }

        binding.btnCancelPatch.setOnClickListener {
            dismiss()
        }

        binding.btnApplyPatch.setOnClickListener {
            val patch = currentPatch ?: return@setOnClickListener
            val root = File(projectRootPath)
            if (!root.exists()) {
                Snackbar.make(binding.root, "Project directory not found: $projectRootPath", Snackbar.LENGTH_LONG).show()
                return@setOnClickListener
            }

            val applyResult = CompilerErrorParser.applyPatch(root, patch)
            applyResult.onSuccess { patchedFile ->
                Snackbar.make(binding.root, "Patched: ${patchedFile.name}. Triggering rebuild…", Snackbar.LENGTH_SHORT).show()
                viewLifecycleOwner.lifecycleScope.launch {
                    val req = TermuxBridge.Request(
                        tool = "gradle",
                        args = listOf("--no-daemon", "assembleDebug"),
                        workDir = root.absolutePath,
                        label = "Rebuild after patch"
                    )
                    TermuxBridge.send(requireContext(), req)
                    onPatchApplied?.invoke()
                    dismiss()
                }
            }.onFailure { err ->
                Snackbar.make(binding.root, "Failed to apply patch: ${err.message}", Snackbar.LENGTH_LONG).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
