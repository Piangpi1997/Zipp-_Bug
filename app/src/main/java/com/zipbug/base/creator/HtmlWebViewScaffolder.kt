package com.zipbug.base.creator

import com.zipbug.base.termux.LogSanitizer
import java.net.URI

/** Screen orientation for a generated web app. */
enum class WebOrientation { AUTO, PORTRAIT, LANDSCAPE }

/** Optional hardware access a generated web app can request. */
enum class WebHardware { CAMERA, MICROPHONE, LOCATION }

/**
 * Specification for an HTML-to-app project, modelled on the structure of
 * dedicated HTML-to-APK builder apps: bundled local web files (offline)
 * or a live start URL, WebView settings, an origin-restricted native JS
 * bridge, and optional hardware permissions.
 *
 * The generated project reuses [AppScaffolder] for the Gradle skeleton
 * (including the Open Source Licenses files), then replaces the native
 * template activity with a WebView shell and bundles the web files under
 * `app/src/main/assets/www/`.
 */
data class WebAppSpec(
    val name: String,
    val packageName: String,
    val goal: String = "",
    val minSdk: Int = 24,
    val targetSdk: Int = 34,
    val startUrl: String? = null,
    val webFiles: Map<String, String> = emptyMap(),
    val orientation: WebOrientation = WebOrientation.AUTO,
    val fullscreen: Boolean = false,
    val keepScreenOn: Boolean = false,
    val userAgent: String? = null,
    val hardware: Set<WebHardware> = emptySet()
)

object HtmlWebViewScaffolder {
    private const val MAX_WEB_FILE_CHARS = 1_000_000
    private const val MAX_WEB_TOTAL_CHARS = 8_000_000
    private const val MAX_WEB_FILES = 200
    private const val LOCAL_START = "file:///android_asset/www/index.html"

    fun validate(spec: WebAppSpec) {
        val permissions = permissionsFor(spec)
        // Reuses the shared app validation (name, package, SDK ranges,
        // permission shape) so web apps follow the same rules.
        AppScaffolder.validate(
            AppSpec(
                name = spec.name,
                packageName = spec.packageName,
                goal = spec.goal,
                minSdk = spec.minSdk,
                targetSdk = spec.targetSdk,
                permissions = permissions
            )
        )
        if (spec.startUrl != null) {
            require(spec.startUrl.startsWith("https://")) {
                "Start URL must use https://"
            }
            require(
                spec.startUrl.none {
                    it == '"' || it == '\\' || it.isWhitespace()
                }
            ) { "Start URL contains unsupported characters" }
            val host = runCatching { URI(spec.startUrl).host }.getOrNull()
            require(!host.isNullOrBlank()) { "Start URL must have a host" }
        } else {
            require(normalizeWebFiles(spec.webFiles).containsKey("index.html")) {
                "Local web apps require an index.html entry file"
            }
        }
        normalizeWebFiles(spec.webFiles) // validates paths and sizes
        if (spec.userAgent != null) {
            require(spec.userAgent.length <= 200) { "User agent is too long" }
            require(spec.userAgent.none { it == '\n' || it == '\r' }) {
                "User agent must be a single line"
            }
        }
    }

    fun permissionsFor(spec: WebAppSpec): List<String> {
        val permissions = mutableListOf(
            "android.permission.INTERNET",
            "android.permission.VIBRATE"
        )
        if (WebHardware.CAMERA in spec.hardware) {
            permissions += "android.permission.CAMERA"
        }
        if (WebHardware.MICROPHONE in spec.hardware) {
            permissions += "android.permission.RECORD_AUDIO"
        }
        if (WebHardware.LOCATION in spec.hardware) {
            permissions += "android.permission.ACCESS_FINE_LOCATION"
        }
        return permissions
    }

