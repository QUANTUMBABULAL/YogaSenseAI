package com.chinmai.yogasense.session

import com.chinmai.yogasense.asana.Asana

/** All guided-session timings in one place. */
data class GuidedConfig(
    val introMs: Long = 5_000,
    /** A demonstration is shown at least this long (time to read), even if the media ends sooner. */
    val demoMinMs: Long = 8_000,
    /** Safety limit so a demo can never block the session (e.g. a long or stalled video). */
    val demoMaxMs: Long = 90_000,
    val prepMs: Long = 5_000,
    val holdTargetMs: Long = 10_000,
    /** Pose lost longer than this resets the hold (same 1.5 s dropout rule as HoldTracker). */
    val lossResetMs: Long = 1_500,
    /** A single frame gap above this is not counted as hold time (e.g. after a stall). */
    val maxFrameGapMs: Long = 500,
    val detectTimeoutMs: Long = 45_000,
    /** "Couldn't detect" prompt: auto-skips after this, so the session stays hands-free. */
    val timeoutPromptMs: Long = 15_000,
    val recoveryMs: Long = 20_000,
    /** A clock jump above this (app stalled) is not counted as stage time. */
    val maxTickMs: Long = 1_000,
)

enum class GuidedStage { INTRO, DEMONSTRATION, PREPARATION_COUNTDOWN, WAITING_FOR_POSE, HOLDING, DETECTION_TIMEOUT, RECOVERY, SESSION_COMPLETED }

enum class PauseReason { USER, BACKGROUND, CAMERA }

data class GuidedPoseResult(val asana: Asana, val completed: Boolean, val skipped: Boolean, val stableHoldMs: Long)

data class GuidedState(
    val plan: List<Asana>,
    val stage: GuidedStage = GuidedStage.INTRO,
    val index: Int = 0,
    val stageElapsedMs: Long = 0,
    /** Stable hold of the CURRENT pose (only frames where the expected pose's hold is counting). */
    val holdMs: Long = 0,
    val demoEnded: Boolean = false,
    val pausedBy: PauseReason? = null,
    val results: List<GuidedPoseResult> = emptyList(),
    val endedEarly: Boolean = false,
    /** Incremented on every stage change; UI callbacks carry it so stale ones are ignored. */
    val epoch: Int = 0,
    val startedAtMs: Long? = null,
    val completedAtMs: Long? = null,
) {
    val current: Asana? get() = plan.getOrNull(index)
    val next: Asana? get() = plan.getOrNull(index + 1)
    val paused: Boolean get() = pausedBy != null
    val isLast: Boolean get() = index >= plan.lastIndex
}

sealed interface GuidedEvent {
    data class Tick(val nowMs: Long) : GuidedEvent
    /** One analysed camera frame: what is recognised, and whether the stable hold is counting for it. */
    data class Frame(val nowMs: Long, val recognised: Asana?, val holdCounting: Boolean, val holdAsana: Asana?) : GuidedEvent
    data class DemoEnded(val epoch: Int) : GuidedEvent
    data object SkipDemo : GuidedEvent
    data object SkipPose : GuidedEvent
    data object RetryPose : GuidedEvent
    data class Pause(val reason: PauseReason) : GuidedEvent
    data object Resume : GuidedEvent
    data object Exit : GuidedEvent
}

sealed interface GuidedEffect {
    /** [interrupt] = cancel whatever is being said (stage changes). */
    data class Speak(val key: String, val text: String, val interrupt: Boolean) : GuidedEffect
    data class PoseFinished(val result: GuidedPoseResult) : GuidedEffect
    /** Emitted exactly once per session. */
    data class Completed(val results: List<GuidedPoseResult>, val endedEarly: Boolean) : GuidedEffect
}

