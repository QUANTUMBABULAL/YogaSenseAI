package com.chinmai.yogasense.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.chinmai.yogasense.asana.Asana
import com.chinmai.yogasense.coaching.VoiceMode

/*
 * Room schema (yogasense.db), version 2 — see YogaSenseDatabase.MIGRATION_1_2. Ownership rules:
 *  - Every user-specific table has a `user_id` FK to user_profiles, or is a child of a table that does
 *    (pose_results / practice_corrections → practice_sessions; assessment_responses → wellness_assessments).
 *  - All ownership FKs are ON DELETE CASCADE: deleting a profile erases everything it owns (right to erasure).
 *  - Primary keys are immutable UUID strings; ON UPDATE is NO ACTION.
 *  - Nothing here stores camera video, frames or raw landmarks — only derived, user-visible results.
 *
 * Sensitive columns (to be encrypted with an Android Keystore key in Phase 7D):
 *  wellness_check_ins.reflection (v2, plaintext until 7D) / reflection_ciphertext, plus mood/stress/sleep values,
 *  wellness_assessments.total_score / safety_flag, assessment_responses.response_value.
 */

enum class ProfileStatus { ACTIVE, ARCHIVED }
enum class ExperienceLevel { BEGINNER, INTERMEDIATE, ADVANCED }
enum class SessionCompletion { COMPLETED, ENDED_EARLY, ABANDONED }
enum class Instrument { PHQ9, GAD7 }
enum class ConsentType { WELLNESS_DATA_STORAGE, PILOT_PARTICIPATION }
/** The user's answer recorded by a consent record (v2). A withdrawn record no longer applies. */
enum class ConsentDecision { GRANTED, DECLINED }

@Entity(tableName = "user_profiles", indices = [Index("status")])
data class UserProfileEntity(
    @PrimaryKey @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "display_name") val displayName: String,
    @ColumnInfo(name = "experience_level") val experienceLevel: ExperienceLevel,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    val status: ProfileStatus,
)

