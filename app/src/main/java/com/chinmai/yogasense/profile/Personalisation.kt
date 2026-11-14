package com.chinmai.yogasense.profile

import com.chinmai.yogasense.data.db.ExperienceLevel
import kotlin.math.roundToInt

/*
 * Profile personalisation (Phase 7B.5). Sources of truth:
 *  - experience level  → user_profiles.experience_level (existing field, not duplicated)
 *  - session duration  → user_preferences.preferred_practice_minutes (existing field)
 *  - everything else   → profile_personalisation / profile_goals / profile_body_areas
 * No date of birth, location or medical history is collected. Height/weight are optional and only
 * stored; nothing is calculated from them (no BMI) and nothing is judged.
 */

enum class BodyArea(val label: String) {
    NECK("Neck"), SHOULDERS("Shoulders"), UPPER_BACK("Upper back"), LOWER_BACK("Lower back"),
    HIPS("Hips"), KNEES("Knees"), WRISTS("Wrists"), ANKLES("Ankles"),
}

/** Why the user picked an area. Null = not said. Selecting an area never implies an injury. */
enum class BodyAreaIntent(val label: String) {
    DISCOMFORT("I feel discomfort here"),
    MOBILITY("Improve flexibility & mobility"),
}

enum class WellnessGoal(val label: String) {
    FLEXIBILITY("Flexibility"), STRENGTH("Strength"), BALANCE("Balance"), POSTURE("Posture"),
    STRESS("Stress management"), SLEEP("Better sleep"), GENERAL_WELLNESS("General wellness"), HABIT("Building a consistent yoga habit"),
}

enum class PracticeFrequency(val label: String, val detail: String) {
    TWO_THREE("2–3 days a week", "A gentle rhythm"),
    FOUR_FIVE("4–5 days a week", "Most days"),
    DAILY("Every day", "A daily ritual"),
    FLEXIBLE("Flexible", "Whenever I can"),
}

enum class HeightUnit { CM, FT_IN }
enum class WeightUnit { KG, LB }

enum class OnboardingStatus { NOT_STARTED, IN_PROGRESS, COMPLETED }

/** Stored personalisation for one profile (excluding experience and minutes, see file header). */
data class Personalisation(
    val heightCm: Float? = null,
    val weightKg: Float? = null,
    val heightUnit: HeightUnit = HeightUnit.CM,
    val weightUnit: WeightUnit = WeightUnit.KG,
    val bodyAreas: Map<BodyArea, BodyAreaIntent?> = emptyMap(),
    val noParticularArea: Boolean = false,
    val goals: Set<WellnessGoal> = emptySet(),
    val weeklyFrequency: PracticeFrequency? = null,
    /** True for profiles created from Phase 7B.5 on: onboarding is completed before Home. */
    val onboardingRequired: Boolean = false,
    val status: OnboardingStatus = OnboardingStatus.NOT_STARTED,
    /** Last step reached (ordinal of [OnboardingStep]) so an interrupted onboarding resumes there. */
    val step: Int = 0,
    val version: Int = ONBOARDING_VERSION,
    val promptDismissed: Boolean = false,
    val updatedAt: Long = 0,
) {
    val isCompleted: Boolean get() = status == OnboardingStatus.COMPLETED

    companion object { const val ONBOARDING_VERSION = 1 }
}

/** Personalisation together with the two existing fields it reuses. */
data class PersonalisationView(
    /** Null for profiles created before Phase 7B.5 that have never opened onboarding. */
    val personalisation: Personalisation?,
    val experienceLevel: ExperienceLevel,
    val preferredMinutes: Int?,
) {
    val p: Personalisation get() = personalisation ?: Personalisation()
    /** Gate: only new profiles must finish onboarding before entering the app. */
    val mustOnboard: Boolean get() = personalisation?.let { it.onboardingRequired && !it.isCompleted } ?: false
    /** Existing profiles get an optional, dismissible prompt. */
    val showPrompt: Boolean get() = !p.isCompleted && !p.promptDismissed && !mustOnboard
}

// ---- Validation -------------------------------------------------------------------------------

sealed interface Measurement {
    data object Empty : Measurement
    data class Valid(val value: Float) : Measurement
    data class Invalid(val message: String) : Measurement
}

