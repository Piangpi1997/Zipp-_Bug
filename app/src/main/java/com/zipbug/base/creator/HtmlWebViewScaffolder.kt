package com.zipbug.base.creator

import com.zipbug.base.termux.LogSanitizer
import java.net.URI

/** Screen orientation for a generated web app. */
enum class WebOrientation { AUTO, PORTRAIT, LANDSCAPE }

/** Optional hardware access a generated web app can request. */
enum class WebHardware { CAMERA, MICROPHONE, LOCATION, NOTIFICATIONS }

/**
 * Specification for an HTML-to-app project, modelled on the structure of
 * dedicated HTML-to-APK builder apps: bundled local web files (offline)
 * or a live start URL, WebView settings, an origin-restricted native JS
 * bridge, and optional hardware permissions.
 *
 * v2 adds the features the reference builders expose: a wider JS bridge
 * with an `onAndroidEvent` result channel (vibration patterns, torch,
 * notifications, network status, app storage, battery, device info,
 * clipboard read), pull-to-refresh, a no-connection dialog, an exit
 * confirmation, a splash screen, a status bar colour, and a custom
 * deep-link URL scheme.
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
    val hardware: Set<WebHardware> = emptySet(),
    val pullToRefresh: Boolean = false,
    val noInternetDialog: Boolean = false,
    val exitConfirmation: Boolean = false,
    val splashEnabled: Boolean = false,
    val splashSeconds: Int = 2,
    val splashColor: String = "#121212",
    val statusBarColor: String? = null,
    val urlScheme: String? = null
)

object HtmlWebViewScaffolder {
    private const val MAX_WEB_FILE_CHARS = 1_000_000
    private const val MAX_WEB_TOTAL_CHARS = 8_000_000
    private const val MAX_WEB_FILES = 200
    private const val LOCAL_START = "file:///android_asset/www/index.html"

    private val HEX_COLOR = Regex("^#[0-9a-fA-F]{6}$")
    private val URL_SCHEME = Regex("^[a-z][a-z0-9+.-]{1,30}$")
    private val RESERVED_SCHEMES =
        setOf("http", "https", "file", "android", "content", "intent")

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
        require(spec.splashSeconds in 0..10) {
            "Splash duration must be between 0 and 10 seconds"
        }
        require(HEX_COLOR.matches(spec.splashColor)) {
            "Splash colour must be a #RRGGBB hex colour"
        }
        if (spec.statusBarColor != null) {
            require(HEX_COLOR.matches(spec.statusBarColor)) {
                "Status bar colour must be a #RRGGBB hex colour"
            }
        }
        if (spec.urlScheme != null) {
            require(URL_SCHEME.matches(spec.urlScheme)) {
                "URL scheme must start with a letter and use only " +
                    "lowercase letters, digits, '+', '-' or '.'"
            }
            require(spec.urlScheme !in RESERVED_SCHEMES) {
                "URL scheme is reserved: ${spec.urlScheme}"
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
        if (WebHardware.NOTIFICATIONS in spec.hardware) {
            permissions += "android.permission.POST_NOTIFICATIONS"
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
        val schemeFilter = if (spec.urlScheme == null) {
            ""
        } else {
            "\n                    <intent-filter>\n" +
                "                        <action android:name=\"android.intent.action.VIEW\" />\n" +
                "                        <category android:name=\"android.intent.category.DEFAULT\" />\n" +
                "                        <category android:name=\"android.intent.category.BROWSABLE\" />\n" +
                "                        <data android:scheme=\"" +
                xml(spec.urlScheme) + "\" />\n" +
                "                    </intent-filter>"
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
                    </intent-filter>__SCHEME__
                </activity>
            </application>
        </manifest>
        """.trimIndent()
            .replace("__PERMISSIONS__", permissionLines)
            .replace("__ORIENTATION__", orientationAttr)
            .replace("__SCHEME__", schemeFilter) + "\n"
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
                WebHardware.NOTIFICATIONS -> null // requested separately (API 33+)
            }
        }
        val code = StringBuilder()
        code.append("package ").append(spec.packageName).append("\n\n")
        code.append(SHELL_IMPORTS)
        code.append("\n/**\n * WebView shell generated by Zip_Bug HTML App creator.\n")
        code.append(" * Start: ").append(startUrl).append("\n */\n")
        code.append("class MainActivity : Activity() {\n\n")
        code.append("    private lateinit var webView: WebView\n")
        code.append("    private lateinit var rootLayout: FrameLayout\n")
        code.append("    private lateinit var refreshSpinner: ProgressBar\n")
        code.append("    private var errorDialogShowing = false\n\n")
        code.append("    private val startUrl = ")
            .append(kotlinString(startUrl)).append("\n")
        code.append("    private val trustedPrefix = ")
            .append(kotlinString(trustedPrefix)).append("\n")
        code.append("    private val noInternetDialog = ")
            .append(spec.noInternetDialog).append("\n")
        code.append("    private val pullTriggerPx = ")
            .append(240).append("\n\n")
        code.append(SHELL_ON_CREATE_HEAD)
        if (spec.fullscreen) code.append(SHELL_FULLSCREEN)
        if (spec.statusBarColor != null) {
            code.append("        window.statusBarColor = Color.parseColor(")
                .append(kotlinString(spec.statusBarColor)).append(")\n")
        }
        code.append(
            if (spec.pullToRefresh) SHELL_ROOT_PTR else SHELL_ROOT_PLAIN
        )
        if (spec.pullToRefresh) code.append(SHELL_PTR_WIRING)
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
        if (WebHardware.NOTIFICATIONS in spec.hardware) {
            code.append(SHELL_NOTIFICATION_PERMISSION)
        }
        if (spec.splashEnabled) {
            code.append(
                splashBlock(
                    spec.splashColor,
                    spec.splashSeconds * 1000L
                )
            )
        }
        code.append(SHELL_ON_CREATE_TAIL)
        if (spec.pullToRefresh) code.append(SHELL_PTR_CLASS)
        code.append(SHELL_TRUST_AND_API)
        code.append(SHELL_CLIENTS)
        code.append(
            if (spec.exitConfirmation) {
                SHELL_LIFECYCLE_EXIT_CONFIRM
            } else {
                SHELL_LIFECYCLE
            }
        )
        code.append("}\n")
        return code.toString()
    }

    private fun splashBlock(color: String, millis: Long): String =
        "\n        val splash = FrameLayout(this)\n" +
            "        splash.setBackgroundColor(Color.parseColor(" +
            kotlinString(color) + "))\n" +
            "        val splashLabel = TextView(this)\n" +
            "        splashLabel.text =\n" +
            "            applicationInfo.loadLabel(packageManager).toString()\n" +
            "        splashLabel.setTextColor(Color.WHITE)\n" +
            "        splashLabel.textSize = 24f\n" +
            "        splashLabel.gravity = Gravity.CENTER\n" +
            "        splash.addView(\n" +
            "            splashLabel,\n" +
            "            FrameLayout.LayoutParams(\n" +
            "                FrameLayout.LayoutParams.MATCH_PARENT,\n" +
            "                FrameLayout.LayoutParams.MATCH_PARENT\n" +
            "            )\n" +
            "        )\n" +
            "        rootLayout.addView(\n" +
            "            splash,\n" +
            "            FrameLayout.LayoutParams(\n" +
            "                FrameLayout.LayoutParams.MATCH_PARENT,\n" +
            "                FrameLayout.LayoutParams.MATCH_PARENT\n" +
            "            )\n" +
            "        )\n" +
            "        Handler(Looper.getMainLooper()).postDelayed(\n" +
            "            { rootLayout.removeView(splash) },\n" +
            "            " + millis + "L\n" +
            "        )\n"

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
            "Pages on the app's own origin can call `window.AndroidAPI`. " +
                "Synchronous: `toast(text)`, `vibrate(ms)`, " +
                "`vibratePattern('[120,60,240]')`, `cancelVibrate()`, " +
                "`torch(true/false)`, `showNotification(title, body)`, " +
                "`shareText(text)`, `copyToClipboard(text)`, " +
                "`getClipboard()`, `getAppInfo()`, `getDeviceInfo()`, " +
                "`isOnline()`, `networkStatus()`, `getBattery()`, " +
                "`setKeepScreenOn(on)`, `storageSave(name, content)`, " +
                "`storageLoad(name)`, `storageDelete(name)`, " +
                "`storageList()`. Results of the asynchronous calls are " +
                "delivered to `window.onAndroidEvent(event, dataJson)` " +
                "with event names `vibrateResult`, `torchResult`, " +
                "`notificationResult`, `networkStatus`, `batteryResult`, " +
                "`clipboardResult` and `storageResult`. The bridge " +
                "ignores calls from any other origin. App storage files " +
                "live in the app's private storage and never leave the " +
                "device.\n\n"
        )
        if (spec.urlScheme != null) {
            append("## Deep link\n")
            append(
                "Links with the `" + spec.urlScheme + "://` scheme open " +
                    "this app (declared in the manifest).\n\n"
            )
        }
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
import android.app.AlertDialog
import android.app.DownloadManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.Gravity
import android.view.MotionEvent
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
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

