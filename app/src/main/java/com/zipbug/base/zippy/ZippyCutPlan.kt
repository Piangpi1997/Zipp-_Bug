package com.zipbug.base.zippy

import com.zipbug.base.recap.SubtitleExporter
import org.json.JSONArray
import org.json.JSONObject

/**
 * Pure edit-plan logic for the Zippy Cut Studio.
 *
 * Everything here is JVM-only (no Android imports) so it can be unit tested
 * without a device: multi-clip list with reorder, per-clip trim, split,
 * manual subtitles -> SRT (reusing [SubtitleExporter]'s cue formatting),
 * title + text overlays, logo overlay, audio keep/mute/replace, and the
 * ffmpeg argument list that the existing Termux engine executes (the same
 * execution path Recap Studio uses — ZIP_BUG ships no native ffmpeg).
 *
 * Drafts are plain JSON in app-private storage; original media is never
 * modified. Subtitle timings are expressed on the OUTPUT timeline (after
 * all clip trims are applied, in clip-list order).
 */
object ZippyCutPlan {

    data class SubtitleLine(
        val startMs: Long,
        val endMs: Long,
        val text: String
    )

    data class Clip(
        val id: String,
        val sourceUri: String = "",
        val sourceName: String = "",
        val stagedPath: String = "",
        val durationMs: Long = 0L,
        val width: Int = 0,
        val height: Int = 0,
        val trimStartMs: Long = 0L,
        val trimEndMs: Long = 0L
    ) {
        val trimmedDurationMs: Long get() = trimEndMs - trimStartMs
    }

    enum class AudioMode { KEEP, MUTE, REPLACE }

    data class EditState(
        val clips: List<Clip> = emptyList(),
        val selectedClip: Int = 0,
        val title: String = "",
        val textOverlay: String = "",
        val logoUri: String = "",
        val logoPath: String = "",
        val audioMode: AudioMode = AudioMode.KEEP,
        val replaceAudioUri: String = "",
        val replaceAudioPath: String = "",
        val replaceAudioName: String = "",
        val subtitles: List<SubtitleLine> = emptyList()
    )

    /** One input range of one clip, in export order. */
    data class Segment(
        val clipIndex: Int,
        val inputPath: String,
        val startMs: Long,
        val endMs: Long
    ) {
        val durationMs: Long get() = endMs - startMs
    }

    fun totalDurationMs(state: EditState): Long =
        state.clips.sumOf { it.trimmedDurationMs.coerceAtLeast(0L) }

    /** Returns a human-readable error, or null when the state is exportable. */
    fun validate(state: EditState): String? {
        if (state.clips.isEmpty()) return "Add at least one video clip"
        state.clips.forEachIndexed { i, clip ->
            val label = "Clip ${i + 1} (${clip.sourceName})"
            if (clip.stagedPath.isBlank()) return "$label has no staged copy yet"
            if (clip.durationMs <= 0L) return "$label has no loaded duration metadata"
            if (clip.trimStartMs < 0L) return "$label trim start is negative"
            if (clip.trimEndMs <= clip.trimStartMs) return "$label trim end must be after trim start"
            if (clip.trimEndMs > clip.durationMs) return "$label trim end exceeds clip duration"
        }
        if (state.audioMode == AudioMode.REPLACE && state.replaceAudioPath.isBlank()) {
            return "Audio mode is REPLACE but no replacement audio is selected/staged"
        }
        val total = totalDurationMs(state)
        state.subtitles.forEachIndexed { i, line ->
            if (line.text.isBlank()) return "Subtitle ${i + 1} has no text"
            if (line.startMs < 0L || line.endMs <= line.startMs) {
                return "Subtitle ${i + 1} end must be after start"
            }
            if (line.endMs > total) {
                return "Subtitle ${i + 1} exceeds the edited video length"
            }
        }
        return null
    }

    fun segments(state: EditState): List<Segment> =
        state.clips.mapIndexed { index, clip ->
            Segment(index, clip.stagedPath, clip.trimStartMs, clip.trimEndMs)
        }

