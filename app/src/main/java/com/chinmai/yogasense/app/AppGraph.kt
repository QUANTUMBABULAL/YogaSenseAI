package com.chinmai.yogasense.app

import android.content.Context
import android.util.Log
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import com.chinmai.yogasense.coaching.DeviceSettings
import com.chinmai.yogasense.coaching.LegacyPracticeSettings
import com.chinmai.yogasense.data.LegacyImportLifecycle
import com.chinmai.yogasense.data.LegacyWellnessMigrator
import com.chinmai.yogasense.data.ProfileRepository
import com.chinmai.yogasense.data.RoomAppMetadata
import com.chinmai.yogasense.data.RoomPracticeHistoryRepository
import com.chinmai.yogasense.data.RoomPersonalisationRepository
import com.chinmai.yogasense.profile.PersonalisationView
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import com.chinmai.yogasense.data.RoomProfileRepository
import com.chinmai.yogasense.data.RoomWellnessRepository
import com.chinmai.yogasense.data.SqliteLegacyWellnessSource
import com.chinmai.yogasense.data.WellnessRepository
import com.chinmai.yogasense.data.db.YogaSenseDatabase
import com.chinmai.yogasense.profile.ActiveProfileManager
import com.chinmai.yogasense.profile.CompletedPracticeSession
import com.chinmai.yogasense.profile.SharedPrefsActiveProfileStore
import com.chinmai.yogasense.profile.UserPreferences
import com.chinmai.yogasense.profile.UserProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Everything one active profile may touch. Each repository is constructed with this profile's id,
 * so every query and write is scoped in the data layer (not only hidden in the UI).
 *
 * Writers that must outlive the screen ([savePreferences], [recordPractice]) run in the app scope
 * but capture [userId] at the time of the call: a pending write can never land in another profile.
 * [close] cancels this profile's observers and clears every ViewModel created under it.
 */
class ProfileSession(
    val userId: String,
    private val db: YogaSenseDatabase,
    private val profiles: ProfileRepository,
    private val appScope: CoroutineScope,
    val deviceSettings: DeviceSettings,
    onWellnessDeleted: suspend (userId: String) -> Unit = {},
) : ViewModelStoreOwner {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    override val viewModelStore = ViewModelStore()

    val practiceHistory = RoomPracticeHistoryRepository(db, userId, scope = scope)
    val personalisation = RoomPersonalisationRepository(db, userId)
    /** Null until loaded. Drives the onboarding gate, Home suggestions and the Profile tab. */
    val personalisationView: StateFlow<PersonalisationView?> = personalisation.observe().stateIn(scope, SharingStarted.Eagerly, null)
    val wellness: WellnessRepository = RoomWellnessRepository(db, userId, scope, afterDeleteAll = { onWellnessDeleted(userId) })

    suspend fun loadPreferences(): UserPreferences = profiles.getPreferences(userId) ?: UserPreferences()

    fun savePreferences(p: UserPreferences) {
        val owner = userId
        appScope.launch { runCatching { profiles.updatePreferences(owner, p) }.onFailure { Log.w(TAG, "Preferences not saved (${it.javaClass.simpleName})") } }
    }

    fun recordPractice(s: CompletedPracticeSession) {
        val owner = userId
        appScope.launch {
            // Uses a repository bound to the owner captured above, not to whoever is active now.
            runCatching { RoomPracticeHistoryRepository(db, owner).record(s) }
                .onFailure { Log.w(TAG, "Practice session not saved (${it.javaClass.simpleName})") } // e.g. profile deleted meanwhile
        }
    }

    fun close() {
        scope.cancel()
        viewModelStore.clear()
    }

    private companion object { const val TAG = "ProfileSession" }
}

val LocalProfileSession = staticCompositionLocalOf<ProfileSession> { error("No active profile: profile-scoped screens require an active profile") }

/** App-wide singletons (no DI framework). */
class AppGraph private constructor(context: Context) {
    private val app = context.applicationContext
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val db = YogaSenseDatabase.get(app)
    val profileRepository: ProfileRepository = RoomProfileRepository(db)
    private val metadata = RoomAppMetadata(db)
    private val legacySettings = LegacyPracticeSettings(app)
    val deviceSettings = DeviceSettings(app)
    val legacyWellness = LegacyWellnessMigrator(db, SqliteLegacyWellnessSource(app))

    private val lifecycle = LegacyImportLifecycle(metadata, legacySettings::read, legacyWellness) { currentProfiles() }

    val profiles = ActiveProfileManager(profileRepository, SharedPrefsActiveProfileStore(app), lifecycle, appScope)

    private suspend fun currentProfiles(): List<UserProfile> = profileRepository.observeProfiles().first()

    init {
        // Startup: evaluate (and retry, if interrupted) the legacy wellness import.
        appScope.launch { legacyWellness.refresh(profiles.profiles.filterNotNull().first()) }
    }

    /** True if the old device-wide practice settings exist and were never imported into a profile. */
    suspend fun legacySettingsPending(): Boolean = kotlinx.coroutines.withContext(Dispatchers.IO) {
        metadata.get(LegacyImportLifecycle.KEY_PREFS_IMPORTED) == null && legacySettings.read() != null
    }

    fun retryLegacyWellness() { appScope.launch { legacyWellness.refresh(currentProfiles()) } }

    fun assignLegacyWellness(userId: String) { appScope.launch { legacyWellness.assignTo(userId, currentProfiles()) } }

    val wellnessStatus get() = legacyWellness.status

    private var current: ProfileSession? = null

    /** Main thread. Returns the session for [userId], closing any other profile's session. */
    fun sessionFor(userId: String): ProfileSession {
        current?.let { if (it.userId == userId) return it else it.close() }
        return ProfileSession(userId, db, profileRepository, appScope, deviceSettings) { legacyWellness.onOwnerDataErased(it) }.also { current = it }
    }

    /** Main thread. Closes [userId]'s session if it is no longer the active profile. */
    fun endSessionIfInactive(userId: String) {
        val c = current ?: return
        if (c.userId == userId && profiles.activeUserId.value != userId) { c.close(); current = null }
    }

    companion object {
        @Volatile private var instance: AppGraph? = null
        fun get(context: Context): AppGraph = instance ?: synchronized(this) { instance ?: AppGraph(context).also { instance = it } }
    }
}
