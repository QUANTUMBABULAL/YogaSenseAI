package com.chinmai.yogasense.data.db

import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.chinmai.yogasense.asana.Asana
import com.chinmai.yogasense.coaching.VoiceMode
import com.chinmai.yogasense.data.RoomPracticeHistoryRepository
import com.chinmai.yogasense.data.RoomProfileRepository
import com.chinmai.yogasense.data.db.YogaSenseDatabase.Companion.configure
import com.chinmai.yogasense.profile.CompletedPracticeSession
import com.chinmai.yogasense.profile.CorrectionOutcome
import com.chinmai.yogasense.profile.PoseOutcome
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real Room on device. Run: connectedDebugAndroidTest, or `am instrument` (see report). */
@RunWith(AndroidJUnit4::class)
class YogaSenseDatabaseTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: YogaSenseDatabase
    private lateinit var profiles: RoomProfileRepository

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, YogaSenseDatabase::class.java).configure().build()
        profiles = RoomProfileRepository(db)
    }

    @After fun tearDown() = db.close()

    private fun session(id: String, start: Long, align: Int? = 80) = CompletedPracticeSession(
        id, start, start + 120_000, 100_000, SessionCompletion.COMPLETED,
        listOf(PoseOutcome(Asana.TREE, align, 70, 30_000, 20_000), PoseOutcome(Asana.CHAIR, null, null, 0, 0)),
        listOf(CorrectionOutcome(Asana.TREE, "TREE.torso_upright.high", start + 10_000, true)),
    )

    @Test fun profileAndDefaultPreferencesCrud() = runBlocking {
        val p = profiles.createProfile("  Asha ", ExperienceLevel.BEGINNER)
        assertEquals("Asha", p.displayName)
        assertEquals(p, profiles.getProfile(p.userId))
        val prefs = profiles.observePreferences(p.userId).first()!!
        assertEquals(VoiceMode.GUIDED, prefs.voiceMode)
        profiles.updatePreferences(p.userId, prefs.copy(voiceMode = VoiceMode.OFF, preferredPracticeMinutes = 15))
        assertEquals(VoiceMode.OFF, profiles.observePreferences(p.userId).first()!!.voiceMode)
        assertEquals(listOf(p), profiles.observeProfiles().first())
        try { profiles.createProfile("   ", ExperienceLevel.BEGINNER); fail() } catch (_: IllegalArgumentException) {}
    }

    @Test fun foreignKeysAreEnforced() = runBlocking {
        try {
            db.practice().insertSession(PracticeSessionEntity("s", "no-such-user", 0, null, 0, SessionCompletion.ABANDONED))
            fail("expected FK violation")
        } catch (_: SQLiteConstraintException) {}
        try {
            db.practice().insertPoseResults(listOf(PoseResultEntity("r", "no-such-session", Asana.TREE, null, null, 0, 0)))
            fail("expected FK violation")
        } catch (_: SQLiteConstraintException) {}
    }

    @Test fun sessionPersistenceWithNullableScores() = runBlocking {
        val u = profiles.createProfile("Ravi", ExperienceLevel.INTERMEDIATE)
        val repo = RoomPracticeHistoryRepository(db, u.userId) { 1_000_000L }
        repo.record(session("s1", 900_000))
        val results = db.practice().poseResults(u.userId, "s1")
        assertEquals(2, results.size)
        val chair = results.first { it.asana == Asana.CHAIR }
        assertNull(chair.alignmentScore); assertNull(chair.stabilityScore)
        assertEquals(80, results.first { it.asana == Asana.TREE }.alignmentScore)
        assertEquals(1, db.practice().corrections(u.userId, "s1").size)
        assertEquals(1, repo.recentSessions(5).first().count { it.asana == Asana.TREE })
        assertNotNull(repo.weeklySummary().first())
    }

    @Test fun profileScopedQueriesIsolateUsers() = runBlocking {
        val a = profiles.createProfile("A", ExperienceLevel.BEGINNER)
        val b = profiles.createProfile("B", ExperienceLevel.BEGINNER)
        RoomPracticeHistoryRepository(db, a.userId).record(session("sa", 1_000))
        RoomPracticeHistoryRepository(db, b.userId).record(session("sb", 2_000))
        assertEquals(listOf("sa"), db.practice().observeSessions(a.userId).first().map { it.sessionId })
        assertEquals(listOf("sb"), db.practice().observeSessions(b.userId).first().map { it.sessionId })
        // B cannot read, update or delete A's session through scoped queries.
        assertTrue(db.practice().poseResults(b.userId, "sa").isEmpty())
        assertTrue(db.practice().corrections(b.userId, "sa").isEmpty())
        assertNull(db.practice().findSession(b.userId, "sa"))
        assertEquals(0, db.practice().finishSession(b.userId, "sa", 9, 9, SessionCompletion.ABANDONED))
        assertEquals(0, db.practice().deleteSession(b.userId, "sa"))
        assertNotNull(db.practice().findSession(a.userId, "sa"))
    }

    @Test fun wellnessCheckInOnePerDayAndScopedAssessments() = runBlocking {
        val a = profiles.createProfile("A", ExperienceLevel.BEGINNER)
        val b = profiles.createProfile("B", ExperienceLevel.BEGINNER)
        db.wellness().upsertCheckInForDay(WellnessCheckInEntity("c1", a.userId, 100, 1, 3, null, 7.5f, null, null))
        db.wellness().upsertCheckInForDay(WellnessCheckInEntity("c2", a.userId, 100, 2, 4, 2, 7.5f, 4, null)) // same day: update
        db.wellness().upsertCheckInForDay(WellnessCheckInEntity("c3", b.userId, 100, 3, 1, null, null, null, null)) // other user, same day
        val aList = db.wellness().observeCheckIns(a.userId).first()
        assertEquals(1, aList.size); assertEquals("c1", aList[0].checkInId); assertEquals(4, aList[0].mood)
        assertEquals(1, db.wellness().observeCheckIns(b.userId).first().size)

        val assessment = WellnessAssessmentEntity("as1", a.userId, Instrument.GAD7, "phqscreeners-en-2010-v2", 10, 7, null)
        db.wellness().insertAssessmentWithResponses(assessment, (1..7).map { AssessmentResponseEntity("r$it", "as1", it, 1) })
        assertEquals(7, db.wellness().responses(a.userId, "as1").size)
        assertTrue(db.wellness().responses(b.userId, "as1").isEmpty())
        assertTrue(db.wellness().observeAssessments(b.userId).first().isEmpty())
        try { // duplicate question number rejected
            db.wellness().insertResponses(listOf(AssessmentResponseEntity("dup", "as1", 1, 0))); fail()
        } catch (_: SQLiteConstraintException) {}
    }

    @Test fun consentGrantAndWithdraw() = runBlocking {
        val a = profiles.createProfile("A", ExperienceLevel.BEGINNER)
        db.wellness().insertConsent(ConsentRecordEntity("k1", a.userId, ConsentType.WELLNESS_DATA_STORAGE, "v1", 10, null))
        assertEquals("k1", db.wellness().activeConsent(a.userId, ConsentType.WELLNESS_DATA_STORAGE)!!.consentId)
        assertEquals(1, db.wellness().withdrawConsent(a.userId, "k1", 20))
        assertNull(db.wellness().activeConsent(a.userId, ConsentType.WELLNESS_DATA_STORAGE))
        assertEquals(20L, db.wellness().consentHistory(a.userId).single().withdrawnAt) // history kept
    }

    @Test fun deletingProfileCascadesOnlyItsRecords() = runBlocking {
        val a = profiles.createProfile("A", ExperienceLevel.BEGINNER)
        val b = profiles.createProfile("B", ExperienceLevel.BEGINNER)
        RoomPracticeHistoryRepository(db, a.userId).record(session("sa", 1_000))
        RoomPracticeHistoryRepository(db, b.userId).record(session("sb", 1_000))
        db.wellness().upsertCheckInForDay(WellnessCheckInEntity("c", a.userId, 1, 1, 3, null, null, null, null))
        profiles.deleteProfile(a.userId)
        assertNull(profiles.getProfile(a.userId))
        assertNull(db.preferences().get(a.userId))
        assertTrue(db.practice().observeSessions(a.userId).first().isEmpty())
        assertTrue(db.practice().poseResults(a.userId, "sa").isEmpty())
        assertTrue(db.wellness().observeCheckIns(a.userId).first().isEmpty())
        assertEquals(2, db.practice().poseResults(b.userId, "sb").size) // B untouched
    }
}

