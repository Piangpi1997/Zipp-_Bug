package com.zipbug.base.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import androidx.fragment.app.Fragment
import com.google.android.material.snackbar.Snackbar
import com.zipbug.base.databinding.FragmentStudioBinding
import java.io.File

class StudioFragment : Fragment() {
    private var _binding: FragmentStudioBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStudioBinding.inflate(
            inflater,
            container,
            false
        )
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.web.settings.javaScriptEnabled = true
        binding.web.settings.domStorageEnabled = true
        binding.web.addJavascriptInterface(
            StudioBridge(),
            "ZipBug"
        )
        binding.web.loadUrl(
            "file:///android_asset/studio/index.html"
        )
    }

    inner class StudioBridge {
        @JavascriptInterface
        fun save(
            name: String,
            schema: String,
            html: String
        ) {
            val safeName = name.replace(
                Regex("[^a-zA-Z0-9._-]"),
                "_"
            )
            val dir = File(
                requireContext().filesDir,
                "studio"
            ).apply { mkdirs() }

            File(dir, "$safeName.json").writeText(schema)
            File(dir, "current_project.json").writeText(schema)
            File(dir, "$safeName.html").writeText(html)

            requireActivity().runOnUiThread {
                Snackbar.make(
                    binding.root,
                    "Saved $safeName",
                    Snackbar.LENGTH_SHORT
                ).show()
            }
        }

        @JavascriptInterface
        fun loadProject(): String {
            val file = File(File(requireContext().filesDir, "studio"), "current_project.json")
            return if (file.exists()) file.readText() else ""
        }

        @JavascriptInterface
        fun exportXml(name: String, xmlContent: String) {
            val safeName = name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val dir = File(requireContext().filesDir, "studio").apply { mkdirs() }
            File(dir, "$safeName.xml").writeText(xmlContent)

            requireActivity().runOnUiThread {
                Snackbar.make(
                    binding.root,
                    "Exported Android XML ($safeName.xml)",
                    Snackbar.LENGTH_SHORT
                ).show()
            }
        }

        @JavascriptInterface
        fun exportCompose(name: String, composeContent: String) {
            val safeName = name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val dir = File(requireContext().filesDir, "studio").apply { mkdirs() }
            File(dir, "$safeName.kt").writeText(composeContent)

            requireActivity().runOnUiThread {
                Snackbar.make(
                    binding.root,
                    "Exported Jetpack Compose ($safeName.kt)",
                    Snackbar.LENGTH_SHORT
                ).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
