package com.chinmai.yogasense.data.room

import com.chinmai.yogasense.coaching.CoachSettings
import com.chinmai.yogasense.coaching.VisualSettings
import com.chinmai.yogasense.coaching.VoiceMode
import com.chinmai.yogasense.data.LegacyCheckInRow
import com.chinmai.yogasense.data.LegacyImportLifecycle
import com.chinmai.yogasense.data.LegacyQuestionnaireRow
import com.chinmai.yogasense.data.LegacyWellnessMapper
import com.chinmai.yogasense.data.LegacyWellnessMigrator
import com.chinmai.yogasense.data.LegacyWellnessSnapshot
import com.chinmai.yogasense.data.LegacyWellnessSource
import com.chinmai.yogasense.data.LegacyWellnessStatus
import com.chinmai.yogasense.data.RoomAppMetadata
import com.chinmai.yogasense.data.RoomProfileRepository
import com.chinmai.yogasense.data.RoomWellnessRepository
import com.chinmai.yogasense.data.db.AppMetadataEntity
import com.chinmai.yogasense.data.db.ExperienceLevel
import com.chinmai.yogasense.data.db.LegacyImportPlan
import com.chinmai.yogasense.data.db.LegacyImportVerificationException
import com.chinmai.yogasense.data.db.WellnessCheckInEntity
import com.chinmai.yogasense.data.db.YogaSenseDatabase
import com.chinmai.yogasense.profile.ActiveProfileManager
import com.chinmai.yogasense.profile.ActiveProfileStore
import com.chinmai.yogasense.profile.CreateResult
import com.chinmai.yogasense.profile.UserPreferences
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
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class FakeLegacySource(var snapshot: LegacyWellnessSnapshot?) : LegacyWellnessSource {
    var erased = false
    var reads = 0
    override fun exists() = snapshot != null && !erased
    override fun read(): LegacyWellnessSnapshot { reads++; return snapshot ?: LegacyWellnessSnapshot.EMPTY }
    override fun erase() { erased = true }
}

object LegacyFixtures {
    val checkIns = listOf(
        LegacyCheckInRow(20_000, 4, 2, 7.5f, 4, "felt calm", 1_000),
        LegacyCheckInRow(20_001, null, 5, null, null, null, 2_000),     // sparse
        LegacyCheckInRow(20_002, 1, null, 0f, 1, "  ", 3_000),           // real zero sleep kept
    )
    val questionnaires = listOf(
        LegacyQuestionnaireRow(1, "PHQ9", 10_000, "0,1,2,3,0,1,2,3,1", 13, "Moderate", 1, 1),
        LegacyQuestionnaireRow(2, "GAD7", 11_000, "0,0,0,0,0,0,0", 0, "Minimal", null, 0),
    )
    val snapshot = LegacyWellnessSnapshot(checkIns, questionnaires, "GRANTED")
}

class LegacyMigrationRoomTest {
    private lateinit var db: YogaSenseDatabase
    private lateinit var profiles: RoomProfileRepository
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Before fun setUp() { db = JvmRoom.inMemory(); profiles = RoomProfileRepository(db) }
    @After fun tearDown() { scope.cancel(); db.close() }

    private suspend fun all() = profiles.observeProfiles().first()

    @Test fun importsEveryRecordWithIdenticalValues() = runBlocking<Unit> {
        val owner = profiles.createProfile("Owner", ExperienceLevel.BEGINNER, com.chinmai.yogasense.profile.ProfileSetup(markers = { mapOf(LegacyWellnessMigrator.KEY_OWNER to it) }))
        val m = LegacyWellnessMigrator(db, FakeLegacySource(LegacyFixtures.snapshot)) { 99 }
        val status = m.refresh(all())
        assertEquals(LegacyWellnessStatus.Completed(owner.userId, 3, 2), status)

        val w = RoomWellnessRepository(db, owner.userId, scope)
        val days = withTimeout(5_000) { w.checkIns.first { it.size == 3 } }
        assertEquals(LegacyFixtures.checkIns.map { LegacyWellnessMapper.domain(it) }.sortedByDescending { it.epochDay }, days)
        assertEquals(0f, days.first { it.epochDay == 20_002L }.sleepHours)
        val qs = withTimeout(5_000) { w.questionnaires.first { it.size == 2 } }
        val phq = qs.first { it.type == QuestionnaireType.PHQ9 }
        assertEquals(listOf(0, 1, 2, 3, 0, 1, 2, 3, 1), phq.answers); assertEquals(13, phq.total)
        assertEquals("Moderate", phq.bandLabel); assertEquals(1, phq.difficulty); assertTrue(phq.item9Positive)
        val gad = qs.first { it.type == QuestionnaireType.GAD7 }
        assertFalse(gad.item9Positive); assertNull(gad.difficulty); assertEquals(7, gad.answers.size)
        assertEquals(WellnessConsent.GRANTED, withTimeout(5_000) { w.consent.first { it != WellnessConsent.UNKNOWN } })
        assertEquals("completed", db.metadata().get(LegacyWellnessMigrator.KEY_STATUS))
    }

