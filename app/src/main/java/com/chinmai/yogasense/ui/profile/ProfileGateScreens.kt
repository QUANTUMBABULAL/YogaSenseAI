package com.chinmai.yogasense.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.chinmai.yogasense.data.db.ExperienceLevel
import com.chinmai.yogasense.profile.ProfileValidation
import com.chinmai.yogasense.profile.UserProfile
import com.chinmai.yogasense.ui.components.CardStyle
import com.chinmai.yogasense.ui.components.IconBadge
import com.chinmai.yogasense.ui.components.PrimaryButton
import com.chinmai.yogasense.ui.components.SecondaryButton
import com.chinmai.yogasense.ui.components.YsCard
import com.chinmai.yogasense.ui.components.YsIcons
import com.chinmai.yogasense.ui.theme.CardWhite
import com.chinmai.yogasense.ui.theme.CoralDeep
import com.chinmai.yogasense.ui.theme.ErrorRed
import com.chinmai.yogasense.ui.theme.Hairline
import com.chinmai.yogasense.ui.theme.Ink
import com.chinmai.yogasense.ui.theme.InkMuted
import com.chinmai.yogasense.ui.theme.Lime
import com.chinmai.yogasense.ui.theme.LimeTint
import com.chinmai.yogasense.ui.theme.Midnight
import com.chinmai.yogasense.ui.theme.Spacing
import com.chinmai.yogasense.ui.theme.TextMuted
import com.chinmai.yogasense.ui.theme.WarmWhite

val ExperienceLevel.label: String get() = when (this) {
    ExperienceLevel.BEGINNER -> "Beginner"
    ExperienceLevel.INTERMEDIATE -> "Intermediate"
    ExperienceLevel.ADVANCED -> "Advanced"
}

private fun initials(name: String) = name.split(' ').filter { it.isNotEmpty() }.take(2).joinToString("") { it.first().uppercase() }

@Composable
fun ProfileAvatar(name: String, size: Int = 44, dark: Boolean = false) {
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(if (dark) Lime else LimeTint),
        contentAlignment = Alignment.Center,
    ) { Text(initials(name), style = MaterialTheme.typography.titleMedium, color = Midnight) }
}


/**
 * Create a local profile. [creating] disables the button so repeated taps can't start a second
 * create (the manager also ignores concurrent creates).
 */
@Composable
fun ProfileCreateScreen(
    isFirstProfile: Boolean,
    legacyWellnessRecords: Int?,
    hasLegacySettings: Boolean,
    creating: Boolean,
    error: String?,
    onCreate: (String, ExperienceLevel) -> Unit,
    onCancel: (() -> Unit)?,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var level by rememberSaveable { mutableStateOf(ExperienceLevel.BEGINNER) }
    val cleaned = ProfileValidation.displayName(name)

    LazyColumn(
        Modifier.fillMaxSize().background(WarmWhite).statusBarsPadding().navigationBarsPadding().imePadding(),
        contentPadding = PaddingValues(horizontal = Spacing.gutter, vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onCancel != null) {
                    IconButton(onClick = onCancel, enabled = !creating) { Icon(YsIcons.Back, "Back to profiles", tint = Ink) }
                    Spacer(Modifier.width(Spacing.xs))
                }
                Text(if (isFirstProfile) "Welcome" else "New profile", style = MaterialTheme.typography.displaySmall, color = Ink)
            }
            Spacer(Modifier.size(Spacing.xs))
            Text(
                if (isFirstProfile) "Create a local profile to keep your practice and wellness history separate from anyone else using this phone."
                else "Each profile has its own practice history, settings and wellness data.",
                style = MaterialTheme.typography.bodyLarge, color = InkMuted,
            )
        }
        item {
            YsCard(Modifier.fillMaxWidth()) {
                Text("Name", style = MaterialTheme.typography.titleSmall, color = Ink)
                Spacer(Modifier.size(Spacing.sm))
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= ProfileValidation.MAX_NAME + 5) name = it },
                    singleLine = true,
                    enabled = !creating,
                    placeholder = { Text("e.g. Asha") },
                    supportingText = { Text("Shown only on this phone · up to ${ProfileValidation.MAX_NAME} characters") },
                    isError = name.isNotBlank() && cleaned == null,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Midnight, cursorColor = Midnight, focusedContainerColor = CardWhite, unfocusedContainerColor = CardWhite),
                    modifier = Modifier.fillMaxWidth(),
                )
                // Experience is asked in onboarding (next), so it isn't asked twice.
            }
        }
        if (isFirstProfile && (legacyWellnessRecords != null || hasLegacySettings)) {
            item {
                YsCard(Modifier.fillMaxWidth(), style = CardStyle.Muted) {
                    Row(verticalAlignment = Alignment.Top) {
                        IconBadge(YsIcons.Shield, size = 36.dp)
                        Spacer(Modifier.width(Spacing.md - 4.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Your existing data moves to this profile", style = MaterialTheme.typography.titleSmall, color = Ink)
                            val parts = buildList {
                                if (legacyWellnessRecords != null) add("wellness check-ins and questionnaire results already saved on this phone ($legacyWellnessRecords)")
                                if (hasLegacySettings) add("your voice and visual practice settings")
                            }
                            Text(
                                "This is the first profile, so ${parts.joinToString(" and ")} will belong to it. The data stays on this phone.",
                                style = MaterialTheme.typography.bodySmall, color = InkMuted,
                            )
                        }
                    }
                }
            }
        }
        if (error != null) item { Text(error, style = MaterialTheme.typography.bodyMedium, color = ErrorRed) }
        item {
            PrimaryButton(
                if (creating) "Creating…" else "Create profile",
                onClick = { cleaned?.let { onCreate(it, level) } },
                enabled = cleaned != null && !creating,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.size(Spacing.sm))
            Text(
                "No account, password or internet connection. Anyone with this phone can open any profile.",
                style = MaterialTheme.typography.bodySmall, color = InkMuted,
            )
        }
    }
}

