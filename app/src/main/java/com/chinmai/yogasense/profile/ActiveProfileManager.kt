package com.chinmai.yogasense.profile

import android.content.Context
import com.chinmai.yogasense.data.ProfileRepository
import com.chinmai.yogasense.data.db.ExperienceLevel
import com.chinmai.yogasense.data.db.ProfileStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext

/** What the app shell may show. Profile-scoped screens exist only under [Active]. */
sealed interface ProfileGate {
    data object Loading : ProfileGate
    data object NoProfiles : ProfileGate
    data class ChooseProfile(val profiles: List<UserProfile>) : ProfileGate
    data class Active(val profile: UserProfile, val profiles: List<UserProfile>) : ProfileGate
}

object ProfileGateResolver {
    /**
     * [profiles] null = still loading. A stored [activeId] is honoured only if that profile is in
     * the (active) list; an id not yet in the list is treated as Loading until it is verified.
     */
    fun resolve(profiles: List<UserProfile>?, activeId: String?, verifying: Boolean = false): ProfileGate = when {
        profiles == null -> ProfileGate.Loading
        activeId != null -> profiles.firstOrNull { it.userId == activeId }?.let { ProfileGate.Active(it, profiles) }
            ?: if (verifying) ProfileGate.Loading else if (profiles.isEmpty()) ProfileGate.NoProfiles else ProfileGate.ChooseProfile(profiles)
        profiles.isEmpty() -> ProfileGate.NoProfiles
        else -> ProfileGate.ChooseProfile(profiles)
    }
}

/** Stores ONLY the active profile id (an opaque UUID) — no names or other personal data. */
interface ActiveProfileStore {
    fun read(): String?
    fun write(userId: String?)
}

class SharedPrefsActiveProfileStore(context: Context) : ActiveProfileStore {
    private val prefs = context.applicationContext.getSharedPreferences("profile_session", Context.MODE_PRIVATE)
    override fun read(): String? = prefs.getString(KEY, null)
    /** commit() so the choice survives an immediate process kill. Call off the main thread. */
    override fun write(userId: String?) { prefs.edit().apply { if (userId == null) remove(KEY) else putString(KEY, userId) }.commit() }
    private companion object { const val KEY = "active_user_id" }
}

/** Hooks run around profile creation/deletion (legacy imports). */
interface ProfileLifecycle {
    /** Preferences and markers for a new profile; [isFirstProfile] = no profile existed before. */
    suspend fun setupFor(isFirstProfile: Boolean): ProfileSetup = ProfileSetup()
    suspend fun afterCreated(profile: UserProfile, isFirstProfile: Boolean) {}
    suspend fun afterDeleted(userId: String) {}
}

sealed interface CreateResult {
    data class Created(val profile: UserProfile) : CreateResult
    data class Invalid(val message: String) : CreateResult
    /** A create was already running (e.g. a repeated tap); nothing new was created. */
    data object Ignored : CreateResult
}

/**
 * Single source of truth for the active profile.
 * - The active id is persisted in [store] and restored only after the profile is verified to exist.
 * - If the active profile disappears (deleted), the active state is cleared.
 * - [create] is serialised: concurrent/repeated calls while one is running are ignored.
 */
class ActiveProfileManager(
    private val repo: ProfileRepository,
    private val store: ActiveProfileStore,
    private val lifecycle: ProfileLifecycle,
    private val scope: CoroutineScope,
    private val io: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO,
) {
    private val _activeId = MutableStateFlow(store.read())
    /** Synchronous view of the active id (null = none). */
    val activeUserId: StateFlow<String?> = _activeId.asStateFlow()
    private val verifying = MutableStateFlow(false)

    /** null until the first list is loaded. */
    val profiles: StateFlow<List<UserProfile>?> = repo.observeProfiles().stateIn(scope, SharingStarted.Eagerly, null)

    val gate: StateFlow<ProfileGate> = combine(profiles, _activeId, verifying) { p, id, v -> ProfileGateResolver.resolve(p, id, v) }
        .stateIn(scope, SharingStarted.Eagerly, ProfileGate.Loading)

    private val createLock = Mutex()
    private val _creating = MutableStateFlow(false)
    val creating: StateFlow<Boolean> = _creating.asStateFlow()

    init {
        // Clear a stored id whose profile no longer exists (checked against the database, not just the list).
        scope.launch {
            combine(profiles, _activeId) { p, id -> p to id }.collect { (p, id) ->
                if (p == null || id == null || p.any { it.userId == id }) {
                    verifying.value = false
                } else {
                    // Not in the list: either just created (list not refreshed yet) or deleted.
                    verifying.value = true
                    val exists = withContext(io) { repo.getProfile(id)?.status == ProfileStatus.ACTIVE }
                    if (!exists && _activeId.value == id) { clear(); verifying.value = false }
                    // If it exists, stay "verifying" (Loading) until the list includes it.
                }
            }
        }
    }

    /** Activates [userId] if it exists. Returns false (and changes nothing) otherwise. */
    suspend fun select(userId: String): Boolean {
        val p = withContext(io) { repo.getProfile(userId) } ?: return false
        if (p.status != ProfileStatus.ACTIVE) return false
        withContext(io) { store.write(userId) }
        _activeId.value = userId
        return true
    }

    /** Leaves the active profile (e.g. "Switch profile"). */
    suspend fun clear() {
        _activeId.value = null
        withContext(io) { store.write(null) }
    }

    suspend fun create(displayName: String, level: ExperienceLevel): CreateResult {
        if (!createLock.tryLock()) return CreateResult.Ignored
        _creating.value = true
        try {
            ProfileValidation.displayName(displayName) ?: return CreateResult.Invalid("Enter a name of 1–${ProfileValidation.MAX_NAME} characters.")
            val first = withContext(io) { repo.profileCount() } == 0
            val setup = lifecycle.setupFor(first)
            val profile = withContext(io) { repo.createProfile(displayName, level, setup) }
            lifecycle.afterCreated(profile, first)
            select(profile.userId)
            return CreateResult.Created(profile)
        } catch (e: IllegalArgumentException) {
            return CreateResult.Invalid(e.message ?: "Invalid profile")
        } finally {
            _creating.value = false
            createLock.unlock()
        }
    }

    /** Permanently deletes [userId] and its data. If it was active, the active state is cleared first. */
    suspend fun delete(userId: String) {
        if (_activeId.value == userId) clear()
        withContext(io) { repo.deleteProfile(userId) }
        lifecycle.afterDeleted(userId)
    }
}