    @Test fun retryIsIdempotentAndCompletionIsRemembered() = runBlocking<Unit> {
        profiles.createProfile("Owner", ExperienceLevel.BEGINNER, com.chinmai.yogasense.profile.ProfileSetup(markers = { mapOf(LegacyWellnessMigrator.KEY_OWNER to it) }))
        val source = FakeLegacySource(LegacyFixtures.snapshot)
        val m = LegacyWellnessMigrator(db, source)
        m.refresh(all()); m.refresh(all())
        val again = LegacyWellnessMigrator(db, source) // e.g. next app launch
        assertTrue(again.refresh(all()) is LegacyWellnessStatus.Completed)
        val owner = all().single().userId
        assertEquals(3, db.wellness().checkIns(owner).size)
        assertEquals(2, db.wellness().assessments(owner).size)
        assertEquals(16, db.wellness().allResponses(owner).size)
        assertEquals(1, db.wellness().consentHistory(owner).size)
        assertFalse(source.erased) // legacy file retained
    }

    @Test fun ambiguousOwnershipIsNeverAssignedAutomatically() = runBlocking<Unit> {
        val a = profiles.createProfile("A", ExperienceLevel.BEGINNER)
        val b = profiles.createProfile("B", ExperienceLevel.BEGINNER)
        val m = LegacyWellnessMigrator(db, FakeLegacySource(LegacyFixtures.snapshot))
        assertEquals(LegacyWellnessStatus.NeedsOwner(5), m.refresh(all()))
        assertTrue(db.wellness().checkIns(a.userId).isEmpty()); assertTrue(db.wellness().checkIns(b.userId).isEmpty())
        assertFalse(m.status.value.isResolved)
        // Explicit user decision.
        assertTrue(m.assignTo(b.userId, all()) is LegacyWellnessStatus.Completed)
        assertEquals(3, db.wellness().checkIns(b.userId).size)
        assertTrue(db.wellness().checkIns(a.userId).isEmpty())
    }

    @Test fun noProfileYetWaitsForTheFirstProfile() = runBlocking<Unit> {
        val m = LegacyWellnessMigrator(db, FakeLegacySource(LegacyFixtures.snapshot))
        assertEquals(LegacyWellnessStatus.AwaitingFirstProfile(5), m.refresh(emptyList()))
    }

    @Test fun noOrEmptyLegacyDataIsNotNeeded() = runBlocking<Unit> {
        assertEquals(LegacyWellnessStatus.NotNeeded, LegacyWellnessMigrator(db, FakeLegacySource(null)).refresh(emptyList()))
        assertEquals(LegacyWellnessStatus.NotNeeded, LegacyWellnessMigrator(db, FakeLegacySource(LegacyWellnessSnapshot.EMPTY)).refresh(emptyList()))
        assertEquals(LegacyWellnessStatus.NotNeeded, LegacyWellnessMigrator(db, FakeLegacySource(LegacyWellnessSnapshot(emptyList(), emptyList(), "UNKNOWN"))).refresh(emptyList()))
    }

    @Test fun unmappableRecordFailsAndImportsNothingThenRetrySucceeds() = runBlocking<Unit> {
        val owner = profiles.createProfile("Owner", ExperienceLevel.BEGINNER, com.chinmai.yogasense.profile.ProfileSetup(markers = { mapOf(LegacyWellnessMigrator.KEY_OWNER to it) }))
        val bad = LegacyFixtures.snapshot.copy(questionnaires = LegacyFixtures.questionnaires + LegacyQuestionnaireRow(3, "XYZ", 1, "1,2", 3, "?", null, 0))
        val source = FakeLegacySource(bad)
        val m = LegacyWellnessMigrator(db, source)
        val s = m.refresh(all())
        assertTrue(s is LegacyWellnessStatus.Failed)
        assertTrue((s as LegacyWellnessStatus.Failed).reason.contains("#3"))
        assertTrue(db.wellness().checkIns(owner.userId).isEmpty())   // nothing partially imported
        assertTrue(db.wellness().assessments(owner.userId).isEmpty())
        assertNull(db.metadata().get(LegacyWellnessMigrator.KEY_STATUS))
        assertFalse(source.erased)
        source.snapshot = LegacyFixtures.snapshot // e.g. after an app fix
        assertTrue(m.refresh(all()) is LegacyWellnessStatus.Completed)
    }

    @Test fun constraintFailureMidImportRollsBackEverything() = runBlocking<Unit> {
        val owner = profiles.createProfile("Owner", ExperienceLevel.BEGINNER, com.chinmai.yogasense.profile.ProfileSetup(markers = { mapOf(LegacyWellnessMigrator.KEY_OWNER to it) }))
        // A conflicting Room check-in for one of the legacy days (unique user/day): the import must not overwrite it.
        db.wellness().insertCheckIn(WellnessCheckInEntity("other", owner.userId, 20_001, 5, 1, 1, null, null, null))
        val s = LegacyWellnessMigrator(db, FakeLegacySource(LegacyFixtures.snapshot)).refresh(all())
        assertTrue(s is LegacyWellnessStatus.Failed)
        assertEquals(listOf("other"), db.wellness().checkIns(owner.userId).map { it.checkInId }) // existing row kept, nothing added
        assertTrue(db.wellness().assessments(owner.userId).isEmpty())
        assertTrue(db.wellness().consentHistory(owner.userId).isEmpty())
        assertNull(db.metadata().get(LegacyWellnessMigrator.KEY_STATUS))
    }

