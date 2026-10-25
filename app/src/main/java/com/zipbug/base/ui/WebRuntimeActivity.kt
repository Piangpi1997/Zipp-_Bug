package com.zipbug.base.ui

import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.zipbug.base.R

class WebRuntimeActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var tvConsole: TextView
    private lateinit var consoleScroll: ScrollView
    private lateinit var btnViewport: Button
    private lateinit var btnToggleConsole: Button

    private var isDesktopViewport = false
    private var defaultUserAgent: String = ""
    private val consoleLogs = mutableListOf<String>()

    @Suppress("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val url = intent.getStringExtra("url")
            ?: "http://127.0.0.1:3131/"

        require(isAllowedLocalRuntimeUrl(url)) {
            "Runtime URL must be local (:3131)"
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(ContextCompat.getColor(this@WebRuntimeActivity, R.color.z_bg))
        }

        // Top toolbar
        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(16, 10, 16, 10)
            setBackgroundColor(ContextCompat.getColor(this@WebRuntimeActivity, R.color.z_surface))
        }

        val tvUrl = TextView(this).apply {
            id = R.id.tvCurrentFileName
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            text = url
            textSize = 11f
            typeface = Typeface.MONOSPACE
            setTextColor(ContextCompat.getColor(this@WebRuntimeActivity, R.color.z_text))
            maxLines = 1
        }

        btnViewport = Button(this).apply {
            text = getString(R.string.preview_mobile_mode)
            textSize = 10f
            setOnClickListener { toggleViewportMode() }
        }

        val btnRefresh = Button(this).apply {
            text = getString(R.string.preview_refresh)
            textSize = 10f
            setOnClickListener {
                appendConsoleLog("INFO", "Reloading $url ...")
                webView.reload()
            }
        }

        btnToggleConsole = Button(this).apply {
            text = "Console (0)"
            textSize = 10f
            setOnClickListener {
                consoleScroll.visibility =
                    if (consoleScroll.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            }
        }

        toolbar.addView(tvUrl)
        toolbar.addView(btnViewport)
        toolbar.addView(btnRefresh)
        toolbar.addView(btnToggleConsole)

        // WebView
        webView = WebView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        defaultUserAgent = webView.settings.userAgentString.orEmpty()

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            @Suppress("DEPRECATION")
            allowFileAccessFromFileURLs = false
            @Suppress("DEPRECATION")
            allowUniversalAccessFromFileURLs = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            useWideViewPort = false
            loadWithOverviewMode = false
            builtInZoomControls = true
            displayZoomControls = false
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                if (consoleMessage != null) {
                    val lvl = consoleMessage.messageLevel()?.name ?: "LOG"
                    val msg = "${consoleMessage.message()} (line ${consoleMessage.lineNumber()})"
                    appendConsoleLog(lvl, msg)
                    if (lvl.equals("ERROR", ignoreCase = true)) {
                        consoleScroll.visibility = View.VISIBLE
                    }
                }
                return true
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val targetUrl = request?.url?.toString().orEmpty()
                if (!isAllowedLocalRuntimeUrl(targetUrl)) {
                    appendConsoleLog("ERROR", "Blocked external navigation: $targetUrl")
                    consoleScroll.visibility = View.VISIBLE
                    return true
                }
                return false
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                val reqUrl = request?.url?.toString() ?: url
                val desc = error?.description?.toString() ?: "Resource load error"
                appendConsoleLog("ERROR", "$desc [$reqUrl]")
                consoleScroll.visibility = View.VISIBLE
            }

            override fun onReceivedHttpError(
                view: WebView?,
                request: WebResourceRequest?,
                errorResponse: WebResourceResponse?
            ) {
                super.onReceivedHttpError(view, request, errorResponse)
                val reqUrl = request?.url?.toString() ?: url
                val code = errorResponse?.statusCode ?: 0
                if (code >= 400 && !reqUrl.endsWith("/favicon.ico")) {
                    appendConsoleLog("ERROR", "HTTP $code loading $reqUrl")
                    consoleScroll.visibility = View.VISIBLE
                }
            }
        }

        // Collapsible Console / Error Feedback Panel
        tvConsole = TextView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setPadding(20, 12, 20, 12)
            typeface = Typeface.MONOSPACE
            textSize = 11f
            setTextColor(ContextCompat.getColor(this@WebRuntimeActivity, R.color.z_green))
            text = "JS Console ready (isolated sandbox)."
            setTextIsSelectable(true)
        }

        consoleScroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                320
            )
            setBackgroundColor(0xFF0A0A0A.toInt())
            visibility = View.GONE
            addView(tvConsole)
        }

        root.addView(toolbar)
        root.addView(webView)
        root.addView(consoleScroll)

        setContentView(root)
        webView.loadUrl(url)
    }

    private fun isAllowedLocalRuntimeUrl(candidate: String): Boolean =
        candidate.startsWith("http://127.0.0.1:3131/") ||
            candidate.startsWith("http://localhost:3131/")

    private fun toggleViewportMode() {
        isDesktopViewport = !isDesktopViewport
        webView.settings.apply {
            useWideViewPort = isDesktopViewport
            loadWithOverviewMode = isDesktopViewport
            userAgentString = if (isDesktopViewport) {
                "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            } else {
                defaultUserAgent
            }
        }
        btnViewport.text = if (isDesktopViewport) {
            getString(R.string.preview_desktop_mode)
        } else {
            getString(R.string.preview_mobile_mode)
        }
        appendConsoleLog("VIEWPORT", if (isDesktopViewport) "Switched to Desktop viewport" else "Switched to Mobile viewport")
        webView.reload()
    }

    private fun appendConsoleLog(level: String, message: String) {
        consoleLogs.add("[$level] $message")
        if (consoleLogs.size > 100) {
            consoleLogs.removeAt(0)
        }
        tvConsole.text = consoleLogs.joinToString("\n")
        btnToggleConsole.text = "Console (${consoleLogs.size})"
        if (level.equals("ERROR", ignoreCase = true)) {
            tvConsole.setTextColor(ContextCompat.getColor(this, R.color.z_red))
        }
    }

    override fun onDestroy() {
        runCatching {
            webView.stopLoading()
            webView.destroy()
        }
        super.onDestroy()
    }
}
