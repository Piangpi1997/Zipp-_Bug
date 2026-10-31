package com.zipbug.base.creator

/** Categories of the built-in web template catalog. */
enum class WebTemplateCategory { INTERACTION, STARTERS, DEVICE, SYSTEM, LIBRARIES }

/**
 * A ready-to-use HTML page demonstrating one capability of the generated
 * WebView shell's `window.AndroidAPI` bridge (or a starter layout /
 * library page). Picking a template in the wizard pre-fills the page and
 * the hardware options it needs.
 */
data class WebTemplate(
    val id: String,
    val category: WebTemplateCategory,
    val title: String,
    val description: String,
    val snippet: String,
    val html: String,
    val icon: String,
    val colorHex: String,
    val hardware: Set<WebHardware> = emptySet(),
    val fullscreen: Boolean = false,
    val keepScreenOn: Boolean = false
)

/**
 * The built-in catalog. All pages are original Zip_Bug examples written
 * for this catalog; library templates load their frameworks from public
 * CDNs at runtime, so apps built from them need an internet connection.
 */
object WebTemplates {

    val ALL: List<WebTemplate> = listOf(
        WebTemplate(
            id = "vibrate",
            category = WebTemplateCategory.INTERACTION,
            icon = "📳",
            colorHex = "#7C4DFF",
            title = "Vibrate",
            description = "One-shot and pattern vibration with a result " +
                "event callback, like native capability demos in other " +
                "HTML-to-app builders.",
            snippet = "AndroidAPI.vibratePattern('[120,60,240]');",
            html = page(
                title = "Vibrate",
                body = """
                    <button id="b1">Short — vibrate(200)</button>
                    <button id="b2">Long — vibrate(1000)</button>
                    <button id="b3">Pattern — [120, 60, 240]</button>
                    <button id="b4">Cancel</button>
                    <pre id="out">Tap a button.</pre>
                """,
                script = """
                    const out = document.querySelector('#out');
                    window.onAndroidEvent = function (event, data) {
                      if (event === 'vibrateResult') {
                        out.textContent = 'vibrateResult: ' + data;
                      }
                    };
                    document.querySelector('#b1').onclick = () => AndroidAPI.vibrate(200);
                    document.querySelector('#b2').onclick = () => AndroidAPI.vibrate(1000);
                    document.querySelector('#b3').onclick = () =>
                      AndroidAPI.vibratePattern('[120,60,240]');
                    document.querySelector('#b4').onclick = () => {
                      AndroidAPI.cancelVibrate();
                      out.textContent = 'Vibration cancelled.';
                    };
                """
            )
        ),
        WebTemplate(
            id = "torch",
            category = WebTemplateCategory.INTERACTION,
            icon = "🔦",
            colorHex = "#FF9100",
            title = "Torch",
            description = "Turns the camera flash on and off as a " +
                "flashlight and reports the result back to the page.",
            snippet = "AndroidAPI.torch(true);",
            html = page(
                title = "Torch",
                body = """
                    <button id="on">Torch ON</button>
                    <button id="off">Torch OFF</button>
                    <pre id="out">No result yet.</pre>
                """,
                script = """
                    const out = document.querySelector('#out');
                    window.onAndroidEvent = function (event, data) {
                      if (event === 'torchResult') {
                        out.textContent = 'torchResult: ' + data;
                      }
                };
                    document.querySelector('#on').onclick = () => AndroidAPI.torch(true);
                    document.querySelector('#off').onclick = () => AndroidAPI.torch(false);
                """
            )
        ),
        WebTemplate(
            id = "notification",
            category = WebTemplateCategory.INTERACTION,
            icon = "🔔",
            colorHex = "#FF5252",
            title = "Notification",
            description = "Posts a local notification from the page. " +
                "Needs the Notifications option in the wizard.",
            snippet = "AndroidAPI.showNotification('Title', 'Body');",
            html = page(
                title = "Notification",
                body = """
                    <button id="n">Show notification</button>
                    <pre id="out">No result yet.</pre>
                """,
                script = """
                    const out = document.querySelector('#out');
                    window.onAndroidEvent = function (event, data) {
                      if (event === 'notificationResult') {
                        out.textContent = 'notificationResult: ' + data;
                      }
                    };
                    document.querySelector('#n').onclick = () =>
                      AndroidAPI.showNotification('Hello', 'This notification came from the web page.');
                """
            ),
            hardware = setOf(WebHardware.NOTIFICATIONS)
        ),
        WebTemplate(
            id = "network-status",
            category = WebTemplateCategory.INTERACTION,
            icon = "📶",
            colorHex = "#00B0FF",
            title = "Network Status",
            description = "Checks whether the device is online and which " +
                "transport it is using (Wi-Fi, cellular, ...).",
            snippet = "AndroidAPI.networkStatus();",
            html = page(
                title = "Network Status",
                body = """
                    <button id="n">Check connection</button>
                    <pre id="out">No result yet.</pre>
                """,
                script = """
                    const out = document.querySelector('#out');
                    window.onAndroidEvent = function (event, data) {
                      if (event === 'networkStatus') {
                        out.textContent = 'networkStatus: ' + data;
                      }
                    };
                    document.querySelector('#n').onclick = () => {
                      out.textContent = 'isOnline (sync): ' + AndroidAPI.isOnline();
                      AndroidAPI.networkStatus();
                    };
                """
            )
        ),
        WebTemplate(
            id = "keep-screen-on",
            category = WebTemplateCategory.INTERACTION,
            icon = "🌙",
            colorHex = "#5C6BC0",
            title = "Keep Screen On",
            description = "Toggles the keep-screen-on flag at runtime so " +
                "pages can control it themselves.",
            snippet = "AndroidAPI.setKeepScreenOn(true);",
            html = page(
                title = "Keep Screen On",
                body = """
                    <button id="on">Keep screen ON</button>
                    <button id="off">Allow screen OFF</button>
                    <pre id="out">Useful for recipes, lyrics and dashboards.</pre>
                """,
                script = """
                    document.querySelector('#on').onclick = () => {
                      AndroidAPI.setKeepScreenOn(true);
                      document.querySelector('#out').textContent = 'Screen will stay on.';
                    };
                    document.querySelector('#off').onclick = () => {
                      AndroidAPI.setKeepScreenOn(false);
                      document.querySelector('#out').textContent = 'Screen may turn off.';
                    };
                """
            ),
            keepScreenOn = true
        ),
        WebTemplate(
            id = "app-storage",
            category = WebTemplateCategory.INTERACTION,
            icon = "💾",
            colorHex = "#26A69A",
            title = "App Storage",
            description = "Saves small text files in the app's private " +
                "storage through the bridge (no browser storage limits).",
            snippet = "AndroidAPI.storageSave('note.txt', 'Hello');",
            html = page(
                title = "App Storage",
                body = """
                    <input id="text" placeholder="Type something to save">
                    <button id="save">Save to app storage</button>
                    <button id="load">Load</button>
                    <button id="list">List files</button>
                    <pre id="out">Nothing yet.</pre>
                """,
                script = """
                    const out = document.querySelector('#out');
                    window.onAndroidEvent = function (event, data) {
                      if (event === 'storageResult') {
                        out.textContent = 'storageResult: ' + data;
                      }
                    };
                    document.querySelector('#save').onclick = () =>
                      AndroidAPI.storageSave('note.txt',
                        document.querySelector('#text').value);
                    document.querySelector('#load').onclick = () =>
                      AndroidAPI.storageLoad('note.txt');
                    document.querySelector('#list').onclick = () =>
                      AndroidAPI.storageList();
                """
            )
        ),
        WebTemplate(
            id = "blank",
            category = WebTemplateCategory.STARTERS,
            icon = "📄",
            colorHex = "#78909C",
            title = "Blank Page",
            description = "A minimal page with the shared dark style — " +
                "the fastest way to start a new web app.",
            snippet = "<!-- your HTML here -->",
            html = page(
                title = "Blank Page",
                body = """
                    <p>Start writing your app here.</p>
                    <pre>window.AndroidAPI is available on this page.</pre>
                """,
                script = ""
            )
        ),
        WebTemplate(
            id = "fullscreen",
            category = WebTemplateCategory.STARTERS,
            icon = "🖥️",
            colorHex = "#8E24AA",
            title = "Fullscreen Layout",
            description = "An edge-to-edge layout for games, readers and " +
                "dashboards; pair it with the Fullscreen wizard option.",
            snippet = "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">",
            html = page(
                title = "Fullscreen Layout",
                body = """
                    <p>This page is designed to fill the whole screen.</p>
                    <button id="t">Toast test</button>
                    <pre id="out"></pre>
                """,
                script = """
                    document.querySelector('#t').onclick = () => {
                      AndroidAPI.toast('Fullscreen page ready');
                      document.querySelector('#out').textContent = 'Toast sent.';
                    };
                """
            ),
            fullscreen = true
        ),
        WebTemplate(
            id = "local-notes",
            category = WebTemplateCategory.STARTERS,
            icon = "📝",
            colorHex = "#FFB300",
            title = "Local Notes",
            description = "A tiny notes app that persists with the " +
                "WebView's localStorage (works fully offline).",
            snippet = "localStorage.setItem('notes', text);",
            html = page(
                title = "Local Notes",
                body = """
                    <textarea id="notes" rows="8"
                      placeholder="Write notes — they are saved on this device"></textarea>
                    <pre>Saved automatically as you type.</pre>
                """,
                script = """
                    const area = document.querySelector('#notes');
                    area.value = localStorage.getItem('notes') || '';
                    area.addEventListener('input', () =>
                      localStorage.setItem('notes', area.value));
                """
            )
        ),
        WebTemplate(
            id = "clipboard",
            category = WebTemplateCategory.DEVICE,
            icon = "📋",
            colorHex = "#42A5F5",
            title = "Clipboard",
            description = "Copies text to the clipboard and reads the " +
                "current clipboard text back through an event.",
            snippet = "AndroidAPI.getClipboard();",
            html = page(
                title = "Clipboard",
                body = """
                    <input id="text" value="Copied from my web app">
                    <button id="copy">Copy text</button>
                    <button id="read">Read clipboard</button>
                    <pre id="out">No result yet.</pre>
                """,
                script = """
                    const out = document.querySelector('#out');
                    window.onAndroidEvent = function (event, data) {
                      if (event === 'clipboardResult') {
                        out.textContent = 'clipboardResult: ' + data;
                      }
                    };
                    document.querySelector('#copy').onclick = () => {
                      AndroidAPI.copyToClipboard(
                        document.querySelector('#text').value);
                      out.textContent = 'Copied.';
                    };
                    document.querySelector('#read').onclick = () =>
                      AndroidAPI.getClipboard();
                """
            )
        ),
        WebTemplate(
            id = "share",
            category = WebTemplateCategory.DEVICE,
            icon = "🔗",
            colorHex = "#66BB6A",
            title = "Share",
            description = "Opens the Android share sheet with text from " +
                "the page.",
            snippet = "AndroidAPI.shareText('Hello!');",
            html = page(
                title = "Share",
                body = """
                    <input id="text" value="Shared from my web app">
                    <button id="s">Share text</button>
                """,
                script = """
                    document.querySelector('#s').onclick = () =>
                      AndroidAPI.shareText(document.querySelector('#text').value);
                """
            )
        ),
        WebTemplate(
            id = "device-battery",
            category = WebTemplateCategory.SYSTEM,
            icon = "🔋",
            colorHex = "#9CCC65",
            title = "Device & Battery",
            description = "Reads device model/Android version " +
                "synchronously and the battery level through an event.",
            snippet = "AndroidAPI.getBattery();",
            html = page(
                title = "Device & Battery",
                body = """
                    <button id="d">Read device + battery</button>
                    <pre id="out">No result yet.</pre>
                """,
                script = """
                    const out = document.querySelector('#out');
                    window.onAndroidEvent = function (event, data) {
                      if (event === 'batteryResult') {
                        out.textContent += '\nbatteryResult: ' + data;
                      }
                    };
                    document.querySelector('#d').onclick = () => {
                      out.textContent = 'device: ' + AndroidAPI.getDeviceInfo();
                      AndroidAPI.getBattery();
                    };
                """
            )
        ),
        WebTemplate(
            id = "bootstrap",
            category = WebTemplateCategory.LIBRARIES,
            icon = "B",
            colorHex = "#7952B3",
            title = "Bootstrap 5 (CDN)",
            description = "A page styled with Bootstrap 5 loaded from " +
                "the jsDelivr CDN. The generated app needs internet " +
                "for the framework files.",
            snippet = "<link href=\"https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/css/bootstrap.min.css\" rel=\"stylesheet\">",
            html = """<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width,initial-scale=1">
  <title>Bootstrap 5 (CDN)</title>
  <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/css/bootstrap.min.css" rel="stylesheet">
</head>
<body class="p-4">
  <h1 class="mb-3">Bootstrap starter</h1>
  <p class="text-secondary">Edit this page or replace it with your own Bootstrap layout.</p>
  <button class="btn btn-primary" onclick="AndroidAPI && AndroidAPI.toast('Bootstrap works')">Native toast</button>
  <script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/js/bootstrap.bundle.min.js"></script>
</body>
</html>
"""
        ),
        WebTemplate(
            id = "tailwind",
            category = WebTemplateCategory.LIBRARIES,
            icon = "🎨",
            colorHex = "#06B6D4",
            title = "Tailwind CSS (CDN)",
            description = "A page styled with the Tailwind Play CDN " +
                "script. The generated app needs internet for the " +
                "framework files.",
            snippet = "<script src=\"https://cdn.tailwindcss.com\"></script>",
            html = """<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width,initial-scale=1">
  <title>Tailwind CSS (CDN)</title>
  <script src="https://cdn.tailwindcss.com"></script>
</head>
<body class="bg-neutral-900 text-neutral-100 p-6">
  <h1 class="text-3xl font-bold mb-3">Tailwind starter</h1>
  <p class="text-neutral-400 mb-4">Edit this page or replace it with your own Tailwind layout.</p>
  <button class="bg-orange-500 text-neutral-900 font-bold px-4 py-2 rounded-xl"
    onclick="AndroidAPI && AndroidAPI.toast('Tailwind works')">Native toast</button>
</body>
</html>
"""
        )
    )

