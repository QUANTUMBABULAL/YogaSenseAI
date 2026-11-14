package com.chinmai.yogasense.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/*
 * Every user-specific query takes a userId, or derives ownership by joining to the owning parent
 * row with that userId. No query returns another user's records.
 */

@Dao
interface ProfileDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(profile: UserProfileEntity)
    @Update suspend fun update(profile: UserProfileEntity)
    @Query("SELECT * FROM user_profiles WHERE user_id = :userId") suspend fun findById(userId: String): UserProfileEntity?
    @Query("SELECT * FROM user_profiles WHERE user_id = :userId") fun observe(userId: String): Flow<UserProfileEntity?>
    /** Profile chooser listing (profiles themselves, not their records). */
    @Query("SELECT * FROM user_profiles WHERE status = 'ACTIVE' ORDER BY created_at") fun observeActive(): Flow<List<UserProfileEntity>>
    /** Deleting a profile cascades to all records it owns. */
    @Query("DELETE FROM user_profiles WHERE user_id = :userId") suspend fun delete(userId: String): Int
    @Query("SELECT COUNT(*) FROM user_profiles") suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertPreferences(prefs: UserPreferencesEntity)   // leaf table
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertPersonalisation(p: PersonalisationEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putMetadata(entries: List<AppMetadataEntity>)     // leaf table

    /** Profile + its initial preferences + one-time markers, atomically. */
    @Transaction
    suspend fun insertWithSetup(
        profile: UserProfileEntity, prefs: UserPreferencesEntity, markers: List<AppMetadataEntity>, personalisation: PersonalisationEntity? = null,
    ) {
        insert(profile)
        upsertPreferences(prefs)
        if (personalisation != null) insertPersonalisation(personalisation)
        if (markers.isNotEmpty()) putMetadata(markers)
    }

    /** Writes [prefs] only if its profile exists (checked in the same transaction). */
    @Transaction
    suspend fun updatePreferencesIfExists(prefs: UserPreferencesEntity): Boolean {
        if (findById(prefs.userId) == null) return false
        upsertPreferences(prefs)
        return true
    }
}

/**
 * The legacy wellness import as ONE transaction: insert-if-absent, read back, verify, mark complete.
 * If [LegacyImportPlan.verify] reports problems, it throws and everything is rolled back.
 */
