package com.zipbug.base.ui.fragments

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.zipbug.base.ai.AiMode
import com.zipbug.base.ai.AiProvider
import com.zipbug.base.ai.AiRepository
import com.zipbug.base.ai.AiRequest
import com.zipbug.base.databinding.FragmentAiBinding
import com.zipbug.base.security.SecretStore
import kotlinx.coroutines.launch

class AiFragment : Fragment() {
    private var _binding: FragmentAiBinding? = null
    private val binding get() = _binding!!
    private val repository = AiRepository()

    private var lastProvider = AiProvider.OPENROUTER

    companion object {
        const val ARG_INITIAL_PROMPT = "arg_initial_prompt"
        const val ARG_INITIAL_MODE = "arg_initial_mode"

        fun newInstance(prompt: String = "", mode: AiMode = AiMode.CHAT): AiFragment {
            return AiFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_INITIAL_PROMPT, prompt)
                    putString(ARG_INITIAL_MODE, mode.name)
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAiBinding.inflate(
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
        val initialModeName = arguments?.getString(ARG_INITIAL_MODE)
        val initialPrompt = arguments?.getString(ARG_INITIAL_PROMPT)

        binding.provider.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            AiProvider.values().map { it.label }
        )

        binding.provider.setSelection(
            AiProvider.OPENROUTER.ordinal
        )
        binding.model.setText(
            AiProvider.OPENROUTER.defaultModel
        )

        binding.mode.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            AiMode.values().map { it.title }
        )

        if (!initialModeName.isNullOrBlank()) {
            val modeEnum = runCatching { AiMode.valueOf(initialModeName) }.getOrDefault(AiMode.CHAT)
            binding.mode.setSelection(modeEnum.ordinal)
        }

        if (!initialPrompt.isNullOrBlank()) {
            binding.prompt.setText(initialPrompt)
        }

        binding.provider.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    val selected = AiProvider.values()[position]
                    val currentModel =
                        binding.model.text.toString().trim()

                    val looksLikePreviousDefault =
                        currentModel.isBlank() ||
                            currentModel == lastProvider.defaultModel ||
                            (
                                currentModel.startsWith("openrouter/") &&
                                    selected != AiProvider.OPENROUTER
                            )

                    if (looksLikePreviousDefault) {
                        binding.model.setText(
                            selected.defaultModel
                        )
                    }

