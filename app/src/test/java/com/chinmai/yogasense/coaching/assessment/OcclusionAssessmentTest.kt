package com.chinmai.yogasense.coaching.assessment

import com.chinmai.yogasense.asana.Asana
import com.chinmai.yogasense.asana.BodyFeatures
import com.chinmai.yogasense.asana.BodySide
import com.chinmai.yogasense.asana.PoseFixtures
import com.chinmai.yogasense.asana.Pt
import com.chinmai.yogasense.asana.Recognition
import com.chinmai.yogasense.coaching.CoachEvent
import com.chinmai.yogasense.pose.CameraOrientation
import com.chinmai.yogasense.pose.FramingStatus
import com.chinmai.yogasense.pose.Landmark
import com.chinmai.yogasense.pose.LandmarkReliability
import com.chinmai.yogasense.pose.LandmarkState
import com.chinmai.yogasense.pose.OcclusionAwareFraming
import com.chinmai.yogasense.pose.OrientationDetector
import com.chinmai.yogasense.pose.PoseFrame
import com.chinmai.yogasense.pose.ReliabilityTracker
import com.chinmai.yogasense.ui.practice.AssessmentPipeline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import com.chinmai.yogasense.pose.PoseLandmarkIndex as L

/*
 * SYNTHETIC tests of the Phase 8 occlusion model. They check the logic on constructed skeletons;
 * they say nothing about real-world accuracy (see the OPPO F15 test matrix in the report).
 */

private val LEFT_SIDE = intArrayOf(L.LEFT_SHOULDER, L.LEFT_ELBOW, L.LEFT_WRIST, L.LEFT_HIP, L.LEFT_KNEE, L.LEFT_ANKLE)
private val RIGHT_SIDE = intArrayOf(L.RIGHT_SHOULDER, L.RIGHT_ELBOW, L.RIGHT_WRIST, L.RIGHT_HIP, L.RIGHT_KNEE, L.RIGHT_ANKLE)

/** Profile view of a front skeleton: the right side is moved onto the left side and hidden behind it. */
private fun PoseFixtures.Skeleton.toProfile(): PoseFixtures.Skeleton = apply {
    val pairs = LEFT_SIDE.zip(RIGHT_SIDE)
    pairs.forEach { (l, r) -> val p = joints.getValue(l); joints[r] = Pt(p.x - 3f, p.y) }
    hide(*RIGHT_SIDE)
}

/** Body turned toward profile: x compressed toward the hip centre (foreshortening), nothing hidden. */
private fun PoseFixtures.Skeleton.turnedSideways(factor: Float = 0.12f): PoseFixtures.Skeleton = apply {
    val cx = (joints.getValue(L.LEFT_HIP).x + joints.getValue(L.RIGHT_HIP).x) / 2f
    joints.replaceAll { _, p -> Pt(cx + (p.x - cx) * factor, p.y) }
}

private val assessor = AsanaAlignmentAssessor()
private fun assess(s: PoseFixtures.Skeleton, a: Asana, side: BodySide = BodySide.NONE) = assessor.assess(BodyFeatures(s.frame()), a, side, 0)

class LandmarkReliabilityTest {
    @Test fun classifiesFourStates() {
        assertEquals(LandmarkState.RELIABLE, LandmarkReliability.classify(Landmark(0.5f, 0.5f, 0f, 0.9f, 0.9f)))
        assertEquals(LandmarkState.PARTIAL, LandmarkReliability.classify(Landmark(0.5f, 0.5f, 0f, 0.55f, 0.9f)))
        assertEquals(LandmarkState.OCCLUDED, LandmarkReliability.classify(Landmark(0.5f, 0.5f, 0f, 0.1f, 0.9f)))
        assertEquals(LandmarkState.MISSING, LandmarkReliability.classify(Landmark(0.5f, 0.5f, 0f, 0.9f, 0.2f)))  // presence too low
        assertEquals(LandmarkState.MISSING, LandmarkReliability.classify(Landmark(0.5f, 1.08f, 0f, 0.9f, 0.9f))) // predicted off-frame
        assertTrue(LandmarkReliability.classify(null as PoseFrame?).all { it == LandmarkState.MISSING })
    }

    @Test fun temporaryLossIsTransientAndPersistentOcclusionIsDetected() {
        val t = ReliabilityTracker(persistMs = 2000)
        val visible = PoseFixtures.tadasana()
        t.update(visible.frame(0), 0)
        val brief = t.update(PoseFixtures.tadasana().occlude(L.LEFT_KNEE).frame(500), 500)
        assertTrue(L.LEFT_KNEE in brief.transientlyUnavailable); assertFalse(L.LEFT_KNEE in brief.persistentlyUnavailable)
        val back = t.update(visible.frame(800), 800)
        assertTrue(back.reliable(L.LEFT_KNEE)) // timer restarts
        t.update(PoseFixtures.tadasana().occlude(L.LEFT_KNEE).frame(1000), 1000)
        val later = t.update(PoseFixtures.tadasana().occlude(L.LEFT_KNEE).frame(3100), 3100)
        assertTrue(L.LEFT_KNEE in later.persistentlyUnavailable)
    }

