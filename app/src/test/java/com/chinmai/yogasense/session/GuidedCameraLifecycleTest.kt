package com.chinmai.yogasense.session

import com.chinmai.yogasense.asana.Asana
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which stages run the live camera, followed over a whole guided session, plus the stale-tap guard.
 * The screen composes CameraPreview exactly when [GuidedState.needsCamera] is true.
 */
class GuidedCameraLifecycleTest {
    private val cfg = GuidedConfig()
    private val m = GuidedSessionMachine(listOf(Asana.TADASANA, Asana.TREE), cfg)
    private var now = 0L
    private fun tick(ms: Long) { var l = ms; while (l > 0) { now += 100; l -= 100; m.onEvent(GuidedEvent.Tick(now)) } }
    private fun holdCurrent() {
        val a = m.state.current
        var guard = 0
        while ((m.state.stage == GuidedStage.WAITING_FOR_POSE || m.state.stage == GuidedStage.HOLDING) && guard++ < 1000) {
            now += 150; m.onEvent(GuidedEvent.Tick(now)); m.onEvent(GuidedEvent.Frame(now, a, true, a))
        }
    }

    @Test fun eachStageDecidesWhetherTheCameraRuns() {
        val expected = mapOf(
            GuidedStage.INTRO to false, GuidedStage.DEMONSTRATION to false, GuidedStage.PREPARATION_COUNTDOWN to true,
            GuidedStage.WAITING_FOR_POSE to true, GuidedStage.HOLDING to true, GuidedStage.DETECTION_TIMEOUT to true,
            GuidedStage.RECOVERY to false, GuidedStage.SESSION_COMPLETED to false,
        )
        assertEquals(GuidedStage.entries.toSet(), expected.keys)
        expected.forEach { (stage, on) -> assertEquals(stage.name, on, GuidedState(listOf(Asana.TREE), stage = stage).needsCamera) }
    }

    @Test fun cameraIsOffForIntroAndDemoAndOnFromPreparationThroughTheHold() {
        m.start(now)
        assertFalse("intro", m.state.needsCamera)
        tick(cfg.introMs)
        assertEquals(GuidedStage.DEMONSTRATION, m.state.stage)
        assertFalse("no camera behind the demonstration video", m.state.needsCamera)
        m.onEvent(GuidedEvent.SkipDemo)
        assertEquals(GuidedStage.PREPARATION_COUNTDOWN, m.state.stage)
        assertTrue("camera starts during the countdown, before detection", m.state.needsCamera)
        tick(cfg.prepMs)
        assertEquals(GuidedStage.WAITING_FOR_POSE, m.state.stage); assertTrue(m.state.needsCamera)
        now += 150; m.onEvent(GuidedEvent.Frame(now, Asana.TADASANA, true, Asana.TADASANA))
        assertEquals(GuidedStage.HOLDING, m.state.stage); assertTrue(m.state.needsCamera)
        holdCurrent()
        assertEquals(GuidedStage.RECOVERY, m.state.stage)
        assertFalse("released during the rest", m.state.needsCamera)
        tick(cfg.recoveryMs)
        assertEquals(GuidedStage.DEMONSTRATION, m.state.stage); assertEquals(Asana.TREE, m.state.current)
        assertFalse(m.state.needsCamera)
    }

    @Test fun pausingKeepsTheCameraDecisionSoACameraPauseCanRecover() {
        m.start(now); tick(cfg.introMs); m.onEvent(GuidedEvent.SkipDemo); tick(cfg.prepMs)
        m.onEvent(GuidedEvent.Pause(PauseReason.CAMERA))
        assertTrue(m.state.paused); assertTrue(m.state.needsCamera)
        m.onEvent(GuidedEvent.Resume)
        assertEquals(GuidedStage.WAITING_FOR_POSE, m.state.stage)
    }

    @Test fun cameraIsOffAfterTheSessionEnds() {
        m.start(now); tick(cfg.introMs); m.onEvent(GuidedEvent.SkipDemo); tick(cfg.prepMs)
        m.onEvent(GuidedEvent.Exit)
        assertEquals(GuidedStage.SESSION_COMPLETED, m.state.stage)
        assertFalse(m.state.needsCamera)
    }

    @Test fun aDoubleTapOnSkipPoseSkipsOnlyOnePose() {
        m.start(now); tick(cfg.introMs)
        val shown = m.state.epoch // the Demonstration screen as the user saw it
        m.onEventAt(shown, GuidedEvent.SkipPose)
        assertEquals(Asana.TREE, m.state.current)
        m.onEventAt(shown, GuidedEvent.SkipPose) // second tap of the same button
        assertEquals("the next pose is not skipped", Asana.TREE, m.state.current)
        assertEquals(GuidedStage.DEMONSTRATION, m.state.stage)
        assertEquals(1, m.state.results.size)
    }

    @Test fun aDoubleTapOnSkipDemoDoesNotSkipThePose() {
        m.start(now); tick(cfg.introMs)
        val shown = m.state.epoch
        m.onEventAt(shown, GuidedEvent.SkipDemo)
        m.onEventAt(shown, GuidedEvent.SkipPose) // a stale tap from the demo screen
        assertEquals(GuidedStage.PREPARATION_COUNTDOWN, m.state.stage)
        assertEquals(Asana.TADASANA, m.state.current)
    }

    @Test fun aCurrentTapStillWorks() {
        m.start(now); tick(cfg.introMs)
        m.onEventAt(m.state.epoch, GuidedEvent.SkipDemo)
        assertEquals(GuidedStage.PREPARATION_COUNTDOWN, m.state.stage)
        m.onEventAt(m.state.epoch, GuidedEvent.SkipPose)
        assertEquals(Asana.TREE, m.state.current)
    }

    @Test fun exitIsIdempotent() {
        m.start(now); tick(cfg.introMs)
        val first = m.onEvent(GuidedEvent.Exit)
        val second = m.onEvent(GuidedEvent.Exit)
        assertEquals(1, first.filterIsInstance<GuidedEffect.Completed>().size)
        assertTrue(second.isEmpty())
    }
}
