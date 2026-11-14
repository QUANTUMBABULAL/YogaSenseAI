package com.chinmai.yogasense.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.chinmai.yogasense.ui.theme.CardWhite
import com.chinmai.yogasense.ui.theme.Hairline
import com.chinmai.yogasense.ui.theme.InkMuted
import com.chinmai.yogasense.ui.theme.Lime
import com.chinmai.yogasense.ui.theme.Midnight
import com.chinmai.yogasense.ui.theme.Spacing

data class BottomNavItem(val route: String, val label: String, val icon: ImageVector)

/**
 * Custom bottom bar: selected tab shows a lime pill behind a midnight icon and a bold label;
 * unselected tabs are muted. Each item is a 64dp-tall selectable target with tab semantics.
 */
@Composable
fun BottomNavigationBar(items: List<BottomNavItem>, selectedRoute: String?, onSelect: (BottomNavItem) -> Unit) {
    Surface(color = CardWhite) {
        Column {
            HorizontalDivider(color = Hairline)
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = Spacing.sm),
                horizontalArrangement = Arrangement.SpaceAround,
            ) {
                items.forEach { item ->
                    NavItem(item, item.route == selectedRoute, Modifier.weight(1f)) { onSelect(item) }
                }
            }
        }
    }
}

@Composable
private fun NavItem(item: BottomNavItem, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val pill by animateColorAsState(if (selected) Lime else CardWhite, label = "pill")
    val tint by animateColorAsState(if (selected) Midnight else InkMuted, label = "tint")
    val scale by animateFloatAsState(if (selected) 1f else 0.94f, label = "scale")
    Column(
        modifier
            .heightIn(min = 64.dp)
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.Tab,
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false, radius = 36.dp),
            )
            .padding(vertical = Spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .clip(CircleShape)
                .background(pill)
                .padding(horizontal = 18.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(item.icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(
            item.label,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = if (selected) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Medium,
            ),
            color = tint,
        )
    }
}

