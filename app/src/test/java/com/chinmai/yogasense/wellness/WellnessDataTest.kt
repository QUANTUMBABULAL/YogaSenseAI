package com.chinmai.yogasense.wellness

import com.chinmai.yogasense.data.ConsentRequiredException
import com.chinmai.yogasense.data.WellnessRepository
import com.chinmai.yogasense.ui.wellness.WellnessOverviewBuilder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.TimeZone

/**
 * TEST DOUBLE ONLY — an in-memory implementation of the [WellnessRepository] contract used to test
 * consent and one-per-day rules on the JVM. Real persistence is SqliteWellnessRepository
 * (covered by the instrumented test in androidTest).
 */
class FakeWellnessRepository : WellnessRepository {
    private val days = sortedMapOf<Long, DailyCheckIn>()
    private val records = mutableListOf<QuestionnaireRecord>()
    override val checkIns = MutableStateFlow<List<DailyCheckIn>>(emptyList())
    override val questionnaires = MutableStateFlow<List<QuestionnaireRecord>>(emptyList())
    override val consent: StateFlow<WellnessConsent> get() = _consent
    private val _consent = MutableStateFlow(WellnessConsent.UNKNOWN)
    private fun publish() { checkIns.value = days.values.reversed(); questionnaires.value = records.sortedByDescending { it.completedAtMillis } }
    override suspend fun upsertCheckIn(checkIn: DailyCheckIn) { if (_consent.value != WellnessConsent.GRANTED) throw ConsentRequiredException(); days[checkIn.epochDay] = checkIn; publish() }
    override suspend fun deleteCheckIn(epochDay: Long) { days.remove(epochDay); publish() }
    override suspend fun addQuestionnaire(record: QuestionnaireRecord): String {
        if (_consent.value != WellnessConsent.GRANTED) throw ConsentRequiredException()
        val r = record.copy(id = (records.size + 1).toString()); records += r; publish(); return r.id
    }
    override suspend fun setConsent(consent: WellnessConsent) { _consent.value = consent }
    override suspend fun deleteAll() { days.clear(); records.clear(); _consent.value = WellnessConsent.UNKNOWN; publish() }
}

class CheckInValidatorTest {
    private fun v(c: DailyCheckIn) = CheckInValidator.validate(c)

    @Test fun emptyEntryIsRejected() {
        assertTrue(v(DailyCheckIn(1)) is CheckInValidation.Invalid)
        assertTrue(v(DailyCheckIn(1, reflection = "   ")) is CheckInValidation.Invalid)
    }

    @Test fun everyFieldIsOptionalOnItsOwn() {
        listOf(DailyCheckIn(1, mood = 3), DailyCheckIn(1, stress = 1), DailyCheckIn(1, sleepHours = 7.5f),
            DailyCheckIn(1, sleepQuality = 5), DailyCheckIn(1, reflection = "ok")).forEach {
            assertTrue("$it", v(it) is CheckInValidation.Valid)
        }
    }

    @Test fun rangesAndSteps() {
        assertTrue(v(DailyCheckIn(1, mood = 0)) is CheckInValidation.Invalid)
        assertTrue(v(DailyCheckIn(1, stress = 6)) is CheckInValidation.Invalid)
        assertTrue(v(DailyCheckIn(1, sleepHours = 7.25f)) is CheckInValidation.Invalid)
        assertTrue(v(DailyCheckIn(1, sleepHours = 17f)) is CheckInValidation.Invalid)
        assertTrue(v(DailyCheckIn(1, sleepHours = 0f)) is CheckInValidation.Valid)
    }

    @Test fun reflectionTrimmedAndLimited() {
        assertEquals("hello", (v(DailyCheckIn(1, reflection = "  hello  ")) as CheckInValidation.Valid).checkIn.reflection)
        assertTrue(v(DailyCheckIn(1, reflection = "x".repeat(501))) is CheckInValidation.Invalid)
    }

