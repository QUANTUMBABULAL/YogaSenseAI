package com.chinmai.yogasense.profile

import com.chinmai.yogasense.asana.Asana
import com.chinmai.yogasense.catalog.AsanaCatalog
import com.chinmai.yogasense.catalog.Difficulty
import com.chinmai.yogasense.data.db.ExperienceLevel
import com.chinmai.yogasense.ui.onboarding.OnboardingController
import com.chinmai.yogasense.ui.onboarding.OnboardingMode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BodyMetricsTest {
    @Test fun heightValidation() {
        assertEquals(Measurement.Empty, BodyMetrics.heightCm(" "))
        assertEquals(Measurement.Valid(172.5f), BodyMetrics.heightCm("172,5"))
        assertTrue(BodyMetrics.heightCm("99") is Measurement.Invalid)
        assertTrue(BodyMetrics.heightCm("251") is Measurement.Invalid)
        assertTrue(BodyMetrics.heightCm("17a") is Measurement.Invalid)
        assertTrue(BodyMetrics.heightCm("1.7.2") is Measurement.Invalid)
        assertEquals(Measurement.Valid(170.2f), BodyMetrics.heightFtIn("5", "7"))
        assertTrue(BodyMetrics.heightFtIn("5", "12") is Measurement.Invalid)
        assertTrue(BodyMetrics.heightFtIn("9", "0") is Measurement.Invalid)
        assertTrue(BodyMetrics.heightFtIn("2", "0") is Measurement.Invalid)
    }

    @Test fun weightValidationAndUnits() {
        assertEquals(Measurement.Valid(62f), BodyMetrics.weightKg("62", WeightUnit.KG))
        assertEquals(64.9f, (BodyMetrics.weightKg("143", WeightUnit.LB) as Measurement.Valid).value, 0.05f)
        assertTrue(BodyMetrics.weightKg("20", WeightUnit.KG) is Measurement.Invalid)
        assertTrue(BodyMetrics.weightKg("700", WeightUnit.LB) is Measurement.Invalid)
        assertTrue(BodyMetrics.weightKg("-5", WeightUnit.KG) is Measurement.Invalid)
        assertEquals("5" to "7", BodyMetrics.feetInches(170.2f))
        val lb = (BodyMetrics.weightKg("143", WeightUnit.LB) as Measurement.Valid).value
        assertEquals("143", BodyMetrics.weightText(lb, WeightUnit.LB)) // round-trips unchanged
        assertEquals("143.1", BodyMetrics.weightText(64.9f, WeightUnit.LB))
    }
}

class OnboardingControllerTest {
    private class Recorder {
        val calls = mutableListOf<Triple<OnboardingStep, OnboardingStep, Boolean>>()
        val save: suspend (OnboardingStep, OnboardingAnswers, OnboardingStep, Boolean, Boolean) -> Boolean = { s, _, n, skipped, _ -> calls += Triple(s, n, skipped); true }
    }

    private fun newProfile() = PersonalisationView(Personalisation(onboardingRequired = true), ExperienceLevel.BEGINNER, null)

    @Test fun continueRequiresAValidAnswerAndOptionalStepsCanBeSkipped() = runBlocking<Unit> {
        val r = Recorder()
        val c = OnboardingController(newProfile(), OnboardingMode.Required, r.save)
        assertFalse(c.canGoBack()) // first screen of the required flow
        c.next()
        assertEquals(OnboardingStep.HEIGHT, c.state.value.step)
        assertFalse("untouched ruler = no height chosen", c.state.value.canContinue)
        c.next(); assertEquals(OnboardingStep.HEIGHT, c.state.value.step) // nothing chosen → nothing saved
        assertTrue(r.calls.none { it.first == OnboardingStep.HEIGHT })
        assertTrue(c.canSkip())
        c.skip()
        assertEquals(OnboardingStep.WEIGHT, c.state.value.step)
        assertEquals(Triple(OnboardingStep.HEIGHT, OnboardingStep.WEIGHT, true), r.calls.last())
        c.skip(); assertEquals(OnboardingStep.EXPERIENCE, c.state.value.step)
        assertFalse("experience is required", c.canSkip()); c.skip(); assertEquals(OnboardingStep.EXPERIENCE, c.state.value.step)
        assertFalse(c.state.value.canContinue)
        c.setExperience(ExperienceLevel.BEGINNER); assertTrue(c.state.value.canContinue)
    }

