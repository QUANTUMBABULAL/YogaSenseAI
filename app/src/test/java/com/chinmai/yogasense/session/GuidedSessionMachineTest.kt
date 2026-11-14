package com.chinmai.yogasense.session

import com.chinmai.yogasense.asana.Asana
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuidedSessionMachineTest {
    private val plan = listOf(Asana.TADASANA, Asana.TREE, Asana.CHAIR)
    private val cfg = GuidedConfig()

    /** Small driver: a clock plus helpers that advance time in 100 ms ticks. */
    private inner class Driver(p: List<Asana> = plan) {
        val m = GuidedSessionMachine(p, cfg) { a -> a.name.lowercase().replaceFirstChar { it.uppercase() } } // e.g. "Tree"
        var now = 0L
        val effects = mutableListOf<GuidedEffect>()
        init { effects += m.start(now) }
        fun tick(ms: Long, step: Long = 100) { var left = ms; while (left > 0) { val d = minOf(step, left); now += d; left -= d; effects += m.onEvent(GuidedEvent.Tick(now)) } }
        fun frames(ms: Long, recognised: Asana?, counting: Boolean, step: Long = 150) {
            var left = ms
            while (left > 0) { val d = minOf(step, left); now += d; left -= d
                effects += m.onEvent(GuidedEvent.Tick(now))
                effects += m.onEvent(GuidedEvent.Frame(now, recognised, counting, if (counting) recognised else null)) }
        }
        fun send(e: GuidedEvent) { effects += m.onEvent(e) }
        val stage get() = m.state.stage
        fun toWaiting() { tick(cfg.introMs); send(GuidedEvent.DemoEnded(m.state.epoch)); tick(cfg.demoMinMs); tick(cfg.prepMs) }
        /** Stable frames of the expected pose until the hold completes (stops exactly then). */
        fun completeCurrent() {
            val a = m.state.current
            var guard = 0
            while ((stage == GuidedStage.WAITING_FOR_POSE || stage == GuidedStage.HOLDING) && guard++ < 1000) frames(150, a, true)
        }
        fun spoken() = effects.filterIsInstance<GuidedEffect.Speak>().map { it.text }
    }

    @Test fun startsWithIntroThenFirstDemonstration() {
        val d = Driver()
        assertEquals(GuidedStage.INTRO, d.stage)
        d.tick(cfg.introMs)
        assertEquals(GuidedStage.DEMONSTRATION, d.stage); assertEquals(Asana.TADASANA, d.m.state.current)
    }

    @Test fun demoCompletionAdvancesToPreparationAfterMinimumTime() {
        val d = Driver(); d.tick(cfg.introMs)
        d.send(GuidedEvent.DemoEnded(d.m.state.epoch))
        assertEquals(GuidedStage.DEMONSTRATION, d.stage)          // still within the reading time
        d.tick(cfg.demoMinMs)
        assertEquals(GuidedStage.PREPARATION_COUNTDOWN, d.stage)
        // A missing/stalled video can never block: the safety limit advances anyway.
        val e = Driver(); e.tick(cfg.introMs); e.tick(cfg.demoMaxMs)
        assertEquals(GuidedStage.PREPARATION_COUNTDOWN, e.stage)
        // Stale "ended" callbacks from an old stage are ignored.
        val f = Driver(); f.tick(cfg.introMs); f.send(GuidedEvent.DemoEnded(f.m.state.epoch - 1)); f.tick(cfg.demoMinMs)
        assertEquals(GuidedStage.DEMONSTRATION, f.stage)
    }

    @Test fun countdownSpeaksAndAdvancesToDetection() {
        val d = Driver(); d.tick(cfg.introMs); d.send(GuidedEvent.SkipDemo)
        assertEquals(GuidedStage.PREPARATION_COUNTDOWN, d.stage)
        d.tick(cfg.prepMs)
        assertEquals(GuidedStage.WAITING_FOR_POSE, d.stage)
        val s = d.spoken()
        assertTrue(s.contains("Get ready. 5"))
        assertEquals(listOf("4", "3", "2", "1"), s.filter { it.length == 1 })
    }

    @Test fun wrongPoseNeverStartsTheHold() {
        val d = Driver(); d.toWaiting()
        d.frames(15_000, Asana.TREE, counting = true)               // a different pose, even with a stable hold
        assertEquals(GuidedStage.WAITING_FOR_POSE, d.stage)
        assertEquals(0L, d.m.state.holdMs)
        d.frames(3_000, Asana.TADASANA, counting = false)           // right pose, not yet stable
        assertEquals(GuidedStage.WAITING_FOR_POSE, d.stage)
    }

    @Test fun correctStablePoseStartsAndCompletesTheTenSecondHoldOnce() {
        val d = Driver(); d.toWaiting()
        d.frames(150, Asana.TADASANA, true)
        assertEquals(GuidedStage.HOLDING, d.stage)
        d.frames(5_000, Asana.TADASANA, true)
        assertEquals(GuidedStage.HOLDING, d.stage)
        assertTrue(d.m.state.holdMs in 4_800L..5_100L)              // actual frame time, not wall clock
        d.frames(5_500, Asana.TADASANA, true)
        assertEquals(GuidedStage.RECOVERY, d.stage)
        val finished = d.effects.filterIsInstance<GuidedEffect.PoseFinished>()
        assertEquals(1, finished.size)
        assertTrue(finished.single().result.completed); assertTrue(finished.single().result.stableHoldMs >= cfg.holdTargetMs)
        assertTrue(d.spoken().any { it.startsWith("Great work! You completed ") })
    }

    @Test fun losingThePosePausesTheHoldAndItResumesFromTheSameTime() {
        val d = Driver(); d.toWaiting()
        d.frames(6_000, Asana.TADASANA, true)
        val held = d.m.state.holdMs
        d.frames(1_200, Asana.TADASANA, false)                      // brief loss (< 1.5 s)
        assertEquals(GuidedStage.HOLDING, d.stage); assertEquals(held, d.m.state.holdMs)
        d.frames(1_000, Asana.TADASANA, true)
        val beforeLoss = d.m.state.holdMs
        assertTrue(beforeLoss > held)
        d.frames(4_000, null, false)                                // long loss: paused, never reset
        assertEquals(GuidedStage.WAITING_FOR_POSE, d.stage); assertEquals(beforeLoss, d.m.state.holdMs)
        assertTrue(d.spoken().any { it.contains("Your timer is paused at") })
        assertTrue(d.effects.none { it is GuidedEffect.PoseFinished })
        // Back in the pose: counting continues from where it stopped and only the remainder is needed.
        d.frames(450, Asana.TADASANA, true)
        assertEquals(GuidedStage.HOLDING, d.stage)
        assertTrue(d.m.state.holdMs in beforeLoss..beforeLoss + 450)
        assertTrue(d.spoken().any { it.contains("more second") })
        val startOfResume = d.now
        d.completeCurrent()
        assertEquals(GuidedStage.RECOVERY, d.stage)
        assertTrue(d.now - startOfResume < cfg.holdTargetMs - beforeLoss + 1_000) // far less than a full 10 s hold
    }

    @Test fun holdProgressIsClearedForTheNextPose() {
        val d = Driver(); d.toWaiting()
        d.completeCurrent()
        d.tick(cfg.recoveryMs)
        assertEquals(GuidedStage.DEMONSTRATION, d.stage); assertEquals(Asana.TREE, d.m.state.current)
        assertEquals(0L, d.m.state.holdMs)
    }

    @Test fun recoveryLastsTwentySecondsAndAdvancesToNextDemonstration() {
        val d = Driver(); d.toWaiting(); d.completeCurrent()
        assertEquals(GuidedStage.RECOVERY, d.stage)
        d.tick(cfg.recoveryMs - 200)
        assertEquals(GuidedStage.RECOVERY, d.stage)
        d.tick(300)
        assertEquals(GuidedStage.DEMONSTRATION, d.stage)
        assertEquals(Asana.TREE, d.m.state.current)                 // next pose gets its own demonstration
        val s = d.spoken()
        assertTrue(s.any { it.startsWith("Rest for 20 seconds. Next: Tree") })
        listOf("15 seconds", "10 seconds", "5 seconds", "3", "2", "1").forEach { assertTrue(it, s.contains(it)) }
    }

    @Test fun skippingAPoseIsSafeAndRecorded() {
        val d = Driver(); d.toWaiting()
        d.send(GuidedEvent.SkipPose)
        assertEquals(GuidedStage.DEMONSTRATION, d.stage); assertEquals(Asana.TREE, d.m.state.current)
        assertEquals(GuidedPoseResult(Asana.TADASANA, completed = false, skipped = true, stableHoldMs = 0), d.m.state.results.single())
        // Detection timeout → prompt → auto-skip (never stuck).
        d.send(GuidedEvent.SkipDemo); d.tick(cfg.prepMs); d.tick(cfg.detectTimeoutMs)
        assertEquals(GuidedStage.DETECTION_TIMEOUT, d.stage)
        d.send(GuidedEvent.RetryPose)
        assertEquals(GuidedStage.PREPARATION_COUNTDOWN, d.stage)
        d.tick(cfg.prepMs); d.tick(cfg.detectTimeoutMs); d.tick(cfg.timeoutPromptMs)
        assertEquals(Asana.CHAIR, d.m.state.current)
        assertTrue(d.m.state.results.last().skipped)
    }

    @Test fun pauseResumeNeverCountsPausedTime() {
        val d = Driver(); d.toWaiting(); d.frames(4_000, Asana.TADASANA, true)
        val held = d.m.state.holdMs
        d.send(GuidedEvent.Pause(PauseReason.BACKGROUND))
        d.frames(30_000, Asana.TADASANA, true)                       // frames + ticks while paused are ignored
        assertEquals(held, d.m.state.holdMs); assertEquals(GuidedStage.HOLDING, d.stage)
        d.send(GuidedEvent.Resume)
        d.frames(300, Asana.TADASANA, true)
        assertTrue(d.m.state.holdMs - held <= 300)
        // Recovery timer also stops while paused.
        val e = Driver(); e.toWaiting(); e.completeCurrent(); e.tick(5_000)
        val elapsed = e.m.state.stageElapsedMs
        e.send(GuidedEvent.Pause(PauseReason.USER)); e.tick(60_000)
        assertEquals(elapsed, e.m.state.stageElapsedMs); assertEquals(GuidedStage.RECOVERY, e.stage)
    }

    @Test fun interruptionCannotCauseDuplicateAdvancement() {
        val d = Driver(); d.toWaiting(); d.completeCurrent()
        // A huge clock jump (process frozen in background) counts as at most one tick.
        d.now += 10 * 60_000; d.send(GuidedEvent.Tick(d.now))
        assertEquals(GuidedStage.RECOVERY, d.stage)
        // Repeated and late events after the pose finished change nothing.
        repeat(5) { d.send(GuidedEvent.Frame(d.now, Asana.TADASANA, true, Asana.TADASANA)) }
        d.send(GuidedEvent.SkipDemo); d.send(GuidedEvent.Resume)
        assertEquals(1, d.effects.count { it is GuidedEffect.PoseFinished })
        assertEquals(GuidedStage.RECOVERY, d.stage)
    }

    @Test fun finalPoseCompletesTheSessionExactlyOnceWithoutRecovery() {
        val d = Driver(listOf(Asana.TADASANA, Asana.TREE))
        d.toWaiting(); d.completeCurrent(); d.tick(cfg.recoveryMs)
        d.send(GuidedEvent.DemoEnded(d.m.state.epoch)); d.tick(cfg.demoMinMs); d.tick(cfg.prepMs)
        d.completeCurrent()
        assertEquals(GuidedStage.SESSION_COMPLETED, d.stage)          // no recovery after the last pose
        d.send(GuidedEvent.Exit); d.tick(5_000); d.send(GuidedEvent.SkipPose)
        val completed = d.effects.filterIsInstance<GuidedEffect.Completed>()
        assertEquals(1, completed.size)
        assertFalse(completed.single().endedEarly)
        assertEquals(listOf(Asana.TADASANA, Asana.TREE), completed.single().results.filter { it.completed }.map { it.asana })
    }

    @Test fun summaryReceivesCorrectResultsIncludingEarlyExit() {
        val d = Driver(); d.toWaiting(); d.completeCurrent(); d.tick(cfg.recoveryMs)
        d.send(GuidedEvent.SkipPose)                                  // Tree skipped during its demo
        d.send(GuidedEvent.Exit)                                      // leave during Chair's demo
        val c = d.effects.filterIsInstance<GuidedEffect.Completed>().single()
        assertTrue(c.endedEarly)
        assertEquals(listOf(true, false), c.results.map { it.completed })
        assertEquals(listOf(Asana.TADASANA, Asana.TREE), c.results.map { it.asana })
        assertTrue(c.results[0].stableHoldMs >= 10_000); assertTrue(c.results[1].skipped)
    }
}
