package com.chinmai.yogasense.pose

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import com.chinmai.yogasense.pose.PoseLandmarkIndex as L

class FramingGuidanceTest {

    /** Synthetic upright standing figure spanning y = 0.15..0.85, centred at x = 0.5. */
    private fun standing(): MutableList<Landmark> {
        val pts = MutableList(L.COUNT) { Landmark(0.5f, 0.5f, 0f, 0.9f, 0.9f) }
        fun set(i: Int, x: Float, y: Float) { pts[i] = Landmark(x, y, 0f, 0.95f, 0.95f) }
        set(L.NOSE, 0.5f, 0.15f)
        set(L.LEFT_SHOULDER, 0.56f, 0.28f); set(L.RIGHT_SHOULDER, 0.44f, 0.28f)
        set(L.LEFT_HIP, 0.54f, 0.52f); set(L.RIGHT_HIP, 0.46f, 0.52f)
        set(L.LEFT_KNEE, 0.54f, 0.68f); set(L.RIGHT_KNEE, 0.46f, 0.68f)
        set(L.LEFT_ANKLE, 0.54f, 0.85f); set(L.RIGHT_ANKLE, 0.46f, 0.85f)
        return pts
    }

    private fun frame(lm: List<Landmark>) = PoseFrame(lm, 1L, 480, 640)

    @Test fun nullOrEmptyFrameIsNoBody() {
        assertEquals(FramingStatus.NO_BODY, FramingGuidance.evaluate(null))
        assertEquals(FramingStatus.NO_BODY, FramingGuidance.evaluate(frame(emptyList())))
        assertFalse(frame(emptyList()).hasPose)
    }

    @Test fun fullStandingBodyIsDetected() {
        val f = frame(standing())
        assertTrue(f.hasPose)
        assertEquals(FramingStatus.FULL_BODY, FramingGuidance.evaluate(f))
        assertEquals(33, FramingGuidance.reliableCount(f))
    }

    @Test fun missingAnklesAsksToShowFeet() {
        val lm = standing()
        lm[L.LEFT_ANKLE] = lm[L.LEFT_ANKLE].copy(visibility = 0.1f)
        assertEquals(FramingStatus.SHOW_FEET, FramingGuidance.evaluate(frame(lm)))
    }

    @Test fun headOutOfFrameAsksToShowHead() {
        val lm = standing()
        lm[L.NOSE] = lm[L.NOSE].copy(y = -0.05f)
        assertEquals(FramingStatus.SHOW_HEAD, FramingGuidance.evaluate(frame(lm)))
    }

    @Test fun smallFigureAsksToMoveCloser() {
        val lm = standing().map { it.copy(y = 0.4f + (it.y - 0.5f) * 0.3f) }
        assertEquals(FramingStatus.TOO_FAR, FramingGuidance.evaluate(frame(lm)))
    }

    @Test fun connectionsReferenceValidIndices() {
        assertTrue(L.CONNECTIONS.all { (a, b) -> a in 0 until L.COUNT && b in 0 until L.COUNT })
    }
}
