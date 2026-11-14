package com.chinmai.yogasense.ui.profile

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.clickable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.chinmai.yogasense.ui.components.CardStyle
import com.chinmai.yogasense.ui.components.ComingSoonTag
import com.chinmai.yogasense.ui.components.IconBadge
import com.chinmai.yogasense.ui.components.ListRow
import com.chinmai.yogasense.ui.components.SecondaryButton
import com.chinmai.yogasense.ui.components.SectionHeader
import com.chinmai.yogasense.ui.components.Tag
import com.chinmai.yogasense.ui.components.TagStyle
import com.chinmai.yogasense.ui.components.YsCard
import com.chinmai.yogasense.ui.components.YsIcons
import com.chinmai.yogasense.ui.theme.Hairline
import com.chinmai.yogasense.ui.theme.Ink
import com.chinmai.yogasense.ui.theme.InkMuted
import com.chinmai.yogasense.ui.theme.Lime
import com.chinmai.yogasense.ui.theme.Midnight
import com.chinmai.yogasense.ui.theme.Spacing
import com.chinmai.yogasense.ui.theme.TextMuted
import com.chinmai.yogasense.ui.theme.WarmWhite

@Composable
fun ProfileScreen(
    onWellnessData: () -> Unit = {},
    onPracticeSettings: () -> Unit = {},
    profile: com.chinmai.yogasense.profile.UserProfile? = null,
    profileCount: Int = 1,
    onSwitchProfile: () -> Unit = {},
    onDeleteProfile: () -> Unit = {},
    personalisation: com.chinmai.yogasense.profile.PersonalisationView? = null,
    /** null = open the full (optional) onboarding; otherwise edit that one answer. */
    onEditPersonalisation: (com.chinmai.yogasense.profile.OnboardingStep?) -> Unit = {},
) {
    val context = LocalContext.current
    var confirmDelete by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    val camera = rememberCameraPermission()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { camera.value = it }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = Spacing.gutter, end = Spacing.gutter, bottom = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item {
            Column(Modifier.padding(top = Spacing.md)) {
                Text("YOU", style = MaterialTheme.typography.labelSmall, color = InkMuted)
                Text("Profile", style = MaterialTheme.typography.displaySmall, color = Ink)
            }
        }
        if (profile != null) {
            item { ActiveProfileCard(profile) }
            item {
                YsCard(Modifier.fillMaxWidth(), contentPadding = Spacing.sm) {
                    ListRow(
                        YsIcons.Profile, "Switch profile",
                        subtitle = if (profileCount > 1) "$profileCount profiles on this phone" else "Add or choose another profile",
                        onClick = onSwitchProfile,
                        trailing = { androidx.compose.material3.Icon(YsIcons.ChevronRight, null, tint = InkMuted) },
                    )
                    HorizontalDivider(color = Hairline)
                    ListRow(
                        YsIcons.Trash, "Delete this profile",
                        subtitle = "Permanently removes ${profile.displayName}'s practice, settings and wellness data",
                        onClick = { confirmDelete = true },
                    )
                }
            }
        }

        if (personalisation != null) {
            item { SectionHeader("Your personalisation", Modifier.padding(top = Spacing.sm), subtitle = "Used for general suggestions only · tap to edit") }
            item { PersonalisationCard(personalisation, onEditPersonalisation) }
        }

        item { SectionHeader("Available now", Modifier.padding(top = Spacing.sm)) }
        item {
            YsCard(Modifier.fillMaxWidth(), contentPadding = Spacing.sm) {
                ListRow(
                    YsIcons.Camera, "Camera access",
                    subtitle = if (camera.value) "Allowed — used only for live practice" else "Needed for live practice",
                    trailing = {
                        if (camera.value) Tag("Allowed", style = TagStyle.Lime)
                        else SecondaryButton("Allow", { launcher.launch(Manifest.permission.CAMERA) })
                    },
                )
                if (!camera.value) {
                    Row(Modifier.padding(start = 56.dp, bottom = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                        Text("Prompt not showing?", style = MaterialTheme.typography.bodySmall, color = InkMuted, modifier = Modifier.weight(1f))
                        SecondaryButton("App settings", { openAppSettings(context) })
                    }
                }
                HorizontalDivider(color = Hairline)
                ListRow(
                    YsIcons.Shield, "Privacy",
                    subtitle = "Video is analysed on this phone in real time and is never recorded or uploaded. No account, no backend.",
                )
                HorizontalDivider(color = Hairline)
                ListRow(
                    YsIcons.Leaf, "Wellness data",
                    subtitle = "Check-ins and questionnaires stay on this phone, excluded from backups. View or delete them.",
                    onClick = onWellnessData,
                )
            }
        }

        item { SectionHeader("Practice", Modifier.padding(top = Spacing.sm), subtitle = "Saved for this profile") }
        item {
            YsCard(Modifier.fillMaxWidth(), contentPadding = Spacing.sm) {
                ListRow(YsIcons.Voice, "Voice guidance", subtitle = "Coach mode, encouragement, countdown, speed", onClick = onPracticeSettings, trailing = { androidx.compose.material3.Icon(YsIcons.ChevronRight, null, tint = InkMuted) })
                HorizontalDivider(color = Hairline)
                ListRow(YsIcons.Eye, "Visual guidance", subtitle = "Skeleton, pose reference, highlights", onClick = onPracticeSettings, trailing = { androidx.compose.material3.Icon(YsIcons.ChevronRight, null, tint = InkMuted) })
                HorizontalDivider(color = Hairline)
                ListRow(YsIcons.Sliders, "Practice preferences", subtitle = "Hold times, rest intervals", enabled = false, trailing = { ComingSoonTag() })
                HorizontalDivider(color = Hairline)
                ListRow(YsIcons.Bell, "Notifications", subtitle = "Practice and check-in reminders", enabled = false, trailing = { ComingSoonTag() })
            }
        }

        item { SectionHeader("About", Modifier.padding(top = Spacing.sm)) }
        item {
            YsCard(Modifier.fillMaxWidth(), contentPadding = Spacing.sm) {
                ListRow(YsIcons.Info, "YogaSenseAI ${appVersion(context)}", subtitle = "Move better. Feel better.")
                HorizontalDivider(color = Hairline)
                ListRow(
                    YsIcons.Spark, "On-device AI",
                    subtitle = "MediaPipe Pose Landmarker Lite · recognises 5 foundational asanas",
                )
                HorizontalDivider(color = Hairline)
                ListRow(
                    YsIcons.Leaf, "Wellness, not diagnosis",
                    subtitle = "YogaSenseAI supports wellbeing. It is not a medical device and does not diagnose or treat any condition.",
                )
            }
        }
    }
    if (confirmDelete && profile != null) {
        DeleteProfileDialog(profile, onConfirm = { confirmDelete = false; onDeleteProfile() }, onDismiss = { confirmDelete = false })
    }
}

