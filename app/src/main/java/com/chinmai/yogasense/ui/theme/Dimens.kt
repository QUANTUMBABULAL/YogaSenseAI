package com.chinmai.yogasense.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** 8-dp spacing system (with a 4-dp half step). */
object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
    val xxl = 48.dp
    val gutter = 20.dp
    val minTouch = 48.dp
}

/** Card radii 16–22 dp per brand direction. */
object Radius {
    val chip = 12.dp
    val card = 24.dp
    val hero = 32.dp
}

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(Radius.chip),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(Radius.card),
    extraLarge = RoundedCornerShape(28.dp),
)
