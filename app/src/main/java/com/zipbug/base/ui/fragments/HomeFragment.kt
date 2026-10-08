package com.zipbug.base.ui.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.zipbug.base.ai.AiMode
import com.zipbug.base.databinding.FragmentHomeBinding
import com.zipbug.base.ui.AppCreatorActivity
import com.zipbug.base.ui.CommandHelpActivity
import com.zipbug.base.ui.MainActivity
import com.zipbug.base.ui.MediaRecapActivity
import com.zipbug.base.ui.TtsStudioActivity

class HomeFragment : Fragment() {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(
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
        val main = requireActivity() as MainActivity

        binding.appCreator.setOnClickListener {
            startActivity(
                Intent(
                    requireContext(),
                    AppCreatorActivity::class.java
                )
            )
        }

        binding.apkBuilder.setOnClickListener {
            main.navigateToTerminal("/apkbuilder")
        }

        binding.studio.setOnClickListener {
            main.navigateToStudio()
        }

        binding.aiCreator.setOnClickListener {
            main.navigateToAiWithPrompt(
                "",
                AiMode.APP_CREATOR
            )
        }

        binding.ttsStudio.setOnClickListener {
            startActivity(
                Intent(
                    requireContext(),
                    TtsStudioActivity::class.java
                )
            )
        }

        binding.mediaRecap.setOnClickListener {
            startActivity(
                Intent(
                    requireContext(),
                    MediaRecapActivity::class.java
                )
            )
        }

        binding.terminal.setOnClickListener {
            main.navigateToTerminal(
                "/termux python --version"
            )
        }

        binding.commandHelp.setOnClickListener {
            startActivity(
                Intent(
                    requireContext(),
                    CommandHelpActivity::class.java
                )
            )
        }

        binding.projects.setOnClickListener {
            main.navigateToProjects()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
