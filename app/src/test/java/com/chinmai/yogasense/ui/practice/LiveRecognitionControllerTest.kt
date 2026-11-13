package com.chinmai.yogasense.ui.practice

import com.chinmai.yogasense.asana.Asana
import com.chinmai.yogasense.asana.BodySide
import com.chinmai.yogasense.asana.PoseFixtures
import com.chinmai.yogasense.asana.Recognition
import com.chinmai.yogasense.pose.PoseFrame
import com.chinmai.yogasense.ui.practice.RecognitionPresenter.Headline
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveRecognitionControllerTest {
    private val dt = 150L

    @Test fun nullFramesAreIgnored() {
        val c = LiveRecognitionController()
        c.onFrame(null)
        assertEquals(0L, c.state.value.framesProcessed)
    }

    @Test fun outOfOrderAndDuplicateFramesAreDropped() {
        val c = LiveRecognitionController()
        c.onFrame(PoseFixtures.tadasana().frame(1000))
        c.onFrame(PoseFixtures.tadasana().frame(1000))
        c.onFrame(PoseFixtures.tadasana().frame(900))
        assertEquals(1L, c.state.value.framesProcessed)
    }

    @Test fun neverConfirmsBeforeTemporalWindowThenConfirms() {
        val c = LiveRecognitionController()
        val logs = mutableListOf<String>()
        val logging = LiveRecognitionController(log = { logs += it }, clockMs = { 0L })
        for (i in 0 until 3) {
            c.onFrame(PoseFixtures.tree(BodySide.LEFT).frame(i * dt))
            logging.onFrame(PoseFixtures.tree(BodySide.LEFT).frame(i * dt))
            val h = RecognitionPresenter.headline(c.state.value.recognition)
            assertFalse("frame $i must not be confirmed", h is Headline.Confirmed)
        }
        for (i in 3 until 8) {
            c.onFrame(PoseFixtures.tree(BodySide.LEFT).frame(i * dt))
            logging.onFrame(PoseFixtures.tree(BodySide.LEFT).frame(i * dt))
        }
        val h = RecognitionPresenter.headline(c.state.value.recognition)
        assertEquals(Headline.Confirmed(Asana.TREE.displayName, "Left foot lifted"), h)
        assertTrue(logs.any { it.startsWith("CONFIRMED TREE LEFT") })
        assertTrue(c.state.value.recognitionMs >= 0f)
    }

    @Test fun missingLandmarksAndEmptyFramesAreSafe() {
        val c = LiveRecognitionController()
        c.onFrame(PoseFrame(emptyList(), 10, 480, 640))
        c.onFrame(PoseFixtures.tadasana().occlude(25, 26, 27, 28).frame(200))
        val r = c.state.value.recognition
        assertEquals(Asana.UNKNOWN, r.asana)
        assertEquals("Keep your knees and ankles in view",
            RecognitionPresenter.landmarkHint(r.frame!!.lowConfidenceLandmarks))
    }

    @Test fun resetClearsSessionState() {
        val c = LiveRecognitionController()
        repeat(8) { c.onFrame(PoseFixtures.tadasana().frame(it * dt)) }
        assertTrue(c.state.value.recognition.isStable)
        c.reset()
        assertFalse(c.state.value.recognition.isStable)
        // Earlier timestamps are accepted again after reset (new session).
        c.onFrame(PoseFixtures.tadasana().frame(0))
        assertEquals(1L, c.state.value.framesProcessed)
    }

    @Test fun runCollectsFlowInOrder() = runBlocking {
        val c = LiveRecognitionController()
        val frames = (0 until 8).map { PoseFixtures.warrior2(BodySide.RIGHT).frame(it * dt) }
        c.run(flowOf(null, *frames.toTypedArray()))
        val r = c.state.value.recognition
        assertEquals(8L, c.state.value.framesProcessed)
        assertEquals(Asana.WARRIOR_II, r.asana)
        assertEquals(BodySide.RIGHT, r.side)
    }

    @Test fun presenterNeverConfirmsUnstable() {
        val unstable = Recognition(Asana.UNKNOWN, 0f, false, pending = Asana.CHAIR, pendingProgress = 0.5f)
        assertEquals(Headline.Pending(Asana.CHAIR.displayName, 0.5f), RecognitionPresenter.headline(unstable))
        assertTrue(RecognitionPresenter.headline(Recognition.NONE) is Headline.NotRecognised)
        assertNull(RecognitionPresenter.sideLabel(Asana.TADASANA, BodySide.NONE))
        assertEquals("Right leg forward", RecognitionPresenter.sideLabel(Asana.TRIANGLE, BodySide.RIGHT))
        assertNull(RecognitionPresenter.landmarkHint(emptyList()))
    }
}