/**
 * The single authoritative state holder for a hands-free guided session. Pure and deterministic:
 * time only advances through [GuidedEvent.Tick] deltas (paused time and clock jumps are not counted),
 * so timers can't fire twice and nothing runs on stale coroutines.
 *
 * INTRO → (per pose) DEMONSTRATION → PREPARATION_COUNTDOWN → WAITING_FOR_POSE → HOLDING →
 * RECOVERY (not after the last pose) → … → SESSION_COMPLETED. WAITING_FOR_POSE times out into a
 * retry/skip prompt that auto-skips, so the session never gets stuck.
 */
class GuidedSessionMachine(plan: List<Asana>, val config: GuidedConfig = GuidedConfig(), private val name: (Asana) -> String = { it.displayName }) {
    init { require(plan.isNotEmpty()) { "A guided session needs at least one asana" } }

    var state = GuidedState(plan)
        private set
    private var lastTickMs: Long? = null
    private var lastHoldFrameMs: Long? = null
    private var lostSinceMs: Long? = null
    private var completedEmitted = false

    fun remainingMs(): Long? = when (state.stage) {
        GuidedStage.INTRO -> config.introMs - state.stageElapsedMs
        GuidedStage.PREPARATION_COUNTDOWN -> config.prepMs - state.stageElapsedMs
        GuidedStage.RECOVERY -> config.recoveryMs - state.stageElapsedMs
        GuidedStage.DETECTION_TIMEOUT -> config.timeoutPromptMs - state.stageElapsedMs
        else -> null
    }?.coerceAtLeast(0)

    /** Starts the session (INTRO). */
    fun start(nowMs: Long): List<GuidedEffect> {
        if (state.startedAtMs != null) return emptyList()
        lastTickMs = nowMs
        state = state.copy(startedAtMs = nowMs)
        return listOf(GuidedEffect.Speak("intro", "Welcome. ${state.plan.size} poses today. Place your phone so your whole body is visible, then follow along.", true))
    }

    fun onEvent(e: GuidedEvent): List<GuidedEffect> {
        val s = state
        if (s.stage == GuidedStage.SESSION_COMPLETED || s.startedAtMs == null) return emptyList()
        return when (e) {
            is GuidedEvent.Tick -> tick(e.nowMs)
            is GuidedEvent.Frame -> if (s.paused) emptyList() else frame(e)
            is GuidedEvent.DemoEnded -> { if (e.epoch == s.epoch && s.stage == GuidedStage.DEMONSTRATION) state = s.copy(demoEnded = true); maybeEndDemo() }
            GuidedEvent.SkipDemo -> if (s.stage == GuidedStage.DEMONSTRATION && !s.paused) enter(GuidedStage.PREPARATION_COUNTDOWN) else emptyList()
            GuidedEvent.SkipPose -> if (s.stage in POSE_STAGES && !s.paused) finishPose(completed = false) else emptyList()
            GuidedEvent.RetryPose -> if (s.stage == GuidedStage.DETECTION_TIMEOUT && !s.paused) enter(GuidedStage.PREPARATION_COUNTDOWN) else emptyList()
            is GuidedEvent.Pause -> if (s.paused) emptyList() else {
                state = s.copy(pausedBy = e.reason)
                val text = when (e.reason) {
                    PauseReason.USER -> "Paused."
                    PauseReason.BACKGROUND -> "Session paused."
                    PauseReason.CAMERA -> "The camera stopped, so the session is paused."
                }
                listOf(GuidedEffect.Speak("pause", text, true))
            }
            GuidedEvent.Resume -> if (!s.paused) emptyList() else {
                // The paused interval is never counted: the next tick starts from now.
                state = s.copy(pausedBy = null); lastTickMs = null; lastHoldFrameMs = null; lostSinceMs = null
                listOf(GuidedEffect.Speak("resume", "Resuming.", true))
            }
            GuidedEvent.Exit -> complete(endedEarly = true)
        }
    }

