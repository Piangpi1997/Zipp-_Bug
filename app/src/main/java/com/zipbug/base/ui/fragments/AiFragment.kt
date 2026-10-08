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

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAiBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.provider.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            AiProvider.values().map { it.label }
        )

        binding.provider.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    val provider = AiProvider.values()[position]

                    if (
                        provider == AiProvider.OPENROUTER &&
                        binding.model.text.isNullOrBlank()
                    ) {
                        binding.model.setText("openrouter/free")
                    }
                }

                override fun onNothingSelected(
                    parent: AdapterView<*>?
                ) = Unit
            }

        binding.send.setOnClickListener {
            val provider = AiProvider.values()[
                binding.provider.selectedItemPosition
            ]

            val key = SecretStore(requireContext())
                .get(provider.name.lowercase())

            val request = AiRequest(
                provider = provider,
                apiKey = key,
                model = binding.model.text.toString().trim(),
                system = "You are Zip_Bug AI Creator. Produce precise implementation plans and code-safe output.",
                prompt = binding.prompt.text.toString().trim()
            )

            binding.output.setText(
                "Connecting to ${provider.label}…"
            )

            viewLifecycleOwner.lifecycleScope.launch {
                val result = repository.complete(request)
                binding.output.setText(
                    result.getOrElse { "Error: ${it.message}" }
                )
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
