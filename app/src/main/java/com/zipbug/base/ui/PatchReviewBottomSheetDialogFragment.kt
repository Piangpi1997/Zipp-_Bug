package com.zipbug.base.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.snackbar.Snackbar
import com.zipbug.base.ZipBugApp
import com.zipbug.base.data.BuildJobEntity
import com.zipbug.base.databinding.DialogPatchReviewBinding
import com.zipbug.base.repair.CompilerErrorParser
import com.zipbug.base.repair.CompilerPatchTermux
import com.zipbug.base.repair.PatchProposal
import com.zipbug.base.termux.TermuxBridge
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

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
        private const val ARG_PROJECT_EDIT = "arg_project_edit"

        fun newInstance(
            patchJson: String,
            projectRoot: String,
            projectEdit: Boolean = false
        ): PatchReviewBottomSheetDialogFragment {
            return PatchReviewBottomSheetDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PATCH_JSON, patchJson)
                    putString(ARG_PROJECT_ROOT, projectRoot)
                    putBoolean(ARG_PROJECT_EDIT, projectEdit)
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

        val projectEdit = arguments?.getBoolean(ARG_PROJECT_EDIT, false) == true
        if (projectEdit) {
            binding.patchTitle.text = "Zippy • Preview Change"
            binding.btnApplyPatch.text = "Apply & Build"
        }

        val parseResult = CompilerErrorParser.parsePatchProposal(patchJson)
        parseResult.onSuccess { patch ->
            if (patch.targetFile.isBlank()) {
                binding.targetFileInfo.text = "More source context required"
                binding.patchExplanation.text = patch.explanation.ifBlank {
                    "Zippy needs exact existing source text before it can create a safe reviewed patch."
                }
                binding.originalSnippet.text = ""
                binding.replacementSnippet.text = ""
                binding.btnApplyPatch.isEnabled = false
                binding.btnRestorePatch.isEnabled = false
                return@onSuccess
            }

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

        binding.btnRestorePatch.setOnClickListener {
            val patch = currentPatch ?: return@setOnClickListener
            val request = CompilerPatchTermux
                .buildRestoreRequest(projectRootPath, patch.targetFile)
                .getOrElse { error ->
                    Snackbar.make(
                        binding.root,
                        error.message ?: "Restore request is invalid",
                        Snackbar.LENGTH_LONG
                    ).show()
                    return@setOnClickListener
                }

            binding.btnRestorePatch.isEnabled = false
            binding.btnRestorePatch.text = "Restoring…"

            viewLifecycleOwner.lifecycleScope.launch {
                val sendResult = TermuxBridge.send(requireContext(), request)
                if (sendResult.isFailure) {
                    binding.btnRestorePatch.isEnabled = true
                    binding.btnRestorePatch.text = "Restore Last"
                    Snackbar.make(
                        binding.root,
                        "Restore dispatch failed: " +
                            (sendResult.exceptionOrNull()?.message ?: "unknown error"),
                        Snackbar.LENGTH_LONG
                    ).show()
                    return@launch
                }

                val jobId = sendResult.getOrThrow()
                val app = requireActivity().application as ZipBugApp
                val finalJob = app.database.buildJobDao()
                    .observe(jobId)
                    .filterNotNull()
                    .filter {
                        it.status != BuildJobEntity.QUEUED &&
                            it.status != BuildJobEntity.RUNNING
                    }
                    .first()

                val restored =
                    finalJob.status == BuildJobEntity.SUCCESS &&
                        finalJob.exitCode == 0 &&
                        finalJob.stdout.contains("ZIPBUG_RESTORE_OK|")

                if (restored) {
                    Snackbar.make(
                        binding.root,
                        "Last reviewed change restored. Opening controlled rebuild…",
                        Snackbar.LENGTH_SHORT
                    ).show()
                    dismiss()
                    (activity as? MainActivity)?.navigateToTerminal(
                        "/apkbuilder --debug \"" + projectRootPath + "\""
                    )
                } else {
                    binding.btnRestorePatch.isEnabled = true
                    binding.btnRestorePatch.text = "Restore Last"
                    val detail = sequenceOf(
                        finalJob.errorMessage,
                        finalJob.stderr,
                        finalJob.stdout
                    ).firstOrNull { it.isNotBlank() }
                        ?.lineSequence()
                        ?.firstOrNull()
                        ?: "No restorable backup was found"

                    Snackbar.make(
                        binding.root,
                        "Restore failed: " + detail,
                        Snackbar.LENGTH_LONG
                    ).show()
                }
            }
        }

        binding.btnApplyPatch.setOnClickListener {
            val patch = currentPatch ?: return@setOnClickListener

            val request = CompilerPatchTermux
                .buildRequest(projectRootPath, patch)
                .getOrElse { error ->
                    Snackbar.make(
                        binding.root,
                        error.message ?: "Reviewed patch is invalid",
                        Snackbar.LENGTH_LONG
                    ).show()
                    return@setOnClickListener
                }

            binding.btnApplyPatch.isEnabled = false
            binding.btnApplyPatch.text = "Applying…"

            viewLifecycleOwner.lifecycleScope.launch {
                val sendResult = TermuxBridge.send(
                    requireContext(),
                    request
                )

                if (sendResult.isFailure) {
                    binding.btnApplyPatch.isEnabled = true
                    binding.btnApplyPatch.text = "Apply Patch"
                    Snackbar.make(
                        binding.root,
                        "Patch dispatch failed: " +
                            (sendResult.exceptionOrNull()?.message
                                ?: "unknown error"),
                        Snackbar.LENGTH_LONG
                    ).show()
                    return@launch
                }

                val jobId = sendResult.getOrThrow()
                val app = requireActivity().application as ZipBugApp
                val finalJob = app.database.buildJobDao()
                    .observe(jobId)
                    .filterNotNull()
                    .filter {
                        it.status != BuildJobEntity.QUEUED &&
                            it.status != BuildJobEntity.RUNNING
                    }
                    .first()

                val applied =
                    finalJob.status == BuildJobEntity.SUCCESS &&
                        finalJob.exitCode == 0 &&
                        finalJob.stdout.contains("ZIPBUG_PATCH_OK|")

                if (applied) {
                    requireContext()
                        .getSharedPreferences("zipbug.settings", 0)
                        .edit()
                        .putString("lastReviewedPatchTarget", patch.targetFile)
                        .apply()

                    Snackbar.make(
                        binding.root,
                        "Reviewed patch applied. Opening controlled rebuild…",
                        Snackbar.LENGTH_SHORT
                    ).show()

                    onPatchApplied?.invoke()
                    dismiss()

                    (activity as? MainActivity)?.navigateToTerminal(
                        "/apkbuilder --debug \"" +
                            projectRootPath +
                            "\""
                    )
                } else {
                    binding.btnApplyPatch.isEnabled = true
                    binding.btnApplyPatch.text = "Apply Patch"
                    val detail = sequenceOf(
                        finalJob.errorMessage,
                        finalJob.stderr,
                        finalJob.stdout
                    ).firstOrNull { it.isNotBlank() }
                        ?.lineSequence()
                        ?.firstOrNull()
                        ?: "Patch command did not report success"

                    Snackbar.make(
                        binding.root,
                        "Patch not applied: " + detail,
                        Snackbar.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
