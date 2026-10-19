package com.zipbug.base.ui

import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.ByteArrayInputStream
import java.net.URI

class WebRuntimeActivity : AppCompatActivity() {
    private lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this)
        setContentView(webView)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val url = request?.url?.toString().orEmpty()
                if (isAllowedRuntimeUrl(url)) return false

                Toast.makeText(
                    this@WebRuntimeActivity,
                    "Blocked non-local runtime navigation",
                    Toast.LENGTH_SHORT
                ).show()
                return true
            }

            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                val url = request?.url?.toString().orEmpty()
                val scheme = runCatching {
                    URI(url).scheme?.lowercase()
                }.getOrNull()

                if (
                    (scheme == "http" || scheme == "https") &&
                    !isAllowedRuntimeUrl(url)
                ) {
                    return WebResourceResponse(
                        "text/plain",
                        "utf-8",
                        403,
                        "Blocked",
                        mapOf("Cache-Control" to "no-store"),
                        ByteArrayInputStream(
                            "Blocked: Zip_Bug runtime is localhost-only."
                                .toByteArray()
                        )
                    )
                }

                return super.shouldInterceptRequest(
                    view,
                    request
                )
            }
        }

        val url = intent.getStringExtra("url")
            ?: "http://127.0.0.1:3131/"

        require(isAllowedRuntimeUrl(url)) {
            "Runtime URL must be local on port 3131"
        }

        webView.loadUrl(url)
    }

    override fun onDestroy() {
        if (::webView.isInitialized) {
            webView.stopLoading()
            webView.loadUrl("about:blank")
            webView.removeAllViews()
            webView.destroy()
        }
        super.onDestroy()
    }

    companion object {
        fun isAllowedRuntimeUrl(url: String): Boolean {
            val uri = runCatching { URI(url) }
                .getOrNull()
                ?: return false

            if (!uri.scheme.equals("http", ignoreCase = true)) {
                return false
            }

            val host = uri.host?.lowercase() ?: return false
            if (host != "127.0.0.1" && host != "localhost") {
                return false
            }

            return uri.port == 3131
        }
    }
}
