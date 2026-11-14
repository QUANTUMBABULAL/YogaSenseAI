package com.chinmai.yogasense.profile

import com.chinmai.yogasense.data.db.ExperienceLevel
import com.chinmai.yogasense.ui.onboarding.OnboardingController
import com.chinmai.yogasense.ui.onboarding.OnboardingMode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MeasurementScalesTest {
    private val cm = MeasurementScales.HEIGHT_CM
    private val inch = MeasurementScales.HEIGHT_IN
    private val kg = MeasurementScales.WEIGHT_KG
    private val lb = MeasurementScales.WEIGHT_LB

    @Test fun cmAndFeetInchesConvertBothWays() {
        assertEquals("5′ 7″", inch.format(67))
        assertEquals(170.18f, inch.toCanonical(67), 1e-4f)
        assertEquals(67, inch.fromCanonical(170f))          // 170 cm → 5′ 7″
        assertEquals(170, cm.fromCanonical(inch.toCanonical(67)))  // 5′ 7″ → 170 cm
        assertEquals("6′ 0″", inch.format(72))
        assertEquals(183, cm.fromCanonical(inch.toCanonical(72)))
        assertEquals("≈ 5′ 7″", MeasurementScales.heightConverted(170f, HeightUnit.CM))
        assertEquals("≈ 170 cm", MeasurementScales.heightConverted(170.18f, HeightUnit.FT_IN))
        // Every inch on the ruler maps back to itself exactly.
        (inch.min..inch.max).forEach { assertEquals(it, inch.fromCanonical(inch.toCanonical(it))) }
        (cm.min..cm.max).forEach { assertEquals(it, cm.fromCanonical(cm.toCanonical(it))) }
    }

    @Test fun kgAndLbsConvertBothWays() {
        assertEquals(64.86f, lb.toCanonical(143), 0.01f)
        assertEquals(143, lb.fromCanonical(lb.toCanonical(143)))
        assertEquals(154, lb.fromCanonical(70f))
        assertEquals(649, kg.fromCanonical(lb.toCanonical(143)))   // 64.9 kg shown
        assertEquals("64.9", kg.format(649)); assertEquals("70", kg.format(700))
        assertEquals("≈ 154 lbs", MeasurementScales.weightConverted(70f, WeightUnit.KG))
        assertEquals("≈ 64.9 kg", MeasurementScales.weightConverted(lb.toCanonical(143), WeightUnit.LB))
        (kg.min..kg.max).forEach { assertEquals(it, kg.fromCanonical(kg.toCanonical(it))) }
        (lb.min..lb.max).forEach { assertEquals(it, lb.fromCanonical(lb.toCanonical(it))) }
    }

    @Test fun unitSwitchesNeverDrift() {
        // Re-snapping through the other unit WOULD drift (70 kg → 154 lb → 69.9 kg)…
        assertEquals(699, kg.fromCanonical(lb.toCanonical(lb.fromCanonical(70f))))
        // …so the controller never re-snaps on a unit switch: 100 round trips keep the exact value.
        val c = OnboardingController(PersonalisationView(null, ExperienceLevel.BEGINNER, null), OnboardingMode.Optional) { _, _, _, _, _ -> true }
        c.setWeightIndex(700); c.setHeightIndex(170)
        repeat(100) {
            c.setWeightUnit(WeightUnit.LB); c.setWeightUnit(WeightUnit.KG)
            c.setHeightUnit(HeightUnit.FT_IN); c.setHeightUnit(HeightUnit.CM)
        }
        assertEquals(70f, c.state.value.answers.weightKg); assertEquals(700, c.state.value.weightIndex)
        assertEquals(170f, c.state.value.answers.heightCm); assertEquals(170, c.state.value.heightIndex)
    }

    @Test fun boundariesClampAndStayInsideValidation() {
        listOf(cm, inch).forEach { s ->
            assertEquals(s.min, s.clamp(-5)); assertEquals(s.max, s.clamp(10_000))
            assertTrue(BodyMetrics.checkHeightCm(s.toCanonical(s.min)) is Measurement.Valid)
            assertTrue(BodyMetrics.checkHeightCm(s.toCanonical(s.max)) is Measurement.Valid)
        }
        listOf(kg, lb).forEach { s ->
            assertTrue(BodyMetrics.checkWeightKg(s.toCanonical(s.min)) is Measurement.Valid)
            assertTrue(BodyMetrics.checkWeightKg(s.toCanonical(s.max)) is Measurement.Valid)
        }
        assertEquals(100, cm.fromCanonical(10f)); assertEquals(250, cm.fromCanonical(900f))
        assertTrue(BodyMetrics.checkHeightCm(260f) is Measurement.Invalid)
        assertTrue(BodyMetrics.checkWeightKg(10f) is Measurement.Invalid)
        assertEquals(Measurement.Empty, BodyMetrics.checkHeightCm(null))
    }

    @Test fun tickLevelsAndLabels() {
        assertEquals(TickLevel.MAJOR, cm.tickLevel(170)); assertEquals(TickLevel.MEDIUM, cm.tickLevel(175)); assertEquals(TickLevel.MINOR, cm.tickLevel(171))
        assertEquals(TickLevel.MAJOR, inch.tickLevel(60)); assertEquals("5 ft", inch.label(60)); assertEquals(TickLevel.MEDIUM, inch.tickLevel(66))
        assertEquals(TickLevel.MAJOR, kg.tickLevel(650)); assertEquals("65", kg.label(650)); assertEquals(TickLevel.MEDIUM, kg.tickLevel(655))
    }
}

