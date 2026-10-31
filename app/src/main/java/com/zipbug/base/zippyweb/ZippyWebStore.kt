package com.zipbug.base.zippyweb

import android.content.Context
import org.json.JSONObject
import java.io.File

/**
 * On-device persistence for the Zippy Web Studio (transplanted web UI).
 * Stores the project state string exactly as the page produced it, the
 * staged copies of the picked source video/audio, overlay images and the
 * last render output — everything the AndroidBridge needs across restarts.
 */
class ZippyWebStore(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("zippyweb", Context.MODE_PRIVATE)

    val mediaDir: File = File(appContext.filesDir, "zippyweb/media").apply { mkdirs() }
    val overlayDir: File = File(appContext.filesDir, "zippyweb/overlays").apply { mkdirs() }
    val outputDir: File = File(appContext.filesDir, "zippyweb/output").apply { mkdirs() }
    val posterDir: File = File(appContext.cacheDir, "zippyweb/posters").apply { mkdirs() }

    var projectState: String?
        get() = prefs.getString(KEY_STATE, null)
        set(value) {
            prefs.edit().apply {
                if (value == null) remove(KEY_STATE) else putString(KEY_STATE, value)
            }.apply()
        }

    var videosCache: String
        get() = prefs.getString(KEY_VIDEOS_CACHE, "") ?: ""
        set(value) {
            prefs.edit().putString(KEY_VIDEOS_CACHE, value).apply()
        }

    var lastOutputPath: String?
        get() = prefs.getString(KEY_LAST_OUTPUT, null)
        set(value) {
            prefs.edit().apply {
                if (value == null) remove(KEY_LAST_OUTPUT) else putString(KEY_LAST_OUTPUT, value)
            }.apply()
        }

    fun setBootPending(pending: Boolean) {
        prefs.edit().putBoolean(KEY_BOOT, pending).apply()
    }

    fun bootPending(): Boolean = prefs.getBoolean(KEY_BOOT, false)

    fun videoFile(): File? = mediaFile(KEY_VIDEO_NAME)
    fun audioFile(): File? = mediaFile(KEY_AUDIO_NAME)
    fun videoName(): String? = prefs.getString(KEY_VIDEO_NAME, null)
    fun audioName(): String? = prefs.getString(KEY_AUDIO_NAME, null)

    private fun mediaFile(nameKey: String): File? {
        val name = prefs.getString(nameKey, null) ?: return null
        val f = File(mediaDir, name)
        return if (f.isFile) f else null
    }

    /** Records a staged media copy; [storedName] is the file inside mediaDir. */
    fun recordMedia(isVideo: Boolean, storedName: String, displayName: String) {
        prefs.edit()
            .putString(if (isVideo) KEY_VIDEO_NAME else KEY_AUDIO_NAME, storedName)
            .putString(if (isVideo) KEY_VIDEO_DISPLAY else KEY_AUDIO_DISPLAY, displayName)
            .apply()
    }

    fun videoDisplayName(): String? = prefs.getString(KEY_VIDEO_DISPLAY, null)
    fun audioDisplayName(): String? = prefs.getString(KEY_AUDIO_DISPLAY, null)

    /** JSON the page's loadBootstrap() expects: {video:{url,name}, audio:{...}, state:{...}}. */
    fun bootstrapJson(): String {
        val root = JSONObject()
        videoFile()?.let { f ->
            root.put("video", JSONObject()
                .put("url", "file://" + f.absolutePath)
                .put("name", videoDisplayName() ?: f.name))
        }
        audioFile()?.let { f ->
            root.put("audio", JSONObject()
                .put("url", "file://" + f.absolutePath)
                .put("name", audioDisplayName() ?: f.name))
        }
        projectState?.let { raw ->
            val parsed = runCatching { JSONObject(raw) }.getOrNull()
            if (parsed != null) root.put("state", parsed) else root.put("state", JSONObject())
        }
        return root.toString()
    }

    fun clearableBytes(): Long {
        var total = 0L
        listOf(mediaDir, overlayDir, outputDir).forEach { dir ->
            dir.walkTopDown().forEach { if (it.isFile) total += it.length() }
        }
        total += (projectState?.length ?: 0).toLong()
        return total
    }

    /** New project: drop state + staged media references (files removed too). */
    fun clearProject(includeMedia: Boolean) {
        projectState = null
        lastOutputPath = null
        if (includeMedia) {
            mediaDir.listFiles()?.forEach { it.delete() }
            overlayDir.listFiles()?.forEach { it.delete() }
            prefs.edit()
                .remove(KEY_VIDEO_NAME).remove(KEY_AUDIO_NAME)
                .remove(KEY_VIDEO_DISPLAY).remove(KEY_AUDIO_DISPLAY)
                .apply()
        }
    }

    fun clearAllData() {
        clearProject(includeMedia = true)
        outputDir.listFiles()?.forEach { it.delete() }
        videosCache = ""
        setBootPending(false)
    }

    companion object {
        private const val KEY_STATE = "project_state"
        private const val KEY_VIDEOS_CACHE = "videos_cache"
        private const val KEY_LAST_OUTPUT = "last_output"
        private const val KEY_BOOT = "boot_pending"
        private const val KEY_VIDEO_NAME = "video_file"
        private const val KEY_AUDIO_NAME = "audio_file"
        private const val KEY_VIDEO_DISPLAY = "video_display"
        private const val KEY_AUDIO_DISPLAY = "audio_display"
    }
}
