package com.chinmai.yogasense.data.room

import com.chinmai.yogasense.asana.Asana
import com.chinmai.yogasense.data.RoomPersonalisationRepository
import com.chinmai.yogasense.data.RoomPracticeHistoryRepository
import com.chinmai.yogasense.data.RoomProfileRepository
import com.chinmai.yogasense.data.db.ExperienceLevel
import com.chinmai.yogasense.data.db.SessionCompletion
import com.chinmai.yogasense.data.db.YogaSenseDatabase
import com.chinmai.yogasense.profile.BodyArea
import com.chinmai.yogasense.profile.BodyAreaIntent
import com.chinmai.yogasense.profile.CompletedPracticeSession
import com.chinmai.yogasense.profile.OnboardingAnswers
import com.chinmai.yogasense.profile.OnboardingStatus
import com.chinmai.yogasense.profile.OnboardingStep
import com.chinmai.yogasense.profile.PoseOutcome
import com.chinmai.yogasense.profile.PracticeFrequency
import com.chinmai.yogasense.profile.ProfileSetup
import com.chinmai.yogasense.profile.WeightUnit
import com.chinmai.yogasense.profile.WellnessGoal
import com.chinmai.yogasense.ui.onboarding.OnboardingController
import com.chinmai.yogasense.ui.onboarding.OnboardingMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class PersonalisationRoomTest {
    private lateinit var db: YogaSenseDatabase
    private lateinit var profiles: RoomProfileRepository
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Before fun setUp() { db = JvmRoom.inMemory(); profiles = RoomProfileRepository(db) }
    @After fun tearDown() { scope.cancel(); db.close() }

    private suspend fun view(r: RoomPersonalisationRepository) = withTimeout(5_000) { r.observe().first() }

    private fun controller(r: RoomPersonalisationRepository, v: com.chinmai.yogasense.profile.PersonalisationView, mode: OnboardingMode) =
        OnboardingController(v, mode) { step, a, next, skipped, edit -> r.saveStep(step, a, next, skipped, edit) }

    @Test fun newProfileMustOnboardAndExistingDoesNot() = runBlocking<Unit> {
        val fresh = profiles.createProfile("New", ExperienceLevel.BEGINNER)
        assertTrue(view(RoomPersonalisationRepository(db, fresh.userId)).mustOnboard)
        // Optional-onboarding profile (Phase 7B profiles have no row at all — see SchemaMigrationTest).
        val old = profiles.createProfile("Old", ExperienceLevel.ADVANCED, ProfileSetup(requireOnboarding = false))
        val ov = view(RoomPersonalisationRepository(db, old.userId))
        assertFalse(ov.mustOnboard)
        assertTrue(ov.showPrompt)
    }

    @Test fun fullFlowPersistsEveryAnswerToItsSingleSourceOfTruth() = runBlocking<Unit> {
        val p = profiles.createProfile("Asha", ExperienceLevel.BEGINNER)
        val repo = RoomPersonalisationRepository(db, p.userId)
        val c = controller(repo, view(repo), OnboardingMode.Required)
        c.next()                                                        // welcome
        c.setHeightIndex(168); c.next()
        c.setWeightUnit(WeightUnit.LB); c.setWeightIndex(143); c.next()
        c.setExperience(ExperienceLevel.INTERMEDIATE); c.next()
        c.toggleArea(BodyArea.LOWER_BACK); c.toggleArea(BodyArea.HIPS); c.setIntent(BodyArea.HIPS, BodyAreaIntent.MOBILITY); c.next()
        c.toggleGoal(WellnessGoal.BALANCE); c.toggleGoal(WellnessGoal.SLEEP); c.next()
        c.setMinutes(15); c.next()
        c.setFrequency(PracticeFrequency.FOUR_FIVE); c.next()
        assertEquals(OnboardingStep.DONE, c.state.value.step)
        assertTrue("not completed before the final CTA", view(repo).mustOnboard)
        c.next()                                                        // "Explore my YogaSenseAI"
        assertTrue(c.state.value.finished)

        val v = view(repo)
        assertFalse(v.mustOnboard)
        assertEquals(OnboardingStatus.COMPLETED, v.p.status)
        assertEquals(168f, v.p.heightCm)
        assertEquals(64.9f, v.p.weightKg!!, 0.05f); assertEquals(WeightUnit.LB, v.p.weightUnit)
        assertEquals(mapOf(BodyArea.LOWER_BACK to null, BodyArea.HIPS to BodyAreaIntent.MOBILITY), v.p.bodyAreas)
        assertEquals(setOf(WellnessGoal.BALANCE, WellnessGoal.SLEEP), v.p.goals)
        assertEquals(PracticeFrequency.FOUR_FIVE, v.p.weeklyFrequency)
        // Reused existing fields — no duplicates:
        assertEquals(ExperienceLevel.INTERMEDIATE, profiles.getProfile(p.userId)!!.experienceLevel)
        assertEquals(15, profiles.getPreferences(p.userId)!!.preferredPracticeMinutes)
    }

    @Test fun skipKeepsStoredValuesAndIncompleteOnboardingResumes() = runBlocking<Unit> {
        val p = profiles.createProfile("A", ExperienceLevel.BEGINNER)
        val repo = RoomPersonalisationRepository(db, p.userId)
        val c = controller(repo, view(repo), OnboardingMode.Required)
        c.next(); c.setHeightIndex(170); c.next()
        c.skip()                                                        // weight skipped
        assertEquals(OnboardingStep.EXPERIENCE, c.state.value.step)
        // "App killed" here. A new controller resumes at the stored step with the stored answers.
        val resumed = controller(repo, view(repo), OnboardingMode.Required)
        assertEquals(OnboardingStep.EXPERIENCE, resumed.state.value.step)
        assertEquals(170, resumed.state.value.heightIndex)
        assertNull(view(repo).p.weightKg)
        assertNull("new profiles choose experience explicitly", resumed.state.value.answers.experience)
        // Skipping a later step never clears an earlier answer.
        resumed.setExperience(ExperienceLevel.ADVANCED); resumed.next(); resumed.skip(); resumed.skip(); resumed.skip(); resumed.skip()
        assertEquals(170f, view(repo).p.heightCm)
        assertEquals(OnboardingStep.DONE, resumed.state.value.step)
    }

    @Test fun existingProfileOptionalFlowNeverOverwritesWithoutAnAnswer() = runBlocking<Unit> {
        val p = profiles.createProfile("Old", ExperienceLevel.ADVANCED, ProfileSetup(requireOnboarding = false))
        profiles.updatePreferences(p.userId, profiles.getPreferences(p.userId)!!.copy(preferredPracticeMinutes = 20))
        val repo = RoomPersonalisationRepository(db, p.userId)
        val c = controller(repo, view(repo), OnboardingMode.Optional)
        assertEquals(ExperienceLevel.ADVANCED, c.state.value.answers.experience) // prefilled, not reset
        assertEquals(20, c.state.value.answers.minutes)
        c.next(); c.skip(); c.skip()                                    // welcome, skip height, skip weight
        c.leave()                                                       // "Not now" mid-way
        assertEquals(ExperienceLevel.ADVANCED, profiles.getProfile(p.userId)!!.experienceLevel)
        assertEquals(20, profiles.getPreferences(p.userId)!!.preferredPracticeMinutes)
        val v = view(repo)
        assertFalse(v.mustOnboard); assertEquals(OnboardingStatus.IN_PROGRESS, v.p.status)
        repo.dismissPrompt()
        assertFalse(view(repo).showPrompt)
    }

    @Test fun editingOneAnswerFromProfileKeepsProgress() = runBlocking<Unit> {
        val p = profiles.createProfile("A", ExperienceLevel.BEGINNER, ProfileSetup(requireOnboarding = false))
        val repo = RoomPersonalisationRepository(db, p.userId)
        val c = controller(repo, view(repo), OnboardingMode.Edit(OnboardingStep.GOALS))
        assertEquals(OnboardingStep.GOALS, c.state.value.step)
        c.toggleGoal(WellnessGoal.POSTURE); c.next()
        assertTrue(c.state.value.finished)
        val v = view(repo)
        assertEquals(setOf(WellnessGoal.POSTURE), v.p.goals)
        assertEquals(OnboardingStatus.NOT_STARTED, v.p.status)
    }

    @Test fun personalisationIsIsolatedPerProfileAndDeletedWithIt() = runBlocking<Unit> {
        val a = profiles.createProfile("A", ExperienceLevel.BEGINNER)
        val b = profiles.createProfile("B", ExperienceLevel.BEGINNER)
        val ra = RoomPersonalisationRepository(db, a.userId)
        ra.saveStep(OnboardingStep.GOALS, OnboardingAnswers(goals = setOf(WellnessGoal.STRENGTH)), OnboardingStep.DURATION, skipped = false)
        ra.saveStep(OnboardingStep.BODY_AREAS, OnboardingAnswers(bodyAreas = mapOf(BodyArea.NECK to BodyAreaIntent.DISCOMFORT)), OnboardingStep.GOALS, skipped = false)
        val vb = view(RoomPersonalisationRepository(db, b.userId))
        assertTrue(vb.p.goals.isEmpty()); assertTrue(vb.p.bodyAreas.isEmpty())
        profiles.deleteProfile(a.userId)
        assertNull(db.personalisation().get(a.userId))
        assertTrue(db.personalisation().goals(a.userId).isEmpty()); assertTrue(db.personalisation().areas(a.userId).isEmpty())
        assertFalse(RoomPersonalisationRepository(db, a.userId).saveStep(OnboardingStep.GOALS, OnboardingAnswers(goals = setOf(WellnessGoal.HABIT)), OnboardingStep.DURATION, false))
    }

    @Test fun answersPersistAcrossAppRestart() = runBlocking<Unit> {
        val f = File.createTempFile("ys-restart", ".db").also { it.delete() }
        try {
            val first = JvmRoom.file(f.path)
            val p = RoomProfileRepository(first).createProfile("A", ExperienceLevel.BEGINNER)
            RoomPersonalisationRepository(first, p.userId).saveStep(OnboardingStep.FREQUENCY, OnboardingAnswers(frequency = PracticeFrequency.DAILY), OnboardingStep.DONE, false)
            RoomPersonalisationRepository(first, p.userId).saveStep(OnboardingStep.DONE, OnboardingAnswers(), OnboardingStep.DONE, true)
            first.close()
            val second = JvmRoom.file(f.path)
            val v = withTimeout(5_000) { RoomPersonalisationRepository(second, p.userId).observe().first() }
            assertEquals(PracticeFrequency.DAILY, v.p.weeklyFrequency); assertFalse(v.mustOnboard)
            second.close()
        } finally { listOf("", "-wal", "-shm", "-journal").forEach { File(f.path + it).delete() } }
    }

    @Test fun historyQueriesAreSharedAcrossCollectors() = runBlocking<Unit> {
        val a = profiles.createProfile("A", ExperienceLevel.BEGINNER)
        RoomPracticeHistoryRepository(db, a.userId).record(CompletedPracticeSession("s", 1, 2, 60_000, SessionCompletion.COMPLETED, listOf(PoseOutcome(Asana.TREE, null, null, 1_000, 1_000)), emptyList()))
        val shared = RoomPracticeHistoryRepository(db, a.userId, scope = scope)
        // Home (weekly + overview) and Progress (weekly + overview) collecting at once:
        val jobs = List(4) { i -> scope.launch { if (i % 2 == 0) shared.weeklySummary().first() else shared.overview().first() } }
        jobs.forEach { it.join() }
        assertEquals(1, shared.overview().first().recordedSessions)
        assertTrue("sessions + pose results upstreams only, got ${shared.upstreamStarts}", shared.upstreamStarts <= 2)
    }
}