    fun byId(id: String): WebTemplate? = ALL.firstOrNull { it.id == id }

    fun byCategory(category: WebTemplateCategory): List<WebTemplate> =
        ALL.filter { it.category == category }

    /** Shared dark-styled demo page used by the built-in templates. */
    private fun page(title: String, body: String, script: String): String =
        """<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width,initial-scale=1">
  <title>${escapeHtml(title)}</title>
  <style>
    :root { color-scheme: dark; font-family: system-ui, sans-serif;
            background: #121212; color: #f7f3ef; }
    body { margin: 0; padding: 24px; }
    h1 { font-size: 26px; margin: 0 0 14px; }
    input, textarea { width: 100%; box-sizing: border-box; margin: 8px 0;
      padding: 12px; border-radius: 12px; border: 1px solid #35312f;
      background: #1b1b1b; color: #f7f3ef; }
    button { width: 100%; border: 0; border-radius: 14px; padding: 14px;
      margin-top: 10px; background: #ff6b00; color: #121212;
      font-weight: 800; }
    pre { margin-top: 14px; padding: 14px; border-radius: 14px;
      background: #101010; color: #39d98a; white-space: pre-wrap;
      min-height: 48px; }
  </style>
</head>
<body>
  <h1>${escapeHtml(title)}</h1>
${body.trimIndent()}
  <script>
${script.trimIndent()}
  </script>
</body>
</html>
"""

    private fun escapeHtml(value: String): String =
        value.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
}
