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
                return !url.startsWith(ALLOWED_ASSET_PREFIX)
            }
        }
        binding.web.addJavascriptInterface(
            StudioBridge(appFilesDir),
            JS_BRIDGE_NAME
        )
        binding.web.loadUrl("${ALLOWED_ASSET_PREFIX}index.html")
    }

    inner class StudioBridge(private val appFilesDir: File) {
        @JavascriptInterface
        fun save(
            name: String,
            schema: String,
            html: String
        ) {
            val safeName = name.replace(
                Regex("[^a-zA-Z0-9._-]"),
                "_"
            ).ifBlank { "screen" }
            val dir = File(
                appFilesDir,
                "studio"
            ).apply { mkdirs() }

            File(dir, "$safeName.json").writeText(schema)
            File(dir, "current_project.json").writeText(schema)
            File(dir, "$safeName.html").writeText(html)

            showStatus("Saved $safeName")
        }

        @JavascriptInterface
        fun loadProject(): String {
            val file = File(File(appFilesDir, "studio"), "current_project.json")
            return if (file.exists()) file.readText() else ""
        }

        @JavascriptInterface
        fun exportXml(name: String, xmlContent: String) {
            val safeName = name.replace(Regex("[^a-zA-Z0-9._-]"), "_").ifBlank { "layout" }
            val dir = File(appFilesDir, "studio").apply { mkdirs() }
            File(dir, "$safeName.xml").writeText(xmlContent)

            showStatus("Exported Android XML ($safeName.xml)")
        }

        @JavascriptInterface
        fun exportCompose(name: String, composeContent: String) {
            val safeName = name.replace(Regex("[^a-zA-Z0-9._-]"), "_").ifBlank { "Screen" }
            val dir = File(appFilesDir, "studio").apply { mkdirs() }
            File(dir, "$safeName.kt").writeText(composeContent)

            showStatus("Exported Jetpack Compose ($safeName.kt)")
        }

        @JavascriptInterface
        fun navigateBack() {
            val hostActivity = activity ?: return
            hostActivity.runOnUiThread {
                (hostActivity as? com.zipbug.base.ui.MainActivity)?.navigateToHome()
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
