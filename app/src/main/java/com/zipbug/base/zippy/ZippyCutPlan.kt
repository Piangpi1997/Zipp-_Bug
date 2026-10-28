package com.zipbug.base.zippy

import com.zipbug.base.recap.SubtitleExporter
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * Pure, JVM-testable edit state + render-plan logic for the Zippy Cut Studio.
 *
 * Design rules (truth rules of this project):
 * - No shell strings are ever built; the ffmpeg plan is an explicit argument list.
 * - Subtitle times are on the OUTPUT timeline (0 .. trimmed length), because the
 *   SRT is burned after the trim/split concatenation.
 * - A title is only burned with drawtext when the caller supplies a real font
 *   file; otherwise it is stored as container metadata. Nothing is faked.
 */
object ZippyCutPlan {

    data class SubtitleLine(
        val startMs: Long,
        val endMs: Long,
        val text: String
    )

    data class Segment(
        val startMs: Long,
        val endMs: Long
    ) {
        val durationMs: Long get() = endMs - startMs
    }

    data class EditState(
        val sourceUri: String = "",
        val sourceName: String = "",
        val stagedPath: String = "",
        val durationMs: Long = 0L,
        val width: Int = 0,
        val height: Int = 0,
        val trimStartMs: Long = 0L,
        val trimEndMs: Long = 0L,
        val splitMs: Long? = null,
        val title: String = "",
        val keepAudio: Boolean = true,
        val subtitles: List<SubtitleLine> = emptyList()
    ) {
        val trimmedDurationMs: Long
            get() = (trimEndMs - trimStartMs).coerceAtLeast(0L)
    }

    /** Returns a human-readable error, or null when the state is renderable. */
    fun validate(state: EditState): String? {
        if (state.stagedPath.isBlank() && state.sourceUri.isBlank()) {
            return "No source video selected"
        }
        if (state.trimStartMs < 0L) return "Trim start cannot be negative"
        if (state.trimEndMs <= state.trimStartMs) return "Trim end must be after trim start"
        if (state.durationMs > 0L && state.trimEndMs > state.durationMs) {
            return "Trim end exceeds video duration"
        }
        val split = state.splitMs
        if (split != null && (split <= state.trimStartMs || split >= state.trimEndMs)) {
            return "Split point must be strictly inside the trim range"
        }
        val outDuration = state.trimmedDurationMs
        for (line in state.subtitles) {
            if (line.text.isBlank()) return "Subtitle text cannot be blank"
            if (line.startMs < 0L) return "Subtitle start cannot be negative"
            if (line.endMs <= line.startMs) return "Subtitle end must be after its start"
            if (outDuration > 0L && line.endMs > outDuration) {
                return "Subtitle end exceeds the trimmed video length"
            }
        }
        return null
    }

    /** The export segments implied by trim + optional split, in source time. */
    fun segments(state: EditState): List<Segment> {
        val split = state.splitMs
        return if (split != null && split > state.trimStartMs && split < state.trimEndMs) {
            listOf(
                Segment(state.trimStartMs, split),
                Segment(split, state.trimEndMs)
            )
        } else {
            listOf(Segment(state.trimStartMs, state.trimEndMs))
        }
    }

    /** SRT (output timeline) for the manual subtitle lines, reusing SubtitleExporter. */
    fun subtitlesToSrt(subtitles: List<SubtitleLine>): String {
        val cues = subtitles.sortedBy { it.startMs }.mapIndexed { idx, line ->
            SubtitleExporter.SubtitleCue(
                index = idx + 1,
                startMs = line.startMs,
                endMs = line.endMs,
                title = "Zippy subtitle ${idx + 1}",
                text = line.text.trim()
            )
        }
        return SubtitleExporter.cuesToSrt(cues)
    }

    fun formatSeconds(ms: Long): String =
        String.format(Locale.US, "%.3f", ms / 1000.0)

    private fun escapeDrawtext(text: String): String = text
        .replace("\\", "\\\\")
        .replace(":", "\\:")
        .replace("'", "\\'")
        .replace("%", "\\%")

    private fun escapeFilterPath(path: String): String = path
        .replace("\\", "\\\\")
        .replace("'", "\\'")
        .replace(":", "\\:")

