package com.chinmai.yogasense.ui.practice

import android.content.Context
import com.chinmai.yogasense.asana.Asana
import com.chinmai.yogasense.asana.BodyFeatures
import com.chinmai.yogasense.asana.BodySide
import com.chinmai.yogasense.asana.Recognition
import com.chinmai.yogasense.coaching.CoachEvent
import com.chinmai.yogasense.coaching.CoachSettings
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
import com.chinmai.yogasense.profile.CompletedPracticeSession
import com.chinmai.yogasense.profile.PracticePreferences.coach
import com.chinmai.yogasense.profile.PracticePreferences.visual
import com.chinmai.yogasense.profile.PracticePreferences.with
import com.chinmai.yogasense.profile.UserPreferences
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

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
    /** Structured correction ids + measured improvement, for the saved session record. */
    val corrections = CorrectionLog()
    private val smoother = LandmarkSmoother(resetOnReliabilityChange = true)
    /** Temporal reliability: brief dropouts vs persistent occlusion (Phase 8). */
    private val reliability = com.chinmai.yogasense.pose.ReliabilityTracker()
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
        val snapshot = reliability.update(frame, nowMs)
        val f = BodyFeatures(smoother.smooth(frame))
        val a = assessor.assess(f, asana, side, nowMs, snapshot)
        val st = stability.add(f, nowMs)
        val reason = when {
            !a.available -> a.cameraHint ?: "Some joints aren't clearly visible"
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
        // Occlusion-aware: in a profile view the hidden far side is not a framing problem.
        val framingOk = com.chinmai.yogasense.pose.OcclusionAwareFraming.accepted(framing, a.orientation, snapshot.states)
        val before = coach.improvements.size
        val assessmentEvents = coach.update(a, st, framingOk, nowMs)
        assessmentEvents.forEach {
            if (it is CoachEvent.AlignmentCorrectionRequired) {
                stats.correction(asana, it.correction.message)
                corrections.corrected(asana, it.correction.id, nowMs)
            }
            if (it is CoachEvent.AlignmentImproved) corrections.improved(it.correctionId)
        }
        coach.improvements.drop(before).forEach { stats.improvement(asana, it) }
        events += assessmentEvents
        return Output(LiveAssessment(a, st, h, coach.currentCorrection(a, framingOk), framingAccepted = framingOk), events)
    }

    fun summary(durationMs: Long): SessionSummary = stats.summary(durationMs, hold.snapshotTotals())
}

/**
 * Glue for one practice session: snapshot → [CoachingEventSource] + [AssessmentPipeline] →
 * [CoachingDecisionEngine] → [VoiceCoachManager]. One instance per practice screen; call
 * [release] when the screen leaves. All calls are expected on the main thread.
 *
 * Phase 7B: settings come from the active profile ([initialPreferences]) and every change is
 * passed to [onPreferencesChanged] (bound to that profile's id by the caller). On [release] the
 * session is turned into a [CompletedPracticeSession] by [SessionRecordPolicy] and handed to
 * [onRecord] exactly once.
 */