    @Test fun verificationFailureRollsBackTheTransaction() = runBlocking<Unit> {
        val owner = profiles.createProfile("Owner", ExperienceLevel.BEGINNER)
        val mapped = (LegacyWellnessMapper.map(LegacyFixtures.snapshot, owner.userId, 1) as LegacyWellnessMapper.Result.Ok).mapped
        val plan = LegacyImportPlan(owner.userId, mapped.checkIns, mapped.assessments, mapped.consent, listOf(AppMetadataEntity("done", "yes"))) { _, _, _, _ -> listOf("simulated mismatch") }
        try { db.legacyImport().importVerified(plan); fail() } catch (_: LegacyImportVerificationException) {}
        assertTrue(db.wellness().checkIns(owner.userId).isEmpty())
        assertTrue(db.wellness().assessments(owner.userId).isEmpty())
        assertNull(db.metadata().get("done"))
    }

    @Test fun legacyCopyIsErasedOnlyAfterVerifiedImportAndOwnerDeletion() = runBlocking<Unit> {
        val owner = profiles.createProfile("Owner", ExperienceLevel.BEGINNER, com.chinmai.yogasense.profile.ProfileSetup(markers = { mapOf(LegacyWellnessMigrator.KEY_OWNER to it) }))
        val other = profiles.createProfile("Other", ExperienceLevel.BEGINNER)
        val source = FakeLegacySource(LegacyFixtures.snapshot)
        val m = LegacyWellnessMigrator(db, source)
        m.onOwnerDataErased(owner.userId)
        assertFalse(source.erased) // not imported yet → never erased
        m.refresh(all())
        m.onOwnerDataErased(other.userId)
        assertFalse(source.erased) // not the owner
        m.onOwnerDataErased(owner.userId)
        assertTrue(source.erased)
    }

    // ---- First-profile flow through ActiveProfileManager + LegacyImportLifecycle ------------------

    private class MemStore(var id: String? = null) : ActiveProfileStore {
        override fun read() = id
        override fun write(userId: String?) { id = userId }
    }

    private fun manager(source: FakeLegacySource, legacySettings: Pair<CoachSettings, VisualSettings>?): Pair<ActiveProfileManager, LegacyWellnessMigrator> {
        val m = LegacyWellnessMigrator(db, source)
        val lifecycle = LegacyImportLifecycle(RoomAppMetadata(db), { legacySettings }, m) { profiles.observeProfiles().first() }
        return ActiveProfileManager(profiles, MemStore(), lifecycle, scope, Dispatchers.Unconfined) to m
    }

    @Test fun firstProfileReceivesLegacyWellnessAndSettingsOnce() = runBlocking<Unit> {
        val legacy = CoachSettings(mode = VoiceMode.MINIMAL, speechRate = 0.9f) to VisualSettings(liveSkeleton = false)
        val (mgr, migrator) = manager(FakeLegacySource(LegacyFixtures.snapshot), legacy)
        val first = (mgr.create("First", ExperienceLevel.BEGINNER) as CreateResult.Created).profile
        assertEquals(LegacyWellnessStatus.Completed(first.userId, 3, 2), migrator.status.value)
        val p = profiles.getPreferences(first.userId)!!
        assertEquals(VoiceMode.MINIMAL, p.voiceMode); assertEquals(0.9f, p.speechRate); assertFalse(p.liveSkeletonEnabled)
        // Second profile: defaults, no legacy data.
        val second = (mgr.create("Second", ExperienceLevel.BEGINNER) as CreateResult.Created).profile
        assertEquals(UserPreferences(), profiles.getPreferences(second.userId))
        assertTrue(db.wellness().checkIns(second.userId).isEmpty())
        // The first profile's newer settings are never overwritten by the legacy import again.
        profiles.updatePreferences(first.userId, p.copy(voiceMode = VoiceMode.OFF))
        val (mgr2, _) = manager(FakeLegacySource(LegacyFixtures.snapshot), legacy) // "next launch"
        mgr2.create("Third", ExperienceLevel.BEGINNER)
        assertEquals(VoiceMode.OFF, profiles.getPreferences(first.userId)!!.voiceMode)
        assertEquals(first.userId, db.metadata().get(LegacyImportLifecycle.KEY_PREFS_IMPORTED))
    }

    @Test fun deviceWithoutLegacyDataGetsDefaults() = runBlocking<Unit> {
        val (mgr, migrator) = manager(FakeLegacySource(null), null)
        val p = (mgr.create("Only", ExperienceLevel.INTERMEDIATE) as CreateResult.Created).profile
        assertEquals(UserPreferences(), profiles.getPreferences(p.userId))
        assertEquals(LegacyWellnessStatus.NotNeeded, migrator.status.value)
        assertNull(db.metadata().get(LegacyImportLifecycle.KEY_PREFS_IMPORTED))
    }
}
