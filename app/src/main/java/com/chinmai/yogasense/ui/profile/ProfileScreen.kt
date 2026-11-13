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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.chinmai.yogasense.ui.components.IconBadge
import com.chinmai.yogasense.ui.components.ListRow
import com.chinmai.yogasense.ui.components.SectionHeader
import com.chinmai.yogasense.ui.components.Tag
import com.chinmai.yogasense.ui.components.TagTone
import com.chinmai.yogasense.ui.components.YsCard
import com.chinmai.yogasense.ui.components.YsIcons
import com.chinmai.yogasense.ui.components.YsSecondaryButton
import com.chinmai.yogasense.ui.theme.Outline
import com.chinmai.yogasense.ui.theme.Spacing
import com.chinmai.yogasense.ui.theme.TextMuted
import com.chinmai.yogasense.ui.theme.TextPrimary
import com.chinmai.yogasense.ui.theme.TextSubtle

@Composable
fun ProfileScreen() {
    val context = LocalContext.current
    val cameraGranted = rememberCameraPermissionState()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { cameraGranted.value = it }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.gutter, vertical = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.xl),
    ) {
        Text("Profile", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)

        YsCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(YsIcons.Profile, size = 56.dp)
                Spacer(Modifier.width(Spacing.lg))
                Column(Modifier.weight(1f)) {
                    Text("Guest practitioner", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    Text("No account needed — everything stays on this phone", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                }
            }
            Spacer(Modifier.height(Spacing.md))
            Tag("Profile details coming soon")
        }

        Section("Privacy & permissions") {
            ListRow(
                YsIcons.Shield, "On-device processing",
                subtitle = "Camera frames are analysed on this phone in real time. Video is never recorded or uploaded.",
            )
            Divider()
            ListRow(
                YsIcons.Camera, "Camera access",
                subtitle = if (cameraGranted.value) "Allowed" else "Not allowed — needed for live practice",
                trailing = {
                    if (cameraGranted.value) Tag("Allowed", tone = TagTone.Accent)
                    else YsSecondaryButton("Allow", { launcher.launch(Manifest.permission.CAMERA) })
                },
            )
            if (!cameraGranted.value) {
                Text(
                    "If the prompt no longer appears, enable Camera in system settings.",
                    style = MaterialTheme.typography.bodySmall, color = TextSubtle,
                )
                Spacer(Modifier.height(Spacing.sm))
                YsSecondaryButton("Open app settings", { openAppSettings(context) })
            }
        }

        Section("Notifications") {
            ListRow(
                YsIcons.Bell, "Practice reminders",
                subtitle = "Gentle daily reminders — not available yet",
                enabled = false,
                trailing = { Switch(checked = false, onCheckedChange = null, enabled = false) },
            )
        }

        Section("Wellness data") {
            ListRow(
                YsIcons.Leaf, "How your data is used",
                subtitle = "Check-ins (mood, stress, sleep) and periodic PHQ-9 / GAD-7 questionnaires will be stored only on this device to show trends alongside your practice.",
            )
            Divider()
            Text(
                "YogaSense AI is a wellness companion, not a medical device. It does not diagnose or treat any condition. If you are struggling, please contact a qualified professional.",
                style = MaterialTheme.typography.bodySmall, color = TextMuted,
                modifier = Modifier.padding(vertical = Spacing.sm),
            )
        }

        Section("Your data") {
            ListRow(YsIcons.Export, "Export data", subtitle = "No data recorded yet", enabled = false)
            Divider()
            ListRow(YsIcons.Trash, "Clear all data", subtitle = "No data recorded yet", enabled = false)
        }

        Section("About") {
            ListRow(YsIcons.Info, "YogaSense AI", subtitle = "Version ${appVersion(context)}")
            Divider()
            ListRow(
                YsIcons.Spark, "On-device AI",
                subtitle = "MediaPipe Pose Landmarker Lite · recognises Tadasana, Tree, Warrior II, Chair and Triangle",
            )
        }
        Spacer(Modifier.height(Spacing.sm))
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        SectionHeader(title)
        YsCard(contentPadding = Spacing.md) { content() }
    }
}

@Composable
private fun Divider() = HorizontalDivider(color = Outline)

/** Camera permission state, re-checked whenever the app resumes (e.g. back from Settings). */
@Composable
private fun rememberCameraPermissionState(): androidx.compose.runtime.MutableState<Boolean> {
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

private fun openAppSettings(context: Context) {
    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
}

private fun appVersion(context: Context): String = runCatching {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName
}.getOrNull() ?: "—"