    /**
     * Builds the ffmpeg argument list for one export.
     *
     * @param fontFile real font file for the title drawtext; when null the title
     * is written as container metadata only (reported honestly by the UI).
     */
    fun buildFfmpegArgs(
        state: EditState,
        inputPath: String,
        outputPath: String,
        subtitlesPath: String? = null,
        fontFile: String? = null
    ): Result<List<String>> {
        validate(state)?.let { return Result.failure(IllegalArgumentException(it)) }
        if (inputPath.isBlank()) {
            return Result.failure(IllegalArgumentException("Input path cannot be blank"))
        }
        if (outputPath.isBlank()) {
            return Result.failure(IllegalArgumentException("Output path cannot be blank"))
        }
        val segments = segments(state)
        val args = mutableListOf<String>()
        args.add("-y")

        for (segment in segments) {
            args.add("-ss")
            args.add(formatSeconds(segment.startMs))
            args.add("-to")
            args.add(formatSeconds(segment.endMs))
            args.add("-i")
            args.add(inputPath)
        }

        val filter = StringBuilder()
        segments.forEachIndexed { i, _ ->
            filter.append("[$i:v]scale=1280:720:force_original_aspect_ratio=decrease," +
                "pad=1280:720:(ow-iw)/2:(oh-ih)/2,setsar=1[v$i];")
            if (state.keepAudio) {
                filter.append("[$i:a]aresample=48000[a$i];")
            }
        }
        if (state.keepAudio) {
            val labels = segments.indices.joinToString("") { "[v$it][a$it]" }
            filter.append("$labels" +
                "concat=n=${segments.size}:v=1:a=1[vconcat][aconcat]")
        } else {
            val vLabels = segments.indices.joinToString("") { "[v$it]" }
            filter.append("${vLabels}concat=n=${segments.size}:v=1:a=0[vconcat]")
        }

        var finalVideo = "[vconcat]"
        if (state.title.isNotBlank() && !fontFile.isNullOrBlank()) {
            filter.append(";[vconcat]drawtext=fontfile='${escapeFilterPath(fontFile)}':" +
                "text='${escapeDrawtext(state.title.trim())}':fontcolor=white:fontsize=36:" +
                "borderw=2:bordercolor=black:x=(w-text_w)/2:y=24[vtitled]")
            finalVideo = "[vtitled]"
        }
        if (!subtitlesPath.isNullOrBlank()) {
            filter.append(";$finalVideo" +
                "subtitles='${escapeFilterPath(subtitlesPath)}'[vfinal]")
            finalVideo = "[vfinal]"
        }

        args.add("-filter_complex")
        args.add(filter.toString())
        args.add("-map")
        args.add(finalVideo)
        if (state.keepAudio) {
            args.add("-map")
            args.add("[aconcat]")
            args.add("-c:a")
            args.add("aac")
            args.add("-b:a")
            args.add("192k")
        }
        args.add("-c:v")
        args.add("libx264")
        args.add("-preset")
        args.add("ultrafast")
        args.add("-pix_fmt")
        args.add("yuv420p")
        if (state.title.isNotBlank()) {
            args.add("-metadata")
            args.add("title=${state.title.trim()}")
        }
        args.add("-movflags")
        args.add("+faststart")
        args.add(outputPath)
        return Result.success(args)
    }

    // ----- Draft persistence (JSON in app-private storage) -----

    fun toJson(state: EditState): String {
        val obj = JSONObject()
        obj.put("sourceUri", state.sourceUri)
        obj.put("sourceName", state.sourceName)
        obj.put("stagedPath", state.stagedPath)
        obj.put("durationMs", state.durationMs)
        obj.put("width", state.width)
        obj.put("height", state.height)
        obj.put("trimStartMs", state.trimStartMs)
        obj.put("trimEndMs", state.trimEndMs)
        if (state.splitMs != null) obj.put("splitMs", state.splitMs) else obj.put("splitMs", JSONObject.NULL)
        obj.put("title", state.title)
        obj.put("keepAudio", state.keepAudio)
        val subs = JSONArray()
        for (line in state.subtitles) {
            val s = JSONObject()
            s.put("startMs", line.startMs)
            s.put("endMs", line.endMs)
            s.put("text", line.text)
            subs.put(s)
        }
        obj.put("subtitles", subs)
        return obj.toString()
    }

    fun fromJson(json: String): EditState {
        val obj = JSONObject(json)
        val subs = mutableListOf<SubtitleLine>()
        val arr = obj.optJSONArray("subtitles")
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val s = arr.optJSONObject(i) ?: continue
                subs.add(
                    SubtitleLine(
                        startMs = s.optLong("startMs", 0L),
                        endMs = s.optLong("endMs", 0L),
                        text = s.optString("text", "")
                    )
                )
            }
        }
        return EditState(
            sourceUri = obj.optString("sourceUri", ""),
            sourceName = obj.optString("sourceName", ""),
            stagedPath = obj.optString("stagedPath", ""),
            durationMs = obj.optLong("durationMs", 0L),
            width = obj.optInt("width", 0),
            height = obj.optInt("height", 0),
            trimStartMs = obj.optLong("trimStartMs", 0L),
            trimEndMs = obj.optLong("trimEndMs", 0L),
            splitMs = if (obj.isNull("splitMs")) null else obj.optLong("splitMs"),
            title = obj.optString("title", ""),
            keepAudio = obj.optBoolean("keepAudio", true),
            subtitles = subs
        )
    }
}