    /** Normalizes and validates web file paths (relative to www/). */
    fun normalizeWebFiles(files: Map<String, String>): Map<String, String> {
        require(files.size <= MAX_WEB_FILES) { "Too many web files" }
        var total = 0
        val normalized = LinkedHashMap<String, String>()
        for ((rawPath, content) in files) {
            val path = rawPath.trim().replace('\\', '/')
            require(path.isNotBlank()) { "Web file path is blank" }
            require(!path.startsWith("/")) {
                "Web file path must be relative: $rawPath"
            }
            val segments = path.split('/')
            require(segments.none { it.isBlank() || it == "." || it == ".." }) {
                "Web file path escapes the www folder: $rawPath"
            }
            require(content.length <= MAX_WEB_FILE_CHARS) {
                "Web file is too large (text assets only): $path"
            }
            total += content.length
            require(total <= MAX_WEB_TOTAL_CHARS) {
                "Web files are too large in total (text assets only)"
            }
            normalized[path] = content
        }
        return normalized
    }

    fun files(spec: WebAppSpec): Map<String, String> {
        validate(spec)
        val permissions = permissionsFor(spec)
        val base = AppScaffolder.files(
            AppSpec(
                name = spec.name,
                packageName = spec.packageName,
                goal = spec.goal,
                minSdk = spec.minSdk,
                targetSdk = spec.targetSdk,
                permissions = permissions
            )
        ).toMutableMap()

        val packagePath = spec.packageName.replace('.', '/')
        base["app/src/main/AndroidManifest.xml"] = manifest(spec, permissions)
        base["app/src/main/java/" + packagePath + "/MainActivity.kt"] =
            mainActivity(spec)
        // The WebView is created in code; the native template layout is
        // not used by the web shell.
        base.remove("app/src/main/res/layout/activity_main.xml")
        for ((path, content) in normalizeWebFiles(spec.webFiles)) {
            base["app/src/main/assets/www/" + path] = content
        }
        base["README.md"] = readme(spec)
        return base
    }

    /** A starter page used when the user has no HTML of their own yet. */
    fun starterIndexHtml(name: String, goal: String): String {
        val safeGoal = LogSanitizer.sanitize(goal)
        return """
            <!doctype html>
            <html lang="en">
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width,initial-scale=1">
              <title>__TITLE__</title>
              <style>
                :root { color-scheme: dark; font-family: system-ui, sans-serif;
                        background: #121212; color: #f7f3ef; }
                body { margin: 0; min-height: 100vh; display: grid;
                       place-items: center; }
                main { width: min(92vw, 620px); padding: 28px;
                       border: 1px solid #35312f; border-radius: 24px;
                       background: #1b1b1b; }
                .badge { color: #39d98a; font: 700 12px monospace; }
                h1 { font-size: 32px; margin: 12px 0; }
                p { color: #b8b0aa; line-height: 1.6; }
                button { width: 100%; border: 0; border-radius: 14px;
                         padding: 14px 18px; margin-top: 10px;
                         background: #ff6b00; color: #121212;
                         font-weight: 800; }
                pre { min-height: 60px; padding: 14px; border-radius: 14px;
                      background: #101010; color: #39d98a;
                      white-space: pre-wrap; }
              </style>
            </head>
            <body>
              <main>
                <div class="badge">ZIP_BUG HTML APP</div>
                <h1>__TITLE__</h1>
                <p id="goal">__GOAL__</p>
                <button id="toastBtn">Test native toast</button>
                <button id="vibrateBtn">Test vibration</button>
                <pre id="output">Replace www/index.html with your own HTML to build your app.</pre>
              </main>
              <script>
                const output = document.querySelector('#output');
                document.querySelector('#toastBtn').addEventListener('click', () => {
                  if (window.AndroidAPI) {
                    window.AndroidAPI.toast('Hello from ' + document.title);
                    output.textContent = 'AndroidAPI.toast called.';
                  } else {
                    output.textContent = 'AndroidAPI is not available in a plain browser.';
                  }
                });
                document.querySelector('#vibrateBtn').addEventListener('click', () => {
                  if (window.AndroidAPI) {
                    window.AndroidAPI.vibrate(120);
                    output.textContent = 'AndroidAPI.vibrate called.';
                  }
                });
              </script>
            </body>
            </html>
        """.trimIndent()
            .replace("__TITLE__", html(name))
            .replace(
                "__GOAL__",
                html(
                    safeGoal.ifBlank {
                        "Generated by Zip_Bug Antigravity Studio."
                    }
                )
            ) + "\n"
    }

