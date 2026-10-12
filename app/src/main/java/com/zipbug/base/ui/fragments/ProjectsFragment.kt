package com.zipbug.base.ui.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.zipbug.base.R
import com.zipbug.base.ZipBugApp
import com.zipbug.base.artifact.ApkArtifactScanner
import com.zipbug.base.data.ApkArtifactEntity
import com.zipbug.base.data.ProjectEntity
import com.zipbug.base.databinding.FragmentProjectsBinding
import com.zipbug.base.creator.ProjectDirectoryZipWriter
import com.zipbug.base.engine.EngineManager
import com.zipbug.base.project.ProjectWorkspaceManager
import com.zipbug.base.project.ZipImporter
import com.zipbug.base.ui.ApkDetailBottomSheetDialogFragment
import com.zipbug.base.ui.MainActivity
import com.zipbug.base.ui.SigningDiagnosticsActivity
import com.zipbug.base.ui.WebRuntimeActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

class ProjectsFragment : Fragment() {
    private var _binding: FragmentProjectsBinding? = null
    private val binding get() = _binding!!
    private var pendingSourceExport: ProjectEntity? = null

    private val exportProjectSource = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        val project = pendingSourceExport
        pendingSourceExport = null

        if (uri == null || project == null) {
            if (_binding != null) {
                Snackbar.make(
                    binding.root,
                    "Source ZIP export cancelled",
                    Snackbar.LENGTH_SHORT
                ).show()
            }
            return@registerForActivityResult
        }

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    ProjectDirectoryZipWriter(requireContext())
                        .writeAndValidate(
                            uri,
                            File(project.rootPath)
                        )
                }
            }

            if (_binding == null) return@launch

            result.onSuccess { validation ->
                Snackbar.make(
                    binding.root,
                    "Source ZIP verified: " +
                        validation.entryCount +
                        " files. Unzip it in a Termux workspace, then build with /apkbuilder --debug.",
                    Snackbar.LENGTH_LONG
                ).show()
            }.onFailure { error ->
                Snackbar.make(
                    binding.root,
                    "Source ZIP export failed: " +
                        (error.message ?: "unknown error"),
                    Snackbar.LENGTH_LONG
                ).show()
            }
        }
    }

    private val pickZip = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewLifecycleOwner.lifecycleScope.launch {
                runCatching {
                    ZipImporter(requireContext()).importZip(uri)
                }.onSuccess { project ->
                    val app = requireActivity().application as ZipBugApp
                    app.database.projectDao().upsert(project)
                    renderProjects()
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

    private val pickApk = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewLifecycleOwner.lifecycleScope.launch {
                runCatching {
                    val input = requireContext().contentResolver.openInputStream(uri)
                        ?: throw IllegalStateException("Cannot open selected APK")
                    val tempFile = File(requireContext().cacheDir, "inspected_${System.currentTimeMillis()}.apk")
                    tempFile.outputStream().use { out -> input.copyTo(out) }
                    ApkDetailBottomSheetDialogFragment.newInstance(tempFile.absolutePath)
                        .show(parentFragmentManager, ApkDetailBottomSheetDialogFragment.TAG)
                }.onFailure {
                    Snackbar.make(binding.root, "Error opening APK: ${it.message}", Snackbar.LENGTH_LONG).show()
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
                    .firstOrNull(::isWebProject)

                if (project == null) {
                    Snackbar.make(
                        binding.root,
                        "No runnable web mini-app project found.",
                        Snackbar.LENGTH_SHORT
                    ).show()
                    return@launch
                }

                runWebProject(project)
            }
        }

        binding.btnSigningDiag.setOnClickListener {
            startActivity(Intent(requireContext(), SigningDiagnosticsActivity::class.java))
        }

        binding.btnScanApks.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                Snackbar.make(binding.root, "Scanning Gradle output directories for APKs…", Snackbar.LENGTH_SHORT).show()
                val found = ApkArtifactScanner.scanAndPersist(requireContext())
                Snackbar.make(binding.root, "Found ${found.size} APK build artifacts", Snackbar.LENGTH_SHORT).show()
                renderArtifacts()
            }
        }

        binding.btnInspectPicker.setOnClickListener {
            pickApk.launch(
                arrayOf(
                    "application/vnd.android.package-archive",
                    "application/octet-stream"
                )
            )
        }

        renderProjects()
        renderArtifacts()
    }

    private fun renderProjects() {
        viewLifecycleOwner.lifecycleScope.launch {
            val app = requireActivity().application as ZipBugApp
            val projects = app.database.projectDao().listAll()

            binding.projectsContainer.removeAllViews()

            if (projects.isEmpty()) {
                val emptyTv = TextView(requireContext()).apply {
                    text = "No projects yet. Import a ZIP or scaffold using AI App Creator."
                    setTextColor(ContextCompat.getColor(requireContext(), R.color.z_muted))
                    setPadding(12, 12, 12, 12)
                    textSize = 13f
                }
                binding.projectsContainer.addView(emptyTv)
                return@launch
            }

            for (p in projects) {
                binding.projectsContainer.addView(createProjectCard(p))
            }
        }
    }

    private fun createProjectCard(project: com.zipbug.base.data.ProjectEntity): View {
        val card = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(ContextCompat.getColor(context, R.color.z_surface))
            setPadding(16, 16, 16, 16)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 12
            }
        }

        val nameTv = TextView(requireContext()).apply {
            text = project.name
            setTextColor(ContextCompat.getColor(context, R.color.z_text))
            textSize = 15f
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        card.addView(nameTv)

        val metaTv = TextView(requireContext()).apply {
            text = "${project.kind} • ${project.rootPath}"
            setTextColor(ContextCompat.getColor(context, R.color.z_muted))
            textSize = 12f
            setPadding(0, 4, 0, 8)
        }
        card.addView(metaTv)

        if (project.kind.equals("react-vite-source", ignoreCase = true)) {
            val sourceNote = TextView(requireContext()).apply {
                text =
                    "React + Vite source only. Build it in a reviewed Node/npm workspace; " +
                        "Zip_Bug does not auto-run package lifecycle scripts from imported ZIPs."
                setTextColor(
                    ContextCompat.getColor(
                        context,
                        R.color.z_muted
                    )
                )
                textSize = 12f
                setPadding(0, 0, 0, 8)
            }
            card.addView(sourceNote)
        }

        val btnRow = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val btnBuild = Button(requireContext()).apply {
            val termuxAccessible =
                ProjectWorkspaceManager.isTermuxAccessible(project.rootPath)
            text =
                if (termuxAccessible) "Build APK"
                else "Export Source ZIP"
            visibility =
                if (isAndroidProject(project)) View.VISIBLE
                else View.GONE
            setBackgroundColor(
                ContextCompat.getColor(context, R.color.z_orange)
            )
            setTextColor(0xFF111111.toInt())
            layoutParams = LinearLayout.LayoutParams(
                0,
                44 * resources.displayMetrics.density.toInt(),
                1f
            ).apply {
                marginEnd = 6
            }
            setOnClickListener {
                if (termuxAccessible) {
                    (activity as? MainActivity)?.navigateToTerminal(
                        "/apkbuilder --debug \"" +
                            project.rootPath +
                            "\""
                    )
                } else {
                    pendingSourceExport = project
                    val filename = project.name
                        .replace(
                            Regex("[^a-zA-Z0-9._-]"),
                            "_"
                        )
                        .ifBlank { "ZipBugProject" } +
                        "-source.zip"
                    exportProjectSource.launch(filename)
                }
            }
        }
        btnRow.addView(btnBuild)

        val btnRun = Button(requireContext()).apply {
            text = "Run :3131"
            visibility = if (isWebProject(project)) View.VISIBLE else View.GONE
            setBackgroundColor(0xFF2D2D2D.toInt())
            setTextColor(ContextCompat.getColor(context, R.color.z_text))
            layoutParams = LinearLayout.LayoutParams(0, 44 * resources.displayMetrics.density.toInt(), 1f).apply {
                marginStart = 6
            }
            setOnClickListener {
                runWebProject(project)
            }
        }
        btnRow.addView(btnRun)

        card.addView(btnRow)
        return card
    }

    private fun runWebProject(project: ProjectEntity) {
        runCatching {
            val root = File(project.rootPath).canonicalFile
            val entry = File(root, project.entryFile).canonicalFile
            val prefix = root.path + File.separator

            require(entry.path.startsWith(prefix) && entry.isFile) {
                "Project entry is outside the sandbox or missing."
            }

            val webRoot = entry.parentFile
                ?: error("Project entry has no parent directory")

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
        }.onFailure {
            Snackbar.make(
                binding.root,
                it.message ?: "Unable to run mini-app",
                Snackbar.LENGTH_LONG
            ).show()
        }
    }

    private fun isWebProject(project: ProjectEntity): Boolean =
        !project.kind.equals("react-vite-source", ignoreCase = true) &&
            (
                project.kind.equals("web-mini-app", ignoreCase = true) ||
                    project.entryFile.endsWith(".html", ignoreCase = true)
            )

    private fun isAndroidProject(project: ProjectEntity): Boolean =
        project.kind.contains("ANDROID", ignoreCase = true) ||
            File(project.rootPath, "settings.gradle.kts").isFile ||
            File(project.rootPath, "settings.gradle").isFile


    private fun renderArtifacts() {
        viewLifecycleOwner.lifecycleScope.launch {
            val app = requireActivity().application as ZipBugApp
            val artifacts = app.database.apkArtifactDao().listAll()

            binding.artifactsContainer.removeAllViews()

            if (artifacts.isEmpty()) {
                val emptyTv = TextView(requireContext()).apply {
                    text = "No APK artifacts discovered yet. Tap 'Scan' or build via Terminal."
                    setTextColor(ContextCompat.getColor(requireContext(), R.color.z_muted))
                    setPadding(12, 12, 12, 12)
                    textSize = 13f
                }
                binding.artifactsContainer.addView(emptyTv)
                return@launch
            }

            for (art in artifacts) {
                binding.artifactsContainer.addView(createArtifactCard(art))
            }
        }
    }

    private fun createArtifactCard(artifact: ApkArtifactEntity): View {
        val card = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(ContextCompat.getColor(context, R.color.z_surface))
            setPadding(16, 16, 16, 16)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 12
            }
        }

        val nameTv = TextView(requireContext()).apply {
            text = artifact.fileName
            setTextColor(ContextCompat.getColor(context, R.color.z_text))
            textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        card.addView(nameTv)

        val metaTv = TextView(requireContext()).apply {
            val sizeMb = String.format(Locale.US, "%.2f MB", artifact.sizeBytes / (1024.0 * 1024.0))
            text = "${artifact.packageName} • v${artifact.versionName} • $sizeMb\nSHA: ${artifact.sha256.take(16)}…"
            setTextColor(ContextCompat.getColor(context, R.color.z_muted))
            textSize = 12f
            setPadding(0, 4, 0, 8)
        }
        card.addView(metaTv)

        val btnInspect = Button(requireContext()).apply {
            text = "Inspect & Install"
            setBackgroundColor(ContextCompat.getColor(context, R.color.z_orange))
            setTextColor(0xFF111111.toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                44 * resources.displayMetrics.density.toInt()
            )
            setOnClickListener {
                ApkDetailBottomSheetDialogFragment.newInstance(artifact.filePath)
                    .show(parentFragmentManager, ApkDetailBottomSheetDialogFragment.TAG)
            }
        }
        card.addView(btnInspect)

        return card
    }

    override fun onDestroyView() {
        pendingSourceExport = null
        super.onDestroyView()
        _binding = null
    }
}
