package com.chinmai.yogasense.ui.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.chinmai.yogasense.ui.components.EmptyState
import com.chinmai.yogasense.ui.components.IconBadge
import com.chinmai.yogasense.ui.components.SectionHeader
import com.chinmai.yogasense.ui.components.YsCard
import com.chinmai.yogasense.ui.components.YsIcons
import com.chinmai.yogasense.ui.components.YsPrimaryButton
import com.chinmai.yogasense.ui.theme.Spacing
import com.chinmai.yogasense.ui.theme.TextMuted
import com.chinmai.yogasense.ui.theme.TextPrimary
import com.chinmai.yogasense.ui.theme.TextSubtle

/** Empty until session recording and check-ins exist. Shows no invented history. */
@Composable
fun ProgressScreen(onStartPractice: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.gutter, vertical = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.xl),
    ) {
        Column {
            Text("Progress", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
            Text("Practice quality and wellness, side by side", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
        }
        YsCard(contentPadding = Spacing.xl) {
            EmptyState(
                YsIcons.Progress,
                "Your progress will grow here",
                "Session history and wellness trends appear after you record practice sessions and daily check-ins.",
                action = { YsPrimaryButton("Start your first session", onStartPractice, icon = YsIcons.Play) },
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            SectionHeader("What you'll see")
            YsCard {
                Preview(YsIcons.Practice, "Practice quality", "Alignment, balance and hold time per asana over time.")
                Preview(YsIcons.Leaf, "Wellness trends", "Mood, stress and sleep from your daily check-ins.")
                Preview(YsIcons.Calendar, "Weekly report", "A summary of the week's practice and wellbeing.")
            }
            Text(
                "Trends show associations, not causes, and are not a medical assessment.",
                style = MaterialTheme.typography.bodySmall, color = TextSubtle,
            )
        }
    }
}

@Composable
private fun Preview(icon: ImageVector, title: String, body: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
        IconBadge(icon, size = 36.dp)
        Spacer(Modifier.width(Spacing.md))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            Spacer(Modifier.height(2.dp))
            Text(body, style = MaterialTheme.typography.bodySmall, color = TextMuted)
        }
    }
}
