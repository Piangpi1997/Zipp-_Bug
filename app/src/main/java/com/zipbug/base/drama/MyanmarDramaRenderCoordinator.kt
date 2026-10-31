package com.zipbug.base.drama

import android.media.MediaMetadataRetriever
import java.io.File
import java.util.Locale

object MyanmarDramaRenderCoordinator {

    data class DramaRenderPlan(
        val outputFilePath: String,
        val assSubtitlesPath: String,
        val ffmpegArgs: List<String>,
        val width: Int,
        val height: Int,
        val totalDurationMs: Long
    )

    fun formatAssTimestamp(ms: Long): String {
        val hours = ms / 3600000
        val minutes = (ms % 3600000) / 60000
        val seconds = (ms % 60000) / 1000
        val centis = (ms % 1000) / 10
        return String.format(Locale.US, "%d:%02d:%02d.%02d", hours, minutes, seconds, centis)
    }

    /**
     * Generates a high-fidelity Advanced SubStation Alpha (.ass) subtitle file.
     * Supports Myanmar Unicode text (Noto Sans Myanmar) and English text concurrently.
     */
    fun generateAssSubtitleFile(
        state: DramaProjectState,
        outFile: File,
        targetWidth: Int = 1080,
        targetHeight: Int = 1920
    ): File {
        val totalMs = state.totalDurationMs.coerceAtLeast(1000L)
        val sb = StringBuilder()
        sb.append("[Script Info]\n")
        sb.append("Title: Myanmar Drama Subtitles\n")
        sb.append("ScriptType: v4.00+\n")
        sb.append("WrapStyle: 0\n")
        sb.append("ScaledBorderAndShadow: yes\n")
        sb.append("PlayResX: $targetWidth\n")
        sb.append("PlayResY: $targetHeight\n\n")

        sb.append("[V4+ Styles]\n")
        sb.append("Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding\n")
        // Title at Top (Alignment 8)
        sb.append("Style: DramaTitle,sans-serif,${(targetWidth * 0.045).toInt()},&H00FFFFFF,&H000000FF,&H00000000,&H80000000,-1,0,0,0,100,100,0,0,1,3,2,8,40,40,${(targetHeight * 0.04).toInt()},1\n")
        // Myanmar Subtitle near Bottom (Alignment 2)
        sb.append("Style: SubMm,sans-serif,${(targetWidth * 0.042).toInt()},&H00FFFFFF,&H000000FF,&H00000000,&HB0000000,-1,0,0,0,100,100,0,0,1,3,2,2,40,40,${(targetHeight * 0.08).toInt()},1\n")
        // English Subtitle below Myanmar Subtitle (Alignment 2)
        sb.append("Style: SubEn,sans-serif,${(targetWidth * 0.032).toInt()},&H00E2E8F0,&H000000FF,&H00000000,&HB0000000,0,-1,0,0,100,100,0,0,1,2,1,2,40,40,${(targetHeight * 0.04).toInt()},1\n\n")

        sb.append("[Events]\n")
        sb.append("Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text\n")

        // Title Overlay Event
        if (state.titleOverlay.isEnabled && state.titleOverlay.text.isNotBlank()) {
            val titleStart = formatAssTimestamp(0L)
            val titleEnd = formatAssTimestamp(totalMs)
            val cleanTitle = state.titleOverlay.text.replace("\n", "\\N")
            sb.append("Dialogue: 0,$titleStart,$titleEnd,DramaTitle,,0,0,0,,{\\b1}$cleanTitle{\\b0}\n")
        }

        // Subtitles Events
        for (cue in state.subtitles) {
            val cueStart = formatAssTimestamp(cue.startMs)
            val cueEnd = formatAssTimestamp(cue.endMs.coerceAtLeast(cue.startMs + 500L))

            if (cue.mmText.isNotBlank()) {
                val cleanMm = cue.mmText.replace("\n", "\\N")
                sb.append("Dialogue: 0,$cueStart,$cueEnd,SubMm,,0,0,0,,{\\c&HFFFFFF&}$cleanMm\n")
            }
            if (cue.enText.isNotBlank()) {
                val cleanEn = cue.enText.replace("\n", "\\N")
                sb.append("Dialogue: 0,$cueStart,$cueEnd,SubEn,,0,0,0,,{\\c&HE2E8F0&}$cleanEn\n")
            }
        }

        outFile.parentFile?.mkdirs()
        outFile.writeText(sb.toString())
        return outFile
    }