    /** Moves a clip in the list (reorder up/down). Out-of-range moves are no-ops. */
    fun moveClip(state: EditState, from: Int, to: Int): EditState {
        if (from !in state.clips.indices || to !in state.clips.indices || from == to) return state
        val mutable = state.clips.toMutableList()
        val clip = mutable.removeAt(from)
        mutable.add(to, clip)
        val selected = when {
            state.selectedClip == from -> to
            state.selectedClip in minOf(from, to)..maxOf(from, to) ->
                if (from < to) state.selectedClip - 1 else state.selectedClip + 1
            else -> state.selectedClip
        }
        return state.copy(clips = mutable, selectedClip = selected)
    }

    /** Splits one clip at an absolute source-time point into two adjacent clips. */
    fun splitClip(state: EditState, clipIndex: Int, atMs: Long): Result<EditState> {
        val clip = state.clips.getOrNull(clipIndex)
            ?: return Result.failure(IllegalArgumentException("No clip at index $clipIndex"))
        if (atMs <= clip.trimStartMs || atMs >= clip.trimEndMs) {
            return Result.failure(
                IllegalArgumentException("Split point must be inside the clip's trim range")
            )
        }
        val first = clip.copy(trimEndMs = atMs)
        val second = clip.copy(id = clip.id + "b", trimStartMs = atMs)
        val mutable = state.clips.toMutableList()
        mutable[clipIndex] = first
        mutable.add(clipIndex + 1, second)
        return Result.success(state.copy(clips = mutable))
    }

    fun subtitlesToSrt(lines: List<SubtitleLine>): String =
        SubtitleExporter.cuesToSrt(
            lines.sortedBy { it.startMs }.mapIndexed { i, line ->
                SubtitleExporter.SubtitleCue(i + 1, line.startMs, line.endMs, "", line.text)
            }
        )

