package com.chinmai.yogasense.profile

import com.chinmai.yogasense.asana.Asana
import com.chinmai.yogasense.coaching.CoachSettings
import com.chinmai.yogasense.coaching.VisualSettings
import com.chinmai.yogasense.coaching.VoiceMode
import com.chinmai.yogasense.data.db.ExperienceLevel
import com.chinmai.yogasense.data.db.ProfileStatus
import com.chinmai.yogasense.data.db.SessionCompletion

/** Local pilot participant profile (no credentials stored here). */
data class UserProfile(
    val userId: String,
    val displayName: String,
    val experienceLevel: ExperienceLevel,
    val createdAt: Long,
    val updatedAt: Long,
    val status: ProfileStatus,
)

/** Per-profile practice preferences. Defaults mirror the existing CoachSettings / VisualSettings. */
data class UserPreferences(
    val voiceMode: VoiceMode = CoachSettings().mode,
    val spokenEncouragement: Boolean = CoachSettings().encouragement,
    val voiceCountdown: Boolean = CoachSettings().countdown,
    val correctionAnnouncements: Boolean = CoachSettings().corrections,
    val speechRate: Float = CoachSettings().speechRate,
    val liveSkeletonEnabled: Boolean = VisualSettings().liveSkeleton,
    /** Stored preference only; the target skeleton feature is not available yet. */
    val targetSkeletonEnabled: Boolean = false,
    val visualCorrectionsEnabled: Boolean = VisualSettings().correctionHighlights,
    val referenceFigureEnabled: Boolean = VisualSettings().referenceFigure,
    val preferredPracticeMinutes: Int? = null,
)

/** A finished practice session to persist (derived results only — no frames or landmarks). */
data class CompletedPracticeSession(
    val sessionId: String,
    val startedAt: Long,
    val endedAt: Long,
    val activeDurationMs: Long,
    val completion: SessionCompletion,
    val poses: List<PoseOutcome>,
    val corrections: List<CorrectionOutcome>,
)

data class PoseOutcome(
    val asana: Asana,
    val alignmentScore: Int?,
    val stabilityScore: Int?,
    val stableHoldTotalMs: Long,
    val longestHoldMs: Long,
)

data class CorrectionOutcome(
    val asana: Asana,
    val correctionType: String,   // structured id, e.g. "WARRIOR_II.front_knee.high"
    val timestamp: Long,
    val improvementObserved: Boolean,
)

object ProfileValidation {
    const val MAX_NAME = 40
    /** Returns the cleaned display name, or null if unusable. */
    fun displayName(raw: String): String? = raw.trim().replace(Regex("\\s+"), " ").takeIf { it.isNotEmpty() && it.length <= MAX_NAME }
}

/** Initial state for a newly created profile, written in the same transaction as the profile. */
data class ProfileSetup(
    val preferences: UserPreferences = UserPreferences(),
    /** app_metadata markers to write atomically with the profile (key -> value), given the new userId. */
    val markers: (userId: String) -> Map<String, String> = { emptyMap() },
    /** New profiles complete onboarding before Home (Phase 7B.5). */
    val requireOnboarding: Boolean = true,
)

/** Bridges per-profile [UserPreferences] and the practice screen's existing settings models. */
object PracticePreferences {
    fun UserPreferences.coach() = CoachSettings(voiceMode, spokenEncouragement, voiceCountdown, correctionAnnouncements, speechRate.coerceIn(0.8f, 1.2f))

    /** [diagnostics] is a device-level developer toggle, not a profile preference. */
    fun UserPreferences.visual(diagnostics: Boolean = false) =
        VisualSettings(liveSkeletonEnabled, visualCorrectionsEnabled, referenceFigureEnabled, diagnostics)

    /** Applies practice-screen settings; fields the screen doesn't edit are kept from [this]. */
    fun UserPreferences.with(c: CoachSettings, v: VisualSettings) = copy(
        voiceMode = c.mode, spokenEncouragement = c.encouragement, voiceCountdown = c.countdown,
        correctionAnnouncements = c.corrections, speechRate = c.speechRate,
        liveSkeletonEnabled = v.liveSkeleton, visualCorrectionsEnabled = v.correctionHighlights, referenceFigureEnabled = v.referenceFigure,
    )

    fun from(c: CoachSettings, v: VisualSettings) = UserPreferences().with(c, v)
}
