package com.chinmai.yogasense.ui.learning

import com.chinmai.yogasense.catalog.AsanaCatalog
import com.chinmai.yogasense.catalog.AsanaInfo
import com.chinmai.yogasense.ui.navigation.Routes

/**
 * Everything the learning screen shows for one asana, read from the catalogue
 * (the single source of truth) — no instructional text lives in the UI layer.
 */
data class LessonContent(
    val info: AsanaInfo,
    val intro: String,
    val steps: List<String>,
    val breathing: List<String>,
    val focusPoints: List<String>,
    val commonMistakes: List<String>,
    val safety: List<String>,
    val demoNote: String?,
) {
    val id: String get() = info.id

    /** Both "Skip lesson" and "Continue to practice" go to live practice for this asana. */
    val practiceRoute: String get() = Routes.practice(info.id)

    companion object {
        fun of(asanaId: String?): LessonContent? = AsanaCatalog.byId(asanaId)?.let { a ->
            LessonContent(
                info = a,
                intro = a.summary,
                steps = a.steps,
                breathing = a.breathing,
                focusPoints = a.focusPoints,
                commonMistakes = a.commonMistakes,
                safety = a.safety,
                demoNote = a.demoNote,
            )
        }
    }
}
