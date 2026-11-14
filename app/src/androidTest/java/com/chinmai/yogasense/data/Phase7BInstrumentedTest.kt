package com.chinmai.yogasense.data

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.chinmai.yogasense.data.db.ConsentDecision
import com.chinmai.yogasense.data.db.ExperienceLevel
import com.chinmai.yogasense.data.db.YogaSenseDatabase
import com.chinmai.yogasense.data.db.YogaSenseDatabase.Companion.configure
import com.chinmai.yogasense.profile.ProfileSetup
import com.chinmai.yogasense.wellness.DailyCheckIn
import com.chinmai.yogasense.wellness.QuestionnaireRecord
import com.chinmai.yogasense.wellness.QuestionnaireType
import com.chinmai.yogasense.wellness.WellnessConsent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Phase 7B on the phone's framework SQLite (the JVM tests use Room's bundled SQLite driver).
 * Run with `am instrument` (see PHASE7B_PROFILE_INTEGRATION_REPORT.md). Uses throwaway database names.
 */
@RunWith(AndroidJUnit4::class)
class Phase7BInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val migName = "yogasense-mig-test.db"
    private val legacyName = "wellness-legacy-test.db"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var db: YogaSenseDatabase

    @get:Rule val helper = MigrationTestHelper(instrumentation, YogaSenseDatabase::class.java, emptyList(), FrameworkSQLiteOpenHelperFactory())

    @Before fun setUp() {
        context.deleteDatabase(migName); context.deleteDatabase(legacyName)
        db = Room.inMemoryDatabaseBuilder(context, YogaSenseDatabase::class.java).configure().build()
    }

    @After fun tearDown() { scope.cancel(); db.close(); context.deleteDatabase(migName); context.deleteDatabase(legacyName) }

    @Test fun migration1To2KeepsDataAndValidates() {
        helper.createDatabase(migName, 1).apply {
            execSQL("INSERT INTO user_profiles (user_id,display_name,experience_level,created_at,updated_at,status) VALUES ('u1','Asha','BEGINNER',1,1,'ACTIVE')")
            execSQL("INSERT INTO consent_records (consent_id,user_id,consent_type,consent_version,granted_at,withdrawn_at) VALUES ('k1','u1','WELLNESS_DATA_STORAGE','v1',5,NULL)")
            execSQL("INSERT INTO wellness_check_ins (check_in_id,user_id,epoch_day,recorded_at,mood,stress,sleep_hours,sleep_quality,reflection_ciphertext) VALUES ('c1','u1',100,9,5,3,7.5,NULL,NULL)")
            close()
        }
        helper.runMigrationsAndValidate(migName, 2, true, YogaSenseDatabase.MIGRATION_1_2).apply {
            query("SELECT decision FROM consent_records WHERE consent_id = 'k1'").use { assertTrue(it.moveToFirst()); assertEquals("GRANTED", it.getString(0)) }
            query("SELECT mood, reflection FROM wellness_check_ins WHERE check_in_id = 'c1'").use { assertTrue(it.moveToFirst()); assertEquals(5, it.getInt(0)); assertTrue(it.isNull(1)) }
            close()
        }
    }

    @Test fun realLegacyWellnessDbImportsIntoRoomAndStaysUntouched() = runBlocking<Unit> {
        // Write Phase 7 data exactly as the Phase 7 app did.
        val legacy = SqliteWellnessRepository(WellnessDatabase(context, legacyName)).also { it.reload() }
        legacy.setConsent(WellnessConsent.GRANTED)
        legacy.upsertCheckIn(DailyCheckIn(20_000, mood = 4, stress = 2, sleepHours = 7.5f, reflection = "calm", updatedAtMillis = 1))
        legacy.upsertCheckIn(DailyCheckIn(20_001, stress = 5, updatedAtMillis = 2))
        legacy.addQuestionnaire(QuestionnaireRecord("", QuestionnaireType.PHQ9, 10, listOf(0, 1, 2, 3, 0, 1, 2, 3, 1), 13, "Moderate", 1, true))
        val before = SqliteLegacyWellnessSource(context, legacyName).read()

        val profiles = RoomProfileRepository(db)
        val owner = profiles.createProfile("Owner", ExperienceLevel.BEGINNER, ProfileSetup(markers = { mapOf(LegacyWellnessMigrator.KEY_OWNER to it) }))
        val other = profiles.createProfile("Other", ExperienceLevel.BEGINNER)
        val source = SqliteLegacyWellnessSource(context, legacyName)
        val status = LegacyWellnessMigrator(db, source).refresh(profiles.observeProfiles().first())
        assertEquals(LegacyWellnessStatus.Completed(owner.userId, 2, 1), status)

        val w = RoomWellnessRepository(db, owner.userId, scope)
        val days = withTimeout(5_000) { w.checkIns.first { it.size == 2 } }
        assertEquals("calm", days.first { it.epochDay == 20_000L }.reflection)
        assertNull(days.first { it.epochDay == 20_001L }.mood)
        val q = withTimeout(5_000) { w.questionnaires.first { it.size == 1 } }.single()
        assertEquals(listOf(0, 1, 2, 3, 0, 1, 2, 3, 1), q.answers); assertTrue(q.item9Positive); assertEquals(1, q.difficulty)
        assertEquals(ConsentDecision.GRANTED, db.wellness().consentHistory(owner.userId).single().decision)
        assertTrue(db.wellness().checkIns(other.userId).isEmpty()) // other profile sees nothing

        // The legacy file was only read.
        assertTrue(source.exists())
        assertEquals(before, SqliteLegacyWellnessSource(context, legacyName).read())
        // Retry (next launch) does not duplicate.
        LegacyWellnessMigrator(db, source).refresh(profiles.observeProfiles().first())
        assertEquals(2, db.wellness().checkIns(owner.userId).size)
    }

    @Test fun wellnessAndPreferencesAreIsolatedOnFrameworkSqlite() = runBlocking<Unit> {
        val profiles = RoomProfileRepository(db)
        val a = profiles.createProfile("A", ExperienceLevel.BEGINNER)
        val b = profiles.createProfile("B", ExperienceLevel.BEGINNER)
        profiles.updatePreferences(a.userId, profiles.getPreferences(a.userId)!!.copy(liveSkeletonEnabled = false))
        assertTrue(profiles.getPreferences(b.userId)!!.liveSkeletonEnabled)
        val wa = RoomWellnessRepository(db, a.userId, scope)
        val wb = RoomWellnessRepository(db, b.userId, scope)
        wa.setConsent(WellnessConsent.GRANTED)
        wa.upsertCheckIn(DailyCheckIn(1, mood = 2))
        withTimeout(5_000) { wa.checkIns.first { it.size == 1 } }
        assertTrue(wb.checkIns.value.isEmpty())
        assertFalse(wb.consent.value == WellnessConsent.GRANTED)
    }
}
