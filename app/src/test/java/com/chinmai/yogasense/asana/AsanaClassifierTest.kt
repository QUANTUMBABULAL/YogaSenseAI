package com.chinmai.yogasense.asana

import com.chinmai.yogasense.asana.PoseFixtures.chair
import com.chinmai.yogasense.asana.PoseFixtures.tadasana
import com.chinmai.yogasense.asana.PoseFixtures.tree
import com.chinmai.yogasense.asana.PoseFixtures.triangle
import com.chinmai.yogasense.asana.PoseFixtures.warrior2
import com.chinmai.yogasense.pose.PoseFrame
import com.chinmai.yogasense.pose.PoseLandmarkIndex as L
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AsanaClassifierTest {
    private val classifier = AsanaClassifier()
    private fun classify(s: PoseFixtures.Skeleton) = classifier.classify(s.frame())

    private fun assertPose(expected: Asana, side: BodySide, r: FrameClassification) {
        assertEquals("reason=${r.reason} best=${r.best?.asana} conf=${r.best?.confidence}", expected, r.asana)
        assertEquals(side, r.side)
        assertTrue("confidence ${r.confidence}", r.confidence >= 0.65f)
    }

    // ---- Correct examples ------------------------------------------------------------------

    @Test fun recognises_tadasana() = assertPose(Asana.TADASANA, BodySide.NONE, classify(tadasana()))
    @Test fun recognises_treeLeft() = assertPose(Asana.TREE, BodySide.LEFT, classify(tree(BodySide.LEFT)))
    @Test fun recognises_treeRight() = assertPose(Asana.TREE, BodySide.RIGHT, classify(tree(BodySide.RIGHT)))
    @Test fun recognises_warrior2Left() = assertPose(Asana.WARRIOR_II, BodySide.LEFT, classify(warrior2(BodySide.LEFT)))
    @Test fun recognises_warrior2Right() = assertPose(Asana.WARRIOR_II, BodySide.RIGHT, classify(warrior2(BodySide.RIGHT)))
    @Test fun recognises_chairSideView() = assertPose(Asana.CHAIR, BodySide.NONE, classify(chair()))
    @Test fun recognises_triangleLeft() = assertPose(Asana.TRIANGLE, BodySide.LEFT, classify(triangle(BodySide.LEFT)))
    @Test fun recognises_triangleRight() = assertPose(Asana.TRIANGLE, BodySide.RIGHT, classify(triangle(BodySide.RIGHT)))

    @Test fun recognitionIsInvariantToCameraDistanceAndPosition() {
        val near = classify(tadasana())
        val far = classify(tadasana().transform(scale = 0.55f, dx = 60f, dy = 40f))
        assertPose(Asana.TADASANA, BodySide.NONE, far)
        assertEquals(near.confidence, far.confidence, 0.01f)
        assertPose(Asana.WARRIOR_II, BodySide.LEFT, classify(warrior2(BodySide.LEFT).transform(0.6f, -30f)))
    }

    @Test fun explanationContainsAllFeatureEvidence() {
        val r = classify(warrior2(BodySide.LEFT))
        val ev = r.best!!.evidence
        assertEquals(AsanaReferences.WARRIOR_II.rules.size, ev.size)
        val front = ev.first { it.name == "front knee angle" }
        assertTrue("front knee ${front.value}", front.value!! in 100f..130f)
        assertEquals(1f, front.score, 1e-3f)
        assertTrue(r.lowConfidenceLandmarks.isEmpty())
    }

    // ---- Incorrect / incomplete / similar-looking -------------------------------------------

    @Test fun tPoseIsNotTadasana() = assertEquals(Asana.UNKNOWN, classify(tadasana(armsHorizontal = true)).asana)

    @Test fun wideStandWithStraightLegsIsNotWarrior2OrTriangle() {
        val r = classify(warrior2(BodySide.LEFT, frontKneeStraight = true))
        assertEquals(r.reason, Asana.UNKNOWN, r.asana)
    }

    @Test fun uprightTriangleIsNotTriangleOrWarrior() {
        val r = classify(triangle(BodySide.LEFT, tiltDeg = 0f))
        assertEquals(r.reason, Asana.UNKNOWN, r.asana)
    }

    @Test fun lowFootLiftIsNotTree() {
        val r = classify(tree(BodySide.LEFT, lowFoot = true))
        assertNotEquals(Asana.TREE, r.asana)
    }

    @Test fun squatWithUprightTrunkIsNotChair() {
        val r = classify(chair(upright = true))
        assertNotEquals(Asana.CHAIR, r.asana)
    }

    @Test fun treeIsNotConfusedWithTadasana() {
        val r = classify(tree(BodySide.LEFT))
        val tad = r.candidates.first { it.asana == Asana.TADASANA }
        assertEquals(0f, tad.confidence, 0f)
    }

    @Test fun warriorAndTriangleAreSeparated() {
        val w = classify(warrior2(BodySide.RIGHT))
        assertEquals(0f, w.candidates.filter { it.asana == Asana.TRIANGLE }.maxOf { it.confidence }, 0f)
        val t = classify(triangle(BodySide.RIGHT))
        assertEquals(0f, t.candidates.filter { it.asana == Asana.WARRIOR_II }.maxOf { it.confidence }, 0f)
    }

    // ---- Occlusion / low visibility --------------------------------------------------------

    @Test fun occludedAnklesGiveUnknownAndReportLandmarks() {
        val r = classify(tadasana().occlude(L.LEFT_ANKLE, L.RIGHT_ANKLE))
        assertEquals(Asana.UNKNOWN, r.asana)
        assertTrue(r.lowConfidenceLandmarks.containsAll(listOf("left ankle", "right ankle")))
        assertTrue(r.best!!.missingFeatures.isNotEmpty())
    }

    @Test fun occludedNonEssentialWristStillRecognisedAndReported() {
        val r = classify(tadasana().occlude(L.LEFT_WRIST))
        assertPose(Asana.TADASANA, BodySide.NONE, r)
        assertEquals(listOf("left wrist"), r.lowConfidenceLandmarks)
    }

    @Test fun lowVisibilityEverywhereIsUnknown() {
        val r = classify(warrior2(BodySide.LEFT).allVisibility(0.3f))
        assertEquals(Asana.UNKNOWN, r.asana)
        assertEquals(12, r.lowConfidenceLandmarks.size)
    }

    @Test fun emptyFrameIsUnknown() {
        val r = classifier.classify(PoseFrame(emptyList(), 0L, 480, 640))
        assertEquals(Asana.UNKNOWN, r.asana)
        assertEquals(0f, r.confidence, 0f)
    }

    @Test fun everyTemplateRejectsEveryOtherPose() {
        val fixtures = mapOf(
            Asana.TADASANA to tadasana(), Asana.TREE to tree(BodySide.LEFT),
            Asana.WARRIOR_II to warrior2(BodySide.LEFT), Asana.CHAIR to chair(),
            Asana.TRIANGLE to triangle(BodySide.LEFT),
        )
        for ((expected, sk) in fixtures) {
            val r = classify(sk)
            val competitors = r.candidates.filter { it.asana != expected }.maxOf { it.confidence }
            assertTrue("$expected: competitor confidence $competitors", r.confidence - competitors >= 0.1f)
        }
    }
}