"""

    const val SHELL_ON_CREATE_HEAD = """    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

"""

    const val SHELL_FULLSCREEN = """        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
"""

    const val SHELL_ROOT_PLAIN = """        rootLayout = FrameLayout(this)
        webView = WebView(this)
        rootLayout.addView(
            webView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        refreshSpinner = ProgressBar(this)
        refreshSpinner.visibility = View.GONE
        val spinnerParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        )
        spinnerParams.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        spinnerParams.topMargin = 48
        rootLayout.addView(refreshSpinner, spinnerParams)
        setContentView(rootLayout)

"""

    const val SHELL_ROOT_PTR = """        val pullContainer = PullRefreshContainer(this)
        rootLayout = pullContainer
        webView = WebView(this)
        pullContainer.addView(
            webView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        refreshSpinner = ProgressBar(this)
        refreshSpinner.visibility = View.GONE
        val spinnerParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        )
        spinnerParams.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        spinnerParams.topMargin = 48
        pullContainer.addView(refreshSpinner, spinnerParams)
        setContentView(pullContainer)

"""

    const val SHELL_PTR_WIRING = """        pullContainer.onRefresh = {
            refreshSpinner.visibility = View.VISIBLE
            webView.reload()
        }
"""

    const val SHELL_KEEP_ON = """        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
