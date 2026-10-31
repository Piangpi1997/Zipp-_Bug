package com.zipbug.base.ui

import android.app.Activity
import android.content.ClipData
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegSession
import com.arthenica.ffmpegkit.ReturnCode
import com.zipbug.base.databinding.ActivityZippyWebStudioBinding
import com.zipbug.base.zippyweb.ZippyRenderPlanner
import com.zipbug.base.zippyweb.ZippyWebBridge
import com.zipbug.base.zippyweb.ZippyWebStore
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/**
 * Hosts the transplanted Zippy web UI (assets/zippyweb/index.html) as a new
 * screen alongside the native Zippy Cut Studio. The page's AndroidBridge
 * calls are served by [ZippyWebBridge] against Zip_Bug's own engines
 * (bundled FFmpegKit) and storage — no Zippy_Cut code or backend is used.
 */
class ZippyWebStudioActivity : AppCompatActivity(), ZippyWebBridge.Host {

    private lateinit var binding: ActivityZippyWebStudioBinding
    private lateinit var store: ZippyWebStore
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private var pendingPickIsVideo: Boolean = true
    private var renderSession: FFmpegSession? = null
    private var renderCancelled: Boolean = false
    private var publishedOutputUri: Uri? = null

    private val filePicker = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val callback = fileCallback
        fileCallback = null
        if (result.resultCode != Activity.RESULT_OK || result.data == null) {
            callback?.onReceiveValue(null)
            return@registerForActivityResult
        }
        val uris = ArrayList<Uri>()
        val data = result.data!!
        data.clipData?.let { clip ->
            for ( i in 0 until clip.itemCount ) uris += clip.getItemAt(i).uri
        } ?: data.data?.let { uris += it }
        val first = uris.firstOrNull()
        if (first != null) stagePickedMedia(first, pendingPickIsVideo)
        callback?.onReceiveValue(uris.toTypedArray())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityZippyWebStudioBinding.inflate(layoutInflater)
        setContentView(binding.root)
        store = ZippyWebStore(this)
        ZippyWebBridge.hostVersion = runCatching {
            packageManager.getPackageInfo(packageName, 0).versionName
        }.getOrNull() ?: ""