    @Test fun orientationFromGeometry() {
        fun o(s: PoseFixtures.Skeleton) = s.frame().let { OrientationDetector.detect(it, LandmarkReliability.classify(it)) }
        assertEquals(CameraOrientation.FRONT, o(PoseFixtures.tadasana()))
        assertEquals(CameraOrientation.FRONT, o(PoseFixtures.triangle(BodySide.LEFT)))   // tilted trunk still "front"
        assertEquals(CameraOrientation.SIDE, o(PoseFixtures.chair()))
        assertEquals(CameraOrientation.SIDE, o(PoseFixtures.tadasana().toProfile()))      // far side hidden
        assertEquals(CameraOrientation.SIDE, o(AssessmentFixtures.warriorIdeal().turnedSideways()))
    }
}

class OcclusionAwareAssessmentTest {
    @Test fun fullyVisibleAllAsanasHaveFullCoverageAndHighConfidence() {
        listOf(
            assess(PoseFixtures.tadasana(), Asana.TADASANA),
            assess(PoseFixtures.tree(BodySide.LEFT), Asana.TREE, BodySide.LEFT),
            assess(AssessmentFixtures.warriorIdeal(BodySide.RIGHT), Asana.WARRIOR_II, BodySide.RIGHT),
            assess(PoseFixtures.chair(), Asana.CHAIR),
            assess(PoseFixtures.triangle(BodySide.LEFT), Asana.TRIANGLE, BodySide.LEFT),
        ).forEach { r ->
            assertTrue("${r.asana} ${r.metrics.filter { it.score == null }.map { it.id to it.reason }}", r.available)
            assertEquals("${r.asana} coverage", 1f, r.coverage, 1e-4f)
            assertEquals("${r.asana}", ConfidenceLevel.HIGH, r.confidence.level)
            assertNull(r.cameraHint)
            assertTrue(r.metrics.none { it.oneSideOnly })
        }
    }

    @Test fun oneArmOccludedMeasuresTheVisibleArmAndSaysSo() {
        val r = assess(AssessmentFixtures.warriorIdeal().occlude(L.RIGHT_ELBOW, L.RIGHT_WRIST), Asana.WARRIOR_II, BodySide.LEFT)
        val arms = r.metric("arms_level")!!
        assertNotNull(arms.score); assertTrue(arms.oneSideOnly)
        assertTrue(r.metric("arms_extended")!!.oneSideOnly)
        assertTrue(r.available); assertEquals(1f, r.coverage, 1e-4f)
        assertTrue(r.issues.none { it.metricId.startsWith("arms") })
    }

    @Test fun oneLegOccludedKeepsTheScoreButNotOnHiddenData() {
        val r = assess(PoseFixtures.tadasana().occlude(L.RIGHT_KNEE), Asana.TADASANA)
        assertTrue(r.metric("legs_straight")!!.oneSideOnly)
        assertTrue(r.available)
        // Warrior II front knee hidden: the knee is not scored, not corrected, and confidence drops.
        val w = assess(AssessmentFixtures.warriorShallow().occlude(L.LEFT_KNEE), Asana.WARRIOR_II, BodySide.LEFT)
        assertEquals(UnavailableReason.OCCLUDED, w.metric("front_knee")!!.reason)
        assertTrue(w.issues.none { it.metricId == "front_knee" })
        assertTrue(w.missingCritical.any { it.id == "front_knee" })
        assertTrue(w.confidence.level != ConfidenceLevel.HIGH)
    }

    @Test fun multipleMissingLandmarksWithholdTheScore() {
        val r = assess(PoseFixtures.tree(BodySide.LEFT).occlude(L.LEFT_HIP, L.RIGHT_HIP, L.LEFT_KNEE, L.RIGHT_KNEE), Asana.TREE, BodySide.LEFT)
        assertNull(r.score)
        assertEquals(ConfidenceLevel.INSUFFICIENT, r.confidence.level)
        assertTrue(r.coverage < 0.6f)
        assertTrue(r.issues.isEmpty())
    }

    @Test fun lowConfidenceLandmarksAreNeverMeasurements() {
        // 0.55 passes the old 0.5 gate but is below RELIABLE: shown as low confidence, not scored.
        val r = assess(PoseFixtures.tadasana().allVisibility(0.55f), Asana.TADASANA)
        assertNull(r.score)
        assertTrue(r.metrics.all { it.reason == UnavailableReason.LOW_CONFIDENCE })
        assertTrue(r.issues.isEmpty())
    }