    /**
     * Builds the ffmpeg argument list (executed by Termux, never a shell
     * string). Video is normalised to 1280x720-padded H.264, matching the
     * Recap renderer's output contract.
     *
     * Input order is deterministic: clip inputs 0..N-1, then the logo image
     * input (if any), then the replacement-audio input (if REPLACE).
     * Title/text overlays use drawtext and therefore need [fontFile]; a
     * title alone falls back to container metadata when no font exists, but
     * a text overlay without a font fails loudly instead of being dropped.
     */
    fun buildFfmpegArgs(
        state: EditState,
        outputPath: String,
        subtitlesPath: String? = null,
        fontFile: String? = null
    ): Result<List<String>> {
        validate(state)?.let { return Result.failure(IllegalArgumentException(it)) }
        if (state.textOverlay.isNotBlank() && fontFile == null) {
            return Result.failure(
                IllegalArgumentException("Text overlay requires a font file for drawtext")
            )
        }
        val segments = segments(state)
        val args = mutableListOf("-y")
        segments.forEach { seg ->
            args += listOf(
                "-ss", formatSeconds(seg.startMs),
                "-to", formatSeconds(seg.endMs),
                "-i", seg.inputPath
            )
        }
        val logoInputIndex = if (state.logoPath.isNotBlank()) {
            args += listOf("-i", state.logoPath)
            segments.size
        } else -1
        val replaceInputIndex = if (state.audioMode == AudioMode.REPLACE) {
            args += listOf("-i", state.replaceAudioPath)
            segments.size + (if (logoInputIndex >= 0) 1 else 0)
        } else -1

        val filter = StringBuilder()
        segments.forEachIndexed { i, seg ->
            filter.append("[$i:v]scale=1280:720:force_original_aspect_ratio=decrease,")
                .append("pad=1280:720:(ow-iw)/2:(oh-ih)/2,setsar=1,fps=30[v$i];")
            if (state.audioMode == AudioMode.KEEP) {
                filter.append("[$i:a]atrim=0:${formatSeconds(seg.durationMs)},")
                    .append("asetpts=PTS-STARTPTS[a$i];")
            }
        }
        val concatInputs = segments.indices.joinToString("") { i ->
            if (state.audioMode == AudioMode.KEEP) "[v$i][a$i]" else "[v$i]"
        }
        filter.append(concatInputs)
            .append("concat=n=${segments.size}:v=1:a=${if (state.audioMode == AudioMode.KEEP) 1 else 0}")
            .append("[vcat]")
        if (state.audioMode == AudioMode.KEEP) {
            filter.append("[aout];")
        } else {
            filter.append(";")
        }
        if (state.audioMode == AudioMode.REPLACE) {
            filter.append("[$replaceInputIndex:a]atrim=0:${formatSeconds(totalDurationMs(state))},")
                .append("asetpts=PTS-STARTPTS[aout];")
        }

        var videoLabel = "vcat"
        var step = 0
        fun nextLabel(): String = "vstep${step++}"
        if (logoInputIndex >= 0) {
            val out = nextLabel()
            filter.append("[$videoLabel][$logoInputIndex:v]overlay=16:16[$out];")
            videoLabel = out
        }
        if (fontFile != null && state.title.isNotBlank()) {
            val out = nextLabel()
            filter.append("[$videoLabel]drawtext=fontfile=${escapeFilterPath(fontFile)}:")
                .append("text='${escapeDrawtext(state.title)}':fontcolor=white:fontsize=44:")
                .append("borderw=2:bordercolor=black:x=(w-text_w)/2:y=24[$out];")
            videoLabel = out
        }
        if (fontFile != null && state.textOverlay.isNotBlank()) {
            val out = nextLabel()
            filter.append("[$videoLabel]drawtext=fontfile=${escapeFilterPath(fontFile)}:")
                .append("text='${escapeDrawtext(state.textOverlay)}':fontcolor=white:fontsize=36:")
                .append("borderw=2:bordercolor=black:x=(w-text_w)/2:y=h-64[$out];")
            videoLabel = out
        }
        if (subtitlesPath != null) {
            val out = nextLabel()
            filter.append("[$videoLabel]subtitles=${escapeFilterPath(subtitlesPath)}[$out];")
            videoLabel = out
        }
        filter.append("[$videoLabel]format=yuv420p[vout]")
        args += listOf("-filter_complex", filter.toString())
        args += listOf("-map", "[vout]")
        if (state.audioMode != AudioMode.MUTE) {
            args += listOf("-map", "[aout]", "-c:a", "aac", "-b:a", "192k")
        }
        args += listOf("-c:v", "libx264", "-preset", "ultrafast", "-pix_fmt", "yuv420p")
        if (fontFile == null && state.title.isNotBlank()) {
            args += listOf("-metadata", "title=${state.title}")
        }
        args += listOf("-movflags", "+faststart", outputPath)
        return Result.success(args)
    }

    internal fun formatSeconds(ms: Long): String =
        String.format(java.util.Locale.US, "%.3f", ms / 1000.0)

    private fun escapeDrawtext(text: String): String =
        text.replace("\\", "\\\\").replace("'", "\\'").replace(":", "\\:")
            .replace("%", "\\%").replace("\n", " ")

    private fun escapeFilterPath(path: String): String =
        path.replace("\\", "\\\\").replace(":", "\\:").replace("'", "\\'")

    // -------- Draft JSON (schema v2, with v1 migration) --------

