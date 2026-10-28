package com.zipbug.base.zippy

import java.io.File
import kotlin.math.abs
import kotlin.system.exitProcess

/**
 * REAL ffmpeg render smoke test for [ZippyCutPlan] — NOT app code.
 *
 * Builds plan states for synthetic media, takes the plan builder's actual
 * ffmpeg argument list, executes the system ffmpeg, and verifies each
 * output with ffprobe (dimensions, audio-stream presence, duration).
 * This is the executable proof for the portrait-canvas and no-audio-clip
 * fixes. Run via scripts/render-smoke/run-render-smoke.sh.
 *
 * Args: <workDir> [fontFile]
 */
object RenderSmokeMain {

    private var failures = 0

    private fun run(cmd: List<String>, dir: File): Pair<Int, String> {
        val proc = ProcessBuilder(cmd).directory(dir).redirectErrorStream(true).start()
        val out = proc.inputStream.bufferedReader().readText()
        return proc.waitFor() to out
    }

    private fun probe(file: File, dir: File): String {
        val (_, out) = run(
            listOf(
                "ffprobe", "-v", "error",
                "-show_entries", "stream=codec_type,width,height",
                "-show_entries", "format=duration",
                "-of", "default=noprint_wrappers=1",
                file.absolutePath
            ), dir
        )
        return out
    }

    private fun check(case: String, ok: Boolean, evidence: String) {
        println((if (ok) "PASS" else "FAIL") + " | " + case + " | " + evidence.replace("\n", " ").trim())
        if (!ok) failures++
    }

    private fun clip(id: String, path: String, hasAudio: Boolean) = ZippyCutPlan.Clip(
        id = id,
        sourceUri = "file://$path",
        sourceName = File(path).name,
        stagedPath = path,
        durationMs = 3_000L,
        width = 640,
        height = 360,
        trimStartMs = 0L,
        trimEndMs = 3_000L,
        hasAudio = hasAudio
    )

    private fun render(
        name: String,
        state: ZippyCutPlan.EditState,
        dir: File,
        expectW: Int,
        expectH: Int,
        expectAudio: Boolean,
        expectDurationSec: Double,
        fontFile: String? = null
    ) {
        val outFile = File(dir, "$name.mp4")
        val argsResult = ZippyCutPlan.buildFfmpegArgs(state, outFile.absolutePath, fontFile = fontFile)
        if (argsResult.isFailure) {
            check(name, false, "plan builder failed: ${argsResult.exceptionOrNull()?.message}")
            return
        }
        val cmd = listOf("ffmpeg") + argsResult.getOrThrow()
        val (exit, log) = run(cmd, dir)
        if (exit != 0) {
            check(name, false, "ffmpeg exit=$exit :: " + log.lines().takeLast(4).joinToString(" / "))
            return
        }
        val info = probe(outFile, dir)
        val hasAudio = info.contains("codec_type=audio")
        val dimsOk = info.contains("width=$expectW") && info.contains("height=$expectH")
        val duration = Regex("duration=([0-9.]+)").find(info)?.groupValues?.get(1)?.toDoubleOrNull() ?: -1.0
        val durOk = abs(duration - expectDurationSec) <= 0.5
        val ok = dimsOk && hasAudio == expectAudio && durOk
        check(
            name, ok,
            "dims=${expectW}x$expectH:$dimsOk audio=$hasAudio(expected $expectAudio) " +
                "duration=$duration(expected ~$expectDurationSec)"
        )
    }