    @Test fun backPreservesAnswersAndUnitSwitchKeepsTheMeasurement() = runBlocking<Unit> {
        val c = OnboardingController(newProfile(), OnboardingMode.Required, Recorder().save)
        c.next(); c.setHeightIndex(180); c.next()
        c.setWeightIndex(700); c.setWeightUnit(WeightUnit.LB)            // 70.0 kg
        assertEquals(70f, c.state.value.answers.weightKg)                // unit switch: canonical unchanged
        assertEquals(154, c.state.value.weightIndex)                     // shown as 154 lbs
        assertTrue(c.back()); assertEquals(OnboardingStep.HEIGHT, c.state.value.step)
        assertEquals(180f, c.state.value.answers.heightCm)
        c.setHeightUnit(HeightUnit.FT_IN)
        assertEquals(71, c.state.value.heightIndex)                      // 5′ 11″
        assertEquals(180f, c.state.value.answers.heightCm)
        c.setHeightUnit(HeightUnit.CM); assertEquals(180, c.state.value.heightIndex) // back and forth: no drift
        c.next() // weight again: still there
        assertEquals(70f, c.state.value.answers.weightKg)
        assertEquals(-1, OnboardingController(newProfile(), OnboardingMode.Required, Recorder().save).let { it.next(); it.back(); it.state.value.direction })
    }

    @Test fun multipleSelectionAndExclusiveNoParticularArea() {
        var a = OnboardingAnswers().toggleArea(BodyArea.NECK).toggleArea(BodyArea.KNEES).toggleArea(BodyArea.NECK)
        assertEquals(setOf(BodyArea.KNEES), a.bodyAreas.keys)
        a = a.setIntent(BodyArea.KNEES, BodyAreaIntent.DISCOMFORT).setIntent(BodyArea.WRISTS, BodyAreaIntent.MOBILITY)
        assertEquals(mapOf(BodyArea.KNEES to BodyAreaIntent.DISCOMFORT), a.bodyAreas) // intent only for selected areas
        a = a.toggleNoParticular()
        assertTrue(a.noParticularArea); assertTrue(a.bodyAreas.isEmpty())
        a = a.toggleArea(BodyArea.HIPS)
        assertFalse(a.noParticularArea)
        val g = OnboardingAnswers().toggleGoal(WellnessGoal.SLEEP).toggleGoal(WellnessGoal.BALANCE).toggleGoal(WellnessGoal.SLEEP)
        assertEquals(setOf(WellnessGoal.BALANCE), g.goals)
    }

    @Test fun resumesAtStoredStepAndCompletesOnlyOnFinalCta() = runBlocking<Unit> {
        val r = Recorder()
        val stored = PersonalisationView(Personalisation(onboardingRequired = true, status = OnboardingStatus.IN_PROGRESS, step = OnboardingStep.GOALS.ordinal), ExperienceLevel.BEGINNER, null)
        val c = OnboardingController(stored, OnboardingMode.Required, r.save)
        assertEquals(OnboardingStep.GOALS, c.state.value.step)
        c.skip(); c.setMinutes(10); c.next(); c.setFrequency(PracticeFrequency.FLEXIBLE); c.next()
        assertEquals(OnboardingStep.DONE, c.state.value.step)
        assertFalse(c.state.value.finished)
        c.next()
        assertTrue(c.state.value.finished)
        assertEquals(OnboardingStep.DONE, r.calls.last().first)
    }

    @Test fun failedSaveStaysOnTheStepWithAnError() = runBlocking<Unit> {
        val c = OnboardingController(newProfile(), OnboardingMode.Required) { _, _, _, _, _ -> false }
        c.next()
        assertEquals(OnboardingStep.WELCOME, c.state.value.step)
        assertTrue(c.state.value.error != null)
    }

