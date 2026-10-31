package com.zipbug.base.creator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class HtmlWebViewScaffolderTest {

    private fun localSpec(
        webFiles: Map<String, String> = mapOf(
            "index.html" to "<html><body>Hello</body></html>"
        )
    ) = WebAppSpec(
        name = "My Web App",
        packageName = "com.example.webapp",
        goal = "Wrap my site",
        webFiles = webFiles
    )

    private fun mainActivity(files: Map<String, String>): String =
        files.getValue(
            "app/src/main/java/com/example/webapp/MainActivity.kt"
        )

    private fun manifest(files: Map<String, String>): String =
        files.getValue("app/src/main/AndroidManifest.xml")

    @Test
    fun localAppBundlesIndexAndWebViewShell() {
        val files = HtmlWebViewScaffolder.files(localSpec())

        assertEquals(
            "<html><body>Hello</body></html>",
            files["app/src/main/assets/www/index.html"]
        )
        val main = mainActivity(files)
        assertTrue(main.contains("file:///android_asset/www/index.html"))
        assertTrue(main.contains("settings.javaScriptEnabled = true"))
        assertTrue(main.contains("settings.domStorageEnabled = true"))
        assertTrue(main.contains("addJavascriptInterface(AndroidApi(), \"AndroidAPI\")"))
        assertTrue(manifest(files).contains("android.permission.INTERNET"))
        assertTrue(manifest(files).contains("android.permission.VIBRATE"))
        // Web shell creates its WebView in code: no template layout file.
        assertFalse(files.containsKey("app/src/main/res/layout/activity_main.xml"))
        // Open Source Licenses files ride along from AppScaffolder.
        assertTrue(files.containsKey("app/src/main/assets/third_party_notices.txt"))
        assertTrue(files.containsKey("license-manifest.json"))
        // 14 base files - 1 removed layout + 1 web file.
        assertEquals(14, files.size)
    }

    @Test
    fun urlAppLoadsRemoteStartWithoutLocalFiles() {
        val files = HtmlWebViewScaffolder.files(
            localSpec(webFiles = emptyMap()).copy(
                startUrl = "https://example.com/app"
            )
        )
        val main = mainActivity(files)
        assertTrue(main.contains("\"https://example.com/app\""))
        assertTrue(main.contains("\"https://example.com/\""))
        assertFalse(files.containsKey("app/src/main/assets/www/index.html"))
        assertTrue(manifest(files).contains("android.permission.INTERNET"))
    }

    @Test
    fun hardwarePermissionsAreDeclaredAndWired() {
        val files = HtmlWebViewScaffolder.files(
            localSpec().copy(
                hardware = setOf(WebHardware.CAMERA, WebHardware.LOCATION)
            )
        )
        val manifest = manifest(files)
        assertTrue(manifest.contains("android.permission.CAMERA"))
        assertTrue(manifest.contains("android.permission.ACCESS_FINE_LOCATION"))
        val main = mainActivity(files)
        assertTrue(main.contains("PermissionRequest.RESOURCE_VIDEO_CAPTURE"))
        assertTrue(main.contains("settings.setGeolocationEnabled(true)"))
        assertTrue(main.contains("requestPermissions("))
    }

    @Test
    fun displayFlagsReachTheShellAndManifest() {
        val files = HtmlWebViewScaffolder.files(
            localSpec().copy(
                fullscreen = true,
                keepScreenOn = true,
                orientation = WebOrientation.PORTRAIT,
                userAgent = "ZipBugWeb/1.0"
            )
        )
        val main = mainActivity(files)
        assertTrue(main.contains("FLAG_FULLSCREEN"))
        assertTrue(main.contains("FLAG_KEEP_SCREEN_ON"))
        assertTrue(main.contains("settings.userAgentString = \"ZipBugWeb/1.0\""))
        assertTrue(
            manifest(files).contains("android:screenOrientation=\"portrait\"")
        )
    }

    @Test
    fun localModeRequiresIndexHtml() {
        try {
            HtmlWebViewScaffolder.files(
                localSpec(webFiles = mapOf("style.css" to "body{}"))
            )
            fail("Expected missing index.html to be rejected")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("index.html"))
        }
    }

    @Test
    fun rejectsEscapingWebPaths() {
        for (bad in listOf("../evil.html", "/abs.html", "a/../../b.html")) {
            try {
                HtmlWebViewScaffolder.files(
                    localSpec(
                        webFiles = mapOf(
                            "index.html" to "<html/>",
                            bad to "x"
                        )
                    )
                )
                fail("Expected path to be rejected: $bad")
            } catch (expected: IllegalArgumentException) {
                // expected
            }
        }
    }

    @Test
    fun rejectsPlainHttpStartUrl() {
        try {
            HtmlWebViewScaffolder.files(
                localSpec(webFiles = emptyMap()).copy(
                    startUrl = "http://example.com"
                )
            )
            fail("Expected http:// start URL to be rejected")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("https://"))
        }
    }

    @Test
    fun starterPageUsesNameGoalAndBridge() {
        val html = HtmlWebViewScaffolder.starterIndexHtml(
            "Shop <One>",
            "Sell things"
        )
        assertTrue(html.contains("Shop &lt;One&gt;"))
        assertTrue(html.contains("Sell things"))
        assertTrue(html.contains("window.AndroidAPI"))
        assertTrue(html.contains("AndroidAPI.toast"))
    }

    @Test
    fun bridgeV2MethodsAndEventChannelAreGenerated() {
        val main = mainActivity(HtmlWebViewScaffolder.files(localSpec()))
        for (needle in listOf(
            "fun vibratePattern(pattern: String)",
            "fun cancelVibrate()",
            "fun torch(on: Boolean)",
            "fun showNotification(title: String, body: String)",
            "fun networkStatus()",
            "fun getBattery()",
            "fun getDeviceInfo(): String",
            "fun getClipboard()",
            "fun setKeepScreenOn(on: Boolean)",
            "fun storageSave(name: String, content: String)",
            "fun storageLoad(name: String)",
            "fun storageList()",
            "window.onAndroidEvent",
            "fun isTrustedPage(): Boolean"
        )) {
            assertTrue("missing in shell: $needle", main.contains(needle))
        }
    }

    @Test
    fun notificationsHardwareAddsPermissionAndRuntimeRequest() {
        val files = HtmlWebViewScaffolder.files(
            localSpec().copy(
                hardware = setOf(WebHardware.NOTIFICATIONS)
            )
        )
        assertTrue(
            manifest(files).contains("android.permission.POST_NOTIFICATIONS")
        )
        val main = mainActivity(files)
        assertTrue(main.contains("Build.VERSION_CODES.TIRAMISU"))
        assertTrue(main.contains("NotificationChannel("))
    }

    @Test
    fun wizardOptionsReachTheShell() {
        val files = HtmlWebViewScaffolder.files(
            localSpec().copy(
                pullToRefresh = true,
                splashEnabled = true,
                splashSeconds = 3,
                splashColor = "#202020",
                statusBarColor = "#101010",
                exitConfirmation = true
            )
        )
        val main = mainActivity(files)
        assertTrue(main.contains("PullRefreshContainer"))
        assertTrue(main.contains("window.statusBarColor = Color.parseColor(\"#101010\")"))
        assertTrue(main.contains("Color.parseColor(\"#202020\")"))
        assertTrue(main.contains("3000L"))
        // Exit confirmation replaces the default back behaviour.
        assertTrue(main.contains("Do you want to exit this app?"))
    }

    @Test
    fun noInternetDialogReplacesErrorPage() {
        val main = mainActivity(
            HtmlWebViewScaffolder.files(
                localSpec().copy(noInternetDialog = true)
            )
        )
        assertTrue(main.contains("private val noInternetDialog = true"))
        assertTrue(main.contains("\"No connection\""))
        val plain = mainActivity(HtmlWebViewScaffolder.files(localSpec()))
        assertTrue(plain.contains("private val noInternetDialog = false"))
    }

    @Test
    fun deepLinkSchemeAddsManifestFilter() {
        val files = HtmlWebViewScaffolder.files(
            localSpec().copy(urlScheme = "mywebapp")
        )
        val manifest = manifest(files)
        assertTrue(manifest.contains("android.intent.action.VIEW"))
        assertTrue(manifest.contains("android:scheme=\"mywebapp\""))
    }

    @Test
    fun rejectsInvalidV2Options() {
        val badSpecs = listOf(
            localSpec().copy(urlScheme = "https"),
            localSpec().copy(urlScheme = "1bad"),
            localSpec().copy(urlScheme = "Bad Scheme"),
            localSpec().copy(splashColor = "red"),
            localSpec().copy(statusBarColor = "#12345"),
            localSpec().copy(splashSeconds = 42)
        )
        for (bad in badSpecs) {
            try {
                HtmlWebViewScaffolder.files(bad)
                fail("Expected invalid spec to be rejected: $bad")
            } catch (expected: IllegalArgumentException) {
                // expected
            }
        }
    }
}
