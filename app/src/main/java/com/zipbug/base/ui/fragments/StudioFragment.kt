package com.zipbug.base.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import androidx.fragment.app.Fragment
import com.google.android.material.snackbar.Snackbar
import com.zipbug.base.databinding.FragmentStudioBinding
import com.zipbug.base.studio.StudioSchemaValidator
import org.json.JSONObject
import java.io.File

class StudioFragment : Fragment() {
    private var _binding: FragmentStudioBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStudioBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.web.settings.javaScriptEnabled = true
        binding.web.settings.domStorageEnabled = true
        binding.web.addJavascriptInterface(StudioBridge(), "ZipBug")
        binding.web.loadUrl("file:///android_asset/studio/index.html")
    }

    inner class StudioBridge {
        private val maxExportChars = 2_000_000

        @JavascriptInterface
        fun save(name: String, schema: String, html: String) {
            if (!isJsonDraftSafe(schema) || html.length > maxExportChars) {
                showMessage("Save blocked: project data is invalid or too large")
                return
            }
            val safeName = safeName(name)
            val dir = studioDir()
            writeAtomically(File(dir, "$safeName.json"), schema)
            writeAtomically(File(dir, "current_project.json"), schema)
            writeAtomically(File(dir, "recovery_project.json"), schema, keepPrevious = true)
            writeAtomically(File(dir, "$safeName.html"), html)
            showMessage("Saved $safeName")
        }

        @JavascriptInterface
        fun autosave(schema: String) {
            if (!isJsonDraftSafe(schema)) return
            writeAtomically(
                File(studioDir(), "recovery_project.json"),
                schema,
                keepPrevious = true
            )
        }

        @JavascriptInterface
        fun loadProject(): String {
            val dir = studioDir()
            val candidates = listOf(
                File(dir, "recovery_project.json"),
                File(dir, "recovery_project.previous.json"),
                File(dir, "current_project.json")
            )
            for (file in candidates) {
                if (!file.exists()) continue
                val content = runCatching { file.readText() }.getOrNull() ?: continue
                if (isJsonDraftSafe(content)) return content
            }
            return ""
        }

        @JavascriptInterface
        fun exportXml(name: String, schema: String, xmlContent: String): String {
            val issues = StudioSchemaValidator.validate(schema, requireComposeNames = false)
            if (issues.isNotEmpty()) return exportError("XML", issues.first().display())
            if (xmlContent.length > maxExportChars ||
                !xmlContent.trimStart().startsWith("<?xml") ||
                !xmlContent.contains("ConstraintLayout")
            ) {
                return exportError("XML", "generated document failed structural checks")
            }

            val safeName = safeName(name)
            val file = File(studioDir(), "$safeName.xml")
            writeAtomically(file, xmlContent)
            showMessage("Exported Android XML ($safeName.xml)")
            return "OK:${file.absolutePath}"
        }

        @JavascriptInterface
        fun exportCompose(name: String, schema: String, composeContent: String): String {
            val issues = StudioSchemaValidator.validate(schema, requireComposeNames = true)
            if (issues.isNotEmpty()) return exportError("Compose", issues.first().display())
            if (composeContent.length > maxExportChars ||
                !composeContent.contains("package com.zipbug.generated.ui") ||
                !composeContent.contains("@Composable")
            ) {
                return exportError("Compose", "generated document failed structural checks")
            }

            val safeName = safeName(name)
            val file = File(studioDir(), "$safeName.kt")
            writeAtomically(file, composeContent)
            showMessage("Exported Jetpack Compose ($safeName.kt)")
            return "OK:${file.absolutePath}"
        }

        private fun exportError(kind: String, detail: String): String {
            val message = "$kind export blocked: $detail"
            showMessage(message)
            return "ERROR:$message"
        }

        private fun isJsonDraftSafe(schema: String): Boolean {
            if (schema.isBlank() || schema.length > StudioSchemaValidator.MAX_SCHEMA_CHARS) return false
            return runCatching { JSONObject(schema) }.isSuccess
        }

        private fun safeName(name: String): String {
            return name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
                .trim('_', '.')
                .ifBlank { "screen" }
        }

        private fun studioDir(): File = File(requireContext().filesDir, "studio").apply { mkdirs() }

        private fun writeAtomically(target: File, content: String, keepPrevious: Boolean = false) {
            target.parentFile?.mkdirs()
            if (keepPrevious && target.exists()) {
                runCatching {
                    target.copyTo(File(target.parentFile, "${target.nameWithoutExtension}.previous.${target.extension}"), overwrite = true)
                }
            }
            val temp = File(target.parentFile, "${target.name}.tmp")
            temp.writeText(content)
            if (!temp.renameTo(target)) {
                target.writeText(content)
                temp.delete()
            }
        }

        private fun showMessage(message: String) {
            requireActivity().runOnUiThread {
                val root = _binding?.root ?: return@runOnUiThread
                Snackbar.make(root, message, Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
