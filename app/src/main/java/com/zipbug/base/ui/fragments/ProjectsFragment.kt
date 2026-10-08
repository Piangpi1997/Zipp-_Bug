package com.zipbug.base.ui.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.zipbug.base.ZipBugApp
import com.zipbug.base.databinding.FragmentProjectsBinding
import com.zipbug.base.engine.EngineManager
import com.zipbug.base.project.ZipImporter
import com.zipbug.base.ui.WebRuntimeActivity
import kotlinx.coroutines.launch
import java.io.File

class ProjectsFragment : Fragment() {
    private var _binding: FragmentProjectsBinding? = null
    private val binding get() = _binding!!

    private val pickZip = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewLifecycleOwner.lifecycleScope.launch {
                runCatching {
                    ZipImporter(requireContext()).import(uri)
                }.onSuccess { project ->
                    val app = requireActivity().application as ZipBugApp
                    app.database.projectDao().upsert(project)
                    render()
                }.onFailure {
                    Snackbar.make(
                        binding.root,
                        it.message ?: "Import failed",
                        Snackbar.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProjectsBinding.inflate(
            inflater,
            container,
            false
        )
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.importZip.setOnClickListener {
            pickZip.launch(
                arrayOf(
                    "application/zip",
                    "application/octet-stream"
                )
            )
        }

        binding.runLatest.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                val app = requireActivity().application as ZipBugApp
                val project = app.database.projectDao()
                    .listAll()
                    .firstOrNull()
                    ?: return@launch

                val entry = File(project.entryFile)
                val webRoot = File(
                    project.rootPath,
                    entry.parent ?: "www"
                )

                EngineManager.run(webRoot)

                startActivity(
                    Intent(
                        requireContext(),
                        WebRuntimeActivity::class.java
                    ).putExtra(
                        "url",
                        "http://127.0.0.1:3131/${entry.name}"
                    )
                )
            }
        }

        render()
    }

    private fun render() {
        viewLifecycleOwner.lifecycleScope.launch {
            val app = requireActivity().application as ZipBugApp
            val projects = app.database.projectDao().listAll()

            binding.projects.text = if (projects.isEmpty()) {
                "No projects yet"
            } else {
                projects.joinToString("\n\n") {
                    "${it.name}\n${it.kind} • ${it.entryFile}"
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
