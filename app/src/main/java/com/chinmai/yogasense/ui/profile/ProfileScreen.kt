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
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
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
fun ProfileScreen() {
    val context = LocalContext.current
    val camera = rememberCameraPermission()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { camera.value = it }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = Spacing.gutter, end = Spacing.gutter, bottom = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item { Text("Profile", style = MaterialTheme.typography.displaySmall, color = Ink, modifier = Modifier.padding(top = Spacing.md)) }
        item {
            YsCard(Modifier.fillMaxWidth(), style = CardStyle.Dark, contentPadding = Spacing.lg - 4.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(YsIcons.Profile, size = 56.dp, tint = Midnight, container = Lime)
                    Spacer(Modifier.width(Spacing.md))
                    Column(Modifier.weight(1f)) {
                        Text("Guest", style = MaterialTheme.typography.titleLarge, color = WarmWhite)
                        Text("Local profile · data stays on this phone", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    }
                }
                Spacer(Modifier.height(Spacing.md - 4.dp))
                Text(
                    "Accounts and personalised onboarding are not part of this version.",
                    style = MaterialTheme.typography.bodySmall, color = TextMuted,
                )
            }
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
            }
        }

        item { SectionHeader("Coming in future updates", Modifier.padding(top = Spacing.sm), subtitle = "Shown for reference — not adjustable yet") }
        item {
            YsCard(Modifier.fillMaxWidth(), contentPadding = Spacing.sm) {
                ListRow(YsIcons.Sliders, "Practice preferences", subtitle = "Hold times, rest intervals", enabled = false, trailing = { ComingSoonTag() })
                HorizontalDivider(color = Hairline)
                ListRow(YsIcons.Voice, "Voice guidance", subtitle = "Spoken corrections and encouragement", enabled = false, trailing = { ComingSoonTag() })
                HorizontalDivider(color = Hairline)
                ListRow(YsIcons.Eye, "Visual guidance", subtitle = "Skeleton overlay and on-screen cues", enabled = false, trailing = { ComingSoonTag() })
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

