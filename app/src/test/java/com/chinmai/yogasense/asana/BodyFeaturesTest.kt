package com.chinmai.yogasense.asana

import com.chinmai.yogasense.pose.Landmark
import com.chinmai.yogasense.pose.LandmarkSmoother
import com.chinmai.yogasense.pose.PoseFrame
import com.chinmai.yogasense.pose.PoseLandmarkIndex as L
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BodyFeaturesTest {

    @Test fun angleBasics() {
        assertEquals(90f, Geometry.angleDeg(Pt(0f, 0f), Pt(0f, 1f), Pt(1f, 1f))!!, 1e-3f)
        assertEquals(180f, Geometry.angleDeg(Pt(0f, 0f), Pt(0f, 1f), Pt(0f, 2f))!!, 1e-3f)
        assertNull(Geometry.angleDeg(Pt(1f, 1f), Pt(1f, 1f), Pt(2f, 2f)))
        assertEquals(45f, Geometry.tiltFromVerticalDeg(Pt(0f, 0f), Pt(1f, -1f)), 1e-3f)
    }

    @Test fun anglesUsePixelSpaceNotNormalisedSpace() {
        // A true 90° knee in pixels looks non-90° in raw normalised coords of a 480x640 image.
        val f = PoseFixtures.tadasana()
        f.joints[L.LEFT_KNEE] = Pt(260f, 435f)
        f.joints[L.LEFT_HIP] = Pt(260f, 335f)
        f.joints[L.LEFT_ANKLE] = Pt(360f, 435f)
        assertEquals(90f, BodyFeatures(f.frame()).knee(BodySide.LEFT)!!, 0.5f)
    }

    @Test fun leftRightAreAnatomical() {
        val feat = BodyFeatures(PoseFixtures.tree(BodySide.LEFT).frame())
        assertTrue(feat.knee(BodySide.LEFT)!! < 110f)   // lifted leg bent
        assertTrue(feat.knee(BodySide.RIGHT)!! > 170f)  // standing leg straight
        assertTrue(feat.footRaise(BodySide.LEFT)!! > 0.35f)
        assertTrue(feat.footRaise(BodySide.RIGHT)!! < 0f)
    }

    @Test fun torsoNormalisation() {
        val feat = BodyFeatures(PoseFixtures.tadasana().frame())
        assertEquals(120f, feat.torsoLength!!, 0.5f)
        assertEquals(40f / 120f, feat.stanceWidth!!, 1e-3f)
    }

    @Test fun lowConfidenceLandmarkGivesNullFeature() {
        val feat = BodyFeatures(PoseFixtures.tadasana().occlude(L.RIGHT_KNEE).frame())
        assertNull(feat.knee(BodySide.RIGHT))
        assertTrue(feat.knee(BodySide.LEFT)!! > 170f)
        assertEquals(listOf("right knee"), feat.lowConfidenceLandmarks)
    }

    @Test fun smootherFollowsStepPartiallyAndResetsOnGap() {
        val s = LandmarkSmoother(tauMs = 60f, resetGapMs = 500L)
        fun f(x: Float, ts: Long) = PoseFrame(List(L.COUNT) { Landmark(x, 0.5f, 0f, 0.9f, 0.9f) }, ts, 480, 640)
        s.smooth(f(0.2f, 0))
        val stepped = s.smooth(f(0.6f, 100)).landmarks[0].x
        assertTrue("stepped=$stepped", stepped > 0.2f && stepped < 0.6f)
        assertTrue(stepped > 0.5f) // mostly caught up after 100 ms: low lag at low FPS
        val afterGap = s.smooth(f(0.9f, 2000)).landmarks[0].x
        assertEquals(0.9f, afterGap, 1e-6f)
    }

    @Test fun smootherDoesNotSmoothConfidence() {
        val s = LandmarkSmoother()
        fun f(v: Float, ts: Long) = PoseFrame(List(L.COUNT) { Landmark(0.5f, 0.5f, 0f, v, v) }, ts, 480, 640)
        s.smooth(f(0.9f, 0))
        assertEquals(0.1f, s.smooth(f(0.1f, 100)).landmarks[0].visibility, 1e-6f)
    }
}
