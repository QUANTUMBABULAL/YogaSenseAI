package com.chinmai.yogasense.profile

import com.chinmai.yogasense.catalog.AsanaCatalog
import com.chinmai.yogasense.catalog.AsanaInfo
import com.chinmai.yogasense.catalog.Category
import com.chinmai.yogasense.catalog.Difficulty
import com.chinmai.yogasense.data.db.ExperienceLevel

/**
 * General wellness suggestions from the user's own answers and the catalogue's own data
 * (difficulty, categories, hold times). Pure and deterministic.
 *
 * What it does NOT do: it never excludes or prescribes a pose because of a body area, never
 * claims to treat anything, and the pose-assessment engine does not use any of this.
 */
object PracticeRecommender {
    /** Goal → catalogue categories it relates to. Goals without a category (stress, sleep…) don't reorder poses. */
    private val goalCategories = mapOf(
        WellnessGoal.FLEXIBILITY to setOf(Category.STRETCH),
        WellnessGoal.STRENGTH to setOf(Category.STRENGTH),
        WellnessGoal.BALANCE to setOf(Category.BALANCE),
        WellnessGoal.POSTURE to setOf(Category.STANDING),
    )

    data class Suggestion(
        val featured: AsanaInfo,
        /** All poses, most relevant first (nothing removed). */
        val ordered: List<AsanaInfo>,
        /** A short session idea sized to the preferred duration, or null if no duration was chosen. */
        val sessionPlan: SessionPlan?,
        /** Plain-language reasons, from the user's answers only. */
        val reasons: List<String>,
    )

    data class SessionPlan(val minutes: Int, val poses: List<AsanaInfo>)

    /** Number of poses that comfortably fit (each with hold, rest and set-up). Capped by the catalogue. */
    fun posesFor(minutes: Int, available: Int): Int = (when {
        minutes <= 5 -> 2
        minutes <= 10 -> 3
        minutes <= 15 -> 4
        else -> 5
    }).coerceAtMost(available)

    fun suggest(
        experience: ExperienceLevel,
        personalisation: Personalisation?,
        preferredMinutes: Int?,
        dayOfYear: Int,
        catalog: List<AsanaInfo> = AsanaCatalog.all,
    ): Suggestion {
        val goals = personalisation?.goals.orEmpty()
        val wanted = goals.flatMap { goalCategories[it].orEmpty() }.toSet()
        fun levelFit(a: AsanaInfo) = when (experience) {
            ExperienceLevel.BEGINNER -> if (a.difficulty == Difficulty.BEGINNER) 2 else 0
            ExperienceLevel.INTERMEDIATE -> 1
            ExperienceLevel.ADVANCED -> if (a.difficulty == Difficulty.INTERMEDIATE) 2 else 1
        }
        fun goalFit(a: AsanaInfo) = a.categories.count { it in wanted }
        // Stable sort: catalogue order breaks ties, so the result is deterministic.
        val ordered = catalog.sortedWith(compareByDescending<AsanaInfo> { levelFit(it) }.thenByDescending { goalFit(it) })
        val top = ordered.filter { levelFit(it) == levelFit(ordered.first()) && goalFit(it) == goalFit(ordered.first()) }
        val featured = top[Math.floorMod(dayOfYear, top.size)]
        val reasons = buildList {
            add(
                when (experience) {
                    ExperienceLevel.BEGINNER -> "Beginner-friendly poses first"
                    ExperienceLevel.INTERMEDIATE -> "A mix for your current level"
                    ExperienceLevel.ADVANCED -> "More challenging poses first"
                },
            )
            val related = goals.filter { it in goalCategories }
            if (related.isNotEmpty()) add("Related to ${related.joinToString { it.label.lowercase() }}")
        }
        return Suggestion(
            featured = featured,
            ordered = ordered,
            sessionPlan = preferredMinutes?.let { m -> SessionPlan(m, ordered.take(posesFor(m, ordered.size))) },
            reasons = reasons,
        )
    }
}