@RunWith(AndroidJUnit4::class)
class YogaSenseDatabaseFileTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val name = "yogasense-test.db"

    @get:Rule val helper = MigrationTestHelper(instrumentation, YogaSenseDatabase::class.java, emptyList(), FrameworkSQLiteOpenHelperFactory())

    @Before @After fun clean() { context.deleteDatabase(name) }

    private fun open() = Room.databaseBuilder(context, YogaSenseDatabase::class.java, name).configure().build()

    @Test fun dataPersistsAcrossReopen() = runBlocking {
        val first = open()
        val p = RoomProfileRepository(first).createProfile("Persist", ExperienceLevel.ADVANCED)
        RoomPracticeHistoryRepository(first, p.userId).record(
            CompletedPracticeSession("s", 1, 2, 1, SessionCompletion.ENDED_EARLY, listOf(PoseOutcome(Asana.TADASANA, null, 55, 3_000, 3_000)), emptyList()),
        )
        first.close()
        val second = open()
        assertEquals("Persist", second.profiles().findById(p.userId)!!.displayName)
        val r = second.practice().poseResults(p.userId, "s").single()
        assertNull(r.alignmentScore); assertEquals(55, r.stabilityScore)
        second.close()
    }

    @Test fun schemaVersion1MatchesExportedSchema() {
        helper.createDatabase(name, 1).close()
        helper.runMigrationsAndValidate(name, 1, true, *YogaSenseDatabase.MIGRATIONS).close()
    }

    @Test fun missingMigrationThrowsAndKeepsData() = runBlocking {
        val first = open()
        val p = RoomProfileRepository(first).createProfile("Keep me", ExperienceLevel.BEGINNER)
        first.close()
        // Simulate an unknown older schema version: Room must refuse (no destructive fallback).
        SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE).use { it.version = 0 }
        val reopened = open()
        try { reopened.profiles().findById(p.userId); fail("expected missing-migration failure") } catch (_: IllegalStateException) {}
        reopened.close()
        SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READONLY).use { raw ->
            raw.rawQuery("SELECT display_name FROM user_profiles WHERE user_id = ?", arrayOf(p.userId)).use { c ->
                assertTrue(c.moveToFirst()); assertEquals("Keep me", c.getString(0))
            }
        }
    }
}
