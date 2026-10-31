package com.zipbug.base.recap

import com.zipbug.base.data.recap.RecapClipEntity
import com.zipbug.base.data.recap.RecapProjectEntity
import com.zipbug.base.data.recap.RecapRenderJobEntity
import com.zipbug.base.data.recap.RecapScriptEntity
import com.zipbug.base.termux.TermuxBridge
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

object RecapRenderCoordinator {

    data class RenderPlan(
        val jobId: String,
        val projectId: String,
        val stages: List<String>,
        val ffmpegArgs: List<String>,
        val outputFilePath: String,
        val workDir: String
    )

    fun formatSeconds(ms: Long): String {
        val totalSec = ms / 1000.0
        return String.format(Locale.US, "%.3f", totalSec)
    }

    /**
     * Builds structured, validated FFmpeg arguments for rendering the recap video.
     * Arguments are returned as an explicit List<String> with zero shell concatenation.
     */
    fun buildFfmpegArgs(
        clips: List<RecapClipEntity>,
        ttsAudioPath: String?,
        outputFilePath: String,
        applySubtitles: Boolean = false,
        subtitlesFilePath: String? = null
    ): Result<List<String>> {
        val timelineVal = RecapTimelineManager.validateTimeline(clips)
        if (timelineVal.isFailure) {
            return Result.failure(timelineVal.exceptionOrNull()!!)
        }

        if (outputFilePath.isBlank()) {
            return Result.failure(IllegalArgumentException("Output file path cannot be blank"))
        }

        val args = mutableListOf<String>()
        args.add("-y") // Overwrite output

        // Add inputs for each clip with trim
        for (clip in clips) {
            args.add("-ss")
            args.add(formatSeconds(clip.startMs))
            args.add("-to")
            args.add(formatSeconds(clip.endMs))
            args.add("-i")
            args.add(clip.sourcePath)
        }

        val clipCount = clips.size
        var audioInputIndex: Int? = null

        // Add TTS audio input if provided
        if (!ttsAudioPath.isNullOrBlank()) {
            args.add("-i")
            args.add(ttsAudioPath)
            audioInputIndex = clipCount
        }

        // Build filter_complex
        val filterComplex = StringBuilder()
        val videoStreamLabels = mutableListOf<String>()

        for (i in 0 until clipCount) {
            val clip = clips[i]
            val inLabel = "[$i:v]"
            val outLabel = "[v$i]"

            val filterChain = mutableListOf<String>()
            // Standardize resolution and frame rate
            filterChain.add("scale=1280:720:force_original_aspect_ratio=decrease,pad=1280:720:(ow-iw)/2:(oh-ih)/2,setsar=1")

            when (clip.effectType) {
                RecapClipEntity.EFFECT_FREEZE_ZOOM -> {
                    // Slight dynamic zoom effect
                    filterChain.add("zoompan=z='min(zoom+0.0015,1.15)':d=125:x='iw/2-(iw/zoom/2)':y='ih/2-(ih/zoom/2)'")
                }
                RecapClipEntity.EFFECT_BLUR -> {
                    filterChain.add("boxblur=5:2")
                }
                RecapClipEntity.EFFECT_SUBTITLES -> {
                    if (!subtitlesFilePath.isNullOrBlank()) {
                        val sanitizedSubPath = subtitlesFilePath.replace("'", "\\'")
                        filterChain.add("subtitles='$sanitizedSubPath'")
                    }
                }
                else -> {
                    // No additional filter
                }
            }

            filterComplex.append("$inLabel${filterChain.joinToString(",")}$outLabel;")
            videoStreamLabels.add(outLabel)
        }

        // Concatenate all video streams
        val concatFilter = StringBuilder()
        for (label in videoStreamLabels) {
            concatFilter.append(label)
        }
        concatFilter.append("concat=n=$clipCount:v=1:a=0[vconcat]")
        filterComplex.append(concatFilter.toString())

        // Add subtitle burning if requested globally
        var finalVideoLabel = "[vconcat]"
        if (applySubtitles && !subtitlesFilePath.isNullOrBlank()) {
            val sanitizedSub = subtitlesFilePath.replace("'", "\\'")
            filterComplex.append(";[vconcat]subtitles='$sanitizedSub'[vfinal]")
            finalVideoLabel = "[vfinal]"
        }

        args.add("-filter_complex")
        args.add(filterComplex.toString())

        // Map video
        args.add("-map")
        args.add(finalVideoLabel)

        // Map audio
        if (audioInputIndex != null) {
            args.add("-map")
            args.add("$audioInputIndex:a")
            args.add("-c:a")
            args.add("aac")
            args.add("-b:a")
            args.add("192k")
        } else {
            // Use audio from first clip if available or silent
            args.add("-map")
            args.add("0:a?")
            args.add("-c:a")
            args.add("aac")
        }

        // Video encoding options
        args.add("-c:v")
        args.add("libx264")
        args.add("-preset")
        args.add("ultrafast")
        args.add("-pix_fmt")
        args.add("yuv420p")
        args.add("-movflags")
        args.add("+faststart")
        args.add(outputFilePath)

        return Result.success(args)
    }

