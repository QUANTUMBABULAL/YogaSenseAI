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

    fun asana(id: String) = "asana/$id"
    fun learn(id: String) = "learn/$id"
    fun practice(asanaId: String? = null) = if (asanaId == null) "practice" else "practice?$ARG_ASANA=$asanaId"
}
