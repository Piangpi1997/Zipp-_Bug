package com.webtoapp.ui.zipstudio

import android.app.Activity
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
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
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                !request.url.toString().startsWith(prefix)
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                if (request.url.toString().startsWith(prefix)) return null
                return WebResourceResponse("text/plain", "utf-8", 403, "Forbidden", emptyMap(), ByteArrayInputStream(ByteArray(0)))
            }
        }
        web.addJavascriptInterface(Bridge(), "ZipBug")
        setContentView(web)
        web.loadUrl(prefix + "index.html")
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
    }

    override fun onDestroy() {
        web.removeJavascriptInterface("ZipBug")
        web.stopLoading()
        web.destroy()
        super.onDestroy()
    }
}
