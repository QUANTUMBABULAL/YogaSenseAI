package com.chinmai.yogasense.coaching.assessment

import com.chinmai.yogasense.asana.Asana
import com.chinmai.yogasense.asana.BodyFeatures
import com.chinmai.yogasense.asana.BodySide
import com.chinmai.yogasense.asana.Geometry
import com.chinmai.yogasense.asana.PoseFixtures
import com.chinmai.yogasense.asana.Pt
import com.chinmai.yogasense.coaching.assessment.AssessmentFixtures.features
import com.chinmai.yogasense.pose.PoseLandmarkIndex as L
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlignmentAssessorTest {
    private val assessor = AsanaAlignmentAssessor()
    private fun assess(s: PoseFixtures.Skeleton, a: Asana, side: BodySide = BodySide.NONE) = assessor.assess(features(s), a, side, 0)

    private fun assertGood(r: AlignmentResult) {
        assertTrue("availability ${r.availability}", r.available)
        assertTrue("score ${r.score} metrics ${r.metrics.map { it.id to it.value }}", r.score!! >= 90)
        assertTrue("issues ${r.issues}", r.issues.isEmpty())
    }

    // ---- Correct postures (synthetic) ---------------------------------------------------------

    @Test fun tadasanaCorrect() = assertGood(assess(PoseFixtures.tadasana(), Asana.TADASANA))
    @Test fun treeCorrectBothSides() {
        assertGood(assess(PoseFixtures.tree(BodySide.LEFT), Asana.TREE, BodySide.LEFT))
        assertGood(assess(PoseFixtures.tree(BodySide.RIGHT), Asana.TREE, BodySide.RIGHT))
    }
    @Test fun warriorCorrectBothSides() {
        assertGood(assess(AssessmentFixtures.warriorIdeal(BodySide.LEFT), Asana.WARRIOR_II, BodySide.LEFT))
        assertGood(assess(AssessmentFixtures.warriorIdeal(BodySide.RIGHT), Asana.WARRIOR_II, BodySide.RIGHT))
    }
    @Test fun chairCorrectSideView() = assertGood(assess(PoseFixtures.chair(), Asana.CHAIR))
    @Test fun triangleCorrectBothSides() {
        assertGood(assess(PoseFixtures.triangle(BodySide.LEFT), Asana.TRIANGLE, BodySide.LEFT))
        assertGood(assess(PoseFixtures.triangle(BodySide.RIGHT), Asana.TRIANGLE, BodySide.RIGHT))
    }

    // ---- Deliberately incorrect postures ------------------------------------------------------

    @Test fun tadasanaHitchedShoulder() {
        val r = assess(AssessmentFixtures.tadasanaShoulderHitch(), Asana.TADASANA)
        assertEquals("shoulders_level", r.primaryIssue!!.metricId)
        assertEquals("Level your shoulders", r.primaryIssue!!.correction)
        assertTrue(r.score!! < 90)
    }

    @Test fun treeLeaningTorso() {
        val r = assess(AssessmentFixtures.treeLeaning(), Asana.TREE, BodySide.LEFT)
        assertEquals("torso_upright", r.primaryIssue!!.metricId)
        assertEquals(Direction.HIGH, r.primaryIssue!!.direction)
    }

    @Test fun warriorFrontKneeTooStraight() {
        val r = assess(AssessmentFixtures.warriorShallow(), Asana.WARRIOR_II, BodySide.LEFT)
        val m = r.metric("front_knee")!!
        assertTrue("knee ${m.value}", m.value!! in 145f..160f)
        assertEquals(MetricStatus.NEEDS_WORK, m.status)
        assertEquals("Bend your front knee a little more", r.primaryIssue!!.correction)
    }

    @Test fun chairUprightTorso() {
        val r = assess(PoseFixtures.chair(upright = true), Asana.CHAIR)
        assertTrue(r.issues.any { it.metricId == "torso_lean" && it.direction == Direction.LOW })
    }

    @Test fun triangleShallowSideBend() {
        val r = assess(PoseFixtures.triangle(BodySide.LEFT, tiltDeg = 25f), Asana.TRIANGLE, BodySide.LEFT)
        assertEquals("side_bend", r.primaryIssue!!.metricId)
        assertEquals("Lengthen further into the side bend", r.primaryIssue!!.correction)
    }

    @Test fun wrongSideGivesDifferentResultThanRightSide() {
        // Warrior assessed with the straight leg as "front" must not look correct.
        val r = assess(AssessmentFixtures.warriorIdeal(BodySide.LEFT), Asana.WARRIOR_II, BodySide.RIGHT)
        assertTrue(r.issues.any { it.metricId == "front_knee" || it.metricId == "back_leg" })
    }

    // ---- Joint angle calculations -------------------------------------------------------------

    @Test fun jointAngles() {
        assertEquals(90f, Geometry.angleDeg(Pt(0f, 0f), Pt(0f, 10f), Pt(10f, 10f))!!, 1e-3f)
        val f = features(AssessmentFixtures.warriorIdeal())
        assertEquals(95f, f.knee(BodySide.LEFT)!!, 1.5f)
        assertEquals(0f, f.shoulderLineTilt!!, 1e-3f)
        val tree = features(PoseFixtures.tree(BodySide.LEFT))
        assertEquals(60f, tree.thighFromVertical(BodySide.LEFT)!!, 1f)
        assertEquals(0f, tree.thighFromVertical(BodySide.RIGHT)!!, 1e-3f)
    }

    // ---- Missing / low-confidence data --------------------------------------------------------

    @Test fun occludedKneeNeverProducesKneeCorrection() {
        val r = assess(AssessmentFixtures.warriorShallow().occlude(L.LEFT_KNEE), Asana.WARRIOR_II, BodySide.LEFT)
        assertEquals(MetricStatus.UNAVAILABLE, r.metric("front_knee")!!.status)
        assertNull(r.metric("front_knee")!!.score)
        assertTrue(r.issues.none { it.metricId == "front_knee" })
        assertTrue(r.available) // remaining metrics carry enough weight
        assertTrue(r.lowConfidenceLandmarks.contains("left knee"))
    }

    @Test fun lowVisibilityMakesScoreUnavailableNotZero() {
        val r = assess(PoseFixtures.tadasana().allVisibility(0.3f), Asana.TADASANA)
        assertEquals(Availability.INSUFFICIENT_LANDMARKS, r.availability)
        assertNull(r.score)
        assertTrue(r.issues.isEmpty())
        assertTrue(r.metrics.all { it.status == MetricStatus.UNAVAILABLE })
    }

    @Test fun tooFewMetricsMakesScoreUnavailable() {
        // Hide hips, knees and ankles: only shoulder-based metrics remain.
        val s = PoseFixtures.tadasana().occlude(L.LEFT_HIP, L.RIGHT_HIP, L.LEFT_KNEE, L.RIGHT_KNEE, L.LEFT_ANKLE, L.RIGHT_ANKLE)
        val r = assess(s, Asana.TADASANA)
        assertNull(r.score)
        assertEquals(Availability.INSUFFICIENT_LANDMARKS, r.availability)
    }

    @Test fun unknownAsanaHasNoProfile() {
        val r = assess(PoseFixtures.tadasana(), Asana.UNKNOWN)
        assertEquals(Availability.NO_PROFILE, r.availability)
        assertNull(r.score)
    }

    // ---- Mirroring, proportions, camera distance ----------------------------------------------

    @Test fun mirroringDoesNotChangeScores() {
        val cases = listOf(
            Triple(PoseFixtures.tadasana(), Asana.TADASANA, BodySide.NONE),
            Triple(AssessmentFixtures.warriorShallow(), Asana.WARRIOR_II, BodySide.LEFT),
            Triple(PoseFixtures.triangle(BodySide.LEFT), Asana.TRIANGLE, BodySide.LEFT),
            Triple(AssessmentFixtures.tadasanaShoulderHitch(), Asana.TADASANA, BodySide.NONE),
        )
        cases.forEach { (s, a, side) ->
            val f = s.frame()
            val normal = assessor.assess(BodyFeatures(f), a, side, 0)
            val mirror = assessor.assess(BodyFeatures(AssessmentFixtures.mirrored(f)), a, side, 0)
            assertEquals("$a", normal.score, mirror.score)
            assertEquals(normal.issues.map { it.correctionId }, mirror.issues.map { it.correctionId })
        }
    }

    @Test fun cameraDistanceAndBodyProportionsDoNotChangeScore() {
        assertEquals(100, assess(PoseFixtures.tadasana().transform(0.55f, 40f, 30f), Asana.TADASANA).score)
        assertEquals(100, assess(AssessmentFixtures.longLegs(PoseFixtures.tadasana()), Asana.TADASANA).score)
        val near = assess(AssessmentFixtures.warriorShallow(), Asana.WARRIOR_II, BodySide.LEFT).score
        val far = assess(AssessmentFixtures.warriorShallow().transform(0.6f), Asana.WARRIOR_II, BodySide.LEFT).score
        assertEquals(near, far)
    }

    @Test fun recognitionConfidenceIsNotPartOfAlignment() {
        // The assessor has no access to recognition confidence: same frame, same score.
        val a = assess(PoseFixtures.chair(), Asana.CHAIR).score
        val b = assess(PoseFixtures.chair(), Asana.CHAIR).score
        assertEquals(a, b)
    }

    @Test fun everyProfileDocumentsRationale() {
        AsanaAssessmentProfiles.ALL.values.forEach { p ->
            assertTrue(p.rules.size >= 4)
            p.rules.forEach { r ->
                assertTrue("${r.id} rationale", r.rationale.isNotBlank())
                assertTrue("${r.id} correction", r.lowCorrection != null || r.highCorrection != null)
                assertTrue(r.tolerance > 0 && r.idealMin <= r.idealMax && r.weight > 0)
            }
        }
    }
}
