package com.zipbug.base.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
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
                }

                override fun onNothingSelected(
                    parent: AdapterView<*>?
                ) = Unit
            }

        binding.send.setOnClickListener {
            sendPrompt()
        }
    }

    private fun sendPrompt() {
        val store = SecretStore(requireContext())

        val selected = AiProvider.values()[
            binding.provider.selectedItemPosition
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
            system =
                "You are Zip_Bug AI Creator. " +
                "Produce precise implementation plans " +
                "and code-safe output.",
            prompt = prompt
        )

        binding.output.setText(
            "Connecting to ${effectiveProvider.label}…\n" +
                "Model: ${request.model}"
        )

        viewLifecycleOwner.lifecycleScope.launch {
            val result = repository.complete(request)
            binding.output.setText(
                result.getOrElse {
                    "Error: ${it.message}"
                }
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
