package com.zipbug.base.ui

import android.os.Bundle
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity

class WebRuntimeActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val webView = WebView(this)
        setContentView(webView)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        }

        val url = intent.getStringExtra("url")
            ?: "http://127.0.0.1:3131/"

        require(
            url.startsWith("http://127.0.0.1:3131/") ||
                url.startsWith("http://localhost:3131/")
        ) { "Runtime URL must be local" }

        webView.loadUrl(url)
    }
}