    private fun manifest(
        spec: WebAppSpec,
        permissions: List<String>
    ): String {
        val permissionLines = permissions
            .distinct()
            .joinToString("\n") {
                "    <uses-permission android:name=\"" + xml(it) + "\" />"
            }
        val orientationAttr = when (spec.orientation) {
            WebOrientation.PORTRAIT ->
                "\n                    android:screenOrientation=\"portrait\""
            WebOrientation.LANDSCAPE ->
                "\n                    android:screenOrientation=\"landscape\""
            WebOrientation.AUTO -> ""
        }
        return """
        <manifest xmlns:android="http://schemas.android.com/apk/res/android">
        __PERMISSIONS__
            <application
                android:allowBackup="false"
                android:label="@string/app_name"
                android:theme="@style/Theme.Generated"
                android:usesCleartextTraffic="false">
                <activity
                    android:name=".MainActivity"
                    android:configChanges="orientation|screenSize|keyboardHidden|smallestScreenSize|screenLayout"
                    android:exported="true"
                    android:windowSoftInputMode="adjustResize"__ORIENTATION__>
                    <intent-filter>
                        <action android:name="android.intent.action.MAIN" />
                        <category android:name="android.intent.category.LAUNCHER" />
                    </intent-filter>
                </activity>
            </application>
        </manifest>
        """.trimIndent()
            .replace("__PERMISSIONS__", permissionLines)
            .replace("__ORIENTATION__", orientationAttr) + "\n"
    }

    private fun mainActivity(spec: WebAppSpec): String {
        val startUrl = spec.startUrl ?: LOCAL_START
        val trustedPrefix = if (spec.startUrl == null) {
            "file:///android_asset/"
        } else {
            val uri = URI(spec.startUrl)
            uri.scheme + "://" + uri.host + "/"
        }
        val runtimePermissions = spec.hardware.mapNotNull {
            when (it) {
                WebHardware.CAMERA -> "android.permission.CAMERA"
                WebHardware.MICROPHONE -> "android.permission.RECORD_AUDIO"
                WebHardware.LOCATION -> "android.permission.ACCESS_FINE_LOCATION"
            }
        }
        val code = StringBuilder()
        code.append("package ").append(spec.packageName).append("\n\n")
        code.append(SHELL_IMPORTS)
        code.append("\n/**\n * WebView shell generated by Zip_Bug HTML App creator.\n")
        code.append(" * Start: ").append(startUrl).append("\n */\n")
        code.append("class MainActivity : Activity() {\n\n")
        code.append("    private lateinit var webView: WebView\n\n")
        code.append("    private val startUrl = ")
            .append(kotlinString(startUrl)).append("\n")
        code.append("    private val trustedPrefix = ")
            .append(kotlinString(trustedPrefix)).append("\n\n")
        code.append(SHELL_ON_CREATE_HEAD)
        if (spec.fullscreen) code.append(SHELL_FULLSCREEN)
        if (spec.keepScreenOn) code.append(SHELL_KEEP_ON)
        code.append(SHELL_ON_CREATE_SETTINGS)
        if (spec.userAgent != null) {
            code.append("        settings.userAgentString = ")
                .append(kotlinString(spec.userAgent)).append("\n")
        }
        if (WebHardware.LOCATION in spec.hardware) {
            code.append("        settings.setGeolocationEnabled(true)\n")
        }
        code.append(SHELL_ON_CREATE_CLIENTS)
        if (runtimePermissions.isNotEmpty()) {
            code.append("\n        requestPermissions(\n")
            code.append("            arrayOf(\n")
            for (permission in runtimePermissions) {
                code.append("                ")
                    .append(kotlinString(permission)).append(",\n")
            }
            code.append("            ),\n            1001\n        )\n")
        }
        code.append(SHELL_ON_CREATE_TAIL)
        code.append(SHELL_TRUST_AND_API)
        code.append(SHELL_CLIENTS)
        code.append(SHELL_LIFECYCLE)
        code.append("}\n")
        return code.toString()
    }