class RulerMathTest {
    private val s = MeasurementScales.HEIGHT_CM
    private val sp = 30f

    @Test fun offsetsAndIndices() {
        assertEquals(0f, RulerMath.offsetFor(100, s, sp))
        assertEquals(70 * sp, RulerMath.offsetFor(170, s, sp))
        assertEquals(170, RulerMath.indexAt(70 * sp + 14f, s, sp))   // rounds to the nearest increment
        assertEquals(171, RulerMath.indexAt(70 * sp + 16f, s, sp))
        assertEquals(100, RulerMath.indexAt(-500f, s, sp)); assertEquals(250, RulerMath.indexAt(1e9f, s, sp))
        assertEquals(150 * sp, RulerMath.maxOffset(s, sp))
    }

    @Test fun flingsSettleExactlyOnIncrementsInsideTheRange() {
        assertEquals(70 * sp, RulerMath.snap(70 * sp + 9.9f, s, sp))
        assertEquals(71 * sp, RulerMath.snap(70 * sp + 21f, s, sp))
        assertEquals(0f, RulerMath.snap(-1234f, s, sp))
        assertEquals(RulerMath.maxOffset(s, sp), RulerMath.snap(99_999f, s, sp))
        (0..2000).forEach { px -> assertEquals(0f, RulerMath.snap(px.toFloat(), s, sp) % sp) }
    }

    @Test fun hapticsOnlyOnMeaningfulTicksAndRateLimited() {
        assertFalse(RulerMath.shouldTick(TickLevel.MINOR, 1_000, 0))
        assertTrue(RulerMath.shouldTick(TickLevel.MEDIUM, 1_000, 0))
        assertFalse(RulerMath.shouldTick(TickLevel.MAJOR, 1_020, 1_000))
        assertEquals(1f, RulerMath.edgeAlpha(0f, 100f)); assertEquals(0.12f, RulerMath.edgeAlpha(100f, 100f), 1e-4f)
    }
}

class RulerOnboardingTest {
    private fun noop(): suspend (OnboardingStep, OnboardingAnswers, OnboardingStep, Boolean, Boolean) -> Boolean = { _, _, _, _, _ -> true }

    @Test fun skippedMeasurementsStayEmptyAndEditingPrefillsTheRuler() = runBlocking<Unit> {
        val c = OnboardingController(PersonalisationView(Personalisation(onboardingRequired = true), ExperienceLevel.BEGINNER, null), OnboardingMode.Required, noop())
        c.next(); c.skip(); c.skip()
        assertNull(c.state.value.answers.heightCm); assertNull(c.state.value.answers.weightKg)
        assertNull(c.state.value.heightIndex)                // ruler shows a muted default, nothing chosen
        // Existing profile editing its stored weight in lbs: the ruler starts at the stored value.
        val stored = PersonalisationView(Personalisation(weightKg = MeasurementScales.WEIGHT_LB.toCanonical(150), weightUnit = WeightUnit.LB), ExperienceLevel.ADVANCED, null)
        val e = OnboardingController(stored, OnboardingMode.Edit(OnboardingStep.WEIGHT), noop())
        assertEquals(150, e.state.value.weightIndex)
        e.setWeightIndex(151)
        assertEquals(151, MeasurementScales.WEIGHT_LB.fromCanonical(e.state.value.answers.weightKg!!))
        assertTrue(e.state.value.canContinue)
    }

    @Test fun rulerInputIsClampedSoInvalidValuesCannotBeSaved() {
        val c = OnboardingController(PersonalisationView(null, ExperienceLevel.BEGINNER, null), OnboardingMode.Optional, noop())
        c.setHeightIndex(9_999); assertEquals(250f, c.state.value.answers.heightCm)
        c.setHeightUnit(HeightUnit.FT_IN); c.setHeightIndex(0); assertEquals(40 * 2.54f, c.state.value.answers.heightCm!!, 1e-4f)
        c.setWeightIndex(-3); assertEquals(25f, c.state.value.answers.weightKg)
    }
}
