package com.zipbug.base.zippy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ZippyCutPlanTest {

    private fun clip(
        id: String = "c1",
        name: String = "a.mp4",
        durationMs: Long = 60_000L,
        trimStartMs: Long = 5_000L,
        trimEndMs: Long = 35_000L
    ) = ZippyCutPlan.Clip(
        id = id,
        sourceUri = "content://media/$id",
        sourceName = name,
        stagedPath = "/storage/emulated/0/Download/Zip_Bug/Recap/$name",
        durationMs = durationMs,
        width = 1920,
        height = 1080,
        trimStartMs = trimStartMs,
        trimEndMs = trimEndMs
    )

    private fun state(
        clips: List<ZippyCutPlan.Clip> = listOf(clip()),
        audioMode: ZippyCutPlan.AudioMode = ZippyCutPlan.AudioMode.KEEP,
        replaceAudioPath: String = "",
        title: String = "",
        textOverlay: String = "",
        logoPath: String = "",
        subtitles: List<ZippyCutPlan.SubtitleLine> = emptyList()
    ) = ZippyCutPlan.EditState(
        clips = clips,
        title = title,
        textOverlay = textOverlay,
        logoPath = logoPath,
        audioMode = audioMode,
        replaceAudioPath = replaceAudioPath,
        subtitles = subtitles
    )

    @Test
    fun validationAcceptsValidState() {
        assertNull(ZippyCutPlan.validate(state()))
    }

    @Test
    fun validationRejectsEmptyClipsAndBadTrims() {
        assertTrue(ZippyCutPlan.validate(state(clips = emptyList()))!!.contains("at least one"))
        assertTrue(
            ZippyCutPlan.validate(state(clips = listOf(clip(trimStartMs = 20_000, trimEndMs = 10_000))))!!
                .contains("after trim start")
        )
        assertTrue(
            ZippyCutPlan.validate(state(clips = listOf(clip(trimEndMs = 61_000))))!!
                .contains("exceeds clip duration")
        )
        assertTrue(
            ZippyCutPlan.validate(state(clips = listOf(clip().copy(stagedPath = ""))))!!
                .contains("no staged copy")
        )
    }

    @Test
    fun validationRejectsReplaceWithoutAudioAndSubtitleOverflow() {
        assertTrue(
            ZippyCutPlan.validate(state(audioMode = ZippyCutPlan.AudioMode.REPLACE))!!
                .contains("REPLACE")
        )
        val badSub = ZippyCutPlan.SubtitleLine(0, 31_000, "too long")
        assertTrue(
            ZippyCutPlan.validate(state(subtitles = listOf(badSub)))!!
                .contains("edited video length")
        )
    }

    @Test
    fun segmentsFollowClipOrderAndTrims() {
        val s = state(
            clips = listOf(
                clip(id = "c1", name = "a.mp4"),
                clip(id = "c2", name = "b.mp4", trimStartMs = 0, trimEndMs = 10_000)
            )
        )
        val segments = ZippyCutPlan.segments(s)
        assertEquals(2, segments.size)
        assertTrue(segments[0].inputPath.endsWith("a.mp4"))
        assertEquals(30_000L, segments[0].durationMs)
        assertTrue(segments[1].inputPath.endsWith("b.mp4"))
        assertEquals(40_000L, ZippyCutPlan.totalDurationMs(s))
    }

    @Test
    fun moveClipReordersAndTracksSelection() {
        val s = state(
            clips = listOf(clip(id = "c1", name = "a.mp4"), clip(id = "c2", name = "b.mp4"))
        ).copy(selectedClip = 0)
        val moved = ZippyCutPlan.moveClip(s, 0, 1)
        assertEquals("b.mp4", moved.clips[0].sourceName)
        assertEquals("a.mp4", moved.clips[1].sourceName)
        assertEquals(1, moved.selectedClip)
        // Out-of-range move is a no-op.
        assertEquals(s, ZippyCutPlan.moveClip(s, 0, 5))
    }

    @Test
    fun splitClipProducesTwoAdjacentClips() {
        val s = state()
        val result = ZippyCutPlan.splitClip(s, 0, 20_000)
        assertTrue(result.isSuccess)
        val split = result.getOrThrow()
        assertEquals(2, split.clips.size)
        assertEquals(15_000L, split.clips[0].trimmedDurationMs)
        assertEquals(15_000L, split.clips[1].trimmedDurationMs)
        assertEquals(split.clips[0].trimEndMs, split.clips[1].trimStartMs)
        // Split outside the trim range fails.
        assertTrue(ZippyCutPlan.splitClip(s, 0, 35_000).isFailure)
        assertTrue(ZippyCutPlan.splitClip(s, 3, 20_000).isFailure)
    }

    @Test
    fun ffmpegArgsMultiClipKeepAudio() {
        val s = state(
            clips = listOf(
                clip(id = "c1", name = "a.mp4"),
                clip(id = "c2", name = "b.mp4", trimStartMs = 0, trimEndMs = 10_000)
            )
        )
        val args = ZippyCutPlan.buildFfmpegArgs(s, "/storage/out.mp4").getOrThrow()
        assertEquals(2, args.count { it == "-i" })
        assertEquals(2, args.count { it == "-ss" })
        assertTrue(args.contains("libx264"))
        assertTrue(args.contains("aac"))
        val filter = args[args.indexOf("-filter_complex") + 1]
        assertTrue(filter.contains("concat=n=2:v=1:a=1"))
        for (arg in args) {
            assertFalse(arg.contains("&&"))
            assertFalse(arg.contains("$("))
        }
    }

    @Test
    fun ffmpegArgsMuteDropsAudio() {
        val args = ZippyCutPlan.buildFfmpegArgs(
            state(audioMode = ZippyCutPlan.AudioMode.MUTE), "/storage/out.mp4"
        ).getOrThrow()
        assertFalse(args.contains("aac"))
        val filter = args[args.indexOf("-filter_complex") + 1]
        assertTrue(filter.contains("concat=n=1:v=1:a=0"))
    }

    @Test
    fun ffmpegArgsReplaceAudioUsesExtraInputTrimmedToTotal() {
        val s = state(
            audioMode = ZippyCutPlan.AudioMode.REPLACE,
            replaceAudioPath = "/storage/music.mp3"
        )
        val args = ZippyCutPlan.buildFfmpegArgs(s, "/storage/out.mp4").getOrThrow()
        // Clip input + replacement audio input.
        assertEquals(2, args.count { it == "-i" })
        assertTrue(args.contains("/storage/music.mp3"))
        assertTrue(args.contains("aac"))
        val filter = args[args.indexOf("-filter_complex") + 1]
        assertTrue(filter.contains("concat=n=1:v=1:a=0"))
        assertTrue(filter.contains("[1:a]atrim=0:30.000"))
    }

    @Test
    fun ffmpegArgsLogoOverlayUsesImageInput() {
        val s = state(logoPath = "/storage/logo.png")
        val args = ZippyCutPlan.buildFfmpegArgs(s, "/storage/out.mp4").getOrThrow()
        assertEquals(2, args.count { it == "-i" })
        assertTrue(args.contains("/storage/logo.png"))
        val filter = args[args.indexOf("-filter_complex") + 1]
        assertTrue(filter.contains("[1:v]overlay=16:16"))
    }

    @Test
    fun titleMetadataFallbackAndDrawtextWithFont() {
        val noFont = ZippyCutPlan.buildFfmpegArgs(
            state(title = "My Title"), "/storage/out.mp4", fontFile = null
        ).getOrThrow()
        assertTrue(noFont.contains("title=My Title"))
        assertFalse(noFont[noFont.indexOf("-filter_complex") + 1].contains("drawtext"))

        val withFont = ZippyCutPlan.buildFfmpegArgs(
            state(title = "My Title"), "/storage/out.mp4",
            fontFile = "/system/fonts/Roboto-Regular.ttf"
        ).getOrThrow()
        assertTrue(withFont[withFont.indexOf("-filter_complex") + 1].contains("drawtext"))
    }

    @Test
    fun textOverlayNeedsFontAndRendersAtBottom() {
        val noFont = ZippyCutPlan.buildFfmpegArgs(
            state(textOverlay = "Hello"), "/storage/out.mp4", fontFile = null
        )
        assertTrue(noFont.isFailure)
        val withFont = ZippyCutPlan.buildFfmpegArgs(
            state(textOverlay = "Hello"), "/storage/out.mp4",
            fontFile = "/system/fonts/Roboto-Regular.ttf"
        ).getOrThrow()
        val filter = withFont[withFont.indexOf("-filter_complex") + 1]
        assertTrue(filter.contains("Hello"))
        assertTrue(filter.contains("y=h-64"))
    }

    @Test
    fun subtitlesBurnUsesGivenSrtPath() {
        val args = ZippyCutPlan.buildFfmpegArgs(
            state(), "/storage/out.mp4", subtitlesPath = "/storage/subs.srt"
        ).getOrThrow()
        assertTrue(args[args.indexOf("-filter_complex") + 1].contains("subtitles="))
    }

    @Test
    fun srtFormattingKeepsMyanmarTextAndOutputTimeline() {
        val srt = ZippyCutPlan.subtitlesToSrt(
            listOf(
                ZippyCutPlan.SubtitleLine(1_500, 4_000, "မြန်မာ စာတန်း"),
                ZippyCutPlan.SubtitleLine(0, 1_500, "Intro")
            )
        )
        assertTrue(srt.contains("00:00:00,000 --> 00:00:01,500"))
        assertTrue(srt.contains("00:00:01,500 --> 00:00:04,000"))
        assertTrue(srt.contains("မြန်မာ စာတန်း"))
        assertTrue(srt.indexOf("Intro") < srt.indexOf("မြန်မာ စာတန်း"))
    }

    @Test
    fun draftJsonRoundTripV2() {
        val original = state(
            clips = listOf(clip(id = "c1", name = "a.mp4"), clip(id = "c2", name = "b.mp4")),
            audioMode = ZippyCutPlan.AudioMode.REPLACE,
            replaceAudioPath = "/storage/music.mp3",
            title = "ခေါင်းစဉ်",
            textOverlay = "Overlay",
            logoPath = "/storage/logo.png",
            subtitles = listOf(ZippyCutPlan.SubtitleLine(0, 2_000, "စာတန်း"))
        ).copy(selectedClip = 1, replaceAudioName = "music.mp3")
        val restored = ZippyCutPlan.fromJson(ZippyCutPlan.toJson(original))
        assertEquals(original, restored)
    }

    @Test
    fun draftJsonMigratesV1SingleSource() {
        val v1 = """
            {"sourceUri":"content://x","sourceName":"old.mp4","stagedPath":"/s/old.mp4",
             "durationMs":60000,"width":1280,"height":720,"trimStartMs":1000,"trimEndMs":9000,
             "title":"Old","keepAudio":false,"splitMs":5000,
             "subtitles":[{"startMs":0,"endMs":2000,"text":"hi"}]}
        """.trimIndent()
        val restored = ZippyCutPlan.fromJson(v1)
        // The v1 split point (5000, inside trim 1000-9000) is preserved as
        // two migrated clips.
        assertEquals(2, restored.clips.size)
        assertEquals("old.mp4", restored.clips[0].sourceName)
        assertEquals(5_000L, restored.clips[0].trimEndMs)
        assertEquals(5_000L, restored.clips[1].trimStartMs)
        assertEquals(ZippyCutPlan.AudioMode.MUTE, restored.audioMode)
        assertEquals("Old", restored.title)
        assertEquals(1, restored.subtitles.size)
    }
}
