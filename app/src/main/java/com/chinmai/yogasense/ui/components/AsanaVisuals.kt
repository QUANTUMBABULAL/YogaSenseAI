package com.chinmai.yogasense.ui.components

import androidx.compose.foundation.Canvas
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

/**
 * Visual slot for an asana. Phase 4: static illustration on a tinted panel.
 * Phase 5 integration point: replace the body of this composable with the animation player;
 * every caller (cards, detail page) picks it up unchanged.
 */
@Composable
fun AsanaVisual(info: AsanaInfo, modifier: Modifier = Modifier, showLabel: Boolean = false) {
    val tint = if (info.difficulty == Difficulty.BEGINNER) LimeTint else CoralTint
    Box(
        modifier
            .clip(RoundedCornerShape(Radius.card))
            .background(tint)
            .semantics { contentDescription = "${info.name} illustration" },
        contentAlignment = Alignment.Center,
    ) {
        AsanaFigure(info.figure, Modifier.fillMaxSize().padding(Spacing.md))
        if (showLabel) {
            Tag("Static illustration", Modifier.align(Alignment.BottomStart).padding(Spacing.sm), style = TagStyle.Outline)
        }
    }
}

/** Library card: illustration, names, difficulty and hold time. */
@Composable
fun AsanaCard(info: AsanaInfo, onClick: () -> Unit, modifier: Modifier = Modifier) {
    YsCard(modifier, onClick = onClick, contentPadding = Spacing.sm) {
        AsanaVisual(info, Modifier.fillMaxWidth().aspectRatio(1f))
        Column(Modifier.padding(horizontal = Spacing.xs, vertical = Spacing.sm)) {
            Text(info.name, style = MaterialTheme.typography.titleMedium, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(info.sanskrit, style = MaterialTheme.typography.bodySmall, color = InkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(Spacing.sm))
            Row(verticalAlignment = Alignment.CenterVertically) {
                DifficultyTag(info.difficulty)
                Spacer(Modifier.weight(1f))
                Icon(YsIcons.Clock, null, tint = InkMuted, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(2.dp))
                Text("${info.holdSeconds.last}s", style = MaterialTheme.typography.labelMedium, color = InkMuted)
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
