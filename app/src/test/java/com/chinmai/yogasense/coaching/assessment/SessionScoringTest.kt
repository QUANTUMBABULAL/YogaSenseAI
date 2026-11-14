package com.chinmai.yogasense.coaching.assessment

import com.chinmai.yogasense.asana.Asana
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionScoringTest {
    private fun pose(a: Asana, align: Int?, stab: Int?, longestMs: Long, totalMs: Long = longestMs, corrections: Map<String, Int> = emptyMap()) =
        PoseSummary(a, totalMs, longestMs, align, stab, corrections, emptyList())

    private fun session(vararg p: PoseSummary) = SessionSummary(120_000, p.map { it.asana }, p.toList())
    private val goal20: (Asana) -> Long = { 20_000 }

    @Test fun poseScoreIsTheWeightedMeanOfAlignmentStabilityAndHold() {
        // 0.5*80 + 0.25*60 + 0.25*(10 s of 20 s = 50) = 67.5 -> 68
        val s = SessionScoring.pose(pose(Asana.TREE, 80, 60, 10_000), 20_000)
        assertEquals(68, s.total)
        assertEquals(80, s.alignment); assertEquals(60, s.stability); assertEquals(50, s.hold)
        assertEquals(ScoreComponent.ALIGNMENT, s.strongest); assertEquals(ScoreComponent.HOLD, s.focus)
        assertEquals(ScoreBand.GOOD, s.band)
    }

    @Test fun holdIsCappedAtTheGoal() {
        val s = SessionScoring.pose(pose(Asana.TADASANA, 100, 100, 90_000), 30_000)
        assertEquals(100, s.hold); assertEquals(100, s.total); assertEquals(ScoreBand.EXCELLENT, s.band)
    }

    @Test fun unmeasuredComponentIsLeftOutNeverCountedAsZero() {
        // Alignment not measured: (0.25*80 + 0.25*100) / 0.5 = 90, not 45.
        val s = SessionScoring.pose(pose(Asana.CHAIR, null, 80, 20_000), 20_000)
        assertNull(s.alignment)
        assertEquals(90, s.total)
    }

    @Test fun poseWithoutAnyPostureMeasurementIsNotScored() {
        val s = SessionScoring.pose(pose(Asana.TRIANGLE, null, null, 15_000), 20_000)
        assertNull(s.total); assertNull(s.hold); assertNull(s.band)
        assertNotNull(s.note)
        val session = SessionScoring.score(session(pose(Asana.TRIANGLE, null, null, 15_000)), goal20)
        assertNull(session.total); assertEquals(0, session.scoredPoses)
        assertTrue(session.headline.contains("Not enough clear measurements"))
    }

    @Test fun emptySessionHasNoScore() {
        val s = SessionScoring.score(SessionSummary(60_000, emptyList(), emptyList()), goal20)
        assertNull(s.total); assertNull(s.band)
        assertTrue(s.headline.contains("No pose was confirmed"))
    }

    @Test fun sessionScoreWeightsPosesBySteadyHoldTimeAndSkipsUnscoredOnes() {
        val long = pose(Asana.TADASANA, 90, 90, 20_000, totalMs = 30_000)   // 0.5*90+0.25*90+0.25*100 = 92.5 -> 93
        val short = pose(Asana.TREE, 50, 50, 2_000, totalMs = 2_000)        // 0.5*50+0.25*50+0.25*10 = 40
        val unscored = pose(Asana.CHAIR, null, null, 0)
        val s = SessionScoring.score(session(long, short, unscored), goal20)
        assertEquals(2, s.scoredPoses)
        // weights 30 s and 5 s (minimum): (93*30 + 40*5) / 35 = 85.4 -> 85
        assertEquals(85, s.total)
        assertEquals(ScoreBand.STRONG, s.band)
        assertNull(s.of(Asana.CHAIR)!!.total)
    }

    @Test fun usesThePerPoseHoldGoal() {
        val s = SessionScoring.score(session(pose(Asana.TREE, 80, 80, 10_000)), holdGoalMs = { 10_000 })
        assertEquals(100, s.poses.single().hold)
    }

    @Test fun noteTargetsTheWeakestComponent() {
        val form = SessionScoring.pose(pose(Asana.WARRIOR_II, 40, 90, 20_000, corrections = mapOf("Level the arms." to 1, "Bend the front knee more." to 3)), 20_000)
        assertEquals(ScoreComponent.ALIGNMENT, form.focus)
        assertEquals("Form first: Bend the front knee more.", form.note)
        val balance = SessionScoring.pose(pose(Asana.TREE, 90, 30, 20_000), 20_000)
        assertTrue(balance.note!!.startsWith("Steadiness"))
        val endurance = SessionScoring.pose(pose(Asana.TREE, 90, 90, 6_000), 20_000)
        assertTrue(endurance.note!!.contains("6 s of 20 s"))
        val great = SessionScoring.pose(pose(Asana.TADASANA, 95, 92, 30_000), 30_000)
        assertTrue(great.note!!.startsWith("Precise and steady"))
    }

    @Test fun bandsCoverTheWholeScale() {
        assertEquals(ScoreBand.EXCELLENT, ScoreBand.of(90)); assertEquals(ScoreBand.STRONG, ScoreBand.of(89))
        assertEquals(ScoreBand.STRONG, ScoreBand.of(75)); assertEquals(ScoreBand.GOOD, ScoreBand.of(60))
        assertEquals(ScoreBand.DEVELOPING, ScoreBand.of(40)); assertEquals(ScoreBand.STARTING, ScoreBand.of(0))
        assertEquals(1f, ScoreComponent.entries.sumOf { it.weight.toDouble() }.toFloat(), 0.0001f)
    }
}
