package com.chinmai.yogasense.coaching

import com.chinmai.yogasense.asana.Asana
import com.chinmai.yogasense.asana.BodySide
import com.chinmai.yogasense.pose.FramingStatus

enum class VoiceMode(val label: String, val description: String) {
    GUIDED("Guided", "Instructions, corrections, breathing, countdown and encouragement"),
    MINIMAL("Minimal", "Important corrections and occasional encouragement only"),
    OFF("Off", "No speech — on-screen guidance continues"),
}

/** User-adjustable coaching preferences (persisted locally). */
data class CoachSettings(
    val mode: VoiceMode = VoiceMode.GUIDED,
    val encouragement: Boolean = true,
    val countdown: Boolean = true,
    val corrections: Boolean = true,
    val speechRate: Float = 1.0f,       // 0.8 – 1.2, passed to TextToSpeech.setSpeechRate
)

/** Visual assistance preferences (persisted locally). */
data class VisualSettings(
    val liveSkeleton: Boolean = true,
    val correctionHighlights: Boolean = true,
    val referenceFigure: Boolean = true,
    val diagnostics: Boolean = false,
) {
    companion object {
        /** Target skeleton needs target landmarks mapped to the camera viewport; not available yet. */
        const val TARGET_SKELETON_AVAILABLE = false
    }
}

/** Higher priority wins; SAFETY and FRAMING may interrupt other speech. */
enum class CuePriority(val rank: Int, val interrupts: Boolean) {
    SAFETY(0, true),
    FRAMING(1, true),
    CORRECTION(2, false),
    INSTRUCTION(3, false),
    BREATHING(4, false),
    COUNTDOWN(5, false),
    ENCOURAGEMENT(6, false),
    INFO(7, false),
}

/** One thing the coach wants to say. [key] identifies the message for cooldown / de-duplication. */
data class Cue(val key: String, val text: String, val priority: CuePriority)

/**
 * A correction produced by the alignment assessment (coaching/assessment). [id] is the stable
 * correction id ("ASANA.metric.direction") used for cooldowns and improvement tracking.
 */
data class AlignmentCorrection(val id: String, val message: String, val safety: Boolean = false)

/** Meaningful session events (not per-frame callbacks). */
sealed interface CoachEvent {
    data class SessionStarted(val target: Asana?) : CoachEvent
    data class FramingIssue(val status: FramingStatus) : CoachEvent
    data object FramingOk : CoachEvent
    /** Emitted only for temporally confirmed (stable) recognition. */
    data class PoseRecognized(val asana: Asana, val side: BodySide, val confidence: Float) : CoachEvent
    data class PoseLost(val asana: Asana, val heldMs: Long) : CoachEvent
    data class StableHoldStarted(val asana: Asana) : CoachEvent
    data class HoldMilestoneReached(val asana: Asana, val seconds: Int) : CoachEvent
    data class AlignmentCorrectionRequired(val correction: AlignmentCorrection) : CoachEvent
    data class AlignmentImproved(val correctionId: String) : CoachEvent
    /** Measured stability dropped from steady to unsteady during a hold. */
    data class StabilityDecreased(val score: Int) : CoachEvent
    data object SessionPaused : CoachEvent
    data object SessionResumed : CoachEvent
    data class SessionCompleted(val durationMs: Long) : CoachEvent
}