    @JvmStatic
    fun main(argv: Array<String>) {
        val dir = File(argv[0]).apply { mkdirs() }
        val font = argv.getOrNull(1)
        val clipA = File(dir, "clipA.mp4").absolutePath // video + audio
        val clipB = File(dir, "clipB.mp4").absolutePath // video only (silent)
        val clipP = File(dir, "clipP.mp4").absolutePath // portrait source + audio
        val music = File(dir, "music.mp3").absolutePath

        // --- generate synthetic media with lavfi ---
        fun gen(cmd: List<String>, label: String) {
            val (exit, log) = run(cmd, dir)
            if (exit != 0) {
                println("FAIL | media-gen $label | exit=$exit :: " + log.lines().takeLast(3).joinToString(" / "))
                exitProcess(2)
            }
        }
        gen(
            listOf(
                "ffmpeg", "-y", "-f", "lavfi", "-i", "testsrc2=duration=3:size=640x360:rate=30",
                "-f", "lavfi", "-i", "sine=frequency=440:duration=3",
                "-c:v", "libx264", "-pix_fmt", "yuv420p", "-c:a", "aac", "-shortest", clipA
            ), "clipA"
        )
        gen(
            listOf(
                "ffmpeg", "-y", "-f", "lavfi", "-i", "testsrc2=duration=3:size=640x360:rate=30",
                "-c:v", "libx264", "-pix_fmt", "yuv420p", clipB
            ), "clipB"
        )
        gen(
            listOf(
                "ffmpeg", "-y", "-f", "lavfi", "-i", "testsrc2=duration=3:size=360x640:rate=30",
                "-f", "lavfi", "-i", "sine=frequency=330:duration=3",
                "-c:v", "libx264", "-pix_fmt", "yuv420p", "-c:a", "aac", "-shortest", clipP
            ), "clipP"
        )
        gen(
            listOf(
                "ffmpeg", "-y", "-f", "lavfi", "-i", "sine=frequency=220:duration=8",
                "-c:a", "libmp3lame", music
            ), "music"
        )
        println("media generated: A=video+audio, B=video-only, P=portrait+audio, music=8s mp3")

        val a = clip("a", clipA, hasAudio = true)
        val b = clip("b", clipB, hasAudio = false)
        val p = clip("p", clipP, hasAudio = true)
        val land = ZippyCutPlan.OutputAspect.LANDSCAPE_16_9
        val port = ZippyCutPlan.OutputAspect.PORTRAIT_9_16

        render(
            "a_audio_only_landscape",
            ZippyCutPlan.EditState(clips = listOf(a), aspect = land), dir, 1280, 720, true, 3.0
        )
        render(
            "b_silent_only_portrait",
            ZippyCutPlan.EditState(clips = listOf(b), aspect = port), dir, 720, 1280, true, 3.0
        )
        render(
            "c1_mixed_portrait",
            ZippyCutPlan.EditState(clips = listOf(a, b), aspect = port), dir, 720, 1280, true, 6.0
        )
        render(
            "c2_mixed_landscape",
            ZippyCutPlan.EditState(clips = listOf(a, b), aspect = land), dir, 1280, 720, true, 6.0
        )
        render(
            "d_mixed_replace_audio",
            ZippyCutPlan.EditState(
                clips = listOf(a, b), aspect = land,
                audioMode = ZippyCutPlan.AudioMode.REPLACE, replaceAudioPath = music
            ), dir, 1280, 720, true, 6.0
        )
        render(
            "e_mixed_mute_portrait",
            ZippyCutPlan.EditState(
                clips = listOf(a, b), aspect = port,
                audioMode = ZippyCutPlan.AudioMode.MUTE
            ), dir, 720, 1280, false, 6.0
        )
        render(
            "g_portrait_source_to_landscape",
            ZippyCutPlan.EditState(clips = listOf(p), aspect = land), dir, 1280, 720, true, 3.0
        )
        if (font != null && File(font).exists()) {
            render(
                "f_mixed_title_text_portrait",
                ZippyCutPlan.EditState(
                    clips = listOf(a, b), aspect = port,
                    title = "Smoke Title", textOverlay = "Bottom text"
                ), dir, 720, 1280, true, 6.0, fontFile = font
            )
        } else {
            println("BLOCKED | f_mixed_title_text_portrait | no font file supplied/found")
            failures++
        }

        println(if (failures == 0) "RENDER SMOKE: ALL PASS" else "RENDER SMOKE: $failures FAILURE(S)")
        exitProcess(if (failures == 0) 0 else 1)
    }
}
