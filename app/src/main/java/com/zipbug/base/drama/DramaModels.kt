package com.zipbug.base.drama

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class DramaClip(
    val id: String = UUID.randomUUID().toString(),
    val sourceIndex: Int = 1, // 1 to 9 (identifies original source)
    var name: String = "VIDEO #1",
    val sourcePath: String,
    val sourceDurationMs: Long,
    var startMs: Long = 0L,
    var endMs: Long = sourceDurationMs,
    var orderIndex: Int = 0
) {
    val durationMs: Long
        get() = (endMs - startMs).coerceAtLeast(0L)

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("sourceIndex", sourceIndex)
        put("name", name)
        put("sourcePath", sourcePath)
        put("sourceDurationMs", sourceDurationMs)
        put("startMs", startMs)
        put("endMs", endMs)
        put("orderIndex", orderIndex)
    }

    companion object {
        fun fromJson(json: JSONObject): DramaClip = DramaClip(
            id = json.optString("id", UUID.randomUUID().toString()),
            sourceIndex = json.optInt("sourceIndex", 1),
            name = json.optString("name", "VIDEO #1"),
            sourcePath = json.optString("sourcePath", ""),
            sourceDurationMs = json.optLong("sourceDurationMs", 0L),
            startMs = json.optLong("startMs", 0L),
            endMs = json.optLong("endMs", json.optLong("sourceDurationMs", 0L)),
            orderIndex = json.optInt("orderIndex", 0)
        )
    }
}

data class DramaSubtitleCue(
    val id: String = UUID.randomUUID().toString(),
    var startMs: Long = 0L,
    var endMs: Long = 3000L,
    var mmText: String = "",
    var enText: String = "",
    var fontSize: Float = 18f,
    var textColor: String = "#FFFFFF",
    var bgColor: String = "#B3000000", // 70% black
    var positionYPercent: Float = 78f // from top
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("startMs", startMs)
        put("endMs", endMs)
        put("mmText", mmText)
        put("enText", enText)
        put("fontSize", fontSize.toDouble())
        put("textColor", textColor)
        put("bgColor", bgColor)
        put("positionYPercent", positionYPercent.toDouble())
    }

    companion object {
        fun fromJson(json: JSONObject): DramaSubtitleCue = DramaSubtitleCue(
            id = json.optString("id", UUID.randomUUID().toString()),
            startMs = json.optLong("startMs", 0L),
            endMs = json.optLong("endMs", 3000L),
            mmText = json.optString("mmText", ""),
            enText = json.optString("enText", ""),
            fontSize = json.optDouble("fontSize", 18.0).toFloat(),
            textColor = json.optString("textColor", "#FFFFFF"),
            bgColor = json.optString("bgColor", "#B3000000"),
            positionYPercent = json.optDouble("positionYPercent", 78.0).toFloat()
        )
    }
}

data class DramaBlurBox(
    var isEnabled: Boolean = true,
    var xPercent: Float = 35f,
    var yPercent: Float = 20f,
    var wPercent: Float = 30f,
    var hPercent: Float = 18f,
    var blurRadius: Int = 15
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("isEnabled", isEnabled)
        put("xPercent", xPercent.toDouble())
        put("yPercent", yPercent.toDouble())
        put("wPercent", wPercent.toDouble())
        put("hPercent", hPercent.toDouble())
        put("blurRadius", blurRadius)
    }

    companion object {
        fun fromJson(json: JSONObject?): DramaBlurBox {
            if (json == null) return DramaBlurBox()
            return DramaBlurBox(
                isEnabled = json.optBoolean("isEnabled", true),
                xPercent = json.optDouble("xPercent", 35.0).toFloat(),
                yPercent = json.optDouble("yPercent", 20.0).toFloat(),
                wPercent = json.optDouble("wPercent", 30.0).toFloat(),
                hPercent = json.optDouble("hPercent", 18.0).toFloat(),
                blurRadius = json.optInt("blurRadius", 15)
            )
        }
    }
}

data class DramaLogo(
    var isEnabled: Boolean = true,
    var uri: String = "",
    var localPath: String = "",
    var opacity: Float = 0.85f,
    var scale: Float = 1.0f,
    var position: String = "top-right" // "top-left", "top-right", "bottom-left", "bottom-right"
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("isEnabled", isEnabled)
        put("uri", uri)
        put("localPath", localPath)
        put("opacity", opacity.toDouble())
        put("scale", scale.toDouble())
        put("position", position)
    }

    companion object {
        fun fromJson(json: JSONObject?): DramaLogo {
            if (json == null) return DramaLogo()
            return DramaLogo(
                isEnabled = json.optBoolean("isEnabled", true),
                uri = json.optString("uri", ""),
                localPath = json.optString("localPath", ""),
                opacity = json.optDouble("opacity", 0.85).toFloat(),
                scale = json.optDouble("scale", 1.0).toFloat(),
                position = json.optString("position", "top-right")
            )
        }
    }
}

