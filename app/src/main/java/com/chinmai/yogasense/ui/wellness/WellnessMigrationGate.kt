package com.chinmai.yogasense.ui.wellness

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.chinmai.yogasense.data.LegacyWellnessStatus
import com.chinmai.yogasense.profile.UserProfile
import com.chinmai.yogasense.ui.components.LoadingState
import com.chinmai.yogasense.ui.components.PrimaryButton
import com.chinmai.yogasense.ui.components.SecondaryButton
import com.chinmai.yogasense.ui.components.YsCard
import com.chinmai.yogasense.ui.components.YsIcons
import com.chinmai.yogasense.ui.theme.CardWhite
import com.chinmai.yogasense.ui.theme.Ink
import com.chinmai.yogasense.ui.theme.InkMuted
import com.chinmai.yogasense.ui.theme.Spacing

/**
 * Wellness screens use the Room store only once the one-time import of the Phase 7 data is
 * resolved. Until then this explains why and offers the safe actions (retry, or an explicit owner
 * choice). Crisis support is never behind this gate.
 */
@Composable
fun WellnessGate(
    status: LegacyWellnessStatus,
    profile: UserProfile,
    onRetry: () -> Unit,
    onAssign: () -> Unit,
    onBack: (() -> Unit)?,
    content: @Composable () -> Unit,
) {
    if (status.isResolved) { content(); return }
    var confirmAssign by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.gutter, vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) IconButton(onClick = onBack) { Icon(YsIcons.Back, "Back", tint = Ink) }
            Text("Wellness", style = MaterialTheme.typography.displaySmall, color = Ink)
        }
        when (status) {
            LegacyWellnessStatus.Checking, LegacyWellnessStatus.InProgress, is LegacyWellnessStatus.AwaitingFirstProfile ->
                LoadingState("Preparing your wellness data…")
            is LegacyWellnessStatus.NeedsOwner -> YsCard(Modifier.fillMaxWidth()) {
                Text("Wellness data from before profiles", style = MaterialTheme.typography.titleMedium, color = Ink)
                Spacer(Modifier.size(Spacing.xs))
                Text(
                    "This phone has ${status.records} wellness record(s) saved before profiles were added. They don't belong to any " +
                        "profile yet, and the app won't guess whose they are. They are kept safely and unchanged.\n\n" +
                        "If they are yours, move them to ${profile.displayName}. Otherwise switch to the right profile first. " +
                        "Wellness tracking becomes available once this is decided.",
                    style = MaterialTheme.typography.bodyMedium, color = InkMuted,
                )
                Spacer(Modifier.size(Spacing.md))
                PrimaryButton("These are mine — move to ${profile.displayName}", { confirmAssign = true }, modifier = Modifier.fillMaxWidth())
            }
            is LegacyWellnessStatus.Failed -> YsCard(Modifier.fillMaxWidth()) {
                Text("Your wellness data couldn't be moved yet", style = MaterialTheme.typography.titleMedium, color = Ink)
                Spacer(Modifier.size(Spacing.xs))
                Text(status.reason, style = MaterialTheme.typography.bodyMedium, color = InkMuted)
                Spacer(Modifier.size(Spacing.xs))
                Text("Your existing data is still on this phone, unchanged.", style = MaterialTheme.typography.bodySmall, color = InkMuted)
                Spacer(Modifier.size(Spacing.md))
                SecondaryButton("Try again", onRetry, icon = YsIcons.Replay, modifier = Modifier.fillMaxWidth())
            }
            else -> Unit
        }
    }
    if (confirmAssign) {
        AlertDialog(
            onDismissRequest = { confirmAssign = false },
            title = { Text("Move wellness data to ${profile.displayName}?") },
            text = { Text("All check-ins and questionnaire results saved before profiles will belong to ${profile.displayName}. Other profiles won't see them.") },
            confirmButton = { TextButton(onClick = { confirmAssign = false; onAssign() }) { Text("Move to ${profile.displayName}", color = Ink) } },
            dismissButton = { TextButton(onClick = { confirmAssign = false }) { Text("Cancel", color = InkMuted) } },
            containerColor = CardWhite,
        )
    }
}
