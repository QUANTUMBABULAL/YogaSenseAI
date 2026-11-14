package com.chinmai.yogasense.coaching.assessment

import com.chinmai.yogasense.asana.Asana
import com.chinmai.yogasense.asana.BodyFeatures
import com.chinmai.yogasense.asana.BodySide
import com.chinmai.yogasense.asana.PoseFixtures
import com.chinmai.yogasense.asana.Recognition
import com.chinmai.yogasense.coaching.CoachEvent
import com.chinmai.yogasense.coaching.CoachSettings
import com.chinmai.yogasense.coaching.CoachingDecisionEngine
import com.chinmai.yogasense.coaching.CuePriority
import com.chinmai.yogasense.coaching.VoiceMode
import com.chinmai.yogasense.pose.FramingStatus
import com.chinmai.yogasense.pose.PoseLandmarkIndex as L
import com.chinmai.yogasense.ui.practice.AssessmentPipeline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sin

private const val DT = 150L

/** Tadasana shifted by [dx] pixels (whole body), to simulate sway. */
private fun swayed(dx: Float) = PoseFixtures.tadasana().transform(1f, dx, 0f)

class StabilityAnalyzerTest {
    @Test fun noScoreUntilEnoughSamples() {
        val s = StabilityAnalyzer()
        repeat(5) { assertNull(s.add(BodyFeatures(swayed(0f).frame()), it * DT).score) }
    }

    @Test fun stillBodyWithJitterIsSteady() {
        val s = StabilityAnalyzer()
        var r = StabilityResult.NONE
        repeat(16) { i -> r = s.add(BodyFeatures(swayed(((i % 3) - 1) * 0.6f).frame()), i * DT) } // ±0.6 px jitter
        assertTrue("score ${r.score}", r.score!! >= 95)
        assertEquals("Steady", r.label)
    }

    @Test fun largeSwayIsUnsteady() {
        val s = StabilityAnalyzer()
        var r = StabilityResult.NONE
        repeat(20) { i -> r = s.add(BodyFeatures(swayed((sin(i * 0.9) * 25).toFloat()).frame()), i * DT) } // ±25 px ≈ ±0.2 torso
        assertTrue("score ${r.score}", r.score!! < 40)
    }

    @Test fun cameraDistanceInvariant() {
        fun run(scale: Float): Int? {
            val s = StabilityAnalyzer(); var r = StabilityResult.NONE
            repeat(20) { i -> r = s.add(BodyFeatures(PoseFixtures.tadasana().transform(scale, (sin(i * 0.9) * 8 * scale).toFloat(), 0f).frame()), i * DT) }
            return r.score
        }
        assertEquals(run(1f)!!.toFloat(), run(0.6f)!!.toFloat(), 3f)
    }

    @Test fun missingTorsoFramesAreSkippedAndGapsReset() {
        val s = StabilityAnalyzer()
        repeat(12) { i -> s.add(BodyFeatures(swayed(0f).frame()), i * DT) }
        val skipped = s.add(BodyFeatures(PoseFixtures.tadasana().occlude(L.LEFT_SHOULDER).frame()), 12 * DT)
        assertEquals(12, skipped.samples)
        val afterGap = s.add(BodyFeatures(swayed(0f).frame()), 12 * DT + 5_000)
        assertEquals(1, afterGap.samples)
        assertNull(afterGap.score)
    }

    @Test fun resetClears() {
        val s = StabilityAnalyzer()
        repeat(12) { i -> s.add(BodyFeatures(swayed(0f).frame()), i * DT) }
        s.reset()
        assertNull(s.current().score)
    }
}

class HoldTrackerTest {
    @Test fun countsOnlyQualifyingTime() {
        val h = HoldTracker()
        var st = HoldStatus.NONE
        for (i in 0..10) st = h.update(Asana.TREE, true, i * 200L)
        assertEquals(2000L, st.currentHoldMs)
        assertTrue(st.counting)
    }

