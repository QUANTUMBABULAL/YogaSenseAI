package com.chinmai.yogasense.coaching

import com.chinmai.yogasense.asana.Asana
import com.chinmai.yogasense.pose.FramingStatus

/**
 * Decides what (if anything) the coach says. Pure Kotlin and deterministic: events + time in,
 * cues out. It never sees raw frames.
 *
 * Rules:
 * - OFF mode: silent. MINIMAL: safety, framing, corrections and occasional encouragement only.
 * - Pose announcements require a confirmed recognition with confidence >= [minConfidence].
 * - While the body is badly framed, instructions/breathing/countdown/encouragement are held back
 *   (no contradictory "great hold" while being told to step back).
 * - Every message has a cooldown by key (duplicate suppression); non-urgent messages also respect a
 *   global minimum gap so the coach never chatters.
 * - "Improved" is only said for a correction that was actually given recently.
 */
class CoachingDecisionEngine(
    var settings: CoachSettings = CoachSettings(),
    private val minConfidence: Float = 0.65f,
    private val minGapMs: Long = 4_000,
    private val tipIntervalMs: Long = 20_000,
    private val improvementWindowMs: Long = 60_000,
) {
    private var target: Asana? = null
    private var paused = false
    private var framingProblem: FramingStatus? = null
    private var holding: Asana? = null
    private var tipIndex = 0
    private var nextTipAt = Long.MAX_VALUE
    private var lastSpeechAt = Long.MIN_VALUE / 2
    private val lastSpokenByKey = HashMap<String, Long>()
    private val givenCorrections = HashMap<String, Long>()

    fun reset(newTarget: Asana?) {
        target = newTarget; paused = false; framingProblem = null; holding = null
        tipIndex = 0; nextTipAt = Long.MAX_VALUE; lastSpeechAt = Long.MIN_VALUE / 2
        lastSpokenByKey.clear(); givenCorrections.clear()
    }

    val isPaused: Boolean get() = paused

    fun onEvent(event: CoachEvent, nowMs: Long): List<Cue> {
        val candidates = mutableListOf<Cue>()
        when (event) {
            is CoachEvent.SessionStarted -> {
                reset(event.target)
                candidates += Cue("start", CoachingContent.SESSION_START, CuePriority.INFO)
                candidates += Cue("safety", CoachingContent.SAFETY_REMINDER, CuePriority.SAFETY)
            }
            is CoachEvent.FramingIssue -> {
                framingProblem = event.status
                CoachingContent.framing(event.status)?.let { candidates += Cue("framing:${event.status}", it, CuePriority.FRAMING) }
            }
            CoachEvent.FramingOk -> framingProblem = null
            is CoachEvent.PoseRecognized -> {
                if (event.confidence < minConfidence) return emptyList()
                holding = event.asana
                tipIndex = 0
                nextTipAt = nowMs + tipIntervalMs
                val t = target
                candidates += if (t != null && t != event.asana) {
                    Cue("different:${event.asana}", CoachingContent.different(t, event.asana), CuePriority.INFO)
                } else {
                    val s = CoachingContent.script(event.asana)
                    Cue("intro:${event.asana}", listOfNotNull(s?.intro, s?.instructions?.firstOrNull()).joinToString(" "), CuePriority.INSTRUCTION)
                }
            }
            is CoachEvent.StableHoldStarted -> holding = event.asana
            is CoachEvent.HoldMilestoneReached -> {
                val goal = CoachingContent.recommendedHoldSeconds(event.asana)
                candidates += if (goal != null && event.seconds >= goal && event.seconds - 10 < goal) {
                    Cue("hold:${event.asana}:${event.seconds}", CoachingContent.holdReached(event.asana, event.seconds), CuePriority.ENCOURAGEMENT)
                } else {
                    Cue("count:${event.asana}:${event.seconds}", CoachingContent.countdown(event.seconds), CuePriority.COUNTDOWN)
                }
            }
            is CoachEvent.PoseLost -> {
                holding = null; nextTipAt = Long.MAX_VALUE
                if (event.heldMs >= 3_000) candidates += Cue("lost", CoachingContent.POSE_LOST, CuePriority.INFO)
            }
            is CoachEvent.AlignmentCorrectionRequired -> {
                val c = event.correction
                givenCorrections[c.id] = nowMs
                candidates += Cue("corr:${c.id}", c.message, if (c.safety) CuePriority.SAFETY else CuePriority.CORRECTION)
            }
            is CoachEvent.AlignmentImproved -> {
                val given = givenCorrections.remove(event.correctionId)
                if (given != null && nowMs - given <= improvementWindowMs) {
                    candidates += Cue("improved:${event.correctionId}", CoachingContent.improved(), CuePriority.ENCOURAGEMENT)
                }
            }
            is CoachEvent.CameraAdjustmentNeeded ->
                candidates += Cue("camera:${event.message}", event.message, CuePriority.FRAMING)
            is CoachEvent.StabilityDecreased ->
                candidates += Cue("stability", CoachingContent.STEADY, CuePriority.CORRECTION)
            CoachEvent.SessionPaused -> { paused = true; return emptyList() }
            CoachEvent.SessionResumed -> { paused = false; nextTipAt = if (holding != null) nowMs + tipIntervalMs else Long.MAX_VALUE; return emptyList() }
            is CoachEvent.SessionCompleted -> {
                paused = false; holding = null
                if (settings.mode == VoiceMode.OFF) return emptyList()
                val c = Cue("complete", CoachingContent.sessionComplete(settings.encouragement), CuePriority.INFO)
                accept(c, nowMs)
                return listOf(c)
            }
        }
        return select(candidates, nowMs)
    }

    /** Periodic guided tips (instructions alternating with breathing) while a pose is held. */
    fun onTick(nowMs: Long): List<Cue> {
        val asana = holding ?: return emptyList()
        if (paused || nowMs < nextTipAt) return emptyList()
        nextTipAt = nowMs + tipIntervalMs
        val instructions = CoachingContent.script(asana)?.instructions.orEmpty().drop(1)
        val breathing = CoachingContent.breathing(asana).ifEmpty { listOf(CoachingContent.GENERIC_BREATH) }
        val tips = buildList {
            val n = maxOf(instructions.size, breathing.size)
            for (i in 0 until n) {
                breathing.getOrNull(i)?.let { add(Cue("breath:$asana:$i", it, CuePriority.BREATHING)) }
                instructions.getOrNull(i)?.let { add(Cue("tip:$asana:$i", it, CuePriority.INSTRUCTION)) }
            }
        }
        if (tips.isEmpty()) return emptyList()
        val cue = tips[tipIndex % tips.size]
        tipIndex++
        return select(listOf(cue), nowMs)
    }

    private fun allowedByMode(c: Cue): Boolean = when (settings.mode) {
        VoiceMode.OFF -> false
        VoiceMode.MINIMAL -> c.priority in setOf(CuePriority.SAFETY, CuePriority.FRAMING, CuePriority.CORRECTION, CuePriority.ENCOURAGEMENT)
        VoiceMode.GUIDED -> true
    }

    private fun allowedBySettings(c: Cue): Boolean = when (c.priority) {
        CuePriority.CORRECTION -> settings.corrections
        CuePriority.ENCOURAGEMENT -> settings.encouragement
        CuePriority.COUNTDOWN -> settings.countdown
        else -> true
    }

    private fun cooldownMs(p: CuePriority): Long = when (p) {
        CuePriority.FRAMING -> 8_000
        CuePriority.CORRECTION -> 12_000
        CuePriority.SAFETY -> 120_000
        else -> 30_000
    }

    private fun select(candidates: List<Cue>, nowMs: Long): List<Cue> {
        if (paused) return emptyList()
        val out = mutableListOf<Cue>()
        for (c in candidates.sortedBy { it.priority.rank }) {
            if (!allowedByMode(c) || !allowedBySettings(c)) continue
            // No coaching chatter while the user is out of frame.
            if (framingProblem != null && c.priority.rank > CuePriority.CORRECTION.rank) continue
            val last = lastSpokenByKey[c.key]
            if (last != null && nowMs - last < cooldownMs(c.priority)) continue
            if (!c.priority.interrupts && out.isEmpty() && nowMs - lastSpeechAt < minGapMs) continue
            if (out.size >= 2) break
            accept(c, nowMs)
            out += c
        }
        return out
    }

    private fun accept(c: Cue, nowMs: Long) {
        lastSpokenByKey[c.key] = nowMs
        lastSpeechAt = nowMs
    }
}