    @Test fun adaptiveScoreReweightsOnlyEvaluatedMetrics() {
        val r = assess(AssessmentFixtures.warriorShallow().occlude(L.RIGHT_ELBOW, L.RIGHT_WRIST, L.LEFT_KNEE), Asana.WARRIOR_II, BodySide.LEFT)
        val ev = r.evaluated
        val expected = Math.round(100f * ev.sumOf { (it.weight * it.score!!).toDouble() }.toFloat() / ev.sumOf { it.weight.toDouble() }.toFloat())
        assertEquals(expected, r.score)
        val total = AsanaAssessmentProfiles.WARRIOR_II.rules.sumOf { it.weight.toDouble() }.toFloat()
        assertEquals(ev.sumOf { it.weight.toDouble() }.toFloat() / total, r.coverage, 1e-4f)
    }

    @Test fun aSmallSubsetCannotProduceAHighScore() {
        // Only the shoulders and wrists visible: shoulder/arm metrics are perfect, but coverage is too low.
        val s = AssessmentFixtures.warriorIdeal().occlude(L.LEFT_HIP, L.RIGHT_HIP, L.LEFT_KNEE, L.RIGHT_KNEE, L.LEFT_ANKLE, L.RIGHT_ANKLE)
        val r = assess(s, Asana.WARRIOR_II, BodySide.LEFT)
        assertTrue(r.evaluated.all { it.score!! > 0.9f })
        assertNull(r.score)
    }

    @Test fun sideViewChairUsesTheVisibleSideWhenTheFarSideIsHidden() {
        val r = assess(PoseFixtures.chair().hide(*RIGHT_SIDE), Asana.CHAIR)
        assertEquals(CameraOrientation.SIDE, r.orientation)
        assertTrue(r.available)
        assertTrue(r.metric("knee_bend")!!.oneSideOnly)
        assertNotNull(r.metric("torso_lean")!!.score) // visible side's shoulder→hip line
        assertTrue(r.score!! >= 90)
    }

    @Test fun profileViewNeverScoresLevelLinesAndAsksForTheIntendedView() {
        val r = assess(PoseFixtures.tadasana().toProfile(), Asana.TADASANA)
        assertEquals(CameraOrientation.SIDE, r.orientation)
        listOf("shoulders_level", "hips_level", "weight_centred").forEach { assertEquals(it, UnavailableReason.NOT_FROM_THIS_VIEW, r.metric(it)!!.reason) }
        assertNotNull(r.metric("torso_upright")!!.score)   // measurable in profile
        assertNull(r.score)                                  // but not enough of the pose for an overall score
        assertEquals("Face the camera so your whole front is visible", r.cameraHint)
    }

    @Test fun sideOrientedWarriorWithholdsKneeScoringAndChairFacingTheCameraAsksToTurn() {
        val w = assess(AssessmentFixtures.warriorIdeal().turnedSideways(), Asana.WARRIOR_II, BodySide.LEFT)
        assertEquals(UnavailableReason.NOT_FROM_THIS_VIEW, w.metric("front_knee")!!.reason)
        assertNull(w.score)
        assertEquals("Face the camera so your whole front is visible", w.cameraHint)
        // Chair seen from the front: knee/hip angles are foreshortened → not scored.
        val chairFront = assess(PoseFixtures.tadasana(), Asana.CHAIR)
        assertEquals(UnavailableReason.NOT_FROM_THIS_VIEW, chairFront.metric("knee_bend")!!.reason)
        assertNull(chairFront.score)
        assertEquals("Turn sideways to the camera for this pose", chairFront.cameraHint)
    }

    @Test fun cameraHintOnlyForPersistentOcclusion() {
        val tracker = ReliabilityTracker(persistMs = 2000)
        val visible = AssessmentFixtures.warriorIdeal()
        tracker.update(visible.frame(0), 0)
        fun hidden(t: Long) = AssessmentFixtures.warriorIdeal().hide(L.LEFT_KNEE).frame(t)
        val brief = assessor.assess(BodyFeatures(hidden(500)), Asana.WARRIOR_II, BodySide.LEFT, 500, tracker.update(hidden(500), 500))
        assertNull("brief dropout: no nagging", brief.cameraHint)
        val long = assessor.assess(BodyFeatures(hidden(2600)), Asana.WARRIOR_II, BodySide.LEFT, 2600, tracker.update(hidden(2600), 2600))
        assertEquals("Turn slightly so the camera can see your left knee", long.cameraHint)
    }
}