/** Camera permission, re-checked on resume (e.g. returning from system settings). */
@Composable
private fun rememberCameraPermission(): MutableState<Boolean> {
    val context = LocalContext.current
    val state = remember { mutableStateOf(isCameraGranted(context)) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) state.value = isCameraGranted(context) }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    return state
}

private fun isCameraGranted(context: Context) =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

private fun openAppSettings(context: Context) =
    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))

private fun appVersion(context: Context): String = runCatching {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName
}.getOrNull().orEmpty()

@Composable
private fun PersonalisationCard(
    v: com.chinmai.yogasense.profile.PersonalisationView,
    onEdit: (com.chinmai.yogasense.profile.OnboardingStep?) -> Unit,
) {
    val p = v.p
    val notSet = "Not set"
    val rows = listOf(
        Triple(com.chinmai.yogasense.profile.OnboardingStep.EXPERIENCE, "Experience", v.experienceLevel.label),
        Triple(com.chinmai.yogasense.profile.OnboardingStep.DURATION, "Session length", v.preferredMinutes?.let { "$it minutes" } ?: notSet),
        Triple(com.chinmai.yogasense.profile.OnboardingStep.FREQUENCY, "Rhythm", p.weeklyFrequency?.label ?: notSet),
        Triple(com.chinmai.yogasense.profile.OnboardingStep.GOALS, "Goals", p.goals.joinToString { it.label }.ifEmpty { notSet }),
        Triple(
            com.chinmai.yogasense.profile.OnboardingStep.BODY_AREAS, "Extra attention",
            when {
                p.bodyAreas.isNotEmpty() -> p.bodyAreas.entries.joinToString { (a, i) -> a.label + (i?.let { if (it == com.chinmai.yogasense.profile.BodyAreaIntent.DISCOMFORT) " (discomfort)" else " (mobility)" } ?: "") }
                p.noParticularArea -> "No particular area"
                else -> notSet
            },
        ),
        Triple(com.chinmai.yogasense.profile.OnboardingStep.HEIGHT, "Height", com.chinmai.yogasense.profile.BodyMetrics.displayHeight(p.heightCm, p.heightUnit) ?: notSet),
        Triple(com.chinmai.yogasense.profile.OnboardingStep.WEIGHT, "Weight", com.chinmai.yogasense.profile.BodyMetrics.displayWeight(p.weightKg, p.weightUnit) ?: notSet),
    )
    YsCard(Modifier.fillMaxWidth(), contentPadding = Spacing.sm) {
        if (!p.isCompleted) {
            ListRow(YsIcons.Spark, "Finish personalising", subtitle = "A few optional questions · about 2 minutes", onClick = { onEdit(null) },
                trailing = { androidx.compose.material3.Icon(YsIcons.ChevronRight, null, tint = InkMuted) })
            HorizontalDivider(color = Hairline)
        }
        rows.forEachIndexed { i, (step, title, value) ->
            androidx.compose.foundation.layout.Row(
                Modifier.fillMaxWidth().heightIn(min = 52.dp)
                    .clickable(onClickLabel = "Edit $title") { onEdit(step) }
                    .padding(horizontal = Spacing.sm, vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = Ink, modifier = Modifier.width(120.dp))
                Text(value, style = MaterialTheme.typography.bodyMedium, color = if (value == notSet) InkMuted else Ink, modifier = Modifier.weight(1f))
                androidx.compose.material3.Icon(YsIcons.ChevronRight, null, tint = InkMuted)
            }
            if (i < rows.lastIndex) HorizontalDivider(color = Hairline)
        }
    }
}
