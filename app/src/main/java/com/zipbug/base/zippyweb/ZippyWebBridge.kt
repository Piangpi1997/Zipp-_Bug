package com.zipbug.base.zippyweb

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.media.MediaMetadataRetriever
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Base64
import android.webkit.JavascriptInterface
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/**
 * The `AndroidBridge` JavaScript interface for the transplanted Zippy web
 * UI. Every method mirrors a call the page already makes (all of them are
 * defensively guarded page-side). Results the page consumes asynchronously
 * are delivered through its own global callbacks (ZippyNative.*,
 * ZippyVideos.*, ZippyCaption.*, ZippyPoster.*) via the host activity.
 *
 * Honest-scope notes: the video bank is local-only (an empty item list —
 * Zip_Bug has no remote content service); openKeyLogin explains that
 * Zip_Bug needs no access key instead of faking a login; requestFocusProxy
 * returns "" so the page falls back to the original video (no proxy is
 * generated in v1).
 */
class ZippyWebBridge(
    private val context: Context,
    private val store: ZippyWebStore,
    private val host: Host
) {

    interface Host {
        fun evalJs(script: String)
        fun toast(message: String)
        fun showKeyLoginInfo()
        fun openTts(text: String)
        fun openExternalUrl(url: String)
        fun startRender(planJson: String)
        fun cancelRender()
        fun saveOutputToDownloads()
        fun openOutput()
        fun shareOutput()
        fun shareMasterPrompt(prompt: String, withVideo: Boolean)
        fun posterFrames(timesJson: String, share: Boolean, prompt: String)
    }

    @JavascriptInterface
    fun appVersion(): String = hostVersion

    @JavascriptInterface
    fun deviceInfo(): String =
        "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"

    @JavascriptInterface
    fun copyText(text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("Zippy", text))
    }

    @JavascriptInterface
    fun readClipboard(): String {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = runCatching { cm.primaryClip }.getOrNull() ?: return ""
        if (clip.itemCount == 0) return ""
        return clip.getItemAt(0)?.coerceToText(context)?.toString() ?: ""
    }

    @JavascriptInterface
    fun haptic(kind: String) {
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
        val ms = when (kind) {
            "success" -> 45L
            "error" -> 90L
            "warn" -> 60L
            else -> 22L
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION") vibrator.vibrate(ms)
            }
        }
    }

    @JavascriptInterface
    fun tapSeen() {
        // Page-side tap log acknowledgement; nothing to store natively.
    }

    @JavascriptInterface
    fun openExternal(url: String) {
        if (url.startsWith("https://") || url.startsWith("http://")) host.openExternalUrl(url)
    }

    @JavascriptInterface
    fun openKeyLogin() = host.showKeyLoginInfo()

    @JavascriptInterface
    fun openVoicer(text: String, lang: String) = host.openTts(text)

    @JavascriptInterface
    fun openUnlimitedTts(text: String, from: String) = host.openTts(text)

    @JavascriptInterface
    fun loadBootstrap(): String = store.bootstrapJson()

    @JavascriptInterface
    fun saveProjectState(raw: String) {
        store.projectState = raw
    }

    @JavascriptInterface
    fun bootMark(on: Boolean) = store.setBootPending(on)

    @JavascriptInterface
    fun bootPending(): Boolean = store.bootPending()

    @JavascriptInterface
    fun newProject() {
        store.clearProject(includeMedia = true)
        host.evalJs("window.ZippyNative&&ZippyNative.onProjectCleared(false)")
    }

    @JavascriptInterface
    fun clearAllProjectData() {
        store.clearAllData()
        host.evalJs("window.ZippyNative&&ZippyNative.onProjectCleared(true)")
    }

    @JavascriptInterface
    fun getClearableBytes(): Long = store.clearableBytes()

    @JavascriptInterface
    fun fetchVideos(fresh: Boolean) {
        // Zip_Bug has no remote video bank: return a valid, empty catalogue
        // (the page renders its empty state) and cache it like the page does.
        val payload = JSONObject().put("items", JSONArray()).toString()
        store.videosCache = payload
        val quoted = JSONObject.quote(payload)
        host.evalJs("window.ZippyVideos&&ZippyVideos.onData($quoted)")
    }

    @JavascriptInterface
    fun videosCache(): String = store.videosCache

    @JavascriptInterface
    fun videoFps(): Double {
        val file = store.videoFile() ?: return 0.0
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val raw = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
            raw?.toDoubleOrNull() ?: 0.0
        } catch (e: Exception) {
            0.0
        } finally {
            runCatching { retriever.release() }
        }
    }

    @JavascriptInterface
    fun analyzeAudioSilence() {
        val audio = store.audioFile()
        if (audio == null) {
            deliverSilence(ok = false)
            return
        }
        Thread {
            val args = arrayOf(
                "-i", audio.absolutePath,
                "-af", "silencedetect=noise=-35dB:d=0.4",
                "-f", "null", "-"
            )
            val session = FFmpegKit.executeWithArguments(args)
            if (!ReturnCode.isSuccess(session.returnCode)) {
                deliverSilence(ok = false)
                return@Thread
            }
            val log = session.allLogsAsString ?: ""
            val starts = Regex("silence_start: ([0-9.]+)").findAll(log).map { it.groupValues[1].toDouble() }.toList()
            val ends = Regex("silence_end: ([0-9.]+)").findAll(log).map { it.groupValues[1].toDouble() }.toList()
            val pairs = JSONArray()
            for ( i in 0 until minOf(starts.size, ends.size) ) {
                pairs.put(JSONArray().put(starts[i]).put(ends[i]))
            }
            val payload = JSONObject().put("ok", true).put("silences", pairs).toString()
            host.evalJs("window.ZippyCaption&&ZippyCaption.onSilence(${JSONObject.quote(payload)})")
        }.start()
    }

    private fun deliverSilence(ok: Boolean) {
        val payload = JSONObject().put("ok", ok).put("silences", JSONArray()).toString()
        host.evalJs("window.ZippyCaption&&ZippyCaption.onSilence(${JSONObject.quote(payload)})")
    }

    @JavascriptInterface
    fun startRender(planJson: String) = host.startRender(planJson)

    @JavascriptInterface
    fun cancelRender() = host.cancelRender()

    @JavascriptInterface
    fun renderModes(): Boolean = true

    @JavascriptInterface
    fun requestFocusProxy(): String = ""

    @JavascriptInterface
    fun saveOutput() = host.saveOutputToDownloads()

    @JavascriptInterface
    fun openOutput() = host.openOutput()

    @JavascriptInterface
    fun shareOutput() = host.shareOutput()

    @JavascriptInterface
    fun shareMasterPrompt(prompt: String, withVideo: Boolean) = host.shareMasterPrompt(prompt, withVideo)

    @JavascriptInterface
    fun saveOverlayImage(name: String, dataUrl: String): String {
        val comma = dataUrl.indexOf(',')
        if (comma < 0) return ""
        val bytes = runCatching { Base64.decode(dataUrl.substring(comma + 1), Base64.DEFAULT) }.getOrNull()
            ?: return ""
        val safe = name.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "overlay" }
        val file = File(store.overlayDir, if (safe.endsWith(".png")) safe else "$safe.png")
        return try {
            FileOutputStream(file).use { it.write(bytes) }
            file.absolutePath
        } catch (e: Exception) {
            ""
        }
    }

    @JavascriptInterface
    fun clearOverlayImages() {
        store.overlayDir.listFiles()?.forEach { it.delete() }
    }

    @JavascriptInterface
    fun savePosterFrames(timesJson: String) = host.posterFrames(timesJson, share = false, prompt = "")

    @JavascriptInterface
    fun sharePoster(timesJson: String, prompt: String) = host.posterFrames(timesJson, share = true, prompt = prompt)

    companion object {
        /** Set by the host activity from PackageInfo before the page loads. */
        var hostVersion: String = ""
    }
}