"""

    const val SHELL_ON_CREATE_SETTINGS = """        val settings = webView.settings
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

    const val SHELL_NOTIFICATION_PERMISSION = """
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(
                arrayOf("android.permission.POST_NOTIFICATIONS"),
                1002
            )
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
        if (url == null ||
            !(url.startsWith("https://") || url.startsWith("http://"))
        ) {
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

    const val SHELL_PTR_CLASS = """    /**
     * Minimal pull-to-refresh container: a downward pull that starts
     * while the WebView is scrolled to the top triggers a reload.
     */
    inner class PullRefreshContainer(context: Context) : FrameLayout(context) {
        var onRefresh: (() -> Unit)? = null
        private var startY = 0f
        private var pulling = false

        override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startY = ev.y
                    pulling = false
                }
                MotionEvent.ACTION_MOVE -> {
                    if (webView.scrollY == 0 && ev.y - startY > 24f) {
                        pulling = true
                        return true
                    }
                }
            }
            return super.onInterceptTouchEvent(ev)
        }

        override fun onTouchEvent(ev: MotionEvent): Boolean {
            when (ev.actionMasked) {
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (pulling && ev.y - startY > pullTriggerPx) {
                        onRefresh?.invoke()
                    }
                    pulling = false
                }
            }
            return true
        }
    }