    @Test fun shortDropoutPausesWithoutCountingThenResumes() {
        val h = HoldTracker(maxDropoutMs = 1500)
        for (i in 0..5) h.update(Asana.TREE, true, i * 200L)               // 1000 ms
        val paused = h.update(Asana.TREE, false, 1200, "unsteady")
        assertFalse(paused.counting); assertEquals("unsteady", paused.reason); assertEquals(1000L, paused.currentHoldMs)
        h.update(Asana.TREE, false, 1600)
        val resumed = h.update(Asana.TREE, true, 2000)                     // resumes; dropout not counted
        assertEquals(1000L, resumed.currentHoldMs)
        val more = h.update(Asana.TREE, true, 2200)
        assertEquals(1200L, more.currentHoldMs)
    }

    @Test fun longDropoutStartsNewSegment() {
        val h = HoldTracker(maxDropoutMs = 1500)
        for (i in 0..5) h.update(Asana.TREE, true, i * 200L)
        h.update(Asana.TREE, false, 3000)
        val st = h.update(Asana.TREE, true, 3200)
        assertEquals(0L, st.currentHoldMs)
        assertEquals(1, h.totals[Asana.TREE]!!.segments)
    }

    @Test fun leavingFrameAndPoseChangeSegment() {
        val h = HoldTracker()
        for (i in 0..5) h.update(Asana.TREE, true, i * 200L)
        val out = h.update(null, false, 1200)
        assertFalse(out.counting)
        for (i in 0..3) h.update(Asana.CHAIR, true, 1400 + i * 200L)
        val t = h.snapshotTotals()
        assertEquals(1000L, t[Asana.TREE]!!.totalMs)
        assertEquals(600L, t[Asana.CHAIR]!!.totalMs)
    }

    @Test fun pausedAppGapIsNotCounted() {
        val h = HoldTracker()
        h.update(Asana.TREE, true, 0); h.update(Asana.TREE, true, 200)
        val st = h.update(Asana.TREE, true, 60_000) // e.g. app paused 1 min
        assertEquals(0L, st.currentHoldMs)
        assertEquals(200L, h.snapshotTotals()[Asana.TREE]!!.longestMs)
    }
}

class AssessmentCoachTest {
    private val assessor = AsanaAlignmentAssessor()
    private val steady = StabilityResult(90, 0.01f, 20)
    private fun bad(ts: Long) = assessor.assess(BodyFeatures(AssessmentFixtures.warriorShallow().frame()), Asana.WARRIOR_II, BodySide.LEFT, ts)
    private fun good(ts: Long) = assessor.assess(BodyFeatures(AssessmentFixtures.warriorIdeal().frame()), Asana.WARRIOR_II, BodySide.LEFT, ts)

    @Test fun correctionOnlyAfterPersistenceAndOnlyOne() {
        val c = AssessmentCoach(persistMs = 2000)
        assertTrue(c.update(bad(0), steady, true, 0).isEmpty())
        assertTrue(c.update(bad(1000), steady, true, 1000).isEmpty())
        val ev = c.update(bad(2100), steady, true, 2100)
        assertEquals(1, ev.filterIsInstance<CoachEvent.AlignmentCorrectionRequired>().size)
        assertEquals("WARRIOR_II.front_knee.high", (ev.single() as CoachEvent.AlignmentCorrectionRequired).correction.id)
    }

    @Test fun duplicateCorrectionSuppressedWithinRepeatWindow() {
        val c = AssessmentCoach(persistMs = 0, repeatMs = 15_000)
        assertEquals(1, c.update(bad(0), steady, true, 0).size)
        assertTrue(c.update(bad(5000), steady, true, 5000).isEmpty())
        assertEquals(1, c.update(bad(15_100), steady, true, 15_100).size)
    }

