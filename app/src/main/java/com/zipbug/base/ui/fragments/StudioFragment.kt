package com.zipbug.base.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.fragment.app.Fragment
import com.google.android.material.snackbar.Snackbar
import com.zipbug.base.databinding.FragmentStudioBinding
import java.io.File
import org.json.JSONObject
import com.zipbug.base.studio.StudioSchemaValidator

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

    @Suppress("SetJavaScriptEnabled")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val appFilesDir = requireContext().applicationContext.filesDir
        val assetPrefix = when (arguments?.getString("variant")) {
            "advanced" -> "file:///android_asset/studio_advanced/"
            "likefigma" -> "file:///android_asset/studio_likefigma/"
            else -> ALLOWED_ASSET_PREFIX
        }
        binding.web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = false
            @Suppress("DEPRECATION")
            allowFileAccessFromFileURLs = false
            @Suppress("DEPRECATION")
            allowUniversalAccessFromFileURLs = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        }
        binding.web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val url = request?.url?.toString().orEmpty()
                return !url.startsWith(assetPrefix)
            }
        }
        binding.web.addJavascriptInterface(
            StudioBridge(appFilesDir),
            JS_BRIDGE_NAME
        )
        val language = resources.configuration.locales[0]?.language ?: "en"
        binding.web.loadUrl("${assetPrefix}index.html?lang=$language")
    }

    inner class StudioBridge(private val appFilesDir: File) {
        @JavascriptInterface
        fun save(
            name: String,
            schema: String,
            html: String
        ): String {
            val safeName = name.replace(
                Regex("[^a-zA-Z0-9._-]"),
                "_"
            ).trim('_', '.').ifBlank { "screen" }
            val dir = File(
                appFilesDir,
                "studio"
            ).apply { mkdirs() }

            if (!safeJson(schema) || html.length > 2_000_000) {
                showStatus("Save blocked: invalid or oversized project")
                return "ERROR: Invalid or oversized project"
            }
            return try {
            writeAtomically(File(dir, "$safeName.json"), schema)
            writeAtomically(File(dir, "current_project.json"), schema)
            writeAtomically(File(dir, "recovery_project.json"), schema, keepPrevious = true)
            writeAtomically(File(dir, "$safeName.html"), html)

            showStatus("Saved $safeName")
            "OK"
            } catch (error: Exception) { "ERROR:${error.message}" }
        }

        @JavascriptInterface
        fun autosave(schema: String) {
            if (!safeJson(schema)) return
            val dir = File(appFilesDir, "studio").apply { mkdirs() }
            writeAtomically(File(dir, "recovery_project.json"), schema, keepPrevious = true)
        }

        @JavascriptInterface
        fun loadProject(): String {
            val dir = File(appFilesDir, "studio")
            for (name in listOf("recovery_project.json", "recovery_project.previous.json", "current_project.json")) {
                val content = runCatching { File(dir, name).readText() }.getOrNull() ?: continue
                if (safeJson(content)) return content
            }
            return ""
        }

        private fun safeJson(value: String): Boolean =
            value.isNotBlank() && value.length <= 1_000_000 && runCatching { JSONObject(value) }.isSuccess

        private fun writeAtomically(target: File, value: String, keepPrevious: Boolean = false) {
            if (keepPrevious && target.exists()) {
                target.copyTo(File(target.parentFile, "${target.nameWithoutExtension}.previous.${target.extension}"), overwrite = true)
            }
            val temp = File(target.parentFile, "${target.name}.tmp")
            temp.writeText(value)
            check(temp.renameTo(target)) { "Could not replace ${target.name}" }
        }

        @JavascriptInterface
        fun exportXml(name: String, xmlContent: String): String = try {
            val safeName = name.replace(Regex("[^a-zA-Z0-9._-]"), "_").ifBlank { "layout" }
            val dir = File(appFilesDir, "studio").apply { mkdirs() }
            File(dir, "$safeName.xml").writeText(xmlContent)

            showStatus("Exported Android XML ($safeName.xml)")
            "OK"
        } catch (error: Exception) { "ERROR:${error.message}" }

        @JavascriptInterface
        fun exportCompose(name: String, composeContent: String): String = try {
            val safeName = name.replace(Regex("[^a-zA-Z0-9._-]"), "_").ifBlank { "Screen" }
            val dir = File(appFilesDir, "studio").apply { mkdirs() }
            File(dir, "$safeName.kt").writeText(composeContent)

            showStatus("Exported Jetpack Compose ($safeName.kt)")
            "OK"
        } catch (error: Exception) { "ERROR:${error.message}" }

        @JavascriptInterface
        fun exportXmlValidated(name: String, schema: String, xmlContent: String): String {
            val issues = StudioSchemaValidator.validate(schema, requireComposeNames = false)
            if (issues.isNotEmpty()) return "ERROR:" + issues.first().display()
            if (xmlContent.length > 2_000_000 || !xmlContent.trimStart().startsWith("<?xml")) return "ERROR:Invalid XML"
            val safe = name.replace(Regex("[^a-zA-Z0-9._-]"), "_").trim('_', '.').ifBlank { "layout" }
            val path = File(File(appFilesDir, "studio").apply { mkdirs() }, "$safe.xml")
            return runCatching { writeAtomically(path, xmlContent); "OK:${path.absolutePath}" }.getOrElse { "ERROR:${it.message}" }
        }

        @JavascriptInterface
        fun exportComposeValidated(name: String, schema: String, content: String): String {
            val issues = StudioSchemaValidator.validate(schema, requireComposeNames = true)
            if (issues.isNotEmpty()) return "ERROR:" + issues.first().display()
            if (content.length > 2_000_000 || !content.contains("@Composable")) return "ERROR:Invalid Compose"
            val safe = name.replace(Regex("[^a-zA-Z0-9._-]"), "_").trim('_', '.').ifBlank { "Screen" }
            val path = File(File(appFilesDir, "studio").apply { mkdirs() }, "$safe.kt")
            return runCatching { writeAtomically(path, content); "OK:${path.absolutePath}" }.getOrElse { "ERROR:${it.message}" }
        }

        @JavascriptInterface
        fun navigateBack() {
            val hostActivity = activity ?: return
            hostActivity.runOnUiThread {
                if (hostActivity is com.zipbug.base.ui.MainActivity) hostActivity.navigateToHome() else hostActivity.finish()
            }
        }

        private fun showStatus(message: String) {
            val hostActivity = activity ?: return
            hostActivity.runOnUiThread {
                val b = _binding ?: return@runOnUiThread
                if (isAdded) {
                    Snackbar.make(b.root, message, Snackbar.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onDestroyView() {
        _binding?.web?.apply {
            removeJavascriptInterface(JS_BRIDGE_NAME)
            stopLoading()
            destroy()
        }
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val JS_BRIDGE_NAME = "ZipBug"
        private const val ALLOWED_ASSET_PREFIX = "file:///android_asset/studio/"
    }
}