"""

    const val SHELL_TRUST_AND_API = """    /** The JS bridge only answers on the app's own pages. */
    private fun isTrustedPage(): Boolean {
        val current = webView.url ?: return false
        return current.startsWith("file:///android_asset/") ||
            current.startsWith(trustedPrefix)
    }

    /**
     * Delivers an asynchronous bridge result to the page as
     * window.onAndroidEvent(event, dataJsonString).
     */
    private fun sendEvent(event: String, data: JSONObject) {
        val script = "window.onAndroidEvent&&window.onAndroidEvent(" +
            JSONObject.quote(event) + "," +
            JSONObject.quote(data.toString()) + ")"
        runOnUiThread { webView.evaluateJavascript(script, null) }
    }

    private fun parsePattern(pattern: String): LongArray? {
        val cleaned = pattern.trim().removePrefix("[").removeSuffix("]")
        if (cleaned.isBlank()) return null
        val parts = cleaned.split(",")
        if (parts.size > 32) return null
        val values = LongArray(parts.size)
        for (i in parts.indices) {
            values[i] = parts[i].trim().toLongOrNull()
                ?.coerceIn(0L, 5000L) ?: return null
        }
        return values
    }

    private fun networkInfo(): JSONObject {
        val info = JSONObject()
        val manager =
            getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = manager.activeNetwork
        val caps = if (network != null) {
            manager.getNetworkCapabilities(network)
        } else {
            null
        }
        val online = caps != null && caps.hasCapability(
            NetworkCapabilities.NET_CAPABILITY_INTERNET
        )
        info.put("online", online)
        info.put(
            "type",
            when {
                caps == null -> "none"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
                caps.hasTransport(
                    NetworkCapabilities.TRANSPORT_CELLULAR
                ) -> "cellular"
                caps.hasTransport(
                    NetworkCapabilities.TRANSPORT_ETHERNET
                ) -> "ethernet"
                else -> "other"
            }
        )
        return info
    }

    private fun storageDir(): File {
        val dir = File(filesDir, "web_storage")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun safeStorageName(name: String): String? {
        if (name.isBlank() || name.length > 64) return null
        if (name.contains("..")) return null
        val allowed = name.all {
            it.isLetterOrDigit() || it == '.' || it == '_' || it == '-'
        }
        return if (allowed) name else null
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
        fun vibratePattern(pattern: String) {
            if (!isTrustedPage()) return
            val result = JSONObject()
            val timings = parsePattern(pattern)
            if (timings == null) {
                result.put("ok", false)
                result.put("error", "invalid_pattern")
            } else {
                val vibrator =
                    getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(
                        VibrationEffect.createWaveform(timings, -1)
                    )
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(timings, -1)
                }
                result.put("ok", true)
                result.put("steps", timings.size)
            }
            sendEvent("vibrateResult", result)
        }

        @JavascriptInterface
        fun cancelVibrate() {
            if (!isTrustedPage()) return
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            vibrator.cancel()
        }

        @JavascriptInterface
        fun torch(on: Boolean) {
            if (!isTrustedPage()) return
            val result = JSONObject()
            try {
                val manager =
                    getSystemService(Context.CAMERA_SERVICE) as CameraManager
                val cameraId = manager.cameraIdList.firstOrNull { id ->
                    manager.getCameraCharacteristics(id)
                        .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                }
                if (cameraId == null) {
                    result.put("ok", false)
                    result.put("error", "no_flash")
                } else {
                    manager.setTorchMode(cameraId, on)
                    result.put("ok", true)
                    result.put("on", on)
                }
            } catch (e: Exception) {
                result.put("ok", false)
                result.put("error", "torch_failed")
            }
            sendEvent("torchResult", result)
        }

        @JavascriptInterface
        fun showNotification(title: String, body: String) {
            if (!isTrustedPage()) return
            runOnUiThread {
                val result = JSONObject()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    checkSelfPermission(
                        "android.permission.POST_NOTIFICATIONS"
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    result.put("ok", false)
                    result.put("error", "permission_denied")
                    sendEvent("notificationResult", result)
                    return@runOnUiThread
                }
                val manager = getSystemService(
                    Context.NOTIFICATION_SERVICE
                ) as NotificationManager
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    manager.createNotificationChannel(
                        NotificationChannel(
                            "web_app",
                            "Web App",
                            NotificationManager.IMPORTANCE_DEFAULT
                        )
                    )
                }
                val builder = if (
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ) {
                    Notification.Builder(this@MainActivity, "web_app")
                } else {
                    @Suppress("DEPRECATION")
                    Notification.Builder(this@MainActivity)
                }
                builder.setContentTitle(title.take(100))
                builder.setContentText(body.take(300))
                builder.setSmallIcon(android.R.drawable.ic_dialog_info)
                builder.setAutoCancel(true)
                manager.notify(
                    (System.currentTimeMillis() % 100000).toInt(),
                    builder.build()
                )
                result.put("ok", true)
                sendEvent("notificationResult", result)
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
        fun getClipboard() {
            if (!isTrustedPage()) return
            runOnUiThread {
                val clipboard = getSystemService(
                    Context.CLIPBOARD_SERVICE
                ) as ClipboardManager
                val text = clipboard.primaryClip
                    ?.getItemAt(0)
                    ?.coerceToText(this@MainActivity)
                    ?.toString() ?: ""
                val result = JSONObject()
                result.put("text", text)
                sendEvent("clipboardResult", result)
            }
        }

        @JavascriptInterface
        fun getAppInfo(): String {
            if (!isTrustedPage()) return "{}"
            val info = JSONObject()
            info.put(
                "name",
                applicationInfo.loadLabel(packageManager).toString()
            )
            info.put("package", packageName)
            info.put("version", "1.0.0")
            return info.toString()
        }

        @JavascriptInterface
        fun getDeviceInfo(): String {
            if (!isTrustedPage()) return "{}"
            val info = JSONObject()
            info.put("manufacturer", Build.MANUFACTURER)
            info.put("model", Build.MODEL)
            info.put("sdk", Build.VERSION.SDK_INT)
            info.put("release", Build.VERSION.RELEASE)
            return info.toString()
        }

        @JavascriptInterface
        fun isOnline(): Boolean {
            if (!isTrustedPage()) return false
            return networkInfo().optBoolean("online")
        }

        @JavascriptInterface
        fun networkStatus() {
            if (!isTrustedPage()) return
            sendEvent("networkStatus", networkInfo())
        }

        @JavascriptInterface
        fun getBattery() {
            if (!isTrustedPage()) return
            val result = JSONObject()
            val intent = registerReceiver(
                null,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            )
            if (intent != null) {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                val status = intent.getIntExtra(
                    BatteryManager.EXTRA_STATUS, -1
                )
                result.put(
                    "level",
                    if (level >= 0 && scale > 0) level * 100 / scale else -1
                )
                result.put(
                    "charging",
                    status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL
                )
            }
            sendEvent("batteryResult", result)
        }

        @JavascriptInterface
        fun setKeepScreenOn(on: Boolean) {
            if (!isTrustedPage()) return
            runOnUiThread {
                if (on) {
                    window.addFlags(
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                    )
                } else {
                    window.clearFlags(
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                    )
                }
            }
        }

        @JavascriptInterface
        fun storageSave(name: String, content: String) {
            if (!isTrustedPage()) return
            val result = JSONObject()
            result.put("action", "save")
            val safe = safeStorageName(name)
            if (safe == null) {
                result.put("ok", false)
                result.put("error", "invalid_name")
            } else {
                try {
                    File(storageDir(), safe)
                        .writeText(content.take(200000))
                    result.put("ok", true)
                    result.put("name", safe)
                } catch (e: Exception) {
                    result.put("ok", false)
                    result.put("error", "write_failed")
                }
            }
            sendEvent("storageResult", result)
        }

        @JavascriptInterface
        fun storageLoad(name: String) {
            if (!isTrustedPage()) return
            val result = JSONObject()
            result.put("action", "load")
            val safe = safeStorageName(name)
            if (safe == null) {
                result.put("ok", false)
                result.put("error", "invalid_name")
            } else {
                val file = File(storageDir(), safe)
                if (!file.exists()) {
                    result.put("ok", false)
                    result.put("error", "not_found")
                } else {
                    result.put("ok", true)
                    result.put("name", safe)
                    result.put("content", file.readText())
                }
            }
            sendEvent("storageResult", result)
        }

        @JavascriptInterface
        fun storageDelete(name: String) {
            if (!isTrustedPage()) return
            val result = JSONObject()
            result.put("action", "delete")
            val safe = safeStorageName(name)
            if (safe == null) {
                result.put("ok", false)
                result.put("error", "invalid_name")
            } else {
                val file = File(storageDir(), safe)
                result.put("ok", file.delete())
                result.put("name", safe)
            }
            sendEvent("storageResult", result)
        }

        @JavascriptInterface
        fun storageList() {
            if (!isTrustedPage()) return
            val result = JSONObject()
            result.put("action", "list")
            val names = JSONArray()
            storageDir().listFiles()?.forEach { names.put(it.name) }
            result.put("ok", true)
            result.put("files", names)
            sendEvent("storageResult", result)
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

        override fun onPageFinished(view: WebView, url: String?) {
            super.onPageFinished(view, url)
            if (this@MainActivity::refreshSpinner.isInitialized) {
                refreshSpinner.visibility = View.GONE
            }
        }

        override fun onReceivedError(
            view: WebView,
            request: WebResourceRequest,
            error: WebResourceError
        ) {
            super.onReceivedError(view, request, error)
            if (!request.isForMainFrame) return
            if (noInternetDialog) {
                if (!errorDialogShowing) {
                    errorDialogShowing = true
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("No connection")
                        .setMessage(
                            "Check your internet connection and try again."
                        )
                        .setPositiveButton("Retry") { _, _ ->
                            errorDialogShowing = false
                            view.loadUrl(startUrl)
                        }
                        .setNegativeButton("Close") { _, _ ->
                            errorDialogShowing = false
                        }
                        .setOnCancelListener { errorDialogShowing = false }
                        .show()
                }
                return
            }
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

    const val SHELL_LIFECYCLE_EXIT_CONFIRM = """    @Deprecated("Uses the WebView history before finishing")
    override fun onBackPressed() {
        if (this::webView.isInitialized && webView.canGoBack()) {
            webView.goBack()
        } else {
            AlertDialog.Builder(this)
                .setTitle("Exit")
                .setMessage("Do you want to exit this app?")
                .setPositiveButton("Exit") { _, _ -> finish() }
                .setNegativeButton("Stay", null)
                .show()
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
