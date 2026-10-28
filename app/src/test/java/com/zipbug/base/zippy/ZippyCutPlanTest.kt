package com.zipbug.base.zippy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ZippyCutPlanTest {

    private fun state(
        durationMs: Long = 60_000L,
        trimStartMs: Long = 5_000L,
        trimEndMs: Long = 35_000L,
        splitMs: Long? = null,
        keepAudio: Boolean = true,
        title: String = "",
        subtitles: List<ZippyCutPlan.SubtitleLine> = emptyList()
    ) = ZippyCutPlan.EditState(
        sourceUri = "content://media/video/1",
        sourceName = "sample.mp4",
        stagedPath = "/storage/emulated/0/Download/Zip_Bug/Recap/sample.mp4",
        durationMs = durationMs,
        trimStartMs = trimStartMs,
        trimEndMs = trimEndMs,
        splitMs = splitMs,
        title = title,
        keepAudio = keepAudio,
        subtitles = subtitles
    )

    @Test
    fun validationAcceptsValidTrim() {
        assertNull(ZippyCutPlan.validate(state()))
    }

    @Test
    fun validationRejectsInvertedTrimAndOverflow() {
        assertTrue(
            ZippyCutPlan.validate(state(trimStartMs = 20_000, trimEndMs = 10_000))!!
                .contains("after trim start")
        )
        assertTrue(
            ZippyCutPlan.validate(state(trimEndMs = 61_000))!!
                .contains("exceeds video duration")
        )
    }

    @Test
    fun validationRejectsSplitOutsideTrimAndSubtitleOverflow() {
        assertTrue(
            ZippyCutPlan.validate(state(splitMs = 35_000))!!
                .contains("Split point")
        )
        val badSub = ZippyCutPlan.SubtitleLine(0, 31_000, "too long")
        assertTrue(
            ZippyCutPlan.validate(state(subtitles = listOf(badSub)))!!
                .contains("trimmed video length")
        )
    }

    @Test
    fun segmentsWithoutSplitIsSingleTrimRange() {
        val segments = ZippyCutPlan.segments(state())
        assertEquals(1, segments.size)
        assertEquals(5_000L, segments[0].startMs)
        assertEquals(35_000L, segments[0].endMs)
        assertEquals(30_000L, segments[0].durationMs)
    }

    @Test
    fun splitProducesTwoContiguousExportSegments() {
        val segments = ZippyCutPlan.segments(state(splitMs = 20_000))
        assertEquals(2, segments.size)
        assertEquals(segments[0].endMs, segments[1].startMs)
        assertEquals(15_000L, segments[0].durationMs)
        assertEquals(15_000L, segments[1].durationMs)
    }

    @Test
    fun ffmpegArgsAreStructuredTrimSplitAndAudioKept() {
        val result = ZippyCutPlan.buildFfmpegArgs(
            state = state(splitMs = 20_000),
            inputPath = "/storage/src.mp4",
            outputPath = "/storage/out.mp4"
        )
        assertTrue(result.isSuccess)
        val args = result.getOrThrow()
        // Two segments => the same input is opened twice, each with -ss/-to.
        assertEquals(2, args.count { it == "-i" })
        assertEquals(2, args.count { it == "-ss" })
        assertTrue(args.contains("libx264"))
        assertTrue(args.contains("aac"))
        assertTrue(args.contains("/storage/out.mp4"))
        val filter = args[args.indexOf("-filter_complex") + 1]
        assertTrue(filter.contains("concat=n=2:v=1:a=1"))
        for (arg in args) {
            assertFalse(arg.contains("&&"))
            assertFalse(arg.contains("$("))
        }
    }

    @Test
    fun ffmpegArgsMuteDropsAudioMapping() {
        val args = ZippyCutPlan.buildFfmpegArgs(
            state = state(keepAudio = false),
            inputPath = "/storage/src.mp4",
            outputPath = "/storage/out.mp4"
        ).getOrThrow()
        assertFalse(args.contains("aac"))
        val filter = args[args.indexOf("-filter_complex") + 1]
        assertTrue(filter.contains("concat=n=1:v=1:a=0"))
    }

    @Test
    fun titleIsMetadataOnlyWithoutFontAndDrawtextWithFont() {
        val noFont = ZippyCutPlan.buildFfmpegArgs(
            state = state(title = "My Title"),
            inputPath = "/storage/src.mp4",
            outputPath = "/storage/out.mp4",
            fontFile = null
        ).getOrThrow()
        assertTrue(noFont.contains("title=My Title"))
        val noFontFilter = noFont[noFont.indexOf("-filter_complex") + 1]
        assertFalse(noFontFilter.contains("drawtext"))

        val withFont = ZippyCutPlan.buildFfmpegArgs(
            state = state(title = "My Title"),
            inputPath = "/storage/src.mp4",
            outputPath = "/storage/out.mp4",
            fontFile = "/system/fonts/Roboto-Regular.ttf"
        ).getOrThrow()
        val filter = withFont[withFont.indexOf("-filter_complex") + 1]
        assertTrue(filter.contains("drawtext"))
        assertTrue(filter.contains("My Title"))
    }

    @Test
    fun subtitlesBurnUsesGivenSrtPath() {
        val args = ZippyCutPlan.buildFfmpegArgs(
            state = state(),
            inputPath = "/storage/src.mp4",
            outputPath = "/storage/out.mp4",
            subtitlesPath = "/storage/subs.srt"
        ).getOrThrow()
        val filter = args[args.indexOf("-filter_complex") + 1]
        assertTrue(filter.contains("subtitles="))
        assertTrue(filter.contains("subs.srt"))
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
    fun draftJsonRoundTrip() {
        val original = state(
            splitMs = 20_000,
            title = "ခေါင်းစဉ်",
            keepAudio = false,
            subtitles = listOf(ZippyCutPlan.SubtitleLine(0, 2_000, "စာတန်း"))
        ).copy(width = 1280, height = 720)
        val restored = ZippyCutPlan.fromJson(ZippyCutPlan.toJson(original))
        assertEquals(original, restored)
    }

    @Test
    fun draftJsonRoundTripWithoutSplit() {
        val original = state()
        val restored = ZippyCutPlan.fromJson(ZippyCutPlan.toJson(original))
        assertEquals(original, restored)
        assertNull(restored.splitMs)
    }
}