    private fun tick(now: Long): List<GuidedEffect> {
        val last = lastTickMs
        lastTickMs = now
        if (state.paused || last == null) return emptyList()
        val dt = (now - last).coerceIn(0, config.maxTickMs)
        val before = state.stageElapsedMs
        state = state.copy(stageElapsedMs = before + dt)
        val after = state.stageElapsedMs
        val out = mutableListOf<GuidedEffect>()
        out += countdownSpeech(before, after)
        when (state.stage) {
            GuidedStage.INTRO -> if (after >= config.introMs) out += enter(GuidedStage.DEMONSTRATION)
            GuidedStage.DEMONSTRATION -> out += maybeEndDemo()
            GuidedStage.PREPARATION_COUNTDOWN -> if (after >= config.prepMs) out += enter(GuidedStage.WAITING_FOR_POSE)
            GuidedStage.WAITING_FOR_POSE -> if (after >= config.detectTimeoutMs) out += enter(GuidedStage.DETECTION_TIMEOUT)
            GuidedStage.HOLDING -> {
                val lost = lostSinceMs
                if (lost != null && now - lost > config.lossResetMs) out += loseHold()
            }
            GuidedStage.DETECTION_TIMEOUT -> if (after >= config.timeoutPromptMs) out += finishPose(completed = false)
            GuidedStage.RECOVERY -> if (after >= config.recoveryMs) out += advance()
            GuidedStage.SESSION_COMPLETED -> Unit
        }
        return out
    }

    private fun frame(f: GuidedEvent.Frame): List<GuidedEffect> {
        val s = state
        val expected = s.current ?: return emptyList()
        // Only the EXPECTED pose's stable hold counts; any other recognised pose is ignored.
        val counting = f.holdCounting && f.holdAsana == expected && f.recognised == expected
        return when (s.stage) {
            GuidedStage.WAITING_FOR_POSE -> if (counting) {
                lastHoldFrameMs = f.nowMs; lostSinceMs = null
                enter(GuidedStage.HOLDING)
            } else emptyList()
            GuidedStage.HOLDING -> {
                if (counting) {
                    val last = lastHoldFrameMs
                    val dt = if (last == null) 0 else (f.nowMs - last).let { if (it in 1..config.maxFrameGapMs) it else 0 }
                    lastHoldFrameMs = f.nowMs; lostSinceMs = null
                    state = s.copy(holdMs = s.holdMs + dt)
                    if (state.holdMs >= config.holdTargetMs) finishPose(completed = true) else emptyList()
                } else {
                    lastHoldFrameMs = null // the pause is never counted
                    if (lostSinceMs == null) lostSinceMs = f.nowMs
                    if (f.nowMs - lostSinceMs!! > config.lossResetMs) loseHold() else emptyList()
                }
            }
            else -> emptyList()
        }
    }

    private fun loseHold(): List<GuidedEffect> {
        lostSinceMs = null; lastHoldFrameMs = null
        state = state.copy(stage = GuidedStage.WAITING_FOR_POSE, holdMs = 0, epoch = state.epoch + 1) // keeps the detection timer running
        return listOf(GuidedEffect.Speak("lost", "Pose lost. Move back into ${name(state.current!!)}.", true))
    }

    private fun maybeEndDemo(): List<GuidedEffect> {
        val s = state
        if (s.stage != GuidedStage.DEMONSTRATION || s.paused) return emptyList()
        val done = (s.demoEnded && s.stageElapsedMs >= config.demoMinMs) || s.stageElapsedMs >= config.demoMaxMs
        return if (done) enter(GuidedStage.PREPARATION_COUNTDOWN) else emptyList()
    }

    private fun finishPose(completed: Boolean): List<GuidedEffect> {
        val s = state
        val a = s.current ?: return emptyList()
        val result = GuidedPoseResult(a, completed, skipped = !completed, stableHoldMs = s.holdMs)
        state = s.copy(results = s.results + result)
        val out = mutableListOf<GuidedEffect>(GuidedEffect.PoseFinished(result))
        out += if (completed) listOf(GuidedEffect.Speak("done:$a", "Great work! You completed ${name(a)}.", true))
        else listOf(GuidedEffect.Speak("skip:$a", "Skipping ${name(a)}.", true))
        out += when {
            state.isLast -> complete(endedEarly = false)
            completed -> enter(GuidedStage.RECOVERY, speakEntry = false)
            else -> advance()
        }
        return out
    }

