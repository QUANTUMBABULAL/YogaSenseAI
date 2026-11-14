package com.chinmai.yogasense.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.unit.Dp
import com.chinmai.yogasense.asana.Asana
import com.chinmai.yogasense.ui.theme.AccentCoral
import com.chinmai.yogasense.ui.theme.AccentLime
import com.chinmai.yogasense.ui.theme.AccentMidnight
import com.chinmai.yogasense.ui.theme.AccentMist
import com.chinmai.yogasense.ui.theme.AccentPeach
import com.chinmai.yogasense.ui.theme.AccentSage
import com.chinmai.yogasense.ui.theme.AccentSand
import com.chinmai.yogasense.ui.theme.Lime
import com.chinmai.yogasense.ui.theme.PoseAccent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.chinmai.yogasense.catalog.AsanaCatalog
import com.chinmai.yogasense.catalog.AsanaInfo
import com.chinmai.yogasense.catalog.Difficulty
import com.chinmai.yogasense.catalog.PoseFigure
import com.chinmai.yogasense.ui.theme.CoralTint
import com.chinmai.yogasense.ui.theme.Ink
import com.chinmai.yogasense.ui.theme.InkMuted
import com.chinmai.yogasense.ui.theme.LimeTint
import com.chinmai.yogasense.ui.theme.Midnight
import com.chinmai.yogasense.ui.theme.Radius
import com.chinmai.yogasense.ui.theme.Spacing
import com.chinmai.yogasense.ui.theme.YogaSenseAITheme
import kotlin.math.min

/** Static stylised pose silhouette drawn from a [PoseFigure]. */
@Composable
fun AsanaFigure(figure: PoseFigure, modifier: Modifier = Modifier, color: Color = Midnight) {
    Canvas(modifier) {
        val s = min(size.width, size.height)
        val ox = (size.width - s) / 2f
        val oy = (size.height - s) / 2f
        fun o(p: Pair<Float, Float>) = Offset(ox + p.first * s, oy + p.second * s)
        val limb = s * 0.045f
        fun bone(a: Pair<Float, Float>, b: Pair<Float, Float>, w: Float = limb) =
            drawLine(color, o(a), o(b), strokeWidth = w, cap = StrokeCap.Round)
        with(figure) {
            // Torso as a filled quad so the figure reads as a body, not a wireframe.
            val torso = Path().apply {
                moveTo(o(shoulderL).x, o(shoulderL).y); lineTo(o(shoulderR).x, o(shoulderR).y)
                lineTo(o(hipR).x, o(hipR).y); lineTo(o(hipL).x, o(hipL).y); close()
            }
            drawPath(torso, color)
            // Spine stroke keeps the torso solid in side views where the quad collapses to a line.
            val midSh = ((shoulderL.first + shoulderR.first) / 2f) to ((shoulderL.second + shoulderR.second) / 2f)
            val midHip = ((hipL.first + hipR.first) / 2f) to ((hipL.second + hipR.second) / 2f)
            bone(midSh, midHip, limb * 2.4f)
            bone(shoulderL, shoulderR); bone(hipL, hipR); bone(shoulderL, hipL); bone(shoulderR, hipR)
            bone(shoulderL, elbowL); bone(elbowL, wristL); bone(shoulderR, elbowR); bone(elbowR, wristR)
            bone(hipL, kneeL, limb * 1.15f); bone(kneeL, ankleL, limb * 1.15f)
            bone(hipR, kneeR, limb * 1.15f); bone(kneeR, ankleR, limb * 1.15f)
            drawCircle(color, radius = s * 0.055f, center = o(head))
        }
    }
}

/** Colour identity per pose, so the library reads as a set of distinct tiles. */
fun accentFor(info: AsanaInfo): PoseAccent = when (info.asana) {
    Asana.TADASANA -> AccentSand
    Asana.TREE -> AccentLime
    Asana.WARRIOR_II -> AccentCoral
    Asana.CHAIR -> AccentMidnight
    Asana.TRIANGLE -> AccentMist
    Asana.BHUJANGASANA -> AccentPeach
    Asana.CHAKRASANA -> AccentSage
    else -> AccentSand
}

/**
 * Illustration slot for an asana: the pose figure over its accent colour with a soft halo.
 * The figure is a static illustration (labelled when [showLabel]).
 */
@Composable
fun AsanaVisual(info: AsanaInfo, modifier: Modifier = Modifier, showLabel: Boolean = false, corner: Dp = Radius.card) {
    val accent = accentFor(info)
    Box(
        modifier
            .clip(RoundedCornerShape(corner))
            .background(accent.bg)
            .drawBehind {
                val halo = if (accent.dark) Lime.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.38f)
                drawCircle(halo, radius = min(size.width, size.height) * 0.40f, center = Offset(size.width / 2f, size.height * 0.52f))
            }
            .semantics { contentDescription = "${info.name} illustration" },
        contentAlignment = Alignment.Center,
    ) {
        AsanaFigure(info.figure, Modifier.fillMaxSize().padding(Spacing.md), color = accent.figure)
        if (showLabel) {
            Text(
                "Illustration", style = MaterialTheme.typography.labelMedium, color = accent.onBgMuted,
                modifier = Modifier.align(Alignment.BottomStart).padding(Spacing.md),
            )
        }
    }
}

/** Library tile: full-bleed accent colour, large figure, name and hold time on the tile. */
@Composable
fun AsanaCard(info: AsanaInfo, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val accent = accentFor(info)
    Surface(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(28.dp), color = accent.bg, contentColor = accent.onBg) {
        Column(
            Modifier.drawBehind {
                val halo = if (accent.dark) Lime.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.38f)
                drawCircle(halo, radius = size.width * 0.36f, center = Offset(size.width / 2f, size.width * 0.50f))
            },
        ) {
            Box(Modifier.fillMaxWidth().aspectRatio(1.05f)) {
                AsanaFigure(info.figure, Modifier.fillMaxSize().padding(Spacing.md), color = accent.figure)
                Row(
                    Modifier.align(Alignment.TopStart).padding(Spacing.sm + 4.dp)
                        .clip(CircleShape).background(accent.onBg.copy(alpha = 0.10f)).padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(YsIcons.Clock, null, tint = accent.onBg, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("${info.holdSeconds.last}s", style = MaterialTheme.typography.labelMedium, color = accent.onBg)
                }
            }
            Column(Modifier.padding(start = Spacing.md, end = Spacing.md, bottom = Spacing.md)) {
                Text(
                    info.name, color = accent.onBg, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = if (info.name.length > 11) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                )
                Text(
                    "${info.englishName} · ${info.difficulty.label}", style = MaterialTheme.typography.bodySmall, color = accent.onBgMuted,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF5F3EE, widthDp = 360)
@Composable
private fun AsanaCardPreview() = YogaSenseAITheme {
    Row(Modifier.padding(Spacing.md)) {
        AsanaCatalog.all.take(2).forEach { AsanaCard(it, {}, Modifier.weight(1f).padding(Spacing.xs)) }
    }
}
