package com.chinmai.yogasense.data.room

import com.chinmai.yogasense.asana.Asana
import com.chinmai.yogasense.coaching.VoiceMode
import com.chinmai.yogasense.data.ConsentRequiredException
import com.chinmai.yogasense.data.RoomPracticeHistoryRepository
import com.chinmai.yogasense.data.RoomProfileRepository
import com.chinmai.yogasense.data.RoomWellnessRepository
import com.chinmai.yogasense.data.db.ExperienceLevel
import com.chinmai.yogasense.data.db.PracticeSessionEntity
import com.chinmai.yogasense.data.db.SessionCompletion
import com.chinmai.yogasense.data.db.YogaSenseDatabase
import com.chinmai.yogasense.profile.CompletedPracticeSession
import com.chinmai.yogasense.profile.CorrectionOutcome
import com.chinmai.yogasense.profile.PoseOutcome
import com.chinmai.yogasense.profile.ProfileSetup
import com.chinmai.yogasense.profile.UserPreferences
import com.chinmai.yogasense.wellness.DailyCheckIn
import com.chinmai.yogasense.wellness.QuestionnaireRecord
import com.chinmai.yogasense.wellness.QuestionnaireType
import com.chinmai.yogasense.wellness.WellnessConsent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/** Real Room + SQLite on the JVM (see [JvmRoom]). */
class ProfileDataRoomTest {
    private lateinit var db: YogaSenseDatabase
    private lateinit var profiles: RoomProfileRepository
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Before fun setUp() { db = JvmRoom.inMemory(); profiles = RoomProfileRepository(db) }
    @After fun tearDown() { scope.cancel(); db.close() }

    private suspend fun <T> StateFlow<T>.await(p: (T) -> Boolean): T = withTimeout(5_000) { first(p) }

    private fun session(id: String, start: Long, align: Int? = 80, completion: SessionCompletion = SessionCompletion.COMPLETED) = CompletedPracticeSession(
        id, start, start + 120_000, 100_000, completion,
        listOf(PoseOutcome(Asana.TREE, align, 70, 30_000, 20_000), PoseOutcome(Asana.CHAIR, null, null, 0, 0)),
        listOf(CorrectionOutcome(Asana.TREE, "TREE.torso_upright.high", start + 10_000, true)),
    )

    @Test fun createListAndDefaultPreferences() = runBlocking<Unit> {
        val a = profiles.createProfile("  Asha  K ", ExperienceLevel.BEGINNER)
        val b = profiles.createProfile("Ravi", ExperienceLevel.ADVANCED)
        assertEquals("Asha K", a.displayName)
        assertEquals(listOf(a, b), profiles.observeProfiles().first())
        assertEquals(2, profiles.profileCount())
        assertEquals(UserPreferences(), profiles.getPreferences(a.userId))
        try { profiles.createProfile("   ", ExperienceLevel.BEGINNER); fail() } catch (_: IllegalArgumentException) {}
        assertEquals(2, profiles.profileCount()) // nothing written for the invalid name
    }

    @Test fun setupPreferencesAndMarkersAreWrittenWithTheProfile() = runBlocking<Unit> {
        val p = profiles.createProfile("A", ExperienceLevel.BEGINNER, ProfileSetup(UserPreferences(voiceMode = VoiceMode.OFF), markers = { mapOf("k" to it) }))
        assertEquals(VoiceMode.OFF, profiles.getPreferences(p.userId)!!.voiceMode)
        assertEquals(p.userId, db.metadata().get("k"))
    }

    @Test fun profileACannotModifyProfileBPreferences() = runBlocking<Unit> {
        val a = profiles.createProfile("A", ExperienceLevel.BEGINNER)
        val b = profiles.createProfile("B", ExperienceLevel.BEGINNER)
        profiles.updatePreferences(a.userId, UserPreferences(voiceMode = VoiceMode.MINIMAL, liveSkeletonEnabled = false, speechRate = 1.2f))
        assertEquals(VoiceMode.MINIMAL, profiles.getPreferences(a.userId)!!.voiceMode)
        assertEquals(UserPreferences(), profiles.getPreferences(b.userId)) // untouched
        try { profiles.updatePreferences("no-such-user", UserPreferences()); fail() } catch (_: IllegalArgumentException) {}
        assertNull(profiles.getPreferences("no-such-user"))
    }

    @Test fun profileACannotReadProfileBSessions() = runBlocking<Unit> {
        val a = profiles.createProfile("A", ExperienceLevel.BEGINNER)
        val b = profiles.createProfile("B", ExperienceLevel.BEGINNER)
        val ra = RoomPracticeHistoryRepository(db, a.userId)
        val rb = RoomPracticeHistoryRepository(db, b.userId)
        ra.record(session("sa", 1_000))
        rb.record(session("sb1", 2_000)); rb.record(session("sb2", 3_000))
        assertEquals(listOf("sa"), ra.overview().first().recent.map { it.sessionId })
        assertEquals(setOf("sb1", "sb2"), rb.overview().first().recent.map { it.sessionId }.toSet())
        assertEquals(1, ra.overview().first().recordedSessions)
        assertTrue(ra.recentSessions(10).first().all { it.startedAtMillis == 1_000L })
        assertTrue(db.practice().poseResults(a.userId, "sb1").isEmpty())
        assertTrue(db.practice().corrections(a.userId, "sb1").isEmpty())
        assertEquals(0, db.practice().deleteSession(a.userId, "sb1"))
        assertEquals(2, db.practice().poseResults(b.userId, "sb1").size)
    }

