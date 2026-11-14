package com.chinmai.yogasense.ui.practice

import android.content.Context
import com.chinmai.yogasense.asana.Asana
import com.chinmai.yogasense.asana.BodyFeatures
import com.chinmai.yogasense.asana.BodySide
import com.chinmai.yogasense.asana.Recognition
import com.chinmai.yogasense.coaching.CoachEvent
import com.chinmai.yogasense.coaching.CoachSettings
import com.chinmai.yogasense.coaching.CoachSettingsStore
import com.chinmai.yogasense.coaching.CoachingDecisionEngine
import com.chinmai.yogasense.coaching.CoachingEventSource
import com.chinmai.yogasense.coaching.Cue
import com.chinmai.yogasense.coaching.PracticeSnapshot
import com.chinmai.yogasense.coaching.VisualSettings
import com.chinmai.yogasense.coaching.VoiceCoachManager
import com.chinmai.yogasense.coaching.VoiceMode
import com.chinmai.yogasense.coaching.assessment.AsanaAlignmentAssessor
import com.chinmai.yogasense.coaching.assessment.AssessmentCoach
import com.chinmai.yogasense.coaching.assessment.HoldTracker
import com.chinmai.yogasense.coaching.assessment.LiveAssessment
import com.chinmai.yogasense.coaching.assessment.SessionStats
import com.chinmai.yogasense.coaching.assessment.SessionSummary
import com.chinmai.yogasense.coaching.assessment.StabilityAnalyzer
import com.chinmai.yogasense.pose.FramingGuidance
import com.chinmai.yogasense.pose.FramingStatus
import com.chinmai.yogasense.pose.LandmarkSmoother
import com.chinmai.yogasense.pose.PoseFrame
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Session clock that excludes paused time. Pure Kotlin. */
class SessionClock(startMs: Long) {
    private var accumulated = 0L
    private var runningSince: Long? = startMs

    val isRunning: Boolean get() = runningSince != null
    fun pause(nowMs: Long) { runningSince?.let { accumulated += nowMs - it }; runningSince = null }
    fun resume(nowMs: Long) { if (runningSince == null) runningSince = nowMs }
    fun elapsedMs(nowMs: Long): Long = accumulated + (runningSince?.let { nowMs - it } ?: 0L)
}

data class PracticeUiState(
    val paused: Boolean = false,
    val elapsedMs: Long = 0,
    val live: LiveAssessment? = null,
    val lastCue: Cue? = null,
    val lastCueAtMs: Long = 0,
    val summary: SessionSummary? = null,
)

/**
 * Pure (Android-free) per-frame assessment pipeline, separated from [PracticeSession] so it can
 * be unit-tested: confirmed recognition → smoothed features → alignment → stability → stable hold
 * → assessment coaching events.
 */
class AssessmentPipeline(
    private val assessor: AsanaAlignmentAssessor = AsanaAlignmentAssessor(),
    private val stability: StabilityAnalyzer = StabilityAnalyzer(),
    val hold: HoldTracker = HoldTracker(),
    private val coach: AssessmentCoach = AssessmentCoach(),
    val stats: SessionStats = SessionStats(),
) {
    private val smoother = LandmarkSmoother()
    private var assessedPose: Pair<Asana, BodySide>? = null
    private var lastMilestone = 0

    data class Output(val live: LiveAssessment?, val events: List<CoachEvent>)

    fun process(frame: PoseFrame?, recognition: Recognition, framing: FramingStatus, nowMs: Long): Output {
        val pose = if (recognition.isStable && recognition.asana != Asana.UNKNOWN) recognition.asana to recognition.side else null
        if (pose != assessedPose) {
            stability.reset(); coach.resetPose(); smoother.reset(); lastMilestone = 0
            assessedPose = pose
            pose?.let { stats.recognised(it.first) }
        }
        if (pose == null || frame == null || !frame.hasPose) {
            hold.update(null, false, nowMs)
            return Output(null, emptyList())
        }
        val (asana, side) = pose
        val f = BodyFeatures(smoother.smooth(frame))
        val a = assessor.assess(f, asana, side, nowMs)
        val st = stability.add(f, nowMs)
        val reason = when {
            !a.available -> "Some joints aren't clearly visible"
            st.score == null -> "Hold still — measuring stability"
            st.score < hold.stabilityThreshold -> "Steady yourself to count the hold"
            else -> null
        }
        val h = hold.update(asana, reason == null, nowMs, reason)
        val events = mutableListOf<CoachEvent>()
        if (h.counting) stats.sample(asana, a.score, st.score)
        if (h.currentHoldMs < lastMilestone * 1000L) lastMilestone = 0
        val ms = (h.currentHoldMs / 1000).toInt() / 10 * 10
        if (h.counting && ms > 0 && ms > lastMilestone) {
            lastMilestone = ms
            events += CoachEvent.HoldMilestoneReached(asana, ms)
        }
        val framingOk = framing == FramingStatus.FULL_BODY
        val before = coach.improvements.size
        val assessmentEvents = coach.update(a, st, framingOk, nowMs)
        assessmentEvents.forEach { if (it is CoachEvent.AlignmentCorrectionRequired) stats.correction(asana, it.correction.message) }
        coach.improvements.drop(before).forEach { stats.improvement(asana, it) }
        events += assessmentEvents
        return Output(LiveAssessment(a, st, h, coach.currentCorrection(a, framingOk)), events)
    }

    fun summary(durationMs: Long): SessionSummary = stats.summary(durationMs, hold.snapshotTotals())
}

