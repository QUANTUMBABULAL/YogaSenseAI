package com.chinmai.yogasense.data

import com.chinmai.yogasense.coaching.CoachSettings
import com.chinmai.yogasense.coaching.VisualSettings
import com.chinmai.yogasense.profile.PracticePreferences
import com.chinmai.yogasense.profile.ProfileLifecycle
import com.chinmai.yogasense.profile.ProfileSetup
import com.chinmai.yogasense.profile.UserPreferences
import com.chinmai.yogasense.profile.UserProfile

/**
 * One-time imports tied to profile creation/deletion.
 *
 * Practice settings: the device-wide Phase 5.2 settings are copied into the FIRST profile created
 * on this device (it is the person who used the app before profiles existed). The marker is written
 * in the same transaction as the profile, so the import happens at most once; later profiles start
 * with defaults and existing Room preferences are never overwritten. The old SharedPreferences file
 * is only read, never modified or deleted.
 *
 * Wellness: the first profile is designated owner of pending Phase 7 wellness data (marker written
 * with the profile), then the verified import runs. See [LegacyWellnessMigrator].
 */
class LegacyImportLifecycle(
    private val metadata: AppMetadata,
    private val legacySettings: () -> Pair<CoachSettings, VisualSettings>?,
    private val wellness: LegacyWellnessMigrator,
    private val currentProfiles: suspend () -> List<UserProfile>,
) : ProfileLifecycle {
    override suspend fun setupFor(isFirstProfile: Boolean): ProfileSetup {
        if (!isFirstProfile) return ProfileSetup()
        val legacy = if (metadata.get(KEY_PREFS_IMPORTED) == null) legacySettings() else null
        val assignWellness = wellness.hasPendingData()
        return ProfileSetup(
            preferences = legacy?.let { (c, v) -> PracticePreferences.from(c, v) } ?: UserPreferences(),
            markers = { id ->
                buildMap {
                    if (legacy != null) put(KEY_PREFS_IMPORTED, id)
                    if (assignWellness) put(LegacyWellnessMigrator.KEY_OWNER, id)
                }
            },
        )
    }

    override suspend fun afterCreated(profile: UserProfile, isFirstProfile: Boolean) {
        wellness.refresh((currentProfiles() + profile).distinctBy { it.userId })
    }

    override suspend fun afterDeleted(userId: String) {
        wellness.onOwnerDataErased(userId)
        wellness.refresh(currentProfiles())
    }

    companion object { const val KEY_PREFS_IMPORTED = "legacy.practice_settings.imported_to" }
}
