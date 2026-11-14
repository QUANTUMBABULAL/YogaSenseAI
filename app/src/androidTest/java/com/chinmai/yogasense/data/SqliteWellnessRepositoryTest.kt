package com.chinmai.yogasense.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.chinmai.yogasense.wellness.DailyCheckIn
import com.chinmai.yogasense.wellness.QuestionnaireRecord
import com.chinmai.yogasense.wellness.QuestionnaireType
import com.chinmai.yogasense.wellness.WellnessConsent
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Real on-device SQLite persistence (run with connectedDebugAndroidTest). */
@RunWith(AndroidJUnit4::class)
class SqliteWellnessRepositoryTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dbName = "wellness-test.db"

    @Before @After fun clean() { context.deleteDatabase(dbName) }

    private fun open() = SqliteWellnessRepository(WellnessDatabase(context, dbName)).also { runBlocking { it.reload() } }

    @Test fun consentEnforcedAndDataPersistsAcrossReopen() = runBlocking {
        val r = open()
        try { r.upsertCheckIn(DailyCheckIn(100, mood = 3)); fail() } catch (_: ConsentRequiredException) {}
        r.setConsent(WellnessConsent.GRANTED)
        r.upsertCheckIn(DailyCheckIn(100, mood = 2, reflection = "first", updatedAtMillis = 1))
        r.upsertCheckIn(DailyCheckIn(100, mood = 4, sleepHours = 7.5f, updatedAtMillis = 2)) // edit same day
        r.upsertCheckIn(DailyCheckIn(101, stress = 5, updatedAtMillis = 3))
        val id = r.addQuestionnaire(QuestionnaireRecord("", QuestionnaireType.PHQ9, 10, listOf(0, 1, 2, 3, 0, 1, 2, 3, 1), 13, "Moderate", 1, true))

        val again = open() // new connection: proves on-disk persistence
        assertEquals(WellnessConsent.GRANTED, again.consent.value)
        assertEquals(listOf(101L, 100L), again.checkIns.value.map { it.epochDay })
        val d100 = again.checkIns.value.first { it.epochDay == 100L }
        assertEquals(4, d100.mood); assertEquals(7.5f, d100.sleepHours); assertEquals(null, d100.reflection)
        val q = again.questionnaires.value.single()
        assertEquals(id, q.id); assertEquals(13, q.total); assertTrue(q.item9Positive); assertEquals(1, q.difficulty)
        assertEquals(listOf(0, 1, 2, 3, 0, 1, 2, 3, 1), q.answers)
    }

    @Test fun deleteAllRemovesEverything() = runBlocking {
        val r = open()
        r.setConsent(WellnessConsent.GRANTED)
        r.upsertCheckIn(DailyCheckIn(1, mood = 3))
        r.addQuestionnaire(QuestionnaireRecord("", QuestionnaireType.GAD7, 1, List(7) { 0 }, 0, "Minimal", null, false))
        r.deleteAll()
        val again = open()
        assertTrue(again.checkIns.value.isEmpty())
        assertTrue(again.questionnaires.value.isEmpty())
        assertEquals(WellnessConsent.UNKNOWN, again.consent.value)
    }

    @Test fun unknownMigrationPathRefusesToDropData() {
        val helper = WellnessDatabase(context, dbName)
        val db = helper.writableDatabase
        try { WellnessDatabase.migrate(db, 1, 2); fail("expected refusal") } catch (_: IllegalStateException) {}
        WellnessDatabase.migrate(db, 1, 1) // no-op
        helper.close()
    }
}
