package com.chinmai.yogasense.data

import com.chinmai.yogasense.data.db.AppMetadataEntity
import com.chinmai.yogasense.data.db.ExperienceLevel
import com.chinmai.yogasense.data.db.Mappers
import com.chinmai.yogasense.data.db.Mappers.toDomain
import com.chinmai.yogasense.data.db.Mappers.toEntity
import com.chinmai.yogasense.data.db.Mappers.toRows
import com.chinmai.yogasense.data.db.PracticeStats
import com.chinmai.yogasense.data.db.ProfileStatus
import com.chinmai.yogasense.data.db.UserProfileEntity
import com.chinmai.yogasense.data.db.YogaSenseDatabase
import com.chinmai.yogasense.profile.CompletedPracticeSession
import com.chinmai.yogasense.profile.ProfileSetup
import com.chinmai.yogasense.profile.ProfileValidation
import com.chinmai.yogasense.profile.UserPreferences
import com.chinmai.yogasense.profile.UserProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.util.UUID

/** Local profiles and their preferences. UI depends on this interface, never on DAOs. */
interface ProfileRepository {
    fun observeProfiles(): Flow<List<UserProfile>>
    suspend fun getProfile(userId: String): UserProfile?
    suspend fun profileCount(): Int
    /**
     * Creates a profile with [setup]'s preferences (defaults unless given) and markers, all in one
     * transaction. Throws IllegalArgumentException for an invalid name.
     */
    suspend fun createProfile(displayName: String, experienceLevel: ExperienceLevel, setup: ProfileSetup = ProfileSetup()): UserProfile
    fun observePreferences(userId: String): Flow<UserPreferences?>
    suspend fun getPreferences(userId: String): UserPreferences?
    /** Updates only [userId]'s preferences. Throws IllegalArgumentException for an unknown profile. */
    suspend fun updatePreferences(userId: String, preferences: UserPreferences)
    /** Permanently deletes the profile and everything it owns (cascade). */
    suspend fun deleteProfile(userId: String)
}

/** Writes finished practice sessions for one profile. */
interface PracticeSessionRecorder {
    /** Returns true if written, false if this session id was already recorded (idempotent). */
    suspend fun record(session: CompletedPracticeSession): Boolean
}

/** App-level one-time markers (Room `app_metadata`). Never holds personal data. */
interface AppMetadata {
    suspend fun get(key: String): String?
    suspend fun put(key: String, value: String)
}

class RoomAppMetadata(private val db: YogaSenseDatabase) : AppMetadata {
    override suspend fun get(key: String) = db.metadata().get(key)
    override suspend fun put(key: String, value: String) = db.metadata().put(AppMetadataEntity(key, value))
}

class RoomProfileRepository(
    private val db: YogaSenseDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) : ProfileRepository {
    override fun observeProfiles(): Flow<List<UserProfile>> = db.profiles().observeActive().map { l -> l.map { it.toDomain() } }

    override suspend fun getProfile(userId: String): UserProfile? = db.profiles().findById(userId)?.toDomain()

    override suspend fun profileCount(): Int = db.profiles().count()

    override suspend fun createProfile(displayName: String, experienceLevel: ExperienceLevel, setup: ProfileSetup): UserProfile {
        val name = requireNotNull(ProfileValidation.displayName(displayName)) { "Display name must be 1–${ProfileValidation.MAX_NAME} characters" }
        val now = clock()
        val entity = UserProfileEntity(newId(), name, experienceLevel, now, now, ProfileStatus.ACTIVE)
        db.profiles().insertWithSetup(
            entity,
            setup.preferences.toEntity(entity.userId, now),
            setup.markers(entity.userId).map { (k, v) -> AppMetadataEntity(k, v) },
            PersonalisationMappers.newProfileRow(entity.userId, setup.requireOnboarding, now),
        )
        return entity.toDomain()
    }

    override fun observePreferences(userId: String): Flow<UserPreferences?> = db.preferences().observe(userId).map { it?.toDomain() }

    override suspend fun getPreferences(userId: String): UserPreferences? = db.preferences().get(userId)?.toDomain()

    override suspend fun updatePreferences(userId: String, preferences: UserPreferences) {
        require(db.profiles().updatePreferencesIfExists(preferences.toEntity(userId, clock()))) { "Unknown profile" }
    }

    override suspend fun deleteProfile(userId: String) { db.profiles().delete(userId) }
}

/**
 * Room-backed implementation of the existing [PracticeHistoryRepository], scoped to ONE profile.
 * Construct a new instance per active profile; every query includes [userId].
 */
class RoomPracticeHistoryRepository(
    private val db: YogaSenseDatabase,
    private val userId: String,
    /**
     * Profile-session scope. When given, the sessions and pose-result queries are shared by all
     * collectors (Home and Progress previously ran the same sessions query 3–5 times per change).
     */
    scope: CoroutineScope? = null,
    private val clock: () -> Long = System::currentTimeMillis,
) : PracticeHistoryRepository, PracticeSessionRecorder {
    /** For tests: how many times each shared upstream query was (re)started. */
    @Volatile var upstreamStarts = 0
        private set

    private fun <T> Flow<T>.shared(scope: CoroutineScope?): Flow<T> =
        if (scope == null) this else onStart { upstreamStarts++ }.shareIn(scope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    private val sessions = db.practice().observeSessions(userId).shared(scope)
    private val allResults = db.practice().observeAllPoseResults(userId).shared(scope)

    override fun weeklySummary(): Flow<WeeklySummary?> = sessions.map { PracticeStats.weekly(it, clock()) }

    override fun recentSessions(limit: Int): Flow<List<SessionRecord>> =
        combine(sessions, db.practice().observeRecentPoseResults(userId, limit)) { s, r -> Mappers.recentRecords(s, r, limit) }

    override fun overview(): Flow<PracticeOverview> = combine(sessions, allResults) { s, r -> PracticeStats.overview(s, r) }

    override suspend fun record(session: CompletedPracticeSession): Boolean {
        val rows = session.toRows(userId)
        return db.practice().insertCompletedSessionIfAbsent(rows.session, rows.results, rows.corrections)
    }
}