@Composable
private fun LevelChip(text: String, selected: Boolean, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .heightIn(min = Spacing.minTouch)
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) Midnight else CardWhite)
            .border(1.5.dp, if (selected) Midnight else Hairline, RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .semantics { this.selected = selected },
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = if (selected) WarmWhite else Ink, modifier = Modifier.padding(horizontal = 6.dp))
    }
}

@Composable
fun ProfileChooserScreen(
    profiles: List<UserProfile>,
    busy: Boolean,
    onSelect: (UserProfile) -> Unit,
    onAdd: () -> Unit,
    onDelete: (UserProfile) -> Unit,
) {
    var pendingDelete by rememberSaveable { mutableStateOf<String?>(null) }
    LazyColumn(
        Modifier.fillMaxSize().background(WarmWhite).statusBarsPadding().navigationBarsPadding(),
        contentPadding = PaddingValues(horizontal = Spacing.gutter, vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md - 4.dp),
    ) {
        item {
            Text("Who's practising?", style = MaterialTheme.typography.displaySmall, color = Ink)
            Text("Choose your profile. Each one keeps its own history and settings.", style = MaterialTheme.typography.bodyLarge, color = InkMuted)
            Spacer(Modifier.size(Spacing.sm))
        }
        items(profiles, key = { it.userId }) { p ->
            YsCard(Modifier.fillMaxWidth(), onClick = if (busy) null else ({ onSelect(p) }), contentPadding = Spacing.md - 4.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProfileAvatar(p.displayName)
                    Spacer(Modifier.width(Spacing.md - 4.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.displayName, style = MaterialTheme.typography.titleMedium, color = Ink)
                        Text(p.experienceLevel.label, style = MaterialTheme.typography.bodySmall, color = InkMuted)
                    }
                    IconButton(onClick = { pendingDelete = p.userId }, enabled = !busy, modifier = Modifier.semantics { contentDescription = "Delete ${p.displayName}" }) {
                        Icon(YsIcons.Trash, null, tint = InkMuted)
                    }
                }
            }
        }
        item { SecondaryButton("Add profile", onAdd, icon = YsIcons.Profile, enabled = !busy, modifier = Modifier.fillMaxWidth()) }
    }
    profiles.firstOrNull { it.userId == pendingDelete }?.let { p ->
        DeleteProfileDialog(p, onConfirm = { pendingDelete = null; onDelete(p) }, onDismiss = { pendingDelete = null })
    }
}

/** Explicit, irreversible confirmation. */
@Composable
fun DeleteProfileDialog(profile: UserProfile, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(YsIcons.Trash, null, tint = CoralDeep) },
        title = { Text("Delete ${profile.displayName}?") },
        text = {
            Text(
                "This permanently deletes this profile and all of its practice sessions, settings, wellness check-ins, " +
                    "questionnaire results and consent records from this phone. It can't be undone.",
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Delete permanently", color = CoralDeep) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Ink) } },
        containerColor = CardWhite,
    )
}

@Composable
fun ActiveProfileCard(profile: UserProfile, modifier: Modifier = Modifier) {
    YsCard(modifier.fillMaxWidth(), style = CardStyle.Dark, contentPadding = Spacing.lg - 4.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProfileAvatar(profile.displayName, size = 56, dark = true)
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(profile.displayName, style = MaterialTheme.typography.titleLarge, color = WarmWhite)
                Text("${profile.experienceLevel.label} · local profile · data stays on this phone", style = MaterialTheme.typography.bodySmall, color = TextMuted)
            }
        }
    }
}