        val web = binding.zippyWebView
        with(web.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            mediaPlaybackRequiresUserGesture = false
            loadWithOverviewMode = true
            useWideViewPort = true
        }
        web.webViewClient = WebViewClient()
        web.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                fileCallback?.onReceiveValue(null)
                fileCallback = filePathCallback
                val accept = fileChooserParams?.acceptTypes?.joinToString(",") ?: ""
                pendingPickIsVideo = !accept.contains("audio")
                val intent = fileChooserParams?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = if (pendingPickIsVideo) "video/*" else "audio/*"
                    addCategory(Intent.CATEGORY_OPENABLE)
                }
                return try {
                    filePicker.launch(intent)
                    true
                } catch (e: Exception) {
                    fileCallback = null
                    false
                }
            }
        }
        web.addJavascriptInterface(ZippyWebBridge(this, store, this), "AndroidBridge")
        web.loadUrl("file:///android_asset/zippyweb/index.html")
    }

    override fun onDestroy() {
        renderSession?.let { runCatching { FFmpegKit.cancel(it.sessionId) } }
        binding.zippyWebView.destroy()
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (binding.zippyWebView.canGoBack()) binding.zippyWebView.goBack()
        else super.onBackPressed()
    }

    // --- media staging -----------------------------------------------------

    private fun stagePickedMedia(uri: Uri, isVideo: Boolean) {
        Thread {
            try {
                val display = queryDisplayName(uri) ?: if (isVideo) "video.mp4" else "audio.mp3"
                val ext = display.substringAfterLast('.', if (isVideo) "mp4" else "mp3")
                val stored = if (isVideo) "video.$ext" else "audio.$ext"
                val target = File(store.mediaDir, stored)
                contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(target).use { input.copyTo(it) }
                } ?: return@Thread
                store.recordMedia(isVideo, stored, display)
            } catch (e: Exception) {
                runOnUiThread { toast("Media copy failed: ${e.message}") }
            }
        }.start()
    }

    private fun queryDisplayName(uri: Uri): String? {
        return contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
    }

    // --- ZippyWebBridge.Host ----------------------------------------------

    override fun evalJs(script: String) {
        runOnUiThread { binding.zippyWebView.evaluateJavascript(script, null) }
    }

    override fun toast(message: String) {
        runOnUiThread { Toast.makeText(this, message, Toast.LENGTH_LONG).show() }
    }

    override fun showKeyLoginInfo() {
        runOnUiThread {
            AlertDialog.Builder(this)
                .setTitle("Access Key")
                .setMessage(
                    "Zip_Bug မှာ Access Key မလိုပါဘူး။ ဒီ Zippy Web Studio ကို " +
                        "Zip_Bug app ရဲ့ အစိတ်အပိုင်းအဖြစ် free သုံးနိုင်ပါတယ်။\n\n" +
                        "No access key is needed — this studio is part of Zip_Bug."
                )
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }
    }

    override fun openTts(text: String) {
        runOnUiThread {
            startActivity(Intent(this, TtsStudioActivity::class.java).putExtra(EXTRA_TTS_TEXT, text))
        }
    }

    override fun openExternalUrl(url: String) {
        runOnUiThread {
            runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        }
    }

    override fun startRender(planJson: String) {
        if (renderSession != null) {
            evalJs("window.ZippyNative&&ZippyNative.onRenderError(${JSONObject.quote("A render is already running")})")
            return
        }
        val video = store.videoFile()
        val audio = store.audioFile()
        if (video == null || audio == null) {
            evalJs("window.ZippyNative&&ZippyNative.onRenderError(${JSONObject.quote("Index a video and an audio file first")})")
            return
        }
        val dims = probeVideoSize(video)
        val job = try {
            ZippyRenderPlanner.build(planJson, video.absolutePath, audio.absolutePath,
                File(store.outputDir, "zippy_render_${System.currentTimeMillis()}.mp4").absolutePath,
                dims.first, dims.second)
        } catch (e: Exception) {
            evalJs("window.ZippyNative&&ZippyNative.onRenderError(${JSONObject.quote(e.message ?: "Plan error")})")
            return
        }
        val outputPath = job.args.last()
        if (job.skippedFx.isNotEmpty()) {
            toast("Effects not applied in this render: ${job.skippedFx.joinToString(", ")}")
        }
        renderCancelled = false
        val totalMs = job.totalSeconds * 1000.0
        renderSession = FFmpegKit.executeWithArgumentsAsync(job.args.toTypedArray(),
            { session ->
                renderSession = null
                val rc = session.returnCode
                when {
                    renderCancelled -> evalJs("window.ZippyNative&&ZippyNative.onRenderCancelled()")
                    ReturnCode.isSuccess(rc) -> {
                        val out = File(outputPath)
                        if (out.isFile && out.length() > 0) {
                            store.lastOutputPath = outputPath
                            publishedOutputUri = null
                            evalJs("window.ZippyNative&&ZippyNative.onRenderComplete(${JSONObject.quote("file://" + out.absolutePath)})")
                        } else {
                            evalJs("window.ZippyNative&&ZippyNative.onRenderError(${JSONObject.quote("Render produced no output file")})")
                        }
                    }
                    else -> {
                        val tail = (session.allLogsAsString ?: "").takeLast(300).replace("\n", " ")
                        evalJs("window.ZippyNative&&ZippyNative.onRenderError(${JSONObject.quote("Render failed (code ${rc?.value}): $tail")})")
                    }
                }
            }, null,
            { stats ->
                val pct = if (totalMs > 0) (stats.time / totalMs * 100.0).coerceIn(0.0, 99.0) else 0.0
                evalJs("window.ZippyNative&&ZippyNative.onRenderProgress(${"%.1f".format(java.util.Locale.US, pct)}, ${JSONObject.quote("Rendering…")})")
            })
    }

    override fun cancelRender() {
        renderCancelled = true
        renderSession?.let { runCatching { FFmpegKit.cancel(it.sessionId) } }
    }

    override fun saveOutputToDownloads() {
        val path = store.lastOutputPath
        if (path == null) {
            toast("No render output yet")
            return
        }
        Thread {
            val uri = publishToDownloads(File(path))
            runOnUiThread {
                if (uri != null) {
                    publishedOutputUri = uri
                    toast("Saved to Downloads")
                } else toast("Save failed")
            }
        }.start()
    }

    override fun openOutput() {
        runOnUiThread {
            val uri = publishedOutputUri ?: store.lastOutputPath?.let {
                FileProvider.getUriForFile(this, "$packageName.fileprovider", File(it))
            }
            if (uri == null) {
                toast("No render output yet")
                return@runOnUiThread
            }
            val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "video/*")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            runCatching { startActivity(intent) }.onFailure { toast("No video player found") }
        }
    }

    override fun shareOutput() {
        runOnUiThread {
            val path = store.lastOutputPath
            if (path == null) {
                toast("No render output yet")
                return@runOnUiThread
            }
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", File(path))
            val intent = Intent(Intent.ACTION_SEND).setType("video/*")
                .putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(Intent.createChooser(intent, "Share video"))
        }
    }

    override fun shareMasterPrompt(prompt: String, withVideo: Boolean) {
        runOnUiThread {
            val intent = Intent(Intent.ACTION_SEND).setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, prompt)
            val video = store.videoFile()
            if (withVideo && video != null) {
                intent.type = "video/*"
                intent.putExtra(Intent.EXTRA_STREAM,
                    FileProvider.getUriForFile(this, "$packageName.fileprovider", video))
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Share prompt"))
        }
    }

    override fun posterFrames(timesJson: String, share: Boolean, prompt: String) {
        val video = store.videoFile()
        if (video == null) {
            evalJs("window.ZippyPoster&&ZippyPoster.onSaved(0, '')")
            return
        }
        Thread {
            val times = runCatching { JSONArray(timesJson) }.getOrNull()
            val retriever = MediaMetadataRetriever()
            var count = 0
            val uris = ArrayList<Uri>()
            try {
                retriever.setDataSource(video.absolutePath)
                if (times != null) {
                    for ( i in 0 until times.length() ) {
                        val seconds = times.optDouble(i, -1.0)
                        if (seconds < 0) continue
                        val frame = retriever.getFrameAtTime(
                            (seconds * 1_000_000).toLong(),
                            MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                        ) ?: continue
                        val file = File(store.posterDir, "frame_${i}_${(seconds * 1000).toInt()}.png")
                        FileOutputStream(file).use { frame.compress(Bitmap.CompressFormat.PNG, 100, it) }
                        frame.recycle()
                        count++
                        if (share) uris += FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
                    }
                }
            } catch (e: Exception) {
                // count stays as-is; reported honestly below
            } finally {
                runCatching { retriever.release() }
            }
            if (share) {
                runOnUiThread {
                    if (uris.isEmpty()) {
                        toast("No frames could be extracted")
                        return@runOnUiThread
                    }
                    val intent = Intent(Intent.ACTION_SEND_MULTIPLE).setType("image/png")
                        .putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                        .putExtra(Intent.EXTRA_TEXT, prompt)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    intent.clipData = ClipData.newRawUri("frames", uris[0])
                    startActivity(Intent.createChooser(intent, "Share frames"))
                }
            } else {
                evalJs("window.ZippyPoster&&ZippyPoster.onSaved($count, ${JSONObject.quote("app cache (poster frames)")})")
            }
        }.start()
    }

    // --- helpers -------------------------------------------------------------

    private fun probeVideoSize(file: File): Pair<Int, Int> {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            var w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 720
            var h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 1280
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            if (rotation == 90 || rotation == 270) {
                val t = w; w = h; h = t
            }
            Pair(w - w % 2, h - h % 2)
        } catch (e: Exception) {
            Pair(720, 1280)
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun publishToDownloads(file: File): Uri? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, file.name)
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
                val uri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                    ?: return null
                contentResolver.openOutputStream(uri)?.use { out ->
                    file.inputStream().use { it.copyTo(out) }
                }
                values.clear()
                values.put(MediaStore.Video.Media.IS_PENDING, 0)
                contentResolver.update(uri, values, null, null)
                uri
            } else {
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val target = File(dir, file.name)
                file.copyTo(target, overwrite = true)
                Uri.fromFile(target)
            }
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        const val EXTRA_TTS_TEXT = "zippyweb.tts_text"
    }
}