    @Test fun completedSessionPersistsAndDuplicateCallbackIsIgnored() = runBlocking<Unit> {
        val a = profiles.createProfile("A", ExperienceLevel.BEGINNER)
        val repo = RoomPracticeHistoryRepository(db, a.userId)
        assertTrue(repo.record(session("s1", 5_000)))
        assertFalse(repo.record(session("s1", 5_000))) // repeated completion callback
        assertEquals(1, db.practice().observeSessions(a.userId).first().size)
        assertEquals(2, db.practice().poseResults(a.userId, "s1").size)
        assertEquals(1, db.practice().corrections(a.userId, "s1").size)
        val stored = db.practice().findSession(a.userId, "s1")!!
        assertEquals(5_000L, stored.startedAt); assertEquals(100_000L, stored.durationMs); assertEquals(SessionCompletion.COMPLETED, stored.completion)
        assertTrue(db.practice().corrections(a.userId, "s1").single().improvementObserved)
    }

    @Test fun nullScoresStayNullThroughHistory() = runBlocking<Unit> {
        val a = profiles.createProfile("A", ExperienceLevel.BEGINNER)
        val repo = RoomPracticeHistoryRepository(db, a.userId)
        repo.record(session("s1", 1_000, align = null)); repo.record(session("s2", 2_000, align = 0))
        val chair = db.practice().poseResults(a.userId, "s1").first { it.asana == Asana.CHAIR }
        assertNull(chair.alignmentScore); assertNull(chair.stabilityScore)
        val o = repo.overview().first()
        val tree = o.perAsana.first { it.asana == Asana.TREE }
        assertEquals(0, tree.averageAlignment)            // a real 0 is kept as 0 …
        assertEquals(1, tree.measuredAlignmentSessions)   // … and the null session is not counted as 0
        assertNull(o.perAsana.first { it.asana == Asana.CHAIR }.averageAlignment)
        assertEquals(2, tree.sessions); assertEquals(60_000L, tree.totalStableHoldMs); assertEquals(20_000L, tree.longestHoldMs)
    }

    @Test fun abandonedSessionsAreStoredButNotCounted() = runBlocking<Unit> {
        val a = profiles.createProfile("A", ExperienceLevel.BEGINNER)
        val repo = RoomPracticeHistoryRepository(db, a.userId)
        repo.record(session("s1", 1_000, completion = SessionCompletion.ABANDONED))
        assertEquals(1, db.practice().observeSessions(a.userId).first().size)
        assertTrue(repo.overview().first().isEmpty)
        assertNull(repo.weeklySummary().first())
        repo.record(session("s2", 2_000, completion = SessionCompletion.ENDED_EARLY))
        val o = repo.overview().first()
        assertEquals(0, o.completedSessions); assertEquals(1, o.recordedSessions)
    }

    @Test fun recordingForDeletedProfileFailsWithoutWriting() = runBlocking<Unit> {
        val a = profiles.createProfile("A", ExperienceLevel.BEGINNER)
        profiles.deleteProfile(a.userId)
        try { RoomPracticeHistoryRepository(db, a.userId).record(session("late", 1)); fail("FK must reject a late write") } catch (_: Exception) {}
        assertNull(db.practice().findSession(a.userId, "late"))
    }

    @Test fun foreignKeysAreEnforced() = runBlocking<Unit> {
        try { db.practice().insertSession(PracticeSessionEntity("x", "nobody", 0, null, 0, SessionCompletion.ABANDONED)); fail() } catch (_: Exception) {}
    }