    @Test fun noCorrectionWhenFramingBadOrAssessmentUnavailable() {
        val c = AssessmentCoach(persistMs = 0)
        assertTrue(c.update(bad(0), steady, framingOk = false, timeMs = 0).isEmpty())
        val unavailable = assessor.assess(BodyFeatures(AssessmentFixtures.warriorShallow().allVisibility(0.2f).frame()), Asana.WARRIOR_II, BodySide.LEFT, 0)
        assertTrue(c.update(unavailable, steady, true, 100).isEmpty())
        assertNull(c.currentCorrection(unavailable, true))
        assertNull(c.currentCorrection(bad(0), framingOk = false))
    }

    @Test fun improvementOnlyAfterMeasuredSustainedImprovement() {
        val c = AssessmentCoach(persistMs = 0, improvePersistMs = 1500)
        c.update(bad(0), steady, true, 0)                                // correction given
        assertTrue(c.update(good(500), steady, true, 500).none { it is CoachEvent.AlignmentImproved })
        assertTrue(c.update(good(1500), steady, true, 1500).none { it is CoachEvent.AlignmentImproved })
        val ev = c.update(good(2100), steady, true, 2100)
        assertEquals(CoachEvent.AlignmentImproved("WARRIOR_II.front_knee.high"), ev.single())
        assertEquals(listOf("Front knee bend"), c.improvements)
        assertTrue(c.update(good(5000), steady, true, 5000).isEmpty())   // announced once
    }

    @Test fun noImprovementWithoutPriorCorrection() {
        val c = AssessmentCoach(persistMs = 0, improvePersistMs = 0)
        repeat(5) { assertTrue(c.update(good(it * 1000L), steady, true, it * 1000L).isEmpty()) }
    }

    @Test fun occludedMetricCannotCountAsImprovement() {
        val c = AssessmentCoach(persistMs = 0, improvePersistMs = 0)
        c.update(bad(0), steady, true, 0)
        val occluded = assessor.assess(BodyFeatures(AssessmentFixtures.warriorIdeal().occlude(L.LEFT_KNEE).frame()), Asana.WARRIOR_II, BodySide.LEFT, 100)
        assertTrue(c.update(occluded, steady, true, 100).none { it is CoachEvent.AlignmentImproved })
    }

    @Test fun stabilityDecreaseAnnouncedOnceUntilRecovered() {
        val c = AssessmentCoach()
        val g = good(0)
        c.update(g, StabilityResult(85, 0.02f, 20), true, 0)
        assertEquals(CoachEvent.StabilityDecreased(30), c.update(g, StabilityResult(30, 0.14f, 20), true, 100).single())
        assertTrue(c.update(g, StabilityResult(25, 0.15f, 20), true, 200).isEmpty())
        c.update(g, StabilityResult(80, 0.03f, 20), true, 300)
        assertEquals(1, c.update(g, StabilityResult(20, 0.16f, 20), true, 400).size)
    }

    @Test fun engineVoicesStabilityAndCorrectionsPerMode() {
        fun engine(mode: VoiceMode, corrections: Boolean = true) =
            CoachingDecisionEngine(CoachSettings(mode = mode, corrections = corrections)).also { it.onEvent(CoachEvent.SessionStarted(null), 0) }
        assertEquals(CuePriority.CORRECTION, engine(VoiceMode.GUIDED).onEvent(CoachEvent.StabilityDecreased(30), 10_000).single().priority)
        assertEquals(1, engine(VoiceMode.MINIMAL).onEvent(CoachEvent.StabilityDecreased(30), 10_000).size)
        assertTrue(engine(VoiceMode.OFF).onEvent(CoachEvent.StabilityDecreased(30), 10_000).isEmpty())
        assertTrue(engine(VoiceMode.GUIDED, corrections = false).onEvent(CoachEvent.StabilityDecreased(30), 10_000).isEmpty())
    }
}

class AssessmentPipelineTest {
    private val stableTada = Recognition(Asana.TADASANA, 0.9f, true)