@Dao
interface LegacyImportDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertCheckIn(c: WellnessCheckInEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertAssessment(a: WellnessAssessmentEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertResponses(r: List<AssessmentResponseEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertConsent(c: ConsentRecordEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putMetadata(entries: List<AppMetadataEntity>)     // leaf table

    @Query("SELECT * FROM wellness_check_ins WHERE user_id = :userId ORDER BY epoch_day DESC") suspend fun checkIns(userId: String): List<WellnessCheckInEntity>
    @Query("SELECT * FROM wellness_assessments WHERE user_id = :userId ORDER BY completed_at DESC") suspend fun assessments(userId: String): List<WellnessAssessmentEntity>
    @Query(
        """SELECT r.* FROM assessment_responses r JOIN wellness_assessments a ON a.assessment_id = r.assessment_id
           WHERE a.user_id = :userId ORDER BY r.assessment_id, r.question_number""",
    )
    suspend fun responses(userId: String): List<AssessmentResponseEntity>
    @Query("SELECT COUNT(*) FROM wellness_check_ins WHERE user_id = :userId AND check_in_id = :id") suspend fun hasCheckIn(userId: String, id: String): Int
    @Query("SELECT COUNT(*) FROM wellness_assessments WHERE user_id = :userId AND assessment_id = :id") suspend fun hasAssessment(userId: String, id: String): Int
    @Query("SELECT * FROM consent_records WHERE user_id = :userId AND consent_id = :id") suspend fun findConsent(userId: String, id: String): ConsentRecordEntity?
    @Query("UPDATE consent_records SET withdrawn_at = :at WHERE user_id = :userId AND consent_type = :type AND withdrawn_at IS NULL")
    suspend fun withdrawActiveConsents(userId: String, type: ConsentType, at: Long): Int

    @Transaction
    suspend fun importVerified(plan: LegacyImportPlan) {
        val u = plan.ownerId
        // Insert only what is absent (deterministic ids) — existing rows are never overwritten.
        plan.checkIns.forEach { if (hasCheckIn(u, it.checkInId) == 0) insertCheckIn(it) }
        plan.assessments.forEach { (a, rs) ->
            if (hasAssessment(u, a.assessmentId) == 0) { insertAssessment(a); if (rs.isNotEmpty()) insertResponses(rs) }
        }
        plan.consent?.let { c ->
            if (findConsent(u, c.consentId) == null) { withdrawActiveConsents(u, c.consentType, c.grantedAt); insertConsent(c) }
        }
        val problems = plan.verify(checkIns(u), assessments(u), responses(u), plan.consent?.let { findConsent(u, it.consentId) })
        if (problems.isNotEmpty()) throw LegacyImportVerificationException(problems)
        putMetadata(plan.completionMarkers)
    }
}

/** Everything the import transaction needs; [verify] is pure (see LegacyWellnessMapper.verify). */
class LegacyImportPlan(
    val ownerId: String,
    val checkIns: List<WellnessCheckInEntity>,
    val assessments: List<Pair<WellnessAssessmentEntity, List<AssessmentResponseEntity>>>,
    val consent: ConsentRecordEntity?,
    val completionMarkers: List<AppMetadataEntity>,
    val verify: (List<WellnessCheckInEntity>, List<WellnessAssessmentEntity>, List<AssessmentResponseEntity>, ConsentRecordEntity?) -> List<String>,
)

class LegacyImportVerificationException(val problems: List<String>) : Exception("Legacy import verification failed (${problems.size} problem(s))")

@Dao
interface MetadataDao {
    /** Leaf key/value table (nothing references it), so REPLACE is a safe upsert. */
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(entry: AppMetadataEntity)
    @Query("SELECT value FROM app_metadata WHERE `key` = :key") suspend fun get(key: String): String?
    @Query("DELETE FROM app_metadata WHERE `key` = :key") suspend fun remove(key: String): Int
}

@Dao
interface PreferencesDao {
    /** user_preferences is a leaf table (no child rows), so REPLACE is a safe upsert. */
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(prefs: UserPreferencesEntity)
    @Query("SELECT * FROM user_preferences WHERE user_id = :userId") suspend fun get(userId: String): UserPreferencesEntity?
    @Query("SELECT * FROM user_preferences WHERE user_id = :userId") fun observe(userId: String): Flow<UserPreferencesEntity?>
}

@Dao
interface PracticeDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertSession(session: PracticeSessionEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertPoseResults(results: List<PoseResultEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertCorrections(corrections: List<PracticeCorrectionEntity>)

    /** Saves a finished session and its children atomically. */
    @Transaction
    suspend fun insertCompletedSession(session: PracticeSessionEntity, results: List<PoseResultEntity>, corrections: List<PracticeCorrectionEntity>) {
        insertSession(session)
        if (results.isNotEmpty()) insertPoseResults(results)
        if (corrections.isNotEmpty()) insertCorrections(corrections)
    }

    /**
     * Idempotent save: returns false (and writes nothing) if [session]'s id already exists, so a
     * repeated completion callback can never create a duplicate session.
     */
    @Transaction
    suspend fun insertCompletedSessionIfAbsent(
        session: PracticeSessionEntity, results: List<PoseResultEntity>, corrections: List<PracticeCorrectionEntity>,
    ): Boolean {
        if (sessionExists(session.sessionId) > 0) return false
        insertCompletedSession(session, results, corrections)
        return true
    }

    @Query("SELECT COUNT(*) FROM practice_sessions WHERE session_id = :sessionId") suspend fun sessionExists(sessionId: String): Int

    @Query(
        """UPDATE practice_sessions SET ended_at = :endedAt, duration_ms = :durationMs, completion = :completion
           WHERE user_id = :userId AND session_id = :sessionId""",
    )
    suspend fun finishSession(userId: String, sessionId: String, endedAt: Long, durationMs: Long, completion: SessionCompletion): Int

    @Query("SELECT * FROM practice_sessions WHERE user_id = :userId ORDER BY started_at DESC")
    fun observeSessions(userId: String): Flow<List<PracticeSessionEntity>>

    @Query("SELECT * FROM practice_sessions WHERE user_id = :userId AND started_at >= :from AND started_at < :to ORDER BY started_at")
    fun observeSessionsBetween(userId: String, from: Long, to: Long): Flow<List<PracticeSessionEntity>>

    @Query("SELECT * FROM practice_sessions WHERE user_id = :userId AND session_id = :sessionId")
    suspend fun findSession(userId: String, sessionId: String): PracticeSessionEntity?

    @Query(
        """SELECT r.* FROM pose_results r JOIN practice_sessions s ON s.session_id = r.session_id
           WHERE s.user_id = :userId AND r.session_id = :sessionId ORDER BY r.asana""",
    )
    suspend fun poseResults(userId: String, sessionId: String): List<PoseResultEntity>

    @Query(
        """SELECT r.* FROM pose_results r JOIN practice_sessions s ON s.session_id = r.session_id
           WHERE s.user_id = :userId ORDER BY s.started_at DESC LIMIT :limit""",
    )
    fun observeRecentPoseResults(userId: String, limit: Int): Flow<List<PoseResultEntity>>

    /** Every pose result of this user's sessions (for per-asana history). */
    @Query(
        """SELECT r.* FROM pose_results r JOIN practice_sessions s ON s.session_id = r.session_id
           WHERE s.user_id = :userId ORDER BY s.started_at DESC""",
    )
    fun observeAllPoseResults(userId: String): Flow<List<PoseResultEntity>>

    @Query(
        """SELECT c.* FROM practice_corrections c JOIN practice_sessions s ON s.session_id = c.session_id
           WHERE s.user_id = :userId AND c.session_id = :sessionId ORDER BY c.timestamp""",
    )
    suspend fun corrections(userId: String, sessionId: String): List<PracticeCorrectionEntity>

    @Query("DELETE FROM practice_sessions WHERE user_id = :userId AND session_id = :sessionId")
    suspend fun deleteSession(userId: String, sessionId: String): Int
}

@Dao
interface WellnessDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertCheckIn(c: WellnessCheckInEntity)
    @Update suspend fun updateCheckIn(c: WellnessCheckInEntity)

    @Query("SELECT * FROM wellness_check_ins WHERE user_id = :userId AND epoch_day = :epochDay")
    suspend fun checkInForDay(userId: String, epochDay: Long): WellnessCheckInEntity?

    /** One check-in per user per day: an existing row for that day is updated (keeping its id). */
    @Transaction
    suspend fun upsertCheckInForDay(c: WellnessCheckInEntity) {
        val existing = checkInForDay(c.userId, c.epochDay)
        if (existing == null) insertCheckIn(c) else updateCheckIn(c.copy(checkInId = existing.checkInId))
    }

    @Query("SELECT * FROM wellness_check_ins WHERE user_id = :userId ORDER BY epoch_day DESC")
    fun observeCheckIns(userId: String): Flow<List<WellnessCheckInEntity>>

    @Query("DELETE FROM wellness_check_ins WHERE user_id = :userId AND epoch_day = :epochDay")
    suspend fun deleteCheckIn(userId: String, epochDay: Long): Int

    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertAssessment(a: WellnessAssessmentEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertResponses(r: List<AssessmentResponseEntity>)

    @Transaction
    suspend fun insertAssessmentWithResponses(a: WellnessAssessmentEntity, responses: List<AssessmentResponseEntity>) {
        insertAssessment(a)
        insertResponses(responses)
    }

    @Query("SELECT * FROM wellness_assessments WHERE user_id = :userId ORDER BY completed_at DESC")
    fun observeAssessments(userId: String): Flow<List<WellnessAssessmentEntity>>

    @Query(
        """SELECT r.* FROM assessment_responses r JOIN wellness_assessments a ON a.assessment_id = r.assessment_id
           WHERE a.user_id = :userId AND r.assessment_id = :assessmentId ORDER BY r.question_number""",
    )
    suspend fun responses(userId: String, assessmentId: String): List<AssessmentResponseEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertConsent(c: ConsentRecordEntity)

    @Query("UPDATE consent_records SET withdrawn_at = :at WHERE user_id = :userId AND consent_id = :consentId AND withdrawn_at IS NULL")
    suspend fun withdrawConsent(userId: String, consentId: String, at: Long): Int

    @Query(
        """SELECT * FROM consent_records WHERE user_id = :userId AND consent_type = :type AND withdrawn_at IS NULL
           ORDER BY granted_at DESC LIMIT 1""",
    )
    suspend fun activeConsent(userId: String, type: ConsentType): ConsentRecordEntity?

    @Query("SELECT * FROM consent_records WHERE user_id = :userId ORDER BY granted_at")
    suspend fun consentHistory(userId: String): List<ConsentRecordEntity>

    // ---- Phase 7B: profile-scoped wellness repository and legacy import ----------------------

    @Query(
        """SELECT * FROM consent_records WHERE user_id = :userId AND consent_type = :type AND withdrawn_at IS NULL
           ORDER BY granted_at DESC LIMIT 1""",
    )
    fun observeActiveConsent(userId: String, type: ConsentType): Flow<ConsentRecordEntity?>

    @Query("UPDATE consent_records SET withdrawn_at = :at WHERE user_id = :userId AND consent_type = :type AND withdrawn_at IS NULL")
    suspend fun withdrawActiveConsents(userId: String, type: ConsentType, at: Long): Int

    @Query(
        """SELECT r.* FROM assessment_responses r JOIN wellness_assessments a ON a.assessment_id = r.assessment_id
           WHERE a.user_id = :userId ORDER BY r.assessment_id, r.question_number""",
    )
    fun observeAllResponses(userId: String): Flow<List<AssessmentResponseEntity>>

    @Query("SELECT * FROM wellness_check_ins WHERE user_id = :userId ORDER BY epoch_day DESC")
    suspend fun checkIns(userId: String): List<WellnessCheckInEntity>

    @Query("SELECT * FROM wellness_assessments WHERE user_id = :userId ORDER BY completed_at DESC")
    suspend fun assessments(userId: String): List<WellnessAssessmentEntity>

    @Query(
        """SELECT r.* FROM assessment_responses r JOIN wellness_assessments a ON a.assessment_id = r.assessment_id
           WHERE a.user_id = :userId ORDER BY r.assessment_id, r.question_number""",
    )
    suspend fun allResponses(userId: String): List<AssessmentResponseEntity>

    @Query("SELECT * FROM wellness_check_ins WHERE user_id = :userId AND check_in_id = :id")
    suspend fun findCheckIn(userId: String, id: String): WellnessCheckInEntity?

    @Query("SELECT * FROM wellness_assessments WHERE user_id = :userId AND assessment_id = :id")
    suspend fun findAssessment(userId: String, id: String): WellnessAssessmentEntity?

    @Query("SELECT * FROM consent_records WHERE user_id = :userId AND consent_id = :id")
    suspend fun findConsent(userId: String, id: String): ConsentRecordEntity?

    @Query("DELETE FROM wellness_check_ins WHERE user_id = :userId") suspend fun deleteAllCheckIns(userId: String): Int
    /** Responses are removed by cascade. */
    @Query("DELETE FROM wellness_assessments WHERE user_id = :userId") suspend fun deleteAllAssessments(userId: String): Int
    @Query("DELETE FROM consent_records WHERE user_id = :userId") suspend fun deleteAllConsents(userId: String): Int

    @Transaction
    suspend fun deleteAllFor(userId: String) {
        deleteAllCheckIns(userId); deleteAllAssessments(userId); deleteAllConsents(userId)
    }

    /** Replaces the active consent decision: withdraws the current one, then records [next] (if any). */
    @Transaction
    suspend fun replaceConsent(userId: String, type: ConsentType, at: Long, next: ConsentRecordEntity?) {
        withdrawActiveConsents(userId, type, at)
        if (next != null) insertConsent(next)
    }
}


/** Personalisation (v3). Every query is scoped to the given userId. */
@Dao
interface PersonalisationDao {
    @Query("SELECT * FROM profile_personalisation WHERE user_id = :userId") fun observe(userId: String): Flow<PersonalisationEntity?>
    @Query("SELECT * FROM profile_personalisation WHERE user_id = :userId") suspend fun get(userId: String): PersonalisationEntity?
    @Query("SELECT * FROM profile_goals WHERE user_id = :userId ORDER BY goal") fun observeGoals(userId: String): Flow<List<ProfileGoalEntity>>
    @Query("SELECT * FROM profile_body_areas WHERE user_id = :userId ORDER BY area") fun observeAreas(userId: String): Flow<List<ProfileBodyAreaEntity>>
    @Query("SELECT * FROM profile_goals WHERE user_id = :userId ORDER BY goal") suspend fun goals(userId: String): List<ProfileGoalEntity>
    @Query("SELECT * FROM profile_body_areas WHERE user_id = :userId ORDER BY area") suspend fun areas(userId: String): List<ProfileBodyAreaEntity>

    /** Leaf table (nothing references it), so REPLACE is a safe upsert. */
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(p: PersonalisationEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertGoals(g: List<ProfileGoalEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertAreas(a: List<ProfileBodyAreaEntity>)
    @Query("DELETE FROM profile_goals WHERE user_id = :userId") suspend fun clearGoals(userId: String): Int
    @Query("DELETE FROM profile_body_areas WHERE user_id = :userId") suspend fun clearAreas(userId: String): Int

    @Query("UPDATE user_profiles SET experience_level = :level, updated_at = :at WHERE user_id = :userId")
    suspend fun setExperience(userId: String, level: ExperienceLevel, at: Long): Int
    @Query("UPDATE user_preferences SET preferred_practice_minutes = :minutes, updated_at = :at WHERE user_id = :userId")
    suspend fun setPreferredMinutes(userId: String, minutes: Int?, at: Long): Int
    @Query("SELECT COUNT(*) FROM user_profiles WHERE user_id = :userId") suspend fun profileExists(userId: String): Int

    /**
     * Saves one onboarding step atomically. Null [experience]/[minutes]/[goals]/[areas] mean
     * "leave unchanged" (Skip never clears or overwrites a stored value).
     */
    @Transaction
    suspend fun saveStep(
        p: PersonalisationEntity,
        goals: List<ProfileGoalEntity>?,
        areas: List<ProfileBodyAreaEntity>?,
        experience: ExperienceLevel?,
        minutes: Int?,
    ): Boolean {
        if (profileExists(p.userId) == 0) return false
        put(p)
        if (goals != null) { clearGoals(p.userId); if (goals.isNotEmpty()) insertGoals(goals) }
        if (areas != null) { clearAreas(p.userId); if (areas.isNotEmpty()) insertAreas(areas) }
        if (experience != null) setExperience(p.userId, experience, p.updatedAt)
        if (minutes != null) setPreferredMinutes(p.userId, minutes, p.updatedAt)
        return true
    }
}