object BodyMetrics {
    const val MIN_CM = 100f
    const val MAX_CM = 250f
    const val MIN_KG = 25f
    const val MAX_KG = 300f
    const val LB_PER_KG = 2.2046226f
    const val CM_PER_INCH = 2.54f

    private fun parse(raw: String): Float? {
        val t = raw.trim().replace(',', '.')
        if (t.isEmpty() || t.count { it == '.' } > 1 || t.any { !it.isDigit() && it != '.' }) return null
        return t.toFloatOrNull()
    }

    fun roundTenth(v: Float) = (v * 10f).roundToInt() / 10f

    fun heightCm(raw: String): Measurement {
        if (raw.isBlank()) return Measurement.Empty
        val v = parse(raw) ?: return Measurement.Invalid("Enter a number, e.g. 165")
        return if (v < MIN_CM || v > MAX_CM) Measurement.Invalid("Enter a height between ${MIN_CM.toInt()} and ${MAX_CM.toInt()} cm")
        else Measurement.Valid(roundTenth(v))
    }

    /** Feet + inches → cm. Either field may be blank (treated as 0) but not both. */
    fun heightFtIn(feetRaw: String, inchesRaw: String): Measurement {
        if (feetRaw.isBlank() && inchesRaw.isBlank()) return Measurement.Empty
        val ft = if (feetRaw.isBlank()) 0f else parse(feetRaw) ?: return Measurement.Invalid("Enter feet as a number")
        val inch = if (inchesRaw.isBlank()) 0f else parse(inchesRaw) ?: return Measurement.Invalid("Enter inches as a number")
        if (ft != ft.toInt().toFloat() || ft > 8f) return Measurement.Invalid("Enter whole feet (3–8)")
        if (inch >= 12f) return Measurement.Invalid("Inches must be less than 12")
        val cm = (ft * 12f + inch) * CM_PER_INCH
        return if (cm < MIN_CM || cm > MAX_CM) Measurement.Invalid("Enter a height between 3 ft 4 in and 8 ft 2 in")
        else Measurement.Valid(roundTenth(cm))
    }

    fun weightKg(raw: String, unit: WeightUnit): Measurement {
        if (raw.isBlank()) return Measurement.Empty
        val v = parse(raw) ?: return Measurement.Invalid("Enter a number, e.g. ${if (unit == WeightUnit.KG) "62" else "137"}")
        val kg = if (unit == WeightUnit.KG) v else v / LB_PER_KG
        return if (kg < MIN_KG || kg > MAX_KG) {
            if (unit == WeightUnit.KG) Measurement.Invalid("Enter a weight between ${MIN_KG.toInt()} and ${MAX_KG.toInt()} kg")
            else Measurement.Invalid("Enter a weight between ${(MIN_KG * LB_PER_KG).roundToInt()} and ${(MAX_KG * LB_PER_KG).roundToInt()} lb")
        } else Measurement.Valid(if (unit == WeightUnit.KG) roundTenth(kg) else kg) // lb kept exact so it reads back unchanged
    }

    /** Text for an existing value in the chosen unit (for editing). */
    fun heightText(cm: Float?): String = cm?.let { fmt(it) }.orEmpty()
    fun feetInches(cm: Float?): Pair<String, String> {
        cm ?: return "" to ""
        val totalIn = cm / CM_PER_INCH
        var ft = (totalIn / 12f).toInt()
        var inch = (totalIn - ft * 12f).roundToInt()
        if (inch == 12) { ft += 1; inch = 0 }
        return ft.toString() to inch.toString()
    }
    fun weightText(kg: Float?, unit: WeightUnit): String = kg?.let { fmt(if (unit == WeightUnit.KG) it else roundTenth(it * LB_PER_KG)) }.orEmpty()

    fun displayHeight(cm: Float?, unit: HeightUnit): String? = cm?.let {
        if (unit == HeightUnit.CM) "${fmt(it)} cm" else feetInches(it).let { (f, i) -> "$f ft $i in" }
    }
    fun displayWeight(kg: Float?, unit: WeightUnit): String? = kg?.let { "${weightText(it, unit)} ${if (unit == WeightUnit.KG) "kg" else "lb"}" }

    private fun fmt(v: Float) = if (v == v.toInt().toFloat()) v.toInt().toString() else "%.1f".format(java.util.Locale.ROOT, v)
}

// ---- Onboarding flow (pure state machine) ------------------------------------------------------