class PracticeSession(
    context: Context,
    private val target: Asana?,
    nowMs: Long,
    initialPreferences: UserPreferences = UserPreferences(),
    diagnostics: Boolean = false,
    private val onPreferencesChanged: (UserPreferences) -> Unit = {},
    private val onDiagnosticsChanged: (Boolean) -> Unit = {},
    private val onRecord: (CompletedPracticeSession) -> Unit = {},
    wallClock: () -> Long = System::currentTimeMillis,
) {
    private var preferences = initialPreferences
    private val sessionId = UUID.randomUUID().toString()
    private val startUptimeMs = nowMs
    private val startedAtWallMs = wallClock()
    private var endConfirmed = false
    private val recorded = AtomicBoolean(false)
    // Hold milestones come from the stable-hold timer in AssessmentPipeline, not raw recognition.
    private val source = CoachingEventSource(milestoneEverySeconds = Int.MAX_VALUE)
    private val pipeline = AssessmentPipeline()
    private val engine = CoachingDecisionEngine(initialPreferences.coach())
    val voice = VoiceCoachManager(context)
    private val clock = SessionClock(nowMs)

    private val _coach = MutableStateFlow(engine.settings)
    val coachSettings: StateFlow<CoachSettings> = _coach.asStateFlow()
    private val _visual = MutableStateFlow(initialPreferences.visual(diagnostics))
    val visualSettings: StateFlow<VisualSettings> = _visual.asStateFlow()
    private val _ui = MutableStateFlow(PracticeUiState())
    val ui: StateFlow<PracticeUiState> = _ui.asStateFlow()

    init {
        voice.setSpeechRate(engine.settings.speechRate)
        dispatch(engine.onEvent(CoachEvent.SessionStarted(target), nowMs), nowMs)
    }

    /** Debug timing of [onSnapshot] (main-thread cost per pose result), for PerfLog. */
    var snapshotCostEmaMs = 0f
        private set
    var snapshots = 0L
        private set

    fun onSnapshot(frame: PoseFrame?, recognition: Recognition, nowMs: Long) {
        if (_ui.value.paused || _ui.value.summary != null) return
        val t0 = System.nanoTime()
        try { onSnapshotInternal(frame, recognition, nowMs) } finally {
            val ms = (System.nanoTime() - t0) / 1_000_000f
            snapshotCostEmaMs = if (snapshots == 0L) ms else snapshotCostEmaMs + 0.1f * (ms - snapshotCostEmaMs)
            snapshots++
        }
    }

    private fun onSnapshotInternal(frame: PoseFrame?, recognition: Recognition, nowMs: Long) {
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

    /** "Done" on the summary: the user confirmed ending the session. */
    fun confirmEnd() { if (_ui.value.summary != null) endConfirmed = true }

    fun continueAfterSummary(nowMs: Long) {
        endConfirmed = false
        _ui.update { it.copy(summary = null) }
        resume(nowMs)
    }

    fun updateCoach(s: CoachSettings) {
        engine.settings = s
        _coach.value = s
        voice.setSpeechRate(s.speechRate)
        if (s.mode == VoiceMode.OFF) voice.stop()
        preferences = preferences.with(s, _visual.value)
        onPreferencesChanged(preferences)
    }

    fun updateVisual(v: VisualSettings) {
        val old = _visual.value
        _visual.value = v
        if (v.diagnostics != old.diagnostics) onDiagnosticsChanged(v.diagnostics)
        preferences = preferences.with(_coach.value, v)
        if (preferences != preferences.with(_coach.value, old)) onPreferencesChanged(preferences)
    }

    /**
     * The session record that [release] would save now (null = not saved, see [SessionRecordPolicy]).
     * Uses the confirmed summary if the user ended the session, otherwise measures up to [nowMs].
     */
    fun buildRecord(nowMs: Long): CompletedPracticeSession? {
        val confirmed = _ui.value.summary?.takeIf { endConfirmed }
        val summary = confirmed ?: run { clock.pause(nowMs); pipeline.summary(clock.elapsedMs(nowMs)) }
        val exit = if (confirmed != null) SessionExit.ENDED_BY_USER else SessionExit.LEFT
        return SessionRecordPolicy.build(sessionId, startedAtWallMs, startUptimeMs, nowMs, exit, summary, pipeline.corrections.snapshot())
    }

    /** Leaves the session: saves it once (repeated calls never record twice) and frees TextToSpeech. */
    fun release(nowMs: Long) {
        if (recorded.compareAndSet(false, true)) buildRecord(nowMs)?.let(onRecord)
        voice.release()
    }

    private fun dispatch(cues: List<Cue>, nowMs: Long) {
        for (c in cues) {
            voice.speak(c)
            _ui.update { it.copy(lastCue = c, lastCueAtMs = nowMs) }
        }
    }
}
