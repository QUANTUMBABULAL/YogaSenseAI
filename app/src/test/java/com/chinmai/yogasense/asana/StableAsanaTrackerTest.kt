package com.chinmai.yogasense.asana

import com.chinmai.yogasense.asana.PoseFixtures.tadasana
import com.chinmai.yogasense.asana.PoseFixtures.tree
import com.chinmai.yogasense.asana.PoseFixtures.warrior2
import com.chinmai.yogasense.pose.PoseFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Frames are spaced 150 ms apart (~6.7 FPS, matching measured on-device inference). */
class StableAsanaTrackerTest {
    private val dt = 150L

    private fun vote(ts: Long, a: Asana, side: BodySide = BodySide.NONE, conf: Float = 0.9f) =
        FrameClassification(ts, a, side, conf, null, emptyList(), emptyList(), "")

    @Test fun notStableOnFirstFramesThenConfirms() {
        val t = StableAsanaTracker()
        val results = (0 until 8).map { t.update(vote(it * dt, Asana.TADASANA)) }
        assertFalse(results[0].isStable)
        assertFalse(results[2].isStable)
        assertEquals(Asana.UNKNOWN, results[2].asana)
        assertEquals(Asana.TADASANA, results[2].pending)
        assertTrue(results[7].isStable)
        assertEquals(Asana.TADASANA, results[7].asana)
        // Confirms within ~1 s at this frame rate.
        val firstStable = results.indexOfFirst { it.isStable }
        assertTrue("first stable at frame $firstStable", firstStable * dt <= 1000L)
    }

    @Test fun singleMisclassifiedFrameDoesNotDropStablePose() {
        val t = StableAsanaTracker()
        var ts = 0L
        repeat(10) { t.update(vote(ts, Asana.TADASANA)); ts += dt }
        val glitch = t.update(vote(ts, Asana.UNKNOWN)); ts += dt
        assertTrue(glitch.isStable)
        assertEquals(Asana.TADASANA, glitch.asana)
        assertTrue(t.update(vote(ts, Asana.CHAIR)).isStable)
    }

    @Test fun flickeringBetweenTwoPosesNeverConfirms() {
        val t = StableAsanaTracker()
        val r = (0 until 20).map { t.update(vote(it * dt, if (it % 2 == 0) Asana.TREE else Asana.TADASANA)) }
        assertTrue(r.none { it.isStable })
    }

    @Test fun lowConfidenceVotesDoNotConfirm() {
        val t = StableAsanaTracker()
        val r = (0 until 15).map { t.update(vote(it * dt, Asana.CHAIR, conf = 0.5f)) }
        assertTrue(r.none { it.isStable })
    }

    @Test fun sideChangeIsTrackedSeparately() {
        val t = StableAsanaTracker()
        var ts = 0L
        repeat(10) { t.update(vote(ts, Asana.TREE, BodySide.LEFT)); ts += dt }
        var last = t.update(vote(ts, Asana.TREE, BodySide.LEFT))
        assertEquals(BodySide.LEFT, last.side)
        repeat(15) { ts += dt; last = t.update(vote(ts, Asana.TREE, BodySide.RIGHT)) }
        assertTrue(last.isStable)
        assertEquals(BodySide.RIGHT, last.side)
    }

    @Test fun staleGapResets() {
        val t = StableAsanaTracker()
        repeat(10) { t.update(vote(it * dt, Asana.TADASANA)) }
        val afterGap = t.update(vote(10 * dt + 5_000L, Asana.TADASANA))
        assertFalse(afterGap.isStable)
    }

    // ---- End-to-end transitions with real classifier + smoother on synthetic fixtures ----

    private fun run(rec: AsanaRecognizer, frames: List<PoseFrame>) = frames.map { rec.process(it) }

    private fun seq(start: Long, n: Int, s: () -> PoseFixtures.Skeleton) =
        (0 until n).map { s().frame(start + it * dt) }

    @Test fun transitionTadasanaToTreeToWarrior() {
        val rec = AsanaRecognizer()
        val a = run(rec, seq(0, 10) { tadasana() })
        assertEquals(Asana.TADASANA, a.last().asana)

        val b = run(rec, seq(10 * dt, 12) { tree(BodySide.RIGHT) })
        // Never reports a pose that was not shown.
        assertTrue(b.all { it.asana in setOf(Asana.TADASANA, Asana.TREE, Asana.UNKNOWN) })
        assertEquals(Asana.TREE, b.last().asana)
        assertEquals(BodySide.RIGHT, b.last().side)

        val c = run(rec, seq(22 * dt, 12) { warrior2(BodySide.LEFT) })
        assertTrue(c.all { it.asana in setOf(Asana.TREE, Asana.WARRIOR_II, Asana.UNKNOWN) })
        assertEquals(Asana.WARRIOR_II, c.last().asana)
        assertTrue(c.last().stability >= 0.6f)
    }

    @Test fun walkingOutOfFrameDropsToUnknown() {
        val rec = AsanaRecognizer()
        run(rec, seq(0, 10) { tadasana() })
        val gone = run(rec, (10 until 25).map { PoseFrame(emptyList(), it * dt, 480, 640) })
        assertEquals(Asana.UNKNOWN, gone.last().asana)
        assertFalse(gone.last().isStable)
    }
}
