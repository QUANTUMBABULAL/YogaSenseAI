package com.chinmai.yogasense.ui.practice

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.chinmai.yogasense.pose.Landmark
import com.chinmai.yogasense.pose.PoseFrame
import com.chinmai.yogasense.pose.PoseLandmarkIndex
import kotlin.math.min

private val ReliableJoint = Color(0xFF7CF5A8)
private val ReliableBone = Color(0xCC4ADE80)
private val WeakJoint = Color(0xFFF5C26B)
private val WeakBone = Color(0x66F5C26B)

/**
 * Draws the skeleton over a PreviewView that uses FIT_CENTER. The image is letterboxed the same
 * way here so normalized landmarks map to the same pixels as the preview.
 * Low-confidence points are drawn amber and small; points below [hideBelow] are not drawn.
 * [highlightWeak] (visual correction highlights): when true, low-confidence key joints are drawn
 * amber with a ring so the user sees what the camera cannot see; when false only reliable joints show.
 */
@Composable
fun SkeletonOverlay(
    /** Read inside the draw phase: a new frame only redraws the canvas, it doesn't recompose. */
    frame: () -> PoseFrame?,
    mirror: Boolean,
    modifier: Modifier = Modifier,
    hideBelow: Float = 0.2f,
    highlightWeak: Boolean = true,
) {
    Canvas(modifier) {
        val frame = frame()
        if (frame == null || !frame.hasPose || frame.imageWidth == 0 || frame.imageHeight == 0) return@Canvas
        val scale = min(size.width / frame.imageWidth, size.height / frame.imageHeight)
        val drawnW = frame.imageWidth * scale
        val drawnH = frame.imageHeight * scale
        val dx = (size.width - drawnW) / 2f
        val dy = (size.height - drawnH) / 2f
        fun map(l: Landmark): Offset {
            val nx = if (mirror) 1f - l.x else l.x
            return Offset(dx + nx * drawnW, dy + l.y * drawnH)
        }
        fun conf(l: Landmark) = min(l.visibility, l.presence)

        val lm = frame.landmarks
        val boneWidth = 4.dp.toPx()
        for ((a, b) in PoseLandmarkIndex.CONNECTIONS) {
            val la = lm[a]
            val lb = lm[b]
            val c = min(conf(la), conf(lb))
            if (c < hideBelow) continue
            val reliable = la.isReliable() && lb.isReliable()
            if (!reliable && !highlightWeak) continue
            drawLine(
                color = if (reliable) ReliableBone else WeakBone,
                start = map(la),
                end = map(lb),
                strokeWidth = if (reliable) boneWidth else boneWidth * 0.6f,
                cap = StrokeCap.Round,
            )
        }
        val r = 5.dp.toPx()
        for (l in lm) {
            if (conf(l) < hideBelow) continue
            val reliable = l.isReliable()
            if (!reliable && !highlightWeak) continue
            val p = map(l)
            drawCircle(Color.Black.copy(alpha = 0.45f), radius = if (reliable) r + 2f else r * 0.7f + 2f, center = p)
            drawCircle(if (reliable) ReliableJoint else WeakJoint, radius = if (reliable) r else r * 0.7f, center = p)
        }
        if (highlightWeak) {
            val ring = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
            for (i in KEY_JOINTS) {
                val l = lm[i]
                if (l.isReliable() || conf(l) < hideBelow) continue
                drawCircle(WeakJoint, radius = r * 2.6f, center = map(l), style = ring)
            }
        }
    }
}

private val KEY_JOINTS = intArrayOf(
    PoseLandmarkIndex.LEFT_SHOULDER, PoseLandmarkIndex.RIGHT_SHOULDER, PoseLandmarkIndex.LEFT_ELBOW, PoseLandmarkIndex.RIGHT_ELBOW,
    PoseLandmarkIndex.LEFT_WRIST, PoseLandmarkIndex.RIGHT_WRIST, PoseLandmarkIndex.LEFT_HIP, PoseLandmarkIndex.RIGHT_HIP,
    PoseLandmarkIndex.LEFT_KNEE, PoseLandmarkIndex.RIGHT_KNEE, PoseLandmarkIndex.LEFT_ANKLE, PoseLandmarkIndex.RIGHT_ANKLE,
)
