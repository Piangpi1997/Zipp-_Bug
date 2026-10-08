package com.zipbug.base.ui.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.zipbug.base.databinding.FragmentHomeBinding
import com.zipbug.base.ui.MediaRecapActivity
import com.zipbug.base.ui.AppCreatorActivity
import com.zipbug.base.ui.TtsStudioActivity

class HomeFragment : Fragment() {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.mediaRecap.setOnClickListener {
            startActivity(
                Intent(requireContext(), MediaRecapActivity::class.java)
            )
        }

        binding.appCreator.setOnClickListener {
            startActivity(
                Intent(requireContext(), AppCreatorActivity::class.java)
            )
        }

        binding.ttsStudio.setOnClickListener {
            startActivity(
                Intent(requireContext(), TtsStudioActivity::class.java)
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
