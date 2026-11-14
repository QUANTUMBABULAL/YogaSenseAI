package com.chinmai.yogasense.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.chinmai.yogasense.ui.theme.Lime
import com.chinmai.yogasense.ui.theme.Midnight
import com.chinmai.yogasense.ui.theme.Spacing
import com.chinmai.yogasense.ui.theme.TextMuted

data class BottomNavItem(val route: String, val label: String, val icon: ImageVector)

/**
 * Floating midnight pill. The selected tab expands into a lime capsule with icon + label;
 * the others are icon-only (their label stays available to screen readers). 56 dp targets.
 */
@Composable
fun BottomNavigationBar(items: List<BottomNavItem>, selectedRoute: String?, onSelect: (BottomNavItem) -> Unit) {
    Box(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp).padding(top = Spacing.sm, bottom = Spacing.sm + 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .shadow(16.dp, CircleShape, ambientColor = Midnight, spotColor = Midnight)
                .clip(CircleShape)
                .background(Midnight)
                .padding(6.dp)
                .selectableGroup(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEach { item -> NavItem(item, item.route == selectedRoute) { onSelect(item) } }
        }
    }
}

@Composable
private fun NavItem(item: BottomNavItem, selected: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) Lime else Midnight, label = "bg")
    val tint by animateColorAsState(if (selected) Midnight else TextMuted, label = "tint")
    Row(
        Modifier
            .heightIn(min = 56.dp)
            .clip(CircleShape)
            .background(bg)
            .selectable(
                selected = selected, onClick = onClick, role = Role.Tab,
                interactionSource = remember { MutableInteractionSource() }, indication = ripple(),
            )
            .semantics { contentDescription = item.label }
            .padding(horizontal = if (selected) 16.dp else 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(item.icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
        AnimatedVisibility(selected, enter = expandHorizontally() + fadeIn(), exit = shrinkHorizontally() + fadeOut()) {
            Row {
                Spacer(Modifier.width(6.dp))
                Text(item.label, style = MaterialTheme.typography.labelLarge, color = Midnight, maxLines = 1)
            }
        }
    }
}
