package com.chinmai.yogasense.data

import com.chinmai.yogasense.data.db.PersonalisationEntity
import com.chinmai.yogasense.data.db.ProfileBodyAreaEntity
import com.chinmai.yogasense.data.db.ProfileGoalEntity
import com.chinmai.yogasense.data.db.YogaSenseDatabase
import com.chinmai.yogasense.profile.OnboardingAnswers
import com.chinmai.yogasense.profile.OnboardingStatus
import com.chinmai.yogasense.profile.OnboardingStep
import com.chinmai.yogasense.profile.Personalisation
import com.chinmai.yogasense.profile.PersonalisationView
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

object PersonalisationMappers {
    fun toDomain(e: PersonalisationEntity, goals: List<ProfileGoalEntity>, areas: List<ProfileBodyAreaEntity>) = Personalisation(
        heightCm = e.heightCm, weightKg = e.weightKg, heightUnit = e.heightUnit, weightUnit = e.weightUnit,
        bodyAreas = areas.associate { it.area to it.intent }, noParticularArea = e.noParticularArea,
        goals = goals.map { it.goal }.toSet(), weeklyFrequency = e.weeklyFrequency,
        onboardingRequired = e.onboardingRequired, status = e.onboardingStatus, step = e.onboardingStep,
        version = e.onboardingVersion, promptDismissed = e.promptDismissed, updatedAt = e.updatedAt,
    )

    fun toEntity(userId: String, p: Personalisation) = PersonalisationEntity(
        userId, p.heightCm, p.weightKg, p.heightUnit, p.weightUnit, p.weeklyFrequency, p.noParticularArea,
        p.onboardingRequired, p.status, p.step, p.version, p.promptDismissed, p.updatedAt,
    )

    /** A fresh row for a newly created profile. */
    fun newProfileRow(userId: String, required: Boolean, now: Long) = toEntity(userId, Personalisation(onboardingRequired = required, updatedAt = now))
}

/**
 * Personalisation for ONE profile ([userId] fixed at construction). Experience level and preferred
 * minutes are written to their existing columns, never duplicated.
 */
class RoomPersonalisationRepository(
    private val db: YogaSenseDatabase,
    private val userId: String,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val dao = db.personalisation()

    fun observe(): Flow<PersonalisationView> = combine(
        dao.observe(userId), dao.observeGoals(userId), dao.observeAreas(userId),
        db.profiles().observe(userId), db.preferences().observe(userId),
    ) { p, g, a, profile, prefs ->
        PersonalisationView(
            personalisation = p?.let { PersonalisationMappers.toDomain(it, g, a) },
            experienceLevel = profile?.experienceLevel ?: com.chinmai.yogasense.data.db.ExperienceLevel.BEGINNER,
            preferredMinutes = prefs?.preferredPracticeMinutes,
        )
    }

    suspend fun current(): Personalisation? = dao.get(userId)?.let { PersonalisationMappers.toDomain(it, dao.goals(userId), dao.areas(userId)) }

    /**
     * Persists the answer of [step] (Continue) or only the position (Skip, [skipped] = true), and
     * marks onboarding COMPLETED only when [step] is [OnboardingStep.DONE] (the final CTA). It
     * records [nextStep] so an interrupted onboarding resumes there. Only the field(s) belonging to
     * [step] are written. [editOnly]: a single answer edited from Profile (progress unchanged).
     * Returns false if the profile no longer exists.
     */
    suspend fun saveStep(step: OnboardingStep, answers: OnboardingAnswers, nextStep: OnboardingStep, skipped: Boolean, editOnly: Boolean = false): Boolean {
        val now = clock()
        val base = current() ?: Personalisation(onboardingRequired = false)
        val status = when {
            editOnly -> base.status // editing one answer from Profile doesn't change onboarding progress
            step == OnboardingStep.DONE -> OnboardingStatus.COMPLETED // only the final CTA completes onboarding
            base.status == OnboardingStatus.COMPLETED -> OnboardingStatus.COMPLETED // editing later never "un-completes"
            else -> OnboardingStatus.IN_PROGRESS
        }
        var p = base.copy(status = status, step = if (editOnly) base.step else if (status == OnboardingStatus.COMPLETED) base.step.coerceAtLeast(nextStep.ordinal) else nextStep.ordinal, updatedAt = now, version = Personalisation.ONBOARDING_VERSION)
        var goals: List<ProfileGoalEntity>? = null
        var areas: List<ProfileBodyAreaEntity>? = null
        var experience: com.chinmai.yogasense.data.db.ExperienceLevel? = null
        var minutes: Int? = null
        if (!skipped) when (step) {
            OnboardingStep.HEIGHT -> p = p.copy(heightCm = answers.heightCm, heightUnit = answers.heightUnit)
            OnboardingStep.WEIGHT -> p = p.copy(weightKg = answers.weightKg, weightUnit = answers.weightUnit)
            OnboardingStep.EXPERIENCE -> experience = answers.experience
            OnboardingStep.BODY_AREAS -> {
                p = p.copy(noParticularArea = answers.noParticularArea)
                areas = answers.bodyAreas.map { (a, i) -> ProfileBodyAreaEntity(userId, a, i) }
            }
            OnboardingStep.GOALS -> goals = answers.goals.map { ProfileGoalEntity(userId, it) }
            OnboardingStep.DURATION -> minutes = answers.minutes
            OnboardingStep.FREQUENCY -> p = p.copy(weeklyFrequency = answers.frequency)
            OnboardingStep.WELCOME, OnboardingStep.DONE -> Unit
        }
        return dao.saveStep(PersonalisationMappers.toEntity(userId, p), goals, areas, experience, minutes)
    }

    /** Hides the optional "personalise" prompt for existing profiles (Profile tab still offers it). */
    suspend fun dismissPrompt() {
        val base = current() ?: Personalisation(onboardingRequired = false)
        dao.saveStep(PersonalisationMappers.toEntity(userId, base.copy(promptDismissed = true, updatedAt = clock())), null, null, null, null)
    }
}
