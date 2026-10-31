package com.zipbug.base.recap

import com.zipbug.base.data.recap.RecapScriptEntity
import java.io.File
import java.io.OutputStream
import java.util.Locale

/**
 * Exports multi-part recap or narration scripts into TXT, SRT, and WebVTT (.vtt) formats.
 * Also supports converting raw plain text into timed subtitle cues.
 */
object SubtitleExporter {

    const val FORMAT_TXT = "TXT"
    const val FORMAT_SRT = "SRT"
    const val FORMAT_VTT = "VTT"

    data class SubtitleCue(
        val index: Int,
        val startMs: Long,
        val endMs: Long,
        val title: String,
        val text: String
    )

    fun buildCuesFromScripts(
        scripts: List<RecapScriptEntity>,
        totalDurationMs: Long = 0L
    ): List<SubtitleCue> {
        val ordered = scripts.sortedBy { it.partNumber }.filter { it.scriptText.isNotBlank() }
        if (ordered.isEmpty()) return emptyList()

        val effectiveTotalMs = if (totalDurationMs > 0L) {
            totalDurationMs
        } else {
            ordered.sumOf { estimateDurationMs(it.scriptText) }
        }.coerceAtLeast(ordered.size * 2000L)

        val totalWeight = ordered.sumOf { it.scriptText.length.coerceAtLeast(10) }
        var cursorMs = 0L

        return ordered.mapIndexed { idx, part ->
            val partDuration = if (idx == ordered.lastIndex) {
                (effectiveTotalMs - cursorMs).coerceAtLeast(1000L)
            } else {
                val weight = part.scriptText.length.coerceAtLeast(10)
                ((effectiveTotalMs * weight) / totalWeight).coerceAtLeast(1000L)
            }
            val start = cursorMs
            val end = start + partDuration
            cursorMs = end
            SubtitleCue(
                index = idx + 1,
                startMs = start,
                endMs = end,
                title = "Part ${part.partNumber}: ${part.title}",
                text = part.scriptText.trim()
            )
        }
    }

    fun buildCuesFromPlainText(rawText: String, perCueDurationMs: Long = 5000L): List<SubtitleCue> {
        val trimmed = rawText.trim()
        if (trimmed.isEmpty()) return emptyList()
        val lines = trimmed.split(Regex("(?<=[။.!?\\n])"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        var cursorMs = 0L
        return lines.mapIndexed { idx, line ->
            val dur = estimateDurationMs(line).coerceAtLeast(perCueDurationMs.coerceAtMost(2500L))
            val start = cursorMs
            val end = start + dur
            cursorMs = end
            SubtitleCue(
                index = idx + 1,
                startMs = start,
                endMs = end,
                title = "Segment ${idx + 1}",
                text = line
            )
        }
    }

    private fun estimateDurationMs(text: String): Long {
        val seconds = (text.length / 12).coerceAtLeast(3)
        return seconds * 1000L
    }

    const val TIMING_NOTE = "Timing is estimated from text length / clip duration (not speech-aligned transcription)."

    fun toTxt(scripts: List<RecapScriptEntity>): String {
        val ordered = scripts.sortedBy { it.partNumber }
        if (ordered.isEmpty()) return ""
        val sb = StringBuilder("# NOTE: $TIMING_NOTE\n\n")
        ordered.forEachIndexed { idx, part ->
            sb.append("[PART ").append(part.partNumber).append(": ").append(part.title).append("]\n")
            sb.append(part.scriptText.trim()).append("\n")
            if (idx < ordered.lastIndex) {
                sb.append("\n")
            }
        }
        return sb.toString()
    }

    fun toSrt(scripts: List<RecapScriptEntity>, totalDurationMs: Long = 0L): String {
        val cues = buildCuesFromScripts(scripts, totalDurationMs)
        return cuesToSrt(cues)
    }

    fun toVtt(scripts: List<RecapScriptEntity>, totalDurationMs: Long = 0L): String {
        val cues = buildCuesFromScripts(scripts, totalDurationMs)
        return cuesToVtt(cues)
    }

    fun cuesToSrt(cues: List<SubtitleCue>): String {
        if (cues.isEmpty()) return ""
        val sb = StringBuilder()
        cues.forEachIndexed { idx, cue ->
            sb.append(cue.index).append("\n")
            sb.append(formatSrtTimestamp(cue.startMs))
                .append(" --> ")
                .append(formatSrtTimestamp(cue.endMs))
                .append("\n")
            sb.append(cue.text).append("\n")
            if (idx < cues.lastIndex) {
                sb.append("\n")
            }
        }
        return sb.toString()
    }

    fun cuesToVtt(cues: List<SubtitleCue>): String {
        val sb = StringBuilder("WEBVTT\n\nNOTE $TIMING_NOTE\n\n")
        cues.forEachIndexed { idx, cue ->
            sb.append(cue.index).append("\n")
            sb.append(formatVttTimestamp(cue.startMs))
                .append(" --> ")
                .append(formatVttTimestamp(cue.endMs))
                .append("\n")
            sb.append(cue.text).append("\n")
            if (idx < cues.lastIndex) {
                sb.append("\n")
            }
        }
        return sb.toString()
    }

    fun formatSrtTimestamp(ms: Long): String {
        val safeMs = ms.coerceAtLeast(0L)
        val hours = safeMs / 3_600_000L
        val minutes = (safeMs % 3_600_000L) / 60_000L
        val seconds = (safeMs % 60_000L) / 1_000L
        val millis = safeMs % 1_000L
        return String.format(Locale.US, "%02d:%02d:%02d,%03d", hours, minutes, seconds, millis)
    }

    fun formatVttTimestamp(ms: Long): String {
        val safeMs = ms.coerceAtLeast(0L)
        val hours = safeMs / 3_600_000L
        val minutes = (safeMs % 3_600_000L) / 60_000L
        val seconds = (safeMs % 60_000L) / 1_000L
        val millis = safeMs % 1_000L
        return String.format(Locale.US, "%02d:%02d:%02d.%03d", hours, minutes, seconds, millis)
    }

    fun formatContent(
        format: String,
        scripts: List<RecapScriptEntity>,
        totalDurationMs: Long = 0L
    ): String = when (format.uppercase(Locale.US)) {
        FORMAT_SRT -> toSrt(scripts, totalDurationMs)
        FORMAT_VTT -> toVtt(scripts, totalDurationMs)
        else -> toTxt(scripts)
    }

    fun exportToFile(
        targetFile: File,
        format: String,
        scripts: List<RecapScriptEntity>,
        totalDurationMs: Long = 0L
    ): Result<Long> = runCatching {
        val content = formatContent(format, scripts, totalDurationMs)
        require(content.isNotBlank()) { "Cannot export empty subtitle/transcript content" }
        targetFile.parentFile?.mkdirs()
        targetFile.writeText(content, Charsets.UTF_8)
        require(targetFile.exists() && targetFile.length() > 0L) {
            "Export verification failed: file does not exist or has 0 bytes"
        }
        targetFile.length()
    }

    fun exportToStream(
        outputStream: OutputStream,
        format: String,
        scripts: List<RecapScriptEntity>,
        totalDurationMs: Long = 0L
    ): Result<Int> = runCatching {
        val content = formatContent(format, scripts, totalDurationMs)
        require(content.isNotBlank()) { "Cannot export empty subtitle/transcript content" }
        val bytes = content.toByteArray(Charsets.UTF_8)
        outputStream.write(bytes)
        outputStream.flush()
        bytes.size
    }
}