    @Test fun wellnessRoundTripOnePerDayAndValuesPreserved() = runBlocking<Unit> {
        val a = profiles.createProfile("A", ExperienceLevel.BEGINNER)
        val w = RoomWellnessRepository(db, a.userId, scope)
        try { w.upsertCheckIn(DailyCheckIn(100, mood = 3)); fail() } catch (_: ConsentRequiredException) {}
        w.setConsent(WellnessConsent.GRANTED)
        w.consent.await { it == WellnessConsent.GRANTED }
        w.upsertCheckIn(DailyCheckIn(100, mood = 2, reflection = "first", updatedAtMillis = 1))
        w.upsertCheckIn(DailyCheckIn(100, mood = 4, sleepHours = 7.5f, updatedAtMillis = 2)) // same day: edit
        w.upsertCheckIn(DailyCheckIn(101, stress = 5, reflection = "second", updatedAtMillis = 3))
        val id = w.addQuestionnaire(QuestionnaireRecord("", QuestionnaireType.PHQ9, 10, listOf(0, 1, 2, 3, 0, 1, 2, 3, 1), 13, "Moderate", 1, true))
        val days = w.checkIns.await { it.size == 2 }
        assertEquals(listOf(101L, 100L), days.map { it.epochDay })
        val d100 = days.first { it.epochDay == 100L }
        assertEquals(4, d100.mood); assertEquals(7.5f, d100.sleepHours); assertNull(d100.reflection)
        assertEquals("second", days.first { it.epochDay == 101L }.reflection)
        val q = w.questionnaires.await { it.size == 1 }.single()
        assertEquals(id, q.id); assertEquals(13, q.total); assertEquals("Moderate", q.bandLabel)
        assertTrue(q.item9Positive); assertEquals(1, q.difficulty); assertEquals(listOf(0, 1, 2, 3, 0, 1, 2, 3, 1), q.answers)
        w.deleteCheckIn(101)
        assertEquals(listOf(100L), w.checkIns.await { it.size == 1 }.map { it.epochDay })
    }

    @Test fun profileACannotReadProfileBWellnessData() = runBlocking<Unit> {
        val a = profiles.createProfile("A", ExperienceLevel.BEGINNER)
        val b = profiles.createProfile("B", ExperienceLevel.BEGINNER)
        val wa = RoomWellnessRepository(db, a.userId, scope)
        val wb = RoomWellnessRepository(db, b.userId, scope)
        wa.setConsent(WellnessConsent.GRANTED)
        wa.upsertCheckIn(DailyCheckIn(100, mood = 1))
        wa.addQuestionnaire(QuestionnaireRecord("", QuestionnaireType.GAD7, 5, List(7) { 1 }, 7, "Mild", null, false))
        // Consent is per profile: B has not consented and cannot write.
        assertEquals(WellnessConsent.UNKNOWN, wb.consent.value)
        try { wb.upsertCheckIn(DailyCheckIn(100, mood = 5)); fail() } catch (_: ConsentRequiredException) {}
        wa.checkIns.await { it.size == 1 }; wa.questionnaires.await { it.size == 1 }
        assertTrue(wb.checkIns.value.isEmpty()); assertTrue(wb.questionnaires.value.isEmpty())
        assertTrue(db.wellness().observeCheckIns(b.userId).first().isEmpty())
        // B deleting "all" data cannot touch A's data.
        wb.deleteAll()
        assertEquals(1, db.wellness().observeCheckIns(a.userId).first().size)
        assertEquals(1, db.wellness().observeAssessments(a.userId).first().size)
        // A's delete-all removes A's data and consent only.
        wa.deleteAll()
        assertTrue(db.wellness().observeCheckIns(a.userId).first().isEmpty())
        assertEquals(WellnessConsent.UNKNOWN, wa.consent.await { it == WellnessConsent.UNKNOWN })
    }

    @Test fun declinedConsentIsRememberedAndBlocksWrites() = runBlocking<Unit> {
        val a = profiles.createProfile("A", ExperienceLevel.BEGINNER)
        val w = RoomWellnessRepository(db, a.userId, scope)
        w.setConsent(WellnessConsent.DECLINED)
        assertEquals(WellnessConsent.DECLINED, w.consent.await { it == WellnessConsent.DECLINED })
        try { w.upsertCheckIn(DailyCheckIn(1, mood = 3)); fail() } catch (_: ConsentRequiredException) {}
        w.setConsent(WellnessConsent.GRANTED)
        w.upsertCheckIn(DailyCheckIn(1, mood = 3))
        assertEquals(2, db.wellness().consentHistory(a.userId).size) // decline kept in history, withdrawn
    }

    @Test fun deletingProfileCascadesOnlyItsRecords() = runBlocking<Unit> {
        val a = profiles.createProfile("A", ExperienceLevel.BEGINNER)
        val b = profiles.createProfile("B", ExperienceLevel.BEGINNER)
        RoomPracticeHistoryRepository(db, a.userId).record(session("sa", 1_000))
        RoomPracticeHistoryRepository(db, b.userId).record(session("sb", 1_000))
        RoomWellnessRepository(db, a.userId, scope).apply { setConsent(WellnessConsent.GRANTED); upsertCheckIn(DailyCheckIn(1, mood = 2)) }
        profiles.deleteProfile(a.userId)
        assertNull(profiles.getProfile(a.userId)); assertNull(profiles.getPreferences(a.userId))
        assertTrue(db.practice().observeSessions(a.userId).first().isEmpty())
        assertTrue(db.wellness().observeCheckIns(a.userId).first().isEmpty())
        assertTrue(db.wellness().consentHistory(a.userId).isEmpty())
        assertEquals(2, db.practice().poseResults(b.userId, "sb").size)
        assertEquals(listOf(b), profiles.observeProfiles().first())
    }
}
