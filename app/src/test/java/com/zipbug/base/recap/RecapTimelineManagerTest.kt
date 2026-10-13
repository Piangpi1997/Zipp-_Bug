package com.zipbug.base.recap

import com.zipbug.base.data.recap.RecapClipEntity
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class RecapTimelineManagerTest {

    private fun createClip(
        order: Int,
        startMs: Long,
        endMs: Long,
        durationMs: Long = 60_000L,
        effect: String = RecapClipEntity.EFFECT_NONE
    ): RecapClipEntity = RecapClipEntity(
        id = UUID.randomUUID().toString(),
        projectId = "test-proj",
        orderIndex = order,
        sourcePath = "/data/data/com.termux/files/home/recap/test.mp4",
        startMs = startMs,
        endMs = endMs,
        durationMs = durationMs,
        effectType = effect
    )

    @Test
    fun testValidClipCountAndTrim() {
        val clips = listOf(
            createClip(0, 0L, 5000L),
            createClip(1, 5000L, 10000L),
            createClip(2, 10000L, 15000L),
            createClip(3, 15000L, 20000L)
        )
        val result = RecapTimelineManager.validateTimeline(clips)
        assertTrue("Timeline should be valid", result.isSuccess)
    }

    @Test
    fun testZeroClipsRejected() {
        val result = RecapTimelineManager.validateTimeline(emptyList())
        assertTrue("Empty clips should be rejected", result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("1 to 18 clips"))
    }

    @Test
    fun testExceedingMax18ClipsRejected() {
        val clips = (0..18).map { i ->
            createClip(i, (i * 1000).toLong(), ((i + 1) * 1000).toLong())
        }
        assertEquals(19, clips.size)
        val result = RecapTimelineManager.validateTimeline(clips)
        assertTrue("19 clips should exceed max bound", result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("1 to 18 clips"))
    }

    @Test
    fun testStartGreaterThanOrEqualToEndRejected() {
        val equalTrim = listOf(createClip(0, 5000L, 5000L))
        val resEqual = RecapTimelineManager.validateTimeline(equalTrim)
        assertTrue("startMs == endMs must fail", resEqual.isFailure)
        assertTrue(resEqual.exceptionOrNull()!!.message!!.contains("strictly less than endMs"))

        val invertedTrim = listOf(createClip(0, 6000L, 4000L))
        val resInverted = RecapTimelineManager.validateTimeline(invertedTrim)
        assertTrue("startMs > endMs must fail", resInverted.isFailure)
        assertTrue(resInverted.exceptionOrNull()!!.message!!.contains("strictly less than endMs"))
    }

    @Test
    fun testNegativeStartMsRejected() {
        val negativeStart = listOf(createClip(0, -100L, 5000L))
        val res = RecapTimelineManager.validateTimeline(negativeStart)
        assertTrue("negative startMs must fail", res.isFailure)
        assertTrue(res.exceptionOrNull()!!.message!!.contains("cannot be negative"))
    }

    @Test
    fun testEndMsExceedingClipDurationRejected() {
        val overDuration = listOf(createClip(0, 0L, 70_000L, durationMs = 60_000L))
        val res = RecapTimelineManager.validateTimeline(overDuration)
        assertTrue("endMs > durationMs must fail", res.isFailure)
        assertTrue(res.exceptionOrNull()!!.message!!.contains("exceeds clip duration"))
    }

    @Test
    fun testReorderClips() {
        val clip0 = createClip(0, 0L, 1000L)
        val clip1 = createClip(1, 1000L, 2000L)
        val clip2 = createClip(2, 2000L, 3000L)
        val list = listOf(clip0, clip1, clip2)

        val reordered = RecapTimelineManager.reorderClips(list, fromIndex = 0, toIndex = 2)
        assertEquals(clip1.id, reordered[0].id)
        assertEquals(clip2.id, reordered[1].id)
        assertEquals(clip0.id, reordered[2].id)

        assertEquals(0, reordered[0].orderIndex)
        assertEquals(1, reordered[1].orderIndex)
        assertEquals(2, reordered[2].orderIndex)
    }

    @Test
    fun testCreateInitialClips() {
        val initial = RecapTimelineManager.createInitialClips(
            projectId = "p1",
            sourcePath = "/path/video.mp4",
            totalDurationMs = 40_000L,
            clipCount = 4
        )
        assertEquals(4, initial.size)
        assertEquals(0L, initial[0].startMs)
        assertEquals(10_000L, initial[0].endMs)
        assertEquals(10_000L, initial[1].startMs)
        assertEquals(20_000L, initial[1].endMs)
        assertEquals(20_000L, initial[2].startMs)
        assertEquals(30_000L, initial[2].endMs)
        assertEquals(30_000L, initial[3].startMs)
        assertEquals(40_000L, initial[3].endMs)
    }
}