/**
 * Glue for one practice session: snapshot → [CoachingEventSource] + [AssessmentPipeline] →
 * [CoachingDecisionEngine] → [VoiceCoachManager]. One instance per practice screen; call
 * [release] when the screen leaves. All calls are expected on the main thread.
 */
class PracticeSession(
    context: Context,
    private val target: Asana?,
    nowMs: Long,
) {
    private val store = CoachSettingsStore(context)
    // Hold milestones come from the stable-hold timer in AssessmentPipeline, not raw recognition.
    private val source = CoachingEventSource(milestoneEverySeconds = Int.MAX_VALUE)
    private val pipeline = AssessmentPipeline()
    private val engine = CoachingDecisionEngine(store.loadCoach())
    val voice = VoiceCoachManager(context)
    private val clock = SessionClock(nowMs)

    private val _coach = MutableStateFlow(engine.settings)
    val coachSettings: StateFlow<CoachSettings> = _coach.asStateFlow()
    private val _visual = MutableStateFlow(store.loadVisual())
    val visualSettings: StateFlow<VisualSettings> = _visual.asStateFlow()
    private val _ui = MutableStateFlow(PracticeUiState())
    val ui: StateFlow<PracticeUiState> = _ui.asStateFlow()

    init {
        voice.setSpeechRate(engine.settings.speechRate)
        dispatch(engine.onEvent(CoachEvent.SessionStarted(target), nowMs), nowMs)
    }

    fun onSnapshot(frame: PoseFrame?, recognition: Recognition, nowMs: Long) {
        if (_ui.value.paused || _ui.value.summary != null) return
        val framing = FramingGuidance.evaluate(frame)
        val events = source.onSnapshot(PracticeSnapshot(nowMs, framing, recognition)).toMutableList()
        val out = pipeline.process(frame, recognition, framing, nowMs)
        events += out.events
        dispatch(events.flatMap { engine.onEvent(it, nowMs) }, nowMs)
        _ui.update { it.copy(live = out.live) }
    }

    /** Called about once per second: timer + periodic guided tips. */
    fun tick(nowMs: Long) {
        if (_ui.value.summary != null) return
        _ui.update { it.copy(elapsedMs = clock.elapsedMs(nowMs)) }
        if (!_ui.value.paused) dispatch(engine.onTick(nowMs), nowMs)
    }

    fun pause(nowMs: Long) {
        if (_ui.value.paused) return
        clock.pause(nowMs)
        engine.onEvent(CoachEvent.SessionPaused, nowMs)
        voice.stop()
        _ui.update { it.copy(paused = true, elapsedMs = clock.elapsedMs(nowMs)) }
    }

    fun resume(nowMs: Long) {
        if (!_ui.value.paused) return
        clock.resume(nowMs)
        source.restartHold(nowMs)
        engine.onEvent(CoachEvent.SessionResumed, nowMs)
        _ui.update { it.copy(paused = false) }
    }

    fun end(nowMs: Long) {
        if (_ui.value.summary != null) return
        clock.pause(nowMs)
        val duration = clock.elapsedMs(nowMs)
        voice.stop()
        dispatch(engine.onEvent(CoachEvent.SessionCompleted(duration), nowMs), nowMs)
        _ui.update { it.copy(paused = true, elapsedMs = duration, summary = pipeline.summary(duration)) }
    }

    fun continueAfterSummary(nowMs: Long) {
        _ui.update { it.copy(summary = null) }
        resume(nowMs)
    }

    fun updateCoach(s: CoachSettings) {
        engine.settings = s
        _coach.value = s
        voice.setSpeechRate(s.speechRate)
        if (s.mode == VoiceMode.OFF) voice.stop()
        store.save(s, _visual.value)
    }

    fun updateVisual(v: VisualSettings) {
        _visual.value = v
        store.save(_coach.value, v)
    }

    fun release() = voice.release()

    private fun dispatch(cues: List<Cue>, nowMs: Long) {
        for (c in cues) {
            voice.speak(c)
            _ui.update { it.copy(lastCue = c, lastCueAtMs = nowMs) }
        }
    }
}