data class DramaTitleOverlay(
    var isEnabled: Boolean = true,
    var text: String = "အမုန်းသင့်အချစ် — အပိုင်း ၁",
    var textColor: String = "#FFFFFF",
    var positionYPercent: Float = 5f
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("isEnabled", isEnabled)
        put("text", text)
        put("textColor", textColor)
        put("positionYPercent", positionYPercent.toDouble())
    }

    companion object {
        fun fromJson(json: JSONObject?): DramaTitleOverlay {
            if (json == null) return DramaTitleOverlay()
            return DramaTitleOverlay(
                isEnabled = json.optBoolean("isEnabled", true),
                text = json.optString("text", "အမုန်းသင့်အချစ် — အပိုင်း ၁"),
                textColor = json.optString("textColor", "#FFFFFF"),
                positionYPercent = json.optDouble("positionYPercent", 5.0).toFloat()
            )
        }
    }
}

data class DramaAudioConfig(
    var isOriginalMuted: Boolean = false,
    var originalVolume: Float = 1.0f,
    var bgmPath: String = "",
    var bgmVolume: Float = 0.7f
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("isOriginalMuted", isOriginalMuted)
        put("originalVolume", originalVolume.toDouble())
        put("bgmPath", bgmPath)
        put("bgmVolume", bgmVolume.toDouble())
    }

    companion object {
        fun fromJson(json: JSONObject?): DramaAudioConfig {
            if (json == null) return DramaAudioConfig()
            return DramaAudioConfig(
                isOriginalMuted = json.optBoolean("isOriginalMuted", false),
                originalVolume = json.optDouble("originalVolume", 1.0).toFloat(),
                bgmPath = json.optString("bgmPath", ""),
                bgmVolume = json.optDouble("bgmVolume", 0.7).toFloat()
            )
        }
    }
}

data class DramaProjectState(
    val projectId: String = UUID.randomUUID().toString(),
    var projectName: String = "Myanmar Drama Reel 1",
    val clips: MutableList<DramaClip> = mutableListOf(),
    val subtitles: MutableList<DramaSubtitleCue> = mutableListOf(),
    val blurBox: DramaBlurBox = DramaBlurBox(),
    val logo: DramaLogo = DramaLogo(),
    val titleOverlay: DramaTitleOverlay = DramaTitleOverlay(),
    val audioConfig: DramaAudioConfig = DramaAudioConfig(),
    var outputResolution: String = "1080p", // "1080p" or "720p"
    var updatedAt: Long = System.currentTimeMillis()
) {
    val totalDurationMs: Long
        get() = clips.sumOf { it.durationMs }

    val distinctSourceCount: Int
        get() = clips.map { it.sourcePath }.distinct().size

    fun toJson(): JSONObject = JSONObject().apply {
        put("projectId", projectId)
        put("projectName", projectName)
        put("outputResolution", outputResolution)
        put("updatedAt", updatedAt)

        val clipsArray = JSONArray()
        clips.forEach { clipsArray.put(it.toJson()) }
        put("clips", clipsArray)

        val subsArray = JSONArray()
        subtitles.forEach { subsArray.put(it.toJson()) }
        put("subtitles", subsArray)

        put("blurBox", blurBox.toJson())
        put("logo", logo.toJson())
        put("titleOverlay", titleOverlay.toJson())
        put("audioConfig", audioConfig.toJson())
    }

    fun saveDraft(file: File) {
        file.parentFile?.mkdirs()
        file.writeText(toJson().toString(2))
    }

    companion object {
        fun fromJson(json: JSONObject): DramaProjectState {
            val state = DramaProjectState(
                projectId = json.optString("projectId", UUID.randomUUID().toString()),
                projectName = json.optString("projectName", "Myanmar Drama Reel 1"),
                outputResolution = json.optString("outputResolution", "1080p"),
                updatedAt = json.optLong("updatedAt", System.currentTimeMillis()),
                blurBox = DramaBlurBox.fromJson(json.optJSONObject("blurBox")),
                logo = DramaLogo.fromJson(json.optJSONObject("logo")),
                titleOverlay = DramaTitleOverlay.fromJson(json.optJSONObject("titleOverlay")),
                audioConfig = DramaAudioConfig.fromJson(json.optJSONObject("audioConfig"))
            )

            val clipsArr = json.optJSONArray("clips")
            if (clipsArr != null) {
                for (i in 0 until clipsArr.length()) {
                    val cObj = clipsArr.optJSONObject(i) ?: continue
                    state.clips.add(DramaClip.fromJson(cObj))
                }
            }

            val subsArr = json.optJSONArray("subtitles")
            if (subsArr != null) {
                for (i in 0 until subsArr.length()) {
                    val sObj = subsArr.optJSONObject(i) ?: continue
                    state.subtitles.add(DramaSubtitleCue.fromJson(sObj))
                }
            }

            return state
        }

        fun loadDraft(file: File): DramaProjectState? {
            if (!file.exists() || file.length() == 0L) return null
            return runCatching {
                val json = JSONObject(file.readText())
                fromJson(json)
            }.getOrNull()
        }
    }
}