class OcclusionCoachingTest {
    @Test fun noCorrectionForAHiddenLimbAndNoImprovementWithoutMeasurement() {
        val c = AssessmentCoach(persistMs = 0, improvePersistMs = 0)
        val shallow = assess(AssessmentFixtures.warriorShallow(), Asana.WARRIOR_II, BodySide.LEFT)
        val first = c.update(shallow, StabilityResult.NONE, true, 0)
        assertTrue(first.any { it is CoachEvent.AlignmentCorrectionRequired })
        // The knee becomes hidden: no repeat of the knee correction and no claimed improvement.
        val hidden = assess(AssessmentFixtures.warriorShallow().occlude(L.LEFT_KNEE), Asana.WARRIOR_II, BodySide.LEFT)
        var events = emptyList<CoachEvent>()
        for (t in 1_000L..40_000L step 1_000L) events = events + c.update(hidden, StabilityResult.NONE, true, t)
        assertTrue(events.none { it is CoachEvent.AlignmentCorrectionRequired && it.correction.id.contains("front_knee") })
        assertTrue(events.none { it is CoachEvent.AlignmentImproved })
        assertNull(c.currentCorrection(hidden, true)?.takeIf { it.contains("knee") })
    }

    @Test fun cameraAdjustmentIsRateLimited() {
        val c = AssessmentCoach(cameraHintRepeatMs = 20_000)
        val r = assess(PoseFixtures.tadasana(), Asana.CHAIR) // facing the camera in Chair → hint
        val events = (0L..30_000L step 500L).flatMap { c.update(r, StabilityResult.NONE, true, it) }
        assertEquals(2, events.count { it is CoachEvent.CameraAdjustmentNeeded }) // t=0 and t=20 s only
    }

    @Test fun occlusionAwareFramingOnlyForProfileViewsWithEverythingInFrame() {
        val profile = PoseFixtures.tadasana().toProfile().frame()
        val states = LandmarkReliability.classify(profile)
        assertTrue(OcclusionAwareFraming.accepted(FramingStatus.SHOW_FEET, CameraOrientation.SIDE, states))
        assertFalse(OcclusionAwareFraming.accepted(FramingStatus.SHOW_FEET, CameraOrientation.FRONT, states)) // front: keep guidance
        val cutOff = PoseFixtures.tadasana().toProfile().transform(1f, 0f, 120f).frame()               // feet below the image
        assertFalse(OcclusionAwareFraming.accepted(FramingStatus.SHOW_FEET, CameraOrientation.SIDE, LandmarkReliability.classify(cutOff)))
        assertFalse(OcclusionAwareFraming.accepted(FramingStatus.TOO_CLOSE, CameraOrientation.SIDE, states))
    }
}

class OcclusionHoldTest {
    private val rec = Recognition(Asana.WARRIOR_II, 0.9f, true, BodySide.LEFT)
    private val dt = 150L
    private val HIDDEN = intArrayOf(L.LEFT_KNEE, L.RIGHT_KNEE, L.LEFT_ANKLE, L.RIGHT_ANKLE, L.LEFT_WRIST, L.RIGHT_WRIST)

    private fun run(p: AssessmentPipeline, from: Long, to: Long, frame: (Long) -> PoseFrame): AssessmentPipeline.Output {
        var t = from; var out: AssessmentPipeline.Output? = null
        while (t <= to) { out = p.process(frame(t), rec, FramingStatus.FULL_BODY, t); t += dt }
        return out!!
    }

    @Test fun briefOcclusionPausesButKeepsTheHold() {
        val p = AssessmentPipeline()
        val before = run(p, 0, 6_000) { AssessmentFixtures.warriorIdeal().frame(it) }
        assertTrue(before.live!!.hold.counting)
        val heldBefore = before.live!!.hold.currentHoldMs
        // ~1 s with legs and hands hidden (coverage < 60 %, score withheld): the hold pauses…
        val during = run(p, 6_150, 7_050) { AssessmentFixtures.warriorIdeal().hide(*HIDDEN).frame(it) }
        assertNull(during.live!!.alignment!!.score)
        assertFalse(during.live!!.hold.counting)
        assertTrue(during.live!!.hold.currentHoldMs >= heldBefore)  // not reset, occluded time not added
        // …and continues afterwards in the same segment.
        val after = run(p, 7_200, 9_000) { AssessmentFixtures.warriorIdeal().frame(it) }
        assertTrue(after.live!!.hold.counting)
        assertTrue("hold ${after.live!!.hold.currentHoldMs} vs $heldBefore", after.live!!.hold.currentHoldMs > heldBefore)
    }

    @Test fun longOcclusionEndsTheHoldSegment() {
        val p = AssessmentPipeline()
        val before = run(p, 0, 6_000) { AssessmentFixtures.warriorIdeal().frame(it) }
        run(p, 6_150, 9_000) { AssessmentFixtures.warriorIdeal().hide(*HIDDEN).frame(it) }
        val after = run(p, 9_150, 9_600) { AssessmentFixtures.warriorIdeal().frame(it) }
        assertTrue(after.live!!.hold.currentHoldMs < before.live!!.hold.currentHoldMs) // new segment
    }
}