    private fun readme(spec: WebAppSpec): String = buildString {
        append("# ").append(spec.name).append("\n\n")
        append("Package: `").append(spec.packageName).append("`\n\n")
        append("## Type\n")
        append("HTML app (WebView shell) generated by Zip_Bug Antigravity Studio.\n\n")
        if (spec.startUrl != null) {
            append("Start URL: ").append(spec.startUrl).append("\n\n")
        } else {
            append(
                "Bundled web files live in `app/src/main/assets/www/` " +
                    "and load offline; `index.html` is the entry file. " +
                    "Replace them with your own HTML/CSS/JS (text files) " +
                    "and rebuild.\n\n"
            )
        }
        append("## Native bridge\n")
        append(
            "Pages on the app's own origin can call `window.AndroidAPI`:\n" +
                "`toast(text)`, `vibrate(ms)`, `shareText(text)`, " +
                "`copyToClipboard(text)`, `getAppInfo()`. The bridge " +
                "ignores calls from any other origin.\n\n"
        )
        append("## Build\n")
        append("Java 17, Gradle 8.9 and Android SDK 34 are expected.\n\n")
        append("In Zip_Bug / Termux:\n")
        append("`/apkbuilder --debug /path/to/this/project`\n")
    }

    private fun kotlinString(value: String): String =
        "\"" + value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("$", "\\$")
            .replace("\n", "\\n")
            .replace("\r", "\\r") + "\""

    private fun xml(value: String): String =
        value.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")

    private fun html(value: String): String =
        value.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")

    const val SHELL_IMPORTS = """import android.app.Activity
import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.View
import android.view.WindowManager
import android.webkit.GeolocationPermissions
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast

"""

    const val SHELL_ON_CREATE_HEAD = """    override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
"""

    const val SHELL_FULLSCREEN = """        window.setFlags(
        WindowManager.LayoutParams.FLAG_FULLSCREEN,
        WindowManager.LayoutParams.FLAG_FULLSCREEN
    )
"""

    const val SHELL_KEEP_ON = """        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
"""

    const val SHELL_ON_CREATE_SETTINGS = """        webView = WebView(this)
    setContentView(webView)

    val settings = webView.settings
    settings.javaScriptEnabled = true
    settings.domStorageEnabled = true
    settings.databaseEnabled = true
    settings.allowFileAccess = true
    settings.mediaPlaybackRequiresUserGesture = false
    settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
"""

    const val SHELL_ON_CREATE_CLIENTS = """        webView.addJavascriptInterface(AndroidApi(), "AndroidAPI")
    webView.webViewClient = ShellWebViewClient()
    webView.webChromeClient = ShellWebChromeClient()
    webView.setDownloadListener { url, _, contentDisposition, mimeType, _ ->
        handleDownload(url, contentDisposition, mimeType)
    }
"""

    const val SHELL_ON_CREATE_TAIL = """
    if (savedInstanceState != null) {
        webView.restoreState(savedInstanceState)
    } else {
        webView.loadUrl(startUrl)
    }
}

private fun handleDownload(
    url: String?,
    contentDisposition: String?,
    mimeType: String?
) {
    if (url == null || !(url.startsWith("https://") || url.startsWith("http://"))) {
        Toast.makeText(
            this,
            "This download type is not supported.",
            Toast.LENGTH_LONG
        ).show()
        return
    }
    val request = DownloadManager.Request(Uri.parse(url))
    request.setMimeType(mimeType)
    request.setNotificationVisibility(
        DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
    )
    request.setDestinationInExternalPublicDir(
        Environment.DIRECTORY_DOWNLOADS,
        URLUtil.guessFileName(url, contentDisposition, mimeType)
    )
    val manager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    manager.enqueue(request)
    Toast.makeText(this, "Downloading…", Toast.LENGTH_SHORT).show()
}

"""

