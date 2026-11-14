package com.chinmai.yogasense.wellness

/** A self-reported 1–5 rating scale with plain-language labels. */
data class RatingScale(val title: String, val labels: List<String>) {
    val range: IntRange get() = 1..labels.size
    fun label(v: Int): String = labels[v - 1]
}

object Scales {
    val MOOD = RatingScale("Mood", listOf("Very low", "Low", "Okay", "Good", "Very good"))
    val STRESS = RatingScale("Stress", listOf("Very low", "Low", "Moderate", "High", "Very high"))
    val SLEEP_QUALITY = RatingScale("Sleep quality", listOf("Very poor", "Poor", "Fair", "Good", "Very good"))
    const val SLEEP_MIN_HOURS = 0f
    const val SLEEP_MAX_HOURS = 16f
    const val SLEEP_STEP = 0.5f
    const val REFLECTION_MAX = 500
}

/**
 * One daily check-in (at most one per local calendar day, keyed by [epochDay]).
 * Every field is optional; nothing has a default value.
 */
data class DailyCheckIn(
    val epochDay: Long,
    val mood: Int? = null,
    val stress: Int? = null,
    val sleepHours: Float? = null,
    val sleepQuality: Int? = null,
    val reflection: String? = null,
    val updatedAtMillis: Long = 0,
) {
    val isEmpty: Boolean get() = mood == null && stress == null && sleepHours == null && sleepQuality == null && reflection.isNullOrBlank()
}

sealed interface CheckInValidation {
    data class Valid(val checkIn: DailyCheckIn) : CheckInValidation
    data class Invalid(val reasons: List<String>) : CheckInValidation
}

object CheckInValidator {
    /** Validates ranges, trims the reflection and rejects a completely empty entry. */
    fun validate(c: DailyCheckIn): CheckInValidation {
        val reasons = mutableListOf<String>()
        c.mood?.let { if (it !in Scales.MOOD.range) reasons += "Mood must be 1–5" }
        c.stress?.let { if (it !in Scales.STRESS.range) reasons += "Stress must be 1–5" }
        c.sleepQuality?.let { if (it !in Scales.SLEEP_QUALITY.range) reasons += "Sleep quality must be 1–5" }
        c.sleepHours?.let {
            if (it < Scales.SLEEP_MIN_HOURS || it > Scales.SLEEP_MAX_HOURS) reasons += "Sleep must be 0–16 hours"
            else if ((it / Scales.SLEEP_STEP) % 1f != 0f) reasons += "Sleep is recorded in half hours"
        }
        val reflection = c.reflection?.trim()?.takeIf { it.isNotEmpty() }
        if (reflection != null && reflection.length > Scales.REFLECTION_MAX) reasons += "Reflection is limited to ${Scales.REFLECTION_MAX} characters"
        val cleaned = c.copy(reflection = reflection)
        if (cleaned.isEmpty) reasons += "Add at least one answer to save a check-in"
        return if (reasons.isEmpty()) CheckInValidation.Valid(cleaned) else CheckInValidation.Invalid(reasons)
    }
}

/** A completed, scored questionnaire. Answers are stored only with consent. */
data class QuestionnaireRecord(
    val id: Long = 0,
    val type: QuestionnaireType,
    val completedAtMillis: Long,
    val answers: List<Int>,
    val total: Int,
    val bandLabel: String,
    val difficulty: Int?,          // index into difficultyOptions, optional and unscored
    val item9Positive: Boolean,
)

/** Whether the user agreed to keep wellness data on this phone. */
enum class WellnessConsent { UNKNOWN, GRANTED, DECLINED }
