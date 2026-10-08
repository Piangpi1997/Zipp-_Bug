package com.zipbug.base.ui.fragments

import android.os.Bundle
import android.util.Xml
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import androidx.fragment.app.Fragment
import com.zipbug.base.databinding.FragmentStudioBinding
import com.zipbug.base.ui.AppLanguage
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.StringReader

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
            "file:///android_asset/studio/index.html?lang=${AppLanguage.selected(requireContext())}"
        )
    }

    inner class StudioBridge {
        @JavascriptInterface
        fun save(
            name: String,
            schema: String,
            html: String
        ): String = runCatching {
            require(schema.toByteArray(Charsets.UTF_8).size <= MAX_PROJECT_BYTES) {
                "Project JSON exceeds the 2 MB limit."
            }
            require(html.toByteArray(Charsets.UTF_8).size <= MAX_EXPORT_BYTES) {
                "Preview HTML exceeds the 4 MB limit."
            }
            validateProjectEnvelope(schema)
            val context = context?.applicationContext ?: error("Studio is no longer attached to the app.")
            val directory = File(context.filesDir, "studio").apply { mkdirs() }
            val safeName = safeFileName(name)
            writeAtomic(directory, "$safeName.json", schema)
            writeAtomic(directory, "current_project.json", schema)
            writeAtomic(directory, "$safeName.html", html)
            "SAVED"
        }.getOrElse { error -> "ERROR:${error.message ?: "Unable to save the project."}" }

        @JavascriptInterface
        fun loadProject(): String = runCatching {
            val context = context?.applicationContext ?: return ""
            val file = File(File(context.filesDir, "studio"), "current_project.json")
            if (!file.isFile || file.length() > MAX_PROJECT_BYTES) "" else file.readText(Charsets.UTF_8)
        }.getOrDefault("")

        @JavascriptInterface
        fun exportXml(name: String, xmlContent: String): String = runCatching {
            require(xmlContent.toByteArray(Charsets.UTF_8).size <= MAX_EXPORT_BYTES) {
                "Android XML exceeds the 4 MB limit."
            }
            validateXml(xmlContent)
            val directory = studioDirectory()
            writeAtomic(directory, "${safeFileName(name)}.xml", xmlContent)
            "EXPORTED"
        }.getOrElse { error -> "ERROR:${error.message ?: "Unable to export Android XML."}" }

        @JavascriptInterface
        fun exportCompose(name: String, composeContent: String): String = runCatching {
            require(composeContent.toByteArray(Charsets.UTF_8).size <= MAX_EXPORT_BYTES) {
                "Compose source exceeds the 4 MB limit."
            }
            require(composeContent.contains("@Composable") &&
                Regex("fun\\s+[A-Za-z_][A-Za-z0-9_]*\\s*\\(").containsMatchIn(composeContent)) {
                "Compose source is missing a valid @Composable screen function."
            }
            val directory = studioDirectory()
            writeAtomic(directory, "${safeFileName(name)}.kt", composeContent)
            "EXPORTED"
        }.getOrElse { error -> "ERROR:${error.message ?: "Unable to export Jetpack Compose."}" }

        private fun studioDirectory(): File {
            val context = context?.applicationContext ?: error("Studio is no longer attached to the app.")
            return File(context.filesDir, "studio").apply { mkdirs() }
        }

        private fun validateProjectEnvelope(schema: String) {
            val root = JSONObject(schema)
            val screens = root.optJSONArray("screens") ?: error("Project JSON must contain a screens array.")
            require(screens.length() in 1..MAX_SCREENS) { "Project must contain between 1 and $MAX_SCREENS screens." }
            for (screenIndex in 0 until screens.length()) {
                val screen = screens.optJSONObject(screenIndex)
                    ?: error("Screen ${screenIndex + 1} must be a JSON object.")
                val nodes = screen.optJSONArray("nodes") ?: screen.optJSONArray("components")
                    ?: error("Screen ${screenIndex + 1} must contain nodes or legacy components.")
                require(nodes.length() <= MAX_NODES_PER_SCREEN) {
                    "Screen ${screenIndex + 1} exceeds the $MAX_NODES_PER_SCREEN layer limit."
                }
            }
        }

        private fun validateXml(source: String) {
            require(source.contains("<androidx.constraintlayout.widget.ConstraintLayout")) {
                "Android XML must contain a ConstraintLayout root."
            }
            val parser = Xml.newPullParser()
            parser.setInput(StringReader(source))
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                // Consume the document so malformed or unclosed XML is rejected before writing.
            }
        }

        private fun safeFileName(value: String): String {
            return value.trim()
                .replace(Regex("[^a-zA-Z0-9._-]"), "_")
                .trim('.', '_', '-')
                .take(80)
                .ifBlank { "studio_project" }
        }

        private fun writeAtomic(directory: File, name: String, contents: String) {
            val target = File(directory, name)
            val temporary = File(directory, ".$name.tmp")
            temporary.writeText(contents, Charsets.UTF_8)
            if (!temporary.renameTo(target)) {
                temporary.copyTo(target, overwrite = true)
                temporary.delete()
            }
        }
    }

    override fun onDestroyView() {
        binding.web.removeJavascriptInterface("ZipBug")
        _binding = null
        super.onDestroyView()
    }

    private companion object {
        const val MAX_PROJECT_BYTES = 2 * 1024 * 1024
        const val MAX_EXPORT_BYTES = 4 * 1024 * 1024
        const val MAX_SCREENS = 100
        const val MAX_NODES_PER_SCREEN = 1000
    }
}