@Entity(
    tableName = "user_preferences",
    foreignKeys = [ForeignKey(UserProfileEntity::class, ["user_id"], ["user_id"], onDelete = ForeignKey.CASCADE)],
)
data class UserPreferencesEntity(
    @PrimaryKey @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "voice_mode") val voiceMode: VoiceMode,
    @ColumnInfo(name = "spoken_encouragement") val spokenEncouragement: Boolean,
    @ColumnInfo(name = "voice_countdown") val voiceCountdown: Boolean,
    @ColumnInfo(name = "correction_announcements") val correctionAnnouncements: Boolean,
    @ColumnInfo(name = "speech_rate") val speechRate: Float,
    @ColumnInfo(name = "live_skeleton_enabled") val liveSkeletonEnabled: Boolean,
    @ColumnInfo(name = "target_skeleton_enabled") val targetSkeletonEnabled: Boolean,
    @ColumnInfo(name = "visual_corrections_enabled") val visualCorrectionsEnabled: Boolean,
    @ColumnInfo(name = "reference_figure_enabled") val referenceFigureEnabled: Boolean,
    @ColumnInfo(name = "preferred_practice_minutes") val preferredPracticeMinutes: Int?,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(
    tableName = "practice_sessions",
    foreignKeys = [ForeignKey(UserProfileEntity::class, ["user_id"], ["user_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["user_id", "started_at"])],
)
data class PracticeSessionEntity(
    @PrimaryKey @ColumnInfo(name = "session_id") val sessionId: String,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "ended_at") val endedAt: Long?,
    /** Active practice time, excluding pauses (not wall-clock time in the app). */
    @ColumnInfo(name = "duration_ms") val durationMs: Long,
    val completion: SessionCompletion,
)

@Entity(
    tableName = "pose_results",
    foreignKeys = [ForeignKey(PracticeSessionEntity::class, ["session_id"], ["session_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["session_id", "asana"], unique = true)],
)
data class PoseResultEntity(
    @PrimaryKey @ColumnInfo(name = "result_id") val resultId: String,
    @ColumnInfo(name = "session_id") val sessionId: String,
    val asana: Asana,
    /** Null = not enough assessed data (never 0 as a substitute). */
    @ColumnInfo(name = "alignment_score") val alignmentScore: Int?,
    @ColumnInfo(name = "stability_score") val stabilityScore: Int?,
    @ColumnInfo(name = "stable_hold_total_ms") val stableHoldTotalMs: Long,
    @ColumnInfo(name = "longest_hold_ms") val longestHoldMs: Long,
)

@Entity(
    tableName = "practice_corrections",
    foreignKeys = [ForeignKey(PracticeSessionEntity::class, ["session_id"], ["session_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("session_id")],
)
data class PracticeCorrectionEntity(
    @PrimaryKey @ColumnInfo(name = "correction_id") val correctionId: String,
    @ColumnInfo(name = "session_id") val sessionId: String,
    val asana: Asana,
    /** Structured correction id from assessment, e.g. "WARRIOR_II.front_knee.high". */
    @ColumnInfo(name = "correction_type") val correctionType: String,
    val timestamp: Long,
    @ColumnInfo(name = "improvement_observed") val improvementObserved: Boolean,
)

@Entity(
    tableName = "wellness_check_ins",
    foreignKeys = [ForeignKey(UserProfileEntity::class, ["user_id"], ["user_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["user_id", "epoch_day"], unique = true)],
)
data class WellnessCheckInEntity(
    @PrimaryKey @ColumnInfo(name = "check_in_id") val checkInId: String,
    @ColumnInfo(name = "user_id") val userId: String,
    /** Local calendar day; one check-in per user per day. */
    @ColumnInfo(name = "epoch_day") val epochDay: Long,
    @ColumnInfo(name = "recorded_at") val recordedAt: Long,
    val mood: Int?,
    val stress: Int?,
    @ColumnInfo(name = "sleep_hours") val sleepHours: Float?,
    @ColumnInfo(name = "sleep_quality") val sleepQuality: Int?,
    /** Encrypted reflection (Phase 7D). Plaintext reflections must never be written here. */
    @ColumnInfo(name = "reflection_ciphertext", typeAffinity = ColumnInfo.BLOB) val reflectionCiphertext: ByteArray?,
    /**
     * v2: reflection text. PLAINTEXT until Phase 7D encrypts it into [reflectionCiphertext]
     * (same exposure as the Phase 7 wellness.db it is migrated from: app-private storage only).
     */
    val reflection: String? = null,
) {
    override fun equals(other: Any?): Boolean = other is WellnessCheckInEntity && other.checkInId == checkInId &&
        other.userId == userId && other.epochDay == epochDay && other.recordedAt == recordedAt && other.mood == mood &&
        other.stress == stress && other.sleepHours == sleepHours && other.sleepQuality == sleepQuality &&
        (other.reflectionCiphertext?.contentEquals(reflectionCiphertext ?: ByteArray(0)) ?: (reflectionCiphertext == null)) &&
        other.reflection == reflection
    override fun hashCode(): Int = checkInId.hashCode()
}

@Entity(
    tableName = "wellness_assessments",
    foreignKeys = [ForeignKey(UserProfileEntity::class, ["user_id"], ["user_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["user_id", "completed_at"])],
)
data class WellnessAssessmentEntity(
    @PrimaryKey @ColumnInfo(name = "assessment_id") val assessmentId: String,
    @ColumnInfo(name = "user_id") val userId: String,
    val instrument: Instrument,
    /** Source/version of the wording used, e.g. "phqscreeners-en-2010". */
    @ColumnInfo(name = "instrument_version") val instrumentVersion: String,
    @ColumnInfo(name = "completed_at") val completedAt: Long,
    @ColumnInfo(name = "total_score") val totalScore: Int,
    /** PHQ-9 item 9 answered above "Not at all"; null for instruments without that item. */
    @ColumnInfo(name = "safety_flag") val safetyFlag: Boolean?,
    /** v2: severity band label shown to the user at completion (stored, not recomputed). */
    @ColumnInfo(name = "band_label") val bandLabel: String? = null,
    /** v2: optional, unscored "how difficult" answer (index into the instrument's options). */
    val difficulty: Int? = null,
)

@Entity(
    tableName = "assessment_responses",
    foreignKeys = [ForeignKey(WellnessAssessmentEntity::class, ["assessment_id"], ["assessment_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["assessment_id", "question_number"], unique = true)],
)
data class AssessmentResponseEntity(
    @PrimaryKey @ColumnInfo(name = "response_id") val responseId: String,
    @ColumnInfo(name = "assessment_id") val assessmentId: String,
    /** 1-based item number. */
    @ColumnInfo(name = "question_number") val questionNumber: Int,
    @ColumnInfo(name = "response_value") val responseValue: Int,
)

@Entity(
    tableName = "consent_records",
    foreignKeys = [ForeignKey(UserProfileEntity::class, ["user_id"], ["user_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["user_id", "consent_type"])],
)
data class ConsentRecordEntity(
    @PrimaryKey @ColumnInfo(name = "consent_id") val consentId: String,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "consent_type") val consentType: ConsentType,
    @ColumnInfo(name = "consent_version") val consentVersion: String,
    @ColumnInfo(name = "granted_at") val grantedAt: Long,
    @ColumnInfo(name = "withdrawn_at") val withdrawnAt: Long?,
    /** v2: GRANTED or DECLINED. [grantedAt] is the time the decision was recorded. */
    @ColumnInfo(defaultValue = "GRANTED") val decision: ConsentDecision = ConsentDecision.GRANTED,
)

/** v2: app-level key/value markers (e.g. one-time migration completion). Never holds personal data. */
@Entity(tableName = "app_metadata")
data class AppMetadataEntity(
    @PrimaryKey val key: String,
    val value: String,
)

// ---- v3 (Phase 7B.5): personalisation -------------------------------------------------------------
// Experience level and preferred session minutes are NOT repeated here: they stay in
// user_profiles.experience_level and user_preferences.preferred_practice_minutes.

/** One row per profile, present for profiles created from 7B.5 on, or once onboarding is opened. */
@Entity(
    tableName = "profile_personalisation",
    foreignKeys = [ForeignKey(UserProfileEntity::class, ["user_id"], ["user_id"], onDelete = ForeignKey.CASCADE)],
)
data class PersonalisationEntity(
    @PrimaryKey @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "height_cm") val heightCm: Float?,
    @ColumnInfo(name = "weight_kg") val weightKg: Float?,
    @ColumnInfo(name = "height_unit") val heightUnit: com.chinmai.yogasense.profile.HeightUnit,
    @ColumnInfo(name = "weight_unit") val weightUnit: com.chinmai.yogasense.profile.WeightUnit,
    @ColumnInfo(name = "weekly_frequency") val weeklyFrequency: com.chinmai.yogasense.profile.PracticeFrequency?,
    @ColumnInfo(name = "no_particular_area") val noParticularArea: Boolean,
    @ColumnInfo(name = "onboarding_required") val onboardingRequired: Boolean,
    @ColumnInfo(name = "onboarding_status") val onboardingStatus: com.chinmai.yogasense.profile.OnboardingStatus,
    @ColumnInfo(name = "onboarding_step") val onboardingStep: Int,
    @ColumnInfo(name = "onboarding_version") val onboardingVersion: Int,
    @ColumnInfo(name = "prompt_dismissed") val promptDismissed: Boolean,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

/** Selected goals (normalised: one row per goal). */
@Entity(
    tableName = "profile_goals",
    primaryKeys = ["user_id", "goal"],
    foreignKeys = [ForeignKey(UserProfileEntity::class, ["user_id"], ["user_id"], onDelete = ForeignKey.CASCADE)],
)
data class ProfileGoalEntity(
    @ColumnInfo(name = "user_id") val userId: String,
    val goal: com.chinmai.yogasense.profile.WellnessGoal,
)

/** Areas to pay extra attention to, with an optional reason. Not a medical record. */
@Entity(
    tableName = "profile_body_areas",
    primaryKeys = ["user_id", "area"],
    foreignKeys = [ForeignKey(UserProfileEntity::class, ["user_id"], ["user_id"], onDelete = ForeignKey.CASCADE)],
)
data class ProfileBodyAreaEntity(
    @ColumnInfo(name = "user_id") val userId: String,
    val area: com.chinmai.yogasense.profile.BodyArea,
    val intent: com.chinmai.yogasense.profile.BodyAreaIntent?,
)