    @Test fun noDefaultValuesInvented() {
        val c = DailyCheckIn(1)
        assertNull(c.mood); assertNull(c.stress); assertNull(c.sleepHours); assertNull(c.sleepQuality); assertNull(c.reflection)
    }
}

class WellnessRepositoryContractTest {
    @Test fun consentRequiredBeforeSaving() = runBlocking {
        val r = FakeWellnessRepository()
        try { r.upsertCheckIn(DailyCheckIn(1, mood = 3)); fail("expected consent exception") } catch (_: ConsentRequiredException) {}
        r.setConsent(WellnessConsent.DECLINED)
        try { r.addQuestionnaire(QuestionnaireRecord("", QuestionnaireType.GAD7, 0, List(7) { 0 }, 0, "Minimal", null, false)); fail() } catch (_: ConsentRequiredException) {}
        assertTrue(r.checkIns.value.isEmpty() && r.questionnaires.value.isEmpty())
    }

    @Test fun onePerDayAndEditing() = runBlocking {
        val r = FakeWellnessRepository(); r.setConsent(WellnessConsent.GRANTED)
        r.upsertCheckIn(DailyCheckIn(100, mood = 2))
        r.upsertCheckIn(DailyCheckIn(100, mood = 4, sleepHours = 8f))   // edit same day
        r.upsertCheckIn(DailyCheckIn(101, stress = 3))
        assertEquals(2, r.checkIns.value.size)
        assertEquals(4, r.checkIns.value.first { it.epochDay == 100L }.mood)
        assertEquals(101L, r.checkIns.value.first().epochDay)            // newest first
    }

    @Test fun deleteAllClearsDataAndConsent() = runBlocking {
        val r = FakeWellnessRepository(); r.setConsent(WellnessConsent.GRANTED)
        r.upsertCheckIn(DailyCheckIn(1, mood = 3))
        r.addQuestionnaire(QuestionnaireRecord("", QuestionnaireType.PHQ9, 5, List(9) { 0 }, 0, "Minimal", null, false))
        r.deleteAll()
        assertTrue(r.checkIns.value.isEmpty() && r.questionnaires.value.isEmpty())
        assertEquals(WellnessConsent.UNKNOWN, r.consent.value)
    }
}

class WellnessInsightsTest {
    private val day = 20_000L

    @Test fun seriesKeepsGapsAndNeedsThreeEntriesForAverage() {
        val entries = listOf(DailyCheckIn(day, mood = 4), DailyCheckIn(day - 2, mood = 2))
        val s = WellnessInsights.series("Mood", entries, day, 7) { it.mood?.toFloat() }
        assertEquals(7, s.points.size)
        assertEquals(listOf(null, null, null, null, 2f, null, 4f), s.points.map { it.value })
        assertEquals(2, s.recordedDays)
        assertNull(s.average)
        val s3 = WellnessInsights.series("Mood", entries + DailyCheckIn(day - 1, mood = 3), day, 7) { it.mood?.toFloat() }
        assertEquals(3f, s3.average!!, 1e-6f)
        assertEquals("Last 7 days", s3.periodLabel)
    }

    @Test fun emptySeries() {
        val s = WellnessInsights.series("Sleep", emptyList(), day, 14) { it.sleepHours }
        assertEquals(0, s.recordedDays); assertNull(s.average); assertTrue(s.points.all { it.value == null })
    }

    @Test fun comparisonOnlyWithPriorSameTypeAndNeutralWording() {
        val a = QuestionnaireRecord("1", QuestionnaireType.PHQ9, 1_000, List(9) { 1 }, 9, "Mild", null, false)
        val g = QuestionnaireRecord("2", QuestionnaireType.GAD7, 2_000, List(7) { 1 }, 7, "Mild", null, false)
        val b = QuestionnaireRecord("3", QuestionnaireType.PHQ9, 3_000, List(9) { 0 }, 6, "Mild", null, false)
        assertNull(WellnessInsights.comparison(a, listOf(a, g)))
        val c = WellnessInsights.comparison(b, listOf(a, g, b))!!
        assertEquals(-3, c.difference)
        val text = c.describe("1 Nov")
        assertEquals("3 points lower than your previous result on 1 Nov.", text)
        assertFalse(text.contains("improv", ignoreCase = true) || text.contains("better", ignoreCase = true))
    }

