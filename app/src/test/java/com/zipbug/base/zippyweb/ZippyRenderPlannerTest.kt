package com.zipbug.base.zippyweb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ZippyRenderPlannerTest {

    private fun plan(links: String, clips: String, extra: String = ""): String =
        """{"version":1,"master":"audio","audioDuration":60.0,"clipSourceSeconds":2.5,
           "lockedSpeed":0.5,"slotSeconds":5.0,"transitionSeconds":2.0,
           "clips":[$clips],"links":[$links]$extra}"""

    @Test
    fun singleLinkBuildsTrimAndTotal() {
        val job = ZippyRenderPlanner.build(
            plan("""{"audioTime":0.0,"clipId":"c1","order":0}""", """{"id":"c1","start":10.0,"end":20.0}"""),
            "/v.mp4", "/a.mp3", "/out.mp4", 720, 1280
        )
        assertEquals("-y", job.args[0])
        assertTrue(job.args.contains("libx264"))
        val filter = job.args[job.args.indexOf("-filter_complex") + 1]
        assertTrue(filter.contains("trim=start=10.000:duration=2.500"))
        assertTrue(filter.contains("concat=n=1:v=1:a=0"))
        assertEquals(5.0, job.totalSeconds, 0.001)
    }

    @Test
    fun linksAreSortedByOrderAndTotalsAddUp() {
        val job = ZippyRenderPlanner.build(
            plan(
                """{"audioTime":5.0,"clipId":"c2","order":1},{"audioTime":0.0,"clipId":"c1","order":0}""",
                """{"id":"c1","start":0.0,"end":4.0},{"id":"c2","start":30.0,"end":40.0}"""
            ),
            "/v.mp4", "/a.mp3", "/out.mp4", 1280, 720
        )
        val filter = job.args[job.args.indexOf("-filter_complex") + 1]
        assertTrue(filter.contains("concat=n=2:v=1:a=0"))
        assertTrue(filter.indexOf("trim=start=0.000") < filter.indexOf("trim=start=30.000"))
        assertEquals(10.0, job.totalSeconds, 0.001)
    }

    @Test
    fun slotFxSpeedAppliesAndUnknownFxAreReported() {
        val job = ZippyRenderPlanner.build(
            plan(
                """{"audioTime":0.0,"clipId":"c1","order":0}""",
                """{"id":"c1","start":0.0,"end":9.0}""",
                ""","slotFx":{"0":{"speed":1.0,"cap":{"on":true},"logo":{"on":true}}}"""
            ),
            "/v.mp4", "/a.mp3", "/out.mp4", 720, 1280
        )
        val filter = job.args[job.args.indexOf("-filter_complex") + 1]
        assertTrue(filter.contains("setpts=PTS/1.000"))
        assertTrue(job.appliedFx.contains("speed"))
        assertEquals(listOf("cap", "logo"), job.skippedFx)
    }

    @Test
    fun fadeTransitionIsApplied() {
        val job = ZippyRenderPlanner.build(
            plan(
                """{"audioTime":0.0,"clipId":"c1","order":0},{"audioTime":5.0,"clipId":"c1","order":1}""",
                """{"id":"c1","start":0.0,"end":30.0}""",
                ""","transitions":[{"from":0,"to":1,"style":"fade","duration":2}]"""
            ),
            "/v.mp4", "/a.mp3", "/out.mp4", 720, 1280
        )
        assertTrue(job.appliedFx.contains("fade"))
        val filter = job.args[job.args.indexOf("-filter_complex") + 1]
        assertTrue(filter.contains("fade=t=in"))
    }

    @Test
    fun emptyLinksThrow() {
        try {
            ZippyRenderPlanner.build(plan("", """{"id":"c1","start":0.0,"end":1.0}"""), "/v", "/a", "/o", 720, 1280)
            fail("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("no audio-to-clip links"))
        }
    }

    @Test
    fun unknownClipThrows() {
        try {
            ZippyRenderPlanner.build(
                plan("""{"audioTime":0.0,"clipId":"nope","order":0}""", """{"id":"c1","start":0.0,"end":1.0}"""),
                "/v", "/a", "/o", 720, 1280
            )
            fail("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("unknown clip"))
        }
    }
}
