package com.chinmai.yogasense.ui.practice

import com.chinmai.yogasense.asana.Asana
import com.chinmai.yogasense.coaching.assessment.PoseSummary
import com.chinmai.yogasense.coaching.assessment.SessionSummary
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionSuggestionsTest {
    private fun pose(a: Asana, longest: Long, align: Int? = null, stab: Int? = null, corrections: Map<String, Int> = emptyMap()) =
        PoseSummary(a, longest, longest, align, stab, corrections, emptyList())

    @Test fun noPoseGivesFramingAdvice() {
        val s = SessionSuggestions.of(SessionSummary(60_000, emptyList(), emptyList()), Asana.TREE)
        assertTrue(s.first().contains("whole body"))
        assertTrue(s.any { it.contains("Vrikshasana lesson") })
    }

    @Test fun mostFrequentCorrectionIsSuggested() {
        val s = SessionSuggestions.of(
            SessionSummary(60_000, listOf(Asana.WARRIOR_II), listOf(pose(Asana.WARRIOR_II, 25_000, 70, 80, mapOf("Level the arms." to 1, "Bend the front knee more." to 3)))),
            Asana.WARRIOR_II,
        )
        assertTrue(s.any { it.contains("Bend the front knee more") })
    }

    @Test fun shortHoldSuggestsBuildingTowardsGoal() {
        val s = SessionSuggestions.of(SessionSummary(60_000, listOf(Asana.TREE), listOf(pose(Asana.TREE, 8_000))), Asana.TREE)
        assertTrue(s.any { it.contains("Build towards 20 s") })
    }

    @Test fun unsteadyBalanceSuggestsSupport() {
        val s = SessionSuggestions.of(SessionSummary(60_000, listOf(Asana.TREE), listOf(pose(Asana.TREE, 25_000, 85, 30))), null)
        assertTrue(s.any { it.contains("wall") })
    }

    @Test fun targetNotRecognisedIsCalledOut() {
        val s = SessionSuggestions.of(SessionSummary(60_000, listOf(Asana.TADASANA), listOf(pose(Asana.TADASANA, 40_000))), Asana.CHAIR)
        assertTrue(s.first().contains("Utkatasana wasn't recognised"))
    }

    @Test fun neverMoreThanThreeAndNeverEmpty() {
        val many = SessionSummary(
            60_000, listOf(Asana.TREE, Asana.CHAIR),
            listOf(pose(Asana.TREE, 5_000, 50, 20, mapOf("x" to 1)), pose(Asana.CHAIR, 3_000, 50, 20, mapOf("y" to 1))),
        )
        assertTrue(SessionSuggestions.of(many, null).size <= SessionSuggestions.MAX)
        val good = SessionSummary(60_000, listOf(Asana.TADASANA), listOf(pose(Asana.TADASANA, 40_000, 70, 75)))
        assertTrue(SessionSuggestions.of(good, null).isNotEmpty())
    }
}