    @Test fun noAssessmentWithoutConfirmedRecognition() {
        val p = AssessmentPipeline()
        val out = p.process(PoseFixtures.tadasana().frame(0), Recognition(Asana.UNKNOWN, 0f, false, pending = Asana.TADASANA), FramingStatus.FULL_BODY, 0)
        assertNull(out.live)
        assertTrue(out.events.isEmpty())
    }

    @Test fun holdCountsOnlyAfterStabilityAvailableAndMilestoneFromStableHold() {
        val p = AssessmentPipeline()
        var t = 0L
        var last: AssessmentPipeline.Output? = null
        val milestones = mutableListOf<Int>()
        while (t <= 14_000) {
            last = p.process(PoseFixtures.tadasana().frame(t), stableTada, FramingStatus.FULL_BODY, t)
            last.events.filterIsInstance<CoachEvent.HoldMilestoneReached>().forEach { milestones += it.seconds }
            if (t == 600L) { assertFalse(last.live!!.hold.counting); assertEquals("Hold still — measuring stability", last.live!!.hold.reason) }
            t += DT
        }
        val live = last!!.live!!
        assertTrue(live.hold.counting)
        assertEquals(100, live.alignment!!.score)
        // Stable hold starts once stability is measurable (~1.5 s), so it is shorter than the 14 s elapsed.
        assertTrue("hold ${live.hold.currentHoldMs}", live.hold.currentHoldMs in 11_000L..13_000L)
        assertEquals(listOf(10), milestones)
    }

    @Test fun poseChangeResetsAndSummaryReportsOnlyRealData() {
        val p = AssessmentPipeline()
        var t = 0L
        while (t <= 6_000) { p.process(PoseFixtures.tadasana().frame(t), stableTada, FramingStatus.FULL_BODY, t); t += DT }
        val chair = Recognition(Asana.CHAIR, 0.9f, true)
        val first = p.process(PoseFixtures.chair().frame(t), chair, FramingStatus.FULL_BODY, t)
        assertFalse(first.live!!.hold.counting)        // stability window was reset
        assertNull(first.live!!.stability.score)
        val s = p.summary(60_000)
        assertEquals(listOf(Asana.TADASANA, Asana.CHAIR), s.recognisedPoses)
        val tada = s.poses.first { it.asana == Asana.TADASANA }
        assertEquals(100, tada.meanAlignment)
        assertTrue(tada.stableHoldTotalMs in 3_500L..5_000L)
        val ch = s.poses.first { it.asana == Asana.CHAIR }
        assertNull(ch.meanAlignment)                   // not enough samples: unavailable, not 0
        assertNull(ch.meanStability)
        assertEquals(0L, ch.stableHoldTotalMs)
    }

    @Test fun incorrectPoseProducesOneCorrectionInSummary() {
        val p = AssessmentPipeline()
        val rec = Recognition(Asana.WARRIOR_II, 0.9f, true, BodySide.LEFT)
        var t = 0L
        val corrections = mutableListOf<CoachEvent.AlignmentCorrectionRequired>()
        while (t <= 5_000) {
            corrections += p.process(AssessmentFixtures.warriorShallow().frame(t), rec, FramingStatus.FULL_BODY, t).events
                .filterIsInstance<CoachEvent.AlignmentCorrectionRequired>()
            t += DT
        }
        assertEquals(1, corrections.size)
        assertEquals(mapOf("Bend your front knee a little more" to 1), p.summary(5_000).poses.single().corrections)
    }

    @Test fun badFramingSuppressesCorrections() {
        val p = AssessmentPipeline()
        val rec = Recognition(Asana.WARRIOR_II, 0.9f, true, BodySide.LEFT)
        var t = 0L
        var any = false
        while (t <= 5_000) {
            val out = p.process(AssessmentFixtures.warriorShallow().frame(t), rec, FramingStatus.SHOW_FEET, t)
            any = any || out.events.any { it is CoachEvent.AlignmentCorrectionRequired } || out.live?.correction != null
            t += DT
        }
        assertFalse(any)
    }
}