    @Test fun questionnaireNotDailyDueAfterTwoWeeks() {
        val dayMs = 86_400_000L
        val rec = QuestionnaireRecord("1", QuestionnaireType.GAD7, 0, List(7) { 0 }, 0, "Minimal", null, false)
        assertTrue(WellnessInsights.isQuestionnaireDue(QuestionnaireType.GAD7, emptyList(), 0))
        assertFalse(WellnessInsights.isQuestionnaireDue(QuestionnaireType.GAD7, listOf(rec), 3 * dayMs))
        assertTrue(WellnessInsights.isQuestionnaireDue(QuestionnaireType.GAD7, listOf(rec), 14 * dayMs))
        assertTrue(WellnessInsights.isQuestionnaireDue(QuestionnaireType.PHQ9, listOf(rec), 3 * dayMs))
    }

    @Test fun weeklyReflectionHonestAboutData() {
        val empty = WellnessInsights.weekly(emptyList(), day, null)
        assertTrue(empty.isEmpty); assertNull(empty.averageSleepHours); assertNull(empty.practiceSessions)
        val two = WellnessInsights.weekly(listOf(DailyCheckIn(day, sleepHours = 7f), DailyCheckIn(day - 1, sleepHours = 8f)), day, null)
        assertEquals(2, two.checkIns); assertFalse(two.hasEnoughForPatterns); assertNull(two.averageSleepHours)
        val entries = listOf(
            DailyCheckIn(day, mood = 4, sleepHours = 7f), DailyCheckIn(day - 1, mood = 3, sleepHours = 8f),
            DailyCheckIn(day - 3, mood = 5, sleepHours = 6f), DailyCheckIn(day - 8, mood = 1, sleepHours = 2f), // outside the week
        )
        val w = WellnessInsights.weekly(entries, day, null)
        assertEquals(3, w.checkIns); assertTrue(w.hasEnoughForPatterns)
        assertEquals(7f, w.averageSleepHours!!, 1e-6f)
        assertEquals(4f, w.averageMood!!, 1e-6f)
        assertNull(w.averageStress)                       // no stress entries: not invented
        assertTrue(w.prompt.isNotBlank())
        assertEquals("Good", WellnessInsights.describeAverage(Scales.MOOD, 4f))
    }

    @Test fun overviewEmptyState() {
        val s = WellnessOverviewBuilder.build(day, emptyList(), emptyList(), WellnessConsent.UNKNOWN)
        assertNull(s.today); assertNull(s.lastPhq9); assertNull(s.lastGad7)
        assertEquals(0, s.mood.recordedDays); assertTrue(s.weekly.isEmpty)
    }

    @Test fun epochDayUsesLocalCalendar() {
        val ist = TimeZone.getTimeZone("Asia/Kolkata")
        // 2026-11-13 20:00 UTC = 2026-11-14 01:30 IST.
        val millis = 1_794_600_000_000L
        assertEquals(WellnessDates.epochDay(millis, TimeZone.getTimeZone("UTC")) + 1, WellnessDates.epochDay(millis, ist))
    }

    @Test fun crisisContentIsPresentAndAccurate() {
        assertEquals("14416", CrisisSupport.TELE_MANAS.number)
        assertEquals("112", CrisisSupport.EMERGENCY.number)
        assertTrue(CrisisSupport.LIMITS.contains("has not contacted anyone"))
        listOf(CrisisSupport.INTRO, CrisisSupport.IMMEDIATE, CrisisSupport.NOT_IMMEDIATE).forEach {
            assertFalse(it.contains("diagnos", ignoreCase = true))
        }
    }
}
