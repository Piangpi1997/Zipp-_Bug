package com.webtoapp.ui.zipstudio

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.webtoapp.core.i18n.Strings
import java.io.ByteArrayInputStream
import java.io.File
import org.json.JSONObject

class ZipStudioActivity : Activity() {
    private lateinit var web: WebView
    private val prefix = "file:///android_asset/zipstudio/"

    @Suppress("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        web = WebView(this)
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = false
            @Suppress("DEPRECATION")
            allowFileAccessFromFileURLs = false
            @Suppress("DEPRECATION")
            allowUniversalAccessFromFileURLs = false
        }
        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                if (!url.startsWith(prefix)) return
                val label = JSONObject.quote(Strings.btnExport + " HTML")
                view.evaluateJavascript("""
                    (function(){
                        if (document.getElementById('pp-export-html')) return;
                        const bar = document.querySelector('header');
                        if (!bar) return;
                        bar.style.overflowX = 'auto';
                        bar.style.whiteSpace = 'nowrap';
                        const button = document.createElement('button');
                        button.id = 'pp-export-html';
                        button.className = 'btn-export';
                        button.textContent = $label;
                        button.onclick = function(){
                            const doc = '<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"></head><body>' + makePreviewHtml(getActiveScreen()) + '</body></html>';
                            window.ZipBug.exportHtml(doc);
                        };
                        bar.insertBefore(button, bar.firstChild);
                    })();
                """.trimIndent(), null)
            }
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                !request.url.toString().startsWith(prefix)
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                if (request.url.toString().startsWith(prefix)) return null
                return WebResourceResponse("text/plain", "utf-8", 403, "Forbidden", emptyMap(), ByteArrayInputStream(ByteArray(0)))
            }
        }
        web.addJavascriptInterface(Bridge(), "ZipBug")
        setContentView(web)
        ViewCompat.setOnApplyWindowInsetsListener(web) { view, insets ->
            val safeArea = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(safeArea.left, safeArea.top, safeArea.right, safeArea.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(web)
        web.loadUrl(prefix + "index.html?lang=" + if (Strings.myanmarEnabled.value) "my" else "en")
    }

    inner class Bridge {
        private fun safeName(value: String): String =
            value.replace(Regex("[^a-zA-Z0-9._-]"), "_").trim('_', '.').take(100).ifBlank { "screen" }
        private fun validJson(value: String): Boolean =
            value.isNotBlank() && value.length <= 1_000_000 && runCatching { JSONObject(value) }.isSuccess
        private fun dir(): File = File(filesDir, "zipstudio").apply { mkdirs() }
        private fun write(target: File, text: String) {
            val temp = File(target.parentFile, target.name + ".tmp")
            temp.writeText(text)
            check(temp.renameTo(target)) { "Could not save file" }
        }
        @JavascriptInterface
        fun save(name: String, schema: String, html: String): String {
            if (!validJson(schema) || html.length > 2_000_000) return "ERROR: Invalid or oversized project"
            return runCatching {
                val d = dir()
                write(File(d, "current_project.json"), schema)
                write(File(d, safeName(name) + ".html"), html)
                "OK"
            }.getOrElse { "ERROR: ${it.message}" }
        }
        @JavascriptInterface
        fun autosave(schema: String) {
            if (validJson(schema)) runCatching { write(File(dir(), "recovery_project.json"), schema) }
        }
        @JavascriptInterface
        fun loadProject(): String {
            val d = dir()
            for (name in listOf("recovery_project.json", "current_project.json")) {
                val content = runCatching { File(d, name).readText() }.getOrNull() ?: continue
                if (validJson(content)) return content
            }
            return ""
        }
        @JavascriptInterface
        fun exportXml(name: String, value: String): String {
            if (value.length > 2_000_000 || !value.trimStart().startsWith("<?xml")) return "ERROR: Invalid XML"
            return runCatching { write(File(dir(), safeName(name) + ".xml"), value); "OK" }.getOrElse { "ERROR: ${it.message}" }
        }
        @JavascriptInterface
        fun exportCompose(name: String, value: String): String {
            if (value.length > 2_000_000 || !value.contains("@Composable")) return "ERROR: Invalid Compose"
            return runCatching { write(File(dir(), safeName(name) + ".kt"), value); "OK" }.getOrElse { "ERROR: ${it.message}" }
        }
        @JavascriptInterface
        fun navigateBack() { runOnUiThread { finish() } }
        @JavascriptInterface
        fun exportHtml(value: String) {
            if (value.length > 2_000_000 || !value.startsWith("<!doctype html>")) return
            val saved = runCatching { write(File(dir(), "pending_export.html"), value) }.isSuccess
            if (!saved) return
            runOnUiThread {
                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "text/html"
                    putExtra(Intent.EXTRA_TITLE, "pp-studio.html")
                }
                startActivityForResult(intent, 41)
            }
        }
    }

    @Deprecated("Deprecated in Android")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 41 || resultCode != RESULT_OK || data?.data == null) return
        val succeeded = runCatching {
            val output = requireNotNull(contentResolver.openOutputStream(data.data!!))
            output.use { stream -> File(filesDir, "zipstudio/pending_export.html").inputStream().use { it.copyTo(stream) } }
        }.isSuccess
        Toast.makeText(this, if (succeeded) Strings.exportSuccess else Strings.exportFailed, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        web.removeJavascriptInterface("ZipBug")
        web.stopLoading()
        web.destroy()
        super.onDestroy()
    }
}