    fun toJson(state: EditState): String {
        val clips = JSONArray()
        state.clips.forEach { clip ->
            clips.put(
                JSONObject()
                    .put("id", clip.id)
                    .put("sourceUri", clip.sourceUri)
                    .put("sourceName", clip.sourceName)
                    .put("stagedPath", clip.stagedPath)
                    .put("durationMs", clip.durationMs)
                    .put("width", clip.width)
                    .put("height", clip.height)
                    .put("trimStartMs", clip.trimStartMs)
                    .put("trimEndMs", clip.trimEndMs)
            )
        }
        val subs = JSONArray()
        state.subtitles.forEach { line ->
            subs.put(
                JSONObject()
                    .put("startMs", line.startMs)
                    .put("endMs", line.endMs)
                    .put("text", line.text)
            )
        }
        return JSONObject()
            .put("version", 2)
            .put("clips", clips)
            .put("selectedClip", state.selectedClip)
            .put("title", state.title)
            .put("textOverlay", state.textOverlay)
            .put("logoUri", state.logoUri)
            .put("logoPath", state.logoPath)
            .put("audioMode", state.audioMode.name)
            .put("replaceAudioUri", state.replaceAudioUri)
            .put("replaceAudioPath", state.replaceAudioPath)
            .put("replaceAudioName", state.replaceAudioName)
            .put("subtitles", subs)
            .toString()
    }

    fun fromJson(json: String): EditState {
        val obj = JSONObject(json)
        val clips = mutableListOf<Clip>()
        val arr = obj.optJSONArray("clips")
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val c = arr.optJSONObject(i) ?: continue
                clips += Clip(
                    id = c.optString("id", "clip$i"),
                    sourceUri = c.optString("sourceUri"),
                    sourceName = c.optString("sourceName"),
                    stagedPath = c.optString("stagedPath"),
                    durationMs = c.optLong("durationMs"),
                    width = c.optInt("width"),
                    height = c.optInt("height"),
                    trimStartMs = c.optLong("trimStartMs"),
                    trimEndMs = c.optLong("trimEndMs")
                )
            }
        } else if (obj.optString("sourceUri").isNotBlank()) {
            // Schema v1 (single source) migration. A v1 split point inside
            // the trim range is preserved by splitting the migrated clip.
            val migrated = Clip(
                id = "clip0",
                sourceUri = obj.optString("sourceUri"),
                sourceName = obj.optString("sourceName"),
                stagedPath = obj.optString("stagedPath"),
                durationMs = obj.optLong("durationMs"),
                width = obj.optInt("width"),
                height = obj.optInt("height"),
                trimStartMs = obj.optLong("trimStartMs"),
                trimEndMs = obj.optLong("trimEndMs")
            )
            val splitMs = if (obj.isNull("splitMs")) null else obj.optLong("splitMs")
            if (splitMs != null && splitMs > migrated.trimStartMs && splitMs < migrated.trimEndMs) {
                clips += migrated.copy(trimEndMs = splitMs)
                clips += migrated.copy(id = "clip0b", trimStartMs = splitMs)
            } else {
                clips += migrated
            }
        }
        val subs = mutableListOf<SubtitleLine>()
        val sarr = obj.optJSONArray("subtitles")
        if (sarr != null) {
            for (i in 0 until sarr.length()) {
                val s = sarr.optJSONObject(i) ?: continue
                subs += SubtitleLine(s.optLong("startMs"), s.optLong("endMs"), s.optString("text"))
            }
        }
        val mode = runCatching {
            AudioMode.valueOf(obj.optString("audioMode", "KEEP"))
        }.getOrDefault(AudioMode.KEEP)
        val keepAudioLegacy = if (obj.has("keepAudio")) obj.optBoolean("keepAudio", true) else true
        return EditState(
            clips = clips,
            selectedClip = obj.optInt("selectedClip", 0)
                .coerceIn(0, maxOf(0, clips.size - 1)),
            title = obj.optString("title"),
            textOverlay = obj.optString("textOverlay"),
            logoUri = obj.optString("logoUri"),
            logoPath = obj.optString("logoPath"),
            audioMode = if (obj.has("audioMode")) mode else if (keepAudioLegacy) AudioMode.KEEP else AudioMode.MUTE,
            replaceAudioUri = obj.optString("replaceAudioUri"),
            replaceAudioPath = obj.optString("replaceAudioPath"),
            replaceAudioName = obj.optString("replaceAudioName"),
            subtitles = subs
        )
    }
}
