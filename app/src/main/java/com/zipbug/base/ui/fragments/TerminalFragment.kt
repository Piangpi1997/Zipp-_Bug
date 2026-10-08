package com.zipbug.base.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.zipbug.base.databinding.FragmentTerminalBinding
import com.zipbug.base.termux.TermuxBridge

class TerminalFragment : Fragment() {
    private var _binding: FragmentTerminalBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTerminalBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.run.setOnClickListener {
            runCommand(binding.command.text.toString())
        }
        binding.ffmpeg.setOnClickListener {
            binding.command.setText("/termux ffmpeg -version")
        }
        binding.python.setOnClickListener {
            binding.command.setText("/termux python --version")
        }
        binding.gradle.setOnClickListener {
            binding.command.setText("/termux gradle --version")
        }
    }

    private fun runCommand(raw: String) {
        val parts = raw.trim()
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }

        if (parts.size < 2 || parts[0] != "/termux") {
            binding.output.text = "Use /termux <allowed-tool> [args]"
            return
        }

        val home = requireContext()
            .getSharedPreferences("zipbug.settings", 0)
            .getString(
                "termuxHome",
                "/data/data/com.termux/files/home"
            )!!

        val result = TermuxBridge.send(
            requireContext(),
            TermuxBridge.Request(
                tool = parts[1],
                args = parts.drop(2),
                workDir = home
            )
        )

        binding.output.text = result.fold(
            onSuccess = {
                "Command sent to Termux. Enable allow-external-apps=true in ~/.termux/termux.properties."
            },
            onFailure = {
                "Failed: ${it.message}"
            }
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