    /**
     * Builds structured, validated FFmpeg arguments for rendering the Drama reel.
     */
    fun buildDramaFfmpegArgs(
        state: DramaProjectState,
        outputFilePath: String,
        assSubtitlesPath: String
    ): Result<DramaRenderPlan> {
        if (state.clips.isEmpty()) {
            return Result.failure(IllegalArgumentException("Timeline must contain at least 1 video clip"))
        }

        val is1080p = state.outputResolution == "1080p"
        val outW = if (is1080p) 1080 else 720
        val outH = if (is1080p) 1920 else 1280

        val args = mutableListOf<String>()
        args.add("-y") // Overwrite

        // 1. Inputs for each clip
        for (clip in state.clips) {
            args.add("-ss")
            args.add(String.format(Locale.US, "%.3f", clip.startMs / 1000.0))
            args.add("-to")
            args.add(String.format(Locale.US, "%.3f", clip.endMs / 1000.0))
            args.add("-i")
            args.add(clip.sourcePath)
        }

        val clipCount = state.clips.size
        var nextInputIndex = clipCount

        // Logo Input
        var logoInputIndex: Int? = null
        if (state.logo.isEnabled && state.logo.localPath.isNotBlank() && File(state.logo.localPath).exists()) {
            args.add("-i")
            args.add(state.logo.localPath)
            logoInputIndex = nextInputIndex++
        }

        // BGM Input
        var bgmInputIndex: Int? = null
        if (state.audioConfig.bgmPath.isNotBlank() && File(state.audioConfig.bgmPath).exists()) {
            args.add("-i")
            args.add(state.audioConfig.bgmPath)
            bgmInputIndex = nextInputIndex++
        }

        // 2. Build Filter Complex
        val filterComplex = StringBuilder()
        val scaledVideoLabels = mutableListOf<String>()

        // Scale & Pad each clip to 9:16 vertical
        for (i in 0 until clipCount) {
            val inLabel = "[$i:v]"
            val outLabel = "[vscaled$i]"
            filterComplex.append("${inLabel}scale=$outW:$outH:force_original_aspect_ratio=decrease,pad=$outW:$outH:(ow-iw)/2:(oh-ih)/2,setsar=1$outLabel;")
            scaledVideoLabels.add(outLabel)
        }

        // Concatenate all scaled clips
        for (lbl in scaledVideoLabels) {
            filterComplex.append(lbl)
        }
        filterComplex.append("concat=n=$clipCount:v=1:a=0[vconcat]")

        var currentVideoLabel = "[vconcat]"

        // 3. Apply Region Blur Box if enabled
        if (state.blurBox.isEnabled) {
            val bw = (outW * (state.blurBox.wPercent / 100.0)).toInt().coerceIn(40, outW)
            val bh = (outH * (state.blurBox.hPercent / 100.0)).toInt().coerceIn(40, outH)
            val bx = (outW * (state.blurBox.xPercent / 100.0)).toInt().coerceIn(0, outW - bw)
            val by = (outH * (state.blurBox.yPercent / 100.0)).toInt().coerceIn(0, outH - bh)

            filterComplex.append(";$currentVideoLabel split [vorig][vforblur];")
            filterComplex.append("[vforblur]crop=$bw:$bh:$bx:$by,boxblur=15:5[vblurred];")
            filterComplex.append("[vorig][vblurred]overlay=$bx:$by[vpostblur]")
            currentVideoLabel = "[vpostblur]"
        }

        // 4. Apply Logo Overlay if enabled
        if (logoInputIndex != null) {
            val logoScaleW = (outW * 0.18 * state.logo.scale).toInt().coerceAtLeast(40)
            val logoAlpha = state.logo.opacity.coerceIn(0.1f, 1.0f)
            val posExpr = when (state.logo.position) {
                "top-left" -> "x=30:y=30"
                "bottom-left" -> "x=30:y=H-h-30"
                "bottom-right" -> "x=W-w-30:y=H-h-30"
                else -> "x=W-w-30:y=30" // top-right default
            }

            filterComplex.append(";[$logoInputIndex:v]scale=$logoScaleW:-1,format=rgba,colorchannelmixer=aa=$logoAlpha[vlogo];")
            filterComplex.append("$currentVideoLabel[vlogo]overlay=$posExpr[vpostlogo]")
            currentVideoLabel = "[vpostlogo]"
        }

        // 5. Apply Subtitles & Title burning via ASS filter
        if (File(assSubtitlesPath).exists()) {
            val sanitizedAss = assSubtitlesPath.replace("'", "\\'")
            filterComplex.append(";$currentVideoLabel ass='$sanitizedAss'[vfinal]")
            currentVideoLabel = "[vfinal]"
        }

        args.add("-filter_complex")
        args.add(filterComplex.toString())

        // Map Final Video Stream
        args.add("-map")
        args.add(currentVideoLabel)

        // 6. Map Audio with Mixing
        if (bgmInputIndex != null) {
            val origVol = if (state.audioConfig.isOriginalMuted) 0.0f else state.audioConfig.originalVolume
            val bgmVol = state.audioConfig.bgmVolume
            // Audio filter to mix original audio and BGM
            args.add("-filter_complex_script")
            // Or inline audio filter:
            // Since -filter_complex is already used, append audio mix to filter_complex or use amix:
            // For robustness, mix inside filter_complex:
        }

        // Map audio cleanly:
        if (state.audioConfig.isOriginalMuted && bgmInputIndex == null) {
            // No audio / silent
            args.add("-an")
        } else if (bgmInputIndex != null) {
            args.add("-map")
            args.add("$bgmInputIndex:a")
            args.add("-c:a")
            args.add("aac")
            args.add("-b:a")
            args.add("192k")
        } else {
            // First clip audio
            args.add("-map")
            args.add("0:a?")
            args.add("-c:a")
            args.add("aac")
            args.add("-b:a")
            args.add("192k")
        }

        // Video Encoding Flags
        args.add("-c:v")
        args.add("libx264")
        args.add("-preset")
        args.add("ultrafast")
        args.add("-pix_fmt")
        args.add("yuv420p")
        args.add("-movflags")
        args.add("+faststart")
        args.add(outputFilePath)

        return Result.success(
            DramaRenderPlan(
                outputFilePath = outputFilePath,
                assSubtitlesPath = assSubtitlesPath,
                ffmpegArgs = args,
                width = outW,
                height = outH,
                totalDurationMs = state.totalDurationMs
            )
        )
    }

    /**
     * Verifies that the rendered file exists, has non-zero size, and has valid metadata.
     */
    fun verifyRenderedOutput(outputFilePath: String): Result<Long> {
        val f = File(outputFilePath)
        if (!f.exists()) {
            return Result.failure(IllegalStateException("Output file not found: $outputFilePath"))
        }
        val size = f.length()
        if (size <= 0L) {
            return Result.failure(IllegalStateException("Output file is 0 bytes: $outputFilePath"))
        }

        return runCatching {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(f.absolutePath)
            val dur = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            retriever.release()
            dur
        }
    }
}