    private fun advance(): List<GuidedEffect> {
        if (state.isLast) return complete(endedEarly = false)
        state = state.copy(index = state.index + 1)
        return enter(GuidedStage.DEMONSTRATION)
    }

    private fun complete(endedEarly: Boolean): List<GuidedEffect> {
        if (completedEmitted) return emptyList()
        completedEmitted = true
        state = state.copy(stage = GuidedStage.SESSION_COMPLETED, endedEarly = endedEarly, completedAtMs = lastTickMs, epoch = state.epoch + 1, pausedBy = null)
        val done = state.results.count { it.completed }
        return listOf(
            GuidedEffect.Speak("complete", if (endedEarly) "Session ended. Here is your summary." else "Session complete. You completed $done of ${state.plan.size} poses.", true),
            GuidedEffect.Completed(state.results, endedEarly),
        )
    }

    private fun enter(stage: GuidedStage, speakEntry: Boolean = true): List<GuidedEffect> {
        lostSinceMs = null; lastHoldFrameMs = null
        state = state.copy(stage = stage, stageElapsedMs = 0, demoEnded = false, epoch = state.epoch + 1,
            holdMs = if (stage == GuidedStage.RECOVERY) state.holdMs else 0) // recovery shows the hold just achieved
        if (!speakEntry) return emptyList()
        val a = state.current?.let(name).orEmpty()
        val n = state.index + 1; val total = state.plan.size
        val text = when (stage) {
            GuidedStage.DEMONSTRATION -> "Pose $n of $total: $a. Watch the demonstration."
            GuidedStage.PREPARATION_COUNTDOWN -> "Get ready. 5"
            GuidedStage.WAITING_FOR_POSE -> "Move into $a."
            GuidedStage.HOLDING -> "Pose detected. Hold for ${config.holdTargetMs / 1000} seconds."
            GuidedStage.DETECTION_TIMEOUT -> "I couldn't detect $a. Try again, or it will be skipped."
            else -> return emptyList()
        }
        return listOf(GuidedEffect.Speak("enter:$stage:${state.epoch}", text, true))
    }

    /** Spoken numbers when crossing whole seconds: prep 4…1, recovery 20,15,10,5,3,2,1. */
    private fun countdownSpeech(before: Long, after: Long): List<GuidedEffect> {
        val total = when (state.stage) {
            GuidedStage.PREPARATION_COUNTDOWN -> config.prepMs
            GuidedStage.RECOVERY -> config.recoveryMs
            else -> return emptyList()
        }
        val out = mutableListOf<GuidedEffect>()
        for (sec in listOf(20, 15, 10, 5, 4, 3, 2, 1)) {
            val at = total - sec * 1000L
            if (at < 0 || at <= before || at > after) continue
            if (state.stage == GuidedStage.PREPARATION_COUNTDOWN && sec == 5) continue // said with "Get ready"
            if (state.stage == GuidedStage.RECOVERY && sec == 4) continue
            val text = if (state.stage == GuidedStage.RECOVERY && sec >= 5) "$sec seconds" else "$sec"
            out += GuidedEffect.Speak("count:${state.stage}:$sec:${state.epoch}", text, true)
        }
        // Recovery opens with the rest announcement (the first tick of the stage).
        if (state.stage == GuidedStage.RECOVERY && before == 0L && after > 0) {
            out.add(0, GuidedEffect.Speak("rest:${state.epoch}", "Rest for ${config.recoveryMs / 1000} seconds. Next: ${state.next?.let(name).orEmpty()}.", false))
        }
        return out
    }

    private companion object {
        val POSE_STAGES = setOf(GuidedStage.DEMONSTRATION, GuidedStage.PREPARATION_COUNTDOWN, GuidedStage.WAITING_FOR_POSE, GuidedStage.HOLDING, GuidedStage.DETECTION_TIMEOUT)
    }
}
