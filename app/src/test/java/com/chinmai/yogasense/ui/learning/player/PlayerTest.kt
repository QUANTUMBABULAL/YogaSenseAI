package com.chinmai.yogasense.ui.learning.player

import com.chinmai.yogasense.catalog.AsanaCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LessonMediaResolverTest {
    private val tree = AsanaCatalog.byId("TREE")!!

    @Test fun prefersBundledVideo() {
        assertEquals(LessonMedia.Video("asana/tree_pose.mp4"), LessonMediaResolver.resolve(tree) { it == "asana/tree_pose.mp4" })
    }

    @Test fun missingVideoFallsBackToAnimatedIllustration() {
        val m = LessonMediaResolver.resolve(tree) { false }
        assertTrue(m is LessonMedia.AnimatedIllustration)
        assertEquals(tree.demoSequence, (m as LessonMedia.AnimatedIllustration).keyframes)
        assertEquals(tree.figure, m.keyframes.last())
    }

    @Test fun noKeyframesFallsBackToStaticIllustration() {
        val bare = tree.copy(demoLeadIn = emptyList())
        assertEquals(LessonMedia.StaticIllustration(bare.figure), LessonMediaResolver.resolve(bare) { false })
        assertEquals(LessonMedia.StaticIllustration(bare.figure), LessonMediaResolver.fallbackFor(bare))
    }

    @Test fun assetNamesFollowConvention() {
        assertEquals(
            listOf("asana/tadasana.mp4", "asana/tree_pose.mp4", "asana/warrior_ii.mp4", "asana/chair_pose.mp4", "asana/triangle_pose.mp4"),
            AsanaCatalog.all.map { it.demoAsset },
        )
    }

    /** Mirrors the real project assets: flips to Video automatically when MP4s are added. */
    @Test fun bundledAssetsResolveConsistently() {
        AsanaCatalog.all.forEach { a ->
            // A video counts as present under the canonical name or the explicitly mapped provided file.
            val present = com.chinmai.yogasense.catalog.AsanaVideoAssets.candidates(a).any { File("src/main/assets/$it").isFile }
            val m = LessonMediaResolver.resolve(a) { File("src/main/assets/$it").isFile }
            if (present) assertTrue(m is LessonMedia.Video) else assertTrue("${a.id}: $m", m is LessonMedia.AnimatedIllustration)
        }
    }

    @Test fun demoKeyframesStayInUnitBox() {
        AsanaCatalog.all.forEach { a ->
            assertTrue("${a.id} needs lead-in", a.demoSequence.size >= 2)
            a.demoSequence.forEach { f ->
                with(f) {
                    listOf(head, shoulderL, shoulderR, elbowL, elbowR, wristL, wristR, hipL, hipR, kneeL, kneeR, ankleL, ankleR)
                        .forEach { (x, y) -> assertTrue("${a.id} $x,$y", x in 0f..1f && y in 0f..1f) }
                }
            }
        }
    }
}

class FigureTimelineTest {
    private val frames = AsanaCatalog.byId("WARRIOR_II")!!.demoSequence
    private val t = FigureTimeline(frames, holdMs = 100, transitionMs = 200, finalHoldMs = 500)

    @Test fun durationAndEndpoints() {
        assertEquals((frames.size - 1) * 300L + 500L, t.durationMs)
        assertEquals(frames.first(), t.frameAt(0))
        assertEquals(frames.last(), t.frameAt(t.durationMs))
        assertEquals(frames.last(), t.frameAt(t.durationMs + 10_000))
        assertEquals(frames.first(), t.frameAt(-5))
    }

    @Test fun holdsThenInterpolates() {
        assertEquals(frames[0], t.frameAt(99))
        val mid = t.frameAt(200) // halfway through first transition
        val a = frames[0].wristL.first; val b = frames[1].wristL.first
        assertTrue(mid.wristL.first in minOf(a, b)..maxOf(a, b))
        assertEquals(frames[1], t.frameAt(300))
    }
}

class FigurePlaybackTest {
    private fun pb(auto: Boolean = true, loop: Boolean = false) =
        FigurePlayback(FigureTimeline(AsanaCatalog.all.first().demoSequence, 100, 200, 500), auto, loop)

    @Test fun autoPlayAdvancesWithFrames() {
        val p = pb()
        assertTrue(p.state.value.isPlaying)
        p.tick(1000); p.tick(1100); p.tick(1200)
        assertEquals(200L, p.state.value.positionMs)
    }

    @Test fun noAutoPlayStaysAtStart() {
        val p = pb(auto = false)
        p.tick(0); p.tick(100)
        assertFalse(p.state.value.isPlaying)
        assertEquals(0L, p.state.value.positionMs)
    }

    @Test fun pauseHoldsAndResumeContinuesWithoutJump() {
        val p = pb()
        p.tick(0); p.tick(100)
        p.pause(); p.tick(5000)
        assertEquals(100L, p.state.value.positionMs)
        p.play(); p.tick(9000); p.tick(9050)
        assertEquals(150L, p.state.value.positionMs)
    }

    @Test fun endsWithoutLoopThenPlayRestarts() {
        val p = pb(loop = false)
        val d = p.state.value.durationMs
        var now = 0L; p.tick(now)
        while (now < d + 500) { now += 50; p.tick(now) }
        assertEquals(PlaybackStatus.Ended, p.state.value.status)
        assertEquals(d, p.state.value.positionMs)
        p.play()
        assertEquals(0L, p.state.value.positionMs)
        assertTrue(p.state.value.isPlaying)
    }

    @Test fun loopsWhenLooping() {
        val p = pb(loop = true)
        val d = p.state.value.durationMs
        var now = 0L; p.tick(now)
        while (now < d + 120) { now += 40; p.tick(now) }
        assertTrue(p.state.value.isPlaying)
        assertTrue(p.state.value.positionMs < d)
    }

    @Test fun replayResetsAndPlays() {
        val p = pb()
        p.tick(0); p.tick(200); p.pause()
        p.replay()
        assertEquals(0L, p.state.value.positionMs)
        assertTrue(p.state.value.isPlaying)
    }

    @Test fun releaseStopsAndIgnoresFurtherCommands() {
        val p = pb()
        p.release()
        assertEquals(PlaybackStatus.Released, p.state.value.status)
        p.play(); p.replay(); p.tick(0); p.tick(100)
        assertFalse(p.state.value.isPlaying)
        assertEquals(0L, p.state.value.positionMs)
    }

    @Test fun toggleAndTimeFormat() {
        val p = pb()
        p.togglePlayPause(); assertFalse(p.state.value.isPlaying)
        p.togglePlayPause(); assertTrue(p.state.value.isPlaying)
        assertEquals("0:00", formatTime(0)); assertEquals("1:05", formatTime(65_400))
    }
}