    /**
     * Generates a standard SRT format subtitle file from the 4 script parts and clip timings.
     */
    fun generateSrtContent(
        scripts: List<RecapScriptEntity>,
        clips: List<RecapClipEntity>
    ): String {
        val sb = StringBuilder()
        var currentMs = 0L
        var srtIndex = 1

        val orderedScripts = scripts.sortedBy { it.partNumber }
        val totalDurationMs = clips.sumOf { (it.endMs - it.startMs).coerceAtLeast(0L) }
        val partDurationMs = if (orderedScripts.isNotEmpty()) {
            totalDurationMs / orderedScripts.size
        } else {
            5000L
        }

        for (script in orderedScripts) {
            if (script.scriptText.isBlank()) continue

            val sentences = script.scriptText.split(Regex("(?<=[။\n.!?])"))
                .map { it.trim() }
                .filter { it.isNotBlank() }

            val sentenceInterval = if (sentences.isNotEmpty()) {
                partDurationMs / sentences.size
            } else {
                partDurationMs
            }

            for (sentence in sentences) {
                val start = currentMs
                val end = currentMs + sentenceInterval
                currentMs = end

                sb.append(srtIndex++).append("\n")
                sb.append(formatSrtTimestamp(start)).append(" --> ").append(formatSrtTimestamp(end)).append("\n")
                sb.append(sentence).append("\n\n")
            }
        }

        return sb.toString()
    }

    private fun formatSrtTimestamp(ms: Long): String {
        val hours = ms / 3600000
        val minutes = (ms % 3600000) / 60000
        val seconds = (ms % 60000) / 1000
        val millis = ms % 1000
        return String.format(Locale.US, "%02d:%02d:%02d,%03d", hours, minutes, seconds, millis)
    }

    /**
     * Prepares a full render plan with verified parameters and structured arguments.
     */
    fun preparePlan(
        project: RecapProjectEntity,
        clips: List<RecapClipEntity>,
        scripts: List<RecapScriptEntity>,
        outputDirectory: String,
        workDir: String = "/data/data/com.termux/files/home"
    ): Result<RenderPlan> {
        val validation = RecapTimelineManager.validateTimeline(clips)
        if (validation.isFailure) {
            return Result.failure(validation.exceptionOrNull()!!)
        }

        val jobId = UUID.randomUUID().toString()
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val safeName = project.name.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val outputFilePath = "$outputDirectory/Recap_${safeName}_$timestamp.mp4"

        // Pick TTS audio path if any script part has generated voiceover
        val ttsAudio = scripts.firstOrNull { !it.ttsAudioPath.isNullOrBlank() }?.ttsAudioPath

        val ffmpegArgsResult = buildFfmpegArgs(
            clips = clips,
            ttsAudioPath = ttsAudio,
            outputFilePath = outputFilePath
        )

        if (ffmpegArgsResult.isFailure) {
            return Result.failure(ffmpegArgsResult.exceptionOrNull()!!)
        }

        return Result.success(
            RenderPlan(
                jobId = jobId,
                projectId = project.id,
                stages = RecapRenderJobEntity.PIPELINE_STAGES,
                ffmpegArgs = ffmpegArgsResult.getOrThrow(),
                outputFilePath = outputFilePath,
                workDir = workDir
            )
        )
    }

    /**
     * Truthfully validates the output video file:
     * - Checks that exit code is 0
     * - Checks that file exists on disk
     * - Checks that file size is strictly > 0 bytes
     * Returns Result.failure with 4-part diagnosis if validation fails.
     */
    fun verifyRenderResult(
        exitCode: Int?,
        outputFilePath: String
    ): Result<FileVerification> {
        if (exitCode != 0) {
            val diag = RecapFailureDiagnosis.ffmpegProcessFailed(exitCode, "Process exited with non-zero exit code")
            return Result.failure(IllegalStateException(diag.format()))
        }

        val file = File(outputFilePath)
        if (!file.exists()) {
            val diag = RecapFailureDiagnosis.outputVerificationFailed(outputFilePath, exists = false, sizeBytes = 0L)
            return Result.failure(IllegalStateException(diag.format()))
        }

        val size = file.length()
        if (size <= 0L) {
            val diag = RecapFailureDiagnosis.outputVerificationFailed(outputFilePath, exists = true, sizeBytes = size)
            return Result.failure(IllegalStateException(diag.format()))
        }

        return Result.success(
            FileVerification(
                filePath = file.absolutePath,
                fileName = file.name,
                sizeBytes = size,
                verified = true
            )
        )
    }

    data class FileVerification(
        val filePath: String,
        val fileName: String,
        val sizeBytes: Long,
        val verified: Boolean
    )
}
