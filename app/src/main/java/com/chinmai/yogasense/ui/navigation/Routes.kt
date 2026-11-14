package com.chinmai.yogasense.ui.navigation

/** Route patterns and builders. Pure Kotlin so navigation arguments are unit-testable. */
object Routes {
    const val HOME = "home"
    const val EXPLORE = "explore"
    const val PROGRESS = "progress"
    const val PROFILE = "profile"

    const val ARG_ID = "id"
    const val ARG_ASANA = "asana"

    const val ASANA = "asana/{$ARG_ID}"
    const val LEARN = "learn/{$ARG_ID}"
    /** Optional selected asana; the plain "practice" route (Home, Progress) still matches. */
    const val PRACTICE = "practice?$ARG_ASANA={$ARG_ASANA}"

    /** Practice preparation; optional pre-selected asana (none = free practice). */
    const val PREPARE = "prepare?$ARG_ASANA={$ARG_ASANA}"
    const val PRACTICE_SETTINGS = "settings/practice"
    fun prepare(asanaId: String? = null) = if (asanaId == null) "prepare" else "prepare?$ARG_ASANA=$asanaId"

    const val WELLNESS = "wellness"
    const val CHECK_IN = "wellness/checkin"
    const val QUESTIONNAIRE = "wellness/questionnaire/{type}"
    const val RECORD = "wellness/record/{recordId}"
    const val WELLNESS_HISTORY = "wellness/history"
    const val SUPPORT = "wellness/support"
    fun questionnaire(type: String) = "wellness/questionnaire/$type"
    /** Record ids are UUIDs or "legacy7:<uuid>:q:<n>" — no "/" or "?", safe as a path segment. */
    fun record(id: String) = "wellness/record/$id"

    /** Optional onboarding (step = -1) or editing one answer (step = OnboardingStep ordinal). */
    const val ONBOARDING = "onboarding?step={step}"
    fun onboarding(step: Int = -1) = "onboarding?step=$step"

    fun asana(id: String) = "asana/$id"
    fun learn(id: String) = "learn/$id"
    fun practice(asanaId: String? = null) = if (asanaId == null) "practice" else "practice?$ARG_ASANA=$asanaId"
}