    const val SHELL_TRUST_AND_API = """    /** The JS bridge only answers on the app's own pages. */
private fun isTrustedPage(): Boolean {
    val current = webView.url ?: return false
    return current.startsWith("file:///android_asset/") ||
        current.startsWith(trustedPrefix)
}

inner class AndroidApi {
    @JavascriptInterface
    fun toast(text: String) {
        if (!isTrustedPage()) return
        runOnUiThread {
            Toast.makeText(
                this@MainActivity,
                text.take(200),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    @JavascriptInterface
    fun vibrate(milliseconds: Long) {
        if (!isTrustedPage()) return
        val ms = milliseconds.coerceIn(1L, 1000L)
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(
                VibrationEffect.createOneShot(
                    ms,
                    VibrationEffect.DEFAULT_AMPLITUDE
                )
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(ms)
        }
    }

    @JavascriptInterface
    fun shareText(text: String) {
        if (!isTrustedPage()) return
        runOnUiThread {
            val intent = Intent(Intent.ACTION_SEND)
            intent.type = "text/plain"
            intent.putExtra(Intent.EXTRA_TEXT, text.take(4000))
            startActivity(Intent.createChooser(intent, "Share"))
        }
    }

    @JavascriptInterface
    fun copyToClipboard(text: String) {
        if (!isTrustedPage()) return
        val clipboard =
            getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(
            ClipData.newPlainText("web", text.take(4000))
        )
    }

    @JavascriptInterface
    fun getAppInfo(): String {
        if (!isTrustedPage()) return "{}"
        return "{\"version\":\"1.0.0\"}"
    }
}

"""

    const val SHELL_CLIENTS = """    inner class ShellWebViewClient : WebViewClient() {
    override fun shouldOverrideUrlLoading(
        view: WebView,
        request: WebResourceRequest
    ): Boolean {
        val url = request.url
        val scheme = url.scheme ?: return false
        if (
            scheme == "https" || scheme == "http" ||
            scheme == "file"
        ) {
            return false
        }
        return try {
            startActivity(Intent(Intent.ACTION_VIEW, url))
            true
        } catch (e: Exception) {
            true
        }
    }

    override fun onReceivedError(
        view: WebView,
        request: WebResourceRequest,
        error: WebResourceError
    ) {
        super.onReceivedError(view, request, error)
        if (request.isForMainFrame) {
            val html = "<html><body style='background:#121212;color:#f7f3ef;" +
                "font-family:sans-serif;padding:24px'>" +
                "<h2>Page unavailable</h2>" +
                "<p>Check the connection, then retry.</p>" +
                "<p><a style='color:#ff9f43' href='" + startUrl +
                "'>Retry</a></p></body></html>"
            view.loadDataWithBaseURL(
                null,
                html,
                "text/html",
                "utf-8",
                null
            )
        }
    }
}

inner class ShellWebChromeClient : WebChromeClient() {
    override fun onPermissionRequest(request: PermissionRequest) {
        val granted = mutableListOf<String>()
        for (resource in request.resources) {
            val permission = when (resource) {
                PermissionRequest.RESOURCE_VIDEO_CAPTURE ->
                    "android.permission.CAMERA"
                PermissionRequest.RESOURCE_AUDIO_CAPTURE ->
                    "android.permission.RECORD_AUDIO"
                else -> null
            }
            if (
                permission != null &&
                isTrustedPage() &&
                checkSelfPermission(permission) ==
                    PackageManager.PERMISSION_GRANTED
            ) {
                granted.add(resource)
            }
        }
        if (granted.isEmpty()) {
            request.deny()
        } else {
            request.grant(granted.toTypedArray())
        }
    }

    override fun onGeolocationPermissionsShowPrompt(
        origin: String?,
        callback: GeolocationPermissions.Callback
    ) {
        val allowed = isTrustedPage() &&
            checkSelfPermission("android.permission.ACCESS_FINE_LOCATION") ==
                PackageManager.PERMISSION_GRANTED
        callback.invoke(origin, allowed, false)
    }
}

"""

    const val SHELL_LIFECYCLE = """    @Deprecated("Uses the WebView history before finishing")
override fun onBackPressed() {
    if (this::webView.isInitialized && webView.canGoBack()) {
        webView.goBack()
    } else {
        super.onBackPressed()
    }
}

override fun onSaveInstanceState(outState: Bundle) {
    super.onSaveInstanceState(outState)
    if (this::webView.isInitialized) {
        webView.saveState(outState)
    }
}

override fun onPause() {
    if (this::webView.isInitialized) {
        webView.onPause()
    }
    super.onPause()
}

override fun onResume() {
    super.onResume()
    if (this::webView.isInitialized) {
        webView.onResume()
    }
}

override fun onDestroy() {
    if (this::webView.isInitialized) {
        webView.destroy()
    }
    super.onDestroy()
}
"""
}