    @Test fun optionalAndEditModes() = runBlocking<Unit> {
        val existing = PersonalisationView(null, ExperienceLevel.INTERMEDIATE, 15)
        val opt = OnboardingController(existing, OnboardingMode.Optional, Recorder().save)
        assertEquals(ExperienceLevel.INTERMEDIATE, opt.state.value.answers.experience)
        assertEquals(15, opt.state.value.answers.minutes)
        assertTrue(opt.back()); assertTrue(opt.state.value.finished) // back on welcome closes the optional flow
        val r = Recorder()
        val edit = OnboardingController(existing, OnboardingMode.Edit(OnboardingStep.DURATION), r.save)
        assertFalse(edit.canSkip())
        edit.setMinutes(30); edit.next()
        assertTrue(edit.state.value.finished)
        assertEquals(Triple(OnboardingStep.DURATION, OnboardingStep.DURATION, false), r.calls.single())
    }

    @Test fun gateRules() {
        assertTrue(PersonalisationView(Personalisation(onboardingRequired = true), ExperienceLevel.BEGINNER, null).mustOnboard)
        assertFalse(PersonalisationView(Personalisation(onboardingRequired = true, status = OnboardingStatus.COMPLETED), ExperienceLevel.BEGINNER, null).mustOnboard)
        val old = PersonalisationView(null, ExperienceLevel.ADVANCED, null)
        assertFalse(old.mustOnboard); assertTrue(old.showPrompt)
        assertFalse(PersonalisationView(Personalisation(promptDismissed = true), ExperienceLevel.BEGINNER, null).showPrompt)
        assertEquals(0f, OnboardingRules.progress(OnboardingStep.WELCOME)); assertEquals(1f, OnboardingRules.progress(OnboardingStep.DONE))
        assertEquals(1f / 7, OnboardingRules.progress(OnboardingStep.HEIGHT), 1e-4f)
    }
}

class PracticeRecommenderTest {
    @Test fun experienceOrdersButNeverRemovesPoses() {
        val b = PracticeRecommender.suggest(ExperienceLevel.BEGINNER, null, null, 0)
        assertEquals(AsanaCatalog.all.size, b.ordered.size)
        assertTrue(b.ordered.last().difficulty != Difficulty.BEGINNER)
        assertTrue(b.featured.liveSupported) // today's practice is always a pose the camera can follow
        assertEquals(Difficulty.BEGINNER, b.featured.difficulty)
        val adv = PracticeRecommender.suggest(ExperienceLevel.ADVANCED, null, null, 0)
        assertEquals(Asana.TRIANGLE, adv.featured.asana)
        assertTrue(adv.ordered.first().difficulty != Difficulty.BEGINNER)
        assertNull(b.sessionPlan)
    }

    @Test fun goalsReorderAndBodyAreasDoNotExcludeAnything() {
        val withGoal = PracticeRecommender.suggest(ExperienceLevel.BEGINNER, Personalisation(goals = setOf(WellnessGoal.BALANCE)), null, 0)
        assertTrue(withGoal.ordered.first().categories.any { it == com.chinmai.yogasense.catalog.Category.BALANCE })
        assertTrue(withGoal.reasons.any { "balance" in it })
        val base = PracticeRecommender.suggest(ExperienceLevel.BEGINNER, Personalisation(), null, 3)
        val withAreas = PracticeRecommender.suggest(ExperienceLevel.BEGINNER, Personalisation(bodyAreas = BodyArea.entries.associateWith { BodyAreaIntent.DISCOMFORT }), null, 3)
        assertEquals(base.ordered, withAreas.ordered) // body areas never change or exclude poses
        assertEquals(base.featured, withAreas.featured)
    }

    @Test fun sessionPlanFollowsPreferredDuration() {
        assertEquals(listOf(2, 3, 4, 5, 5), listOf(5, 10, 15, 20, 30).map { PracticeRecommender.posesFor(it, 5) })
        val s = PracticeRecommender.suggest(ExperienceLevel.INTERMEDIATE, null, 10, 0)
        assertEquals(10, s.sessionPlan!!.minutes); assertEquals(3, s.sessionPlan!!.poses.size)
    }
}