enum class OnboardingStep(val optional: Boolean) {
    WELCOME(false), HEIGHT(true), WEIGHT(true), EXPERIENCE(false), BODY_AREAS(true),
    GOALS(true), DURATION(true), FREQUENCY(true), DONE(false);

    companion object {
        /** Steps that are actual questions (for "Question n of m"). */
        val questions = entries.filter { it != WELCOME && it != DONE }
    }
}

/** In-progress answers. Nothing here is written until the user presses Continue on that step. */
data class OnboardingAnswers(
    val heightCm: Float? = null,
    val weightKg: Float? = null,
    val heightUnit: HeightUnit = HeightUnit.CM,
    val weightUnit: WeightUnit = WeightUnit.KG,
    val experience: ExperienceLevel? = null,
    val bodyAreas: Map<BodyArea, BodyAreaIntent?> = emptyMap(),
    val noParticularArea: Boolean = false,
    val goals: Set<WellnessGoal> = emptySet(),
    val minutes: Int? = null,
    val frequency: PracticeFrequency? = null,
) {
    fun toggleArea(a: BodyArea) = copy(bodyAreas = if (a in bodyAreas) bodyAreas - a else bodyAreas + (a to null), noParticularArea = false)
    fun setIntent(a: BodyArea, i: BodyAreaIntent?) = if (a in bodyAreas) copy(bodyAreas = bodyAreas + (a to i)) else this
    /** "No particular area" is exclusive with specific areas. */
    fun toggleNoParticular() = if (noParticularArea) copy(noParticularArea = false) else copy(noParticularArea = true, bodyAreas = emptyMap())
    fun toggleGoal(g: WellnessGoal) = copy(goals = if (g in goals) goals - g else goals + g)

    companion object {
        val DURATIONS = listOf(5, 10, 15, 20, 30)

        /** Prefills from stored data (resume / edit). Experience is prefilled only for existing profiles. */
        fun from(v: PersonalisationView, prefillExperience: Boolean) = OnboardingAnswers(
            heightCm = v.p.heightCm, weightKg = v.p.weightKg, heightUnit = v.p.heightUnit, weightUnit = v.p.weightUnit,
            experience = if (prefillExperience) v.experienceLevel else null,
            bodyAreas = v.p.bodyAreas, noParticularArea = v.p.noParticularArea, goals = v.p.goals,
            minutes = v.preferredMinutes?.takeIf { it in DURATIONS }, frequency = v.p.weeklyFrequency,
        )
    }
}

/** Navigation rules. Back never discards answers; Skip leaves that step's stored value unchanged. */
object OnboardingRules {
    fun next(s: OnboardingStep): OnboardingStep = OnboardingStep.entries[(s.ordinal + 1).coerceAtMost(OnboardingStep.DONE.ordinal)]
    fun back(s: OnboardingStep): OnboardingStep = OnboardingStep.entries[(s.ordinal - 1).coerceAtLeast(0)]

    /** Progress 0..1 across the questions (Welcome = 0, Done = 1). */
    fun progress(s: OnboardingStep): Float = when (s) {
        OnboardingStep.WELCOME -> 0f
        OnboardingStep.DONE -> 1f
        else -> (OnboardingStep.questions.indexOf(s) + 1f) / OnboardingStep.questions.size
    }

    /** Continue is enabled only with a valid answer. Optional steps can still be skipped. */
    fun canContinue(s: OnboardingStep, a: OnboardingAnswers, heightInput: Measurement, weightInput: Measurement): Boolean = when (s) {
        OnboardingStep.WELCOME, OnboardingStep.DONE -> true
        OnboardingStep.HEIGHT -> heightInput is Measurement.Valid
        OnboardingStep.WEIGHT -> weightInput is Measurement.Valid
        OnboardingStep.EXPERIENCE -> a.experience != null
        OnboardingStep.BODY_AREAS -> a.bodyAreas.isNotEmpty() || a.noParticularArea
        OnboardingStep.GOALS -> a.goals.isNotEmpty()
        OnboardingStep.DURATION -> a.minutes != null
        OnboardingStep.FREQUENCY -> a.frequency != null
    }
}

const val BODY_SAFETY_NOTE =
    "YogaSenseAI provides general wellness guidance, not medical diagnosis or treatment. Avoid movements that cause pain and consult a qualified healthcare professional for persistent or serious symptoms."