                    lastProvider = selected
                    updateKeyPreview()
                }

                override fun onNothingSelected(
                    parent: AdapterView<*>?
                ) = Unit
            }

        binding.btnHealthCheck.setOnClickListener {
            runHealthCheck()
        }

        updateKeyPreview()

        binding.mode.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    val mode = AiMode.values()[position]
                    when (mode) {
                        AiMode.APP_CREATOR -> binding.prompt.hint = "Describe the app to generate structured JSON spec…"
                        AiMode.TTS_SCRIPT -> binding.prompt.hint = "Describe the Myanmar or English voiceover script…"
                        AiMode.FIX_ERROR -> binding.prompt.hint = "Paste compiler errors or build failure log…"
                        AiMode.SOCIAL_RECAP -> binding.prompt.hint = "Paste video transcript for social recap timeline…"
                        else -> binding.prompt.hint = "Describe the task, code, or architecture…"
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }

        binding.send.setOnClickListener {
            sendPrompt()
        }

        binding.copy.setOnClickListener {
            val text = binding.output.text.toString()
            if (text.isNotBlank()) {
                val cm = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("AI Output", text))
                Snackbar.make(binding.root, "Copied to clipboard", Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateKeyPreview() {
        val store = SecretStore(requireContext())
        val selected = AiProvider.values()[binding.provider.selectedItemPosition]
        val key = store.get(selected.name.lowercase())
        binding.maskedKeyPreview.text = "Key: ${AiRepository.maskApiKey(key)}"
    }

    private fun runHealthCheck() {
        val store = SecretStore(requireContext())
        val selected = AiProvider.values()[binding.provider.selectedItemPosition]
        val key = store.get(selected.name.lowercase())
        val model = binding.model.text.toString().trim()

        binding.providerHealthBadge.text = "CONNECTING"
        binding.providerHealthBadge.setTextColor(0xFFFF6B00.toInt())
        binding.output.setText("Testing provider health for ${selected.label}…")

        viewLifecycleOwner.lifecycleScope.launch {
            val result = repository.checkHealth(selected, key, model)
            binding.providerHealthBadge.text = result.status.label
            when (result.status) {
                com.zipbug.base.ai.HealthStatus.READY -> {
                    binding.providerHealthBadge.setTextColor(0xFF39D98A.toInt())
                }
                com.zipbug.base.ai.HealthStatus.NOT_CONFIGURED -> {
                    binding.providerHealthBadge.setTextColor(0xFFA9A29D.toInt())
                }
                else -> {
                    binding.providerHealthBadge.setTextColor(0xFFFF4D4D.toInt())
                }
            }

            binding.output.setText(buildString {
                append("PROVIDER HEALTH CHECK:\n")
                append("Provider: ${result.provider.label}\n")
                append("Status: ${result.status.label}\n")
                append("Masked Key: ${result.maskedKey}\n")
                append("Detail: ${result.message}\n\n")
                if (result.status != com.zipbug.base.ai.HealthStatus.READY) {
                    append("FIX:\n")
                    when (result.status) {
                        com.zipbug.base.ai.HealthStatus.NOT_CONFIGURED ->
                            append("Tap the Settings icon, configure your API key, and tap Save.")
                        com.zipbug.base.ai.HealthStatus.AUTH_ERROR ->
                            append("Verify your API key is active and has valid billing/permissions.")
                        com.zipbug.base.ai.HealthStatus.RATE_LIMITED ->
                            append("Provider is rate limited. Wait a few moments or switch models.")
                        com.zipbug.base.ai.HealthStatus.MODEL_ERROR ->
                            append("Verify the specified model name is supported by this provider.")
                        else ->
                            append("Check internet connection and endpoint availability.")
                    }
                }
            })
        }
    }

    private fun sendPrompt() {
        val store = SecretStore(requireContext())

        val selected = AiProvider.values()[
            binding.provider.selectedItemPosition
        ]
        val selectedMode = AiMode.values()[
            binding.mode.selectedItemPosition
        ]

        var model = binding.model.text
            .toString()
            .trim()

        // Common typo seen during testing.
        if (model == "openrouter/tree") {
            model = "openrouter/free"
            binding.model.setText(model)
        }

        val effectiveProvider =
            if (model.startsWith("openrouter/")) {
                AiProvider.OPENROUTER
            } else {
                selected
            }

        if (effectiveProvider != selected) {
            binding.provider.setSelection(
                effectiveProvider.ordinal
            )
        }

        val key = store.get(
            effectiveProvider.name.lowercase()
        )

        if (key.isBlank()) {
            binding.output.setText(
                "No ${effectiveProvider.label} API key saved.\n\n" +
                    "Tap the wrench icon → Settings → " +
                    "enter the correct key → Save encrypted settings."
            )
            return
        }

        if (
            effectiveProvider != AiProvider.OPENROUTER &&
            key.startsWith("sk-or-")
        ) {
            binding.output.setText(
                "This is an OpenRouter key, but " +
                    "${effectiveProvider.label} is selected.\n\n" +
                    "Select OpenRouter • Free and use model " +
                    "openrouter/free."
            )
            return
        }

        val prompt = binding.prompt.text
            .toString()
            .trim()

        if (prompt.isBlank()) {
            binding.output.setText(
                "Enter a prompt first."
            )
            return
        }

        val request = AiRequest(
            provider = effectiveProvider,
            apiKey = key,
            model = model.ifBlank {
                effectiveProvider.defaultModel
            },
            system = selectedMode.systemPrompt,
            prompt = prompt,
            mode = selectedMode
        )

        binding.output.setText(
            "Connecting to ${effectiveProvider.label}…\n" +
                "Mode: ${selectedMode.title}\n" +
                "Model: ${request.model}"
        )

        viewLifecycleOwner.lifecycleScope.launch {
            val result = repository.complete(request)
            result.onSuccess { text ->
                binding.output.setText(text)

                when (selectedMode) {
                    AiMode.APP_CREATOR -> {
                        val parsed = com.zipbug.base.creator.AppCreatorParser.parse(text)
                        if (parsed.isSuccess) {
                            binding.reviewAction.isVisible = true
                            binding.reviewAction.text = "Review Spec"
                            binding.reviewAction.setOnClickListener {
                                val sheet = com.zipbug.base.ui.AppSpecReviewBottomSheetDialogFragment.newInstance(text)
                                sheet.onRegenerateRequested = { sendPrompt() }
                                sheet.show(parentFragmentManager, com.zipbug.base.ui.AppSpecReviewBottomSheetDialogFragment.TAG)
                            }
                            // Proactively present review sheet
                            val sheet = com.zipbug.base.ui.AppSpecReviewBottomSheetDialogFragment.newInstance(text)
                            sheet.onRegenerateRequested = { sendPrompt() }
                            sheet.show(parentFragmentManager, com.zipbug.base.ui.AppSpecReviewBottomSheetDialogFragment.TAG)
                        } else {
                            binding.reviewAction.isVisible = false
                        }
                    }

                    AiMode.FIX_ERROR -> {
                        val parsedPatch = com.zipbug.base.repair.CompilerErrorParser.parsePatchProposal(text)
                        if (parsedPatch.isSuccess) {
                            val prefs = requireContext().getSharedPreferences("zipbug.settings", 0)
                            val home = prefs.getString("termuxHome", "/data/data/com.termux/files/home")!!
                            val projectRoot = prefs.getString("projectRoot", "$home/OpenDots/Zip_Bug_Antigravity")!!

                            binding.reviewAction.isVisible = true
                            binding.reviewAction.text = "Review Patch"
                            binding.reviewAction.setOnClickListener {
                                val sheet = com.zipbug.base.ui.PatchReviewBottomSheetDialogFragment.newInstance(text, projectRoot)
                                sheet.show(parentFragmentManager, com.zipbug.base.ui.PatchReviewBottomSheetDialogFragment.TAG)
                            }
                            // Proactively present review sheet
                            val sheet = com.zipbug.base.ui.PatchReviewBottomSheetDialogFragment.newInstance(text, projectRoot)
                            sheet.show(parentFragmentManager, com.zipbug.base.ui.PatchReviewBottomSheetDialogFragment.TAG)
                        } else {
                            binding.reviewAction.isVisible = false
                        }
                    }

                    else -> {
                        binding.reviewAction.isVisible = false
                    }
                }
            }.onFailure { err ->
                binding.reviewAction.isVisible = false
                binding.output.setText("Error: ${err.message}")
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
