package com.chinmai.yogasense.coaching.assessment

import com.chinmai.yogasense.asana.BodyFeatures
import kotlin.math.sqrt

/**
 * Temporal stability from body-centre movement.
 *
 * Each reliable frame contributes the mid-hip and mid-shoulder positions divided by that frame's
 * torso length (camera-distance invariant). Sway = RMS distance of those points from their window
 * mean, averaged over the two points, in torso lengths.
 *
 * Noise handling: MediaPipe jitter on a still person is roughly 1–2 % of torso length after
 * smoothing, so sway below [noiseFloor] scores 100; the score falls linearly to 0 at
 * noiseFloor + [swayRange]. These are prototype values to be tuned on device.
 *
 * Score is null until ≥ [minSamples] reliable samples span ≥ [minSpanMs]. Frames without a reliable
 * torso are skipped; a gap > [maxGapMs] or [reset] (pose lost/changed) clears the window.
 */
class StabilityAnalyzer(
    private val windowMs: Long = 3000,
    private val minSamples: Int = 8,
    private val minSpanMs: Long = 1500,
    private val noiseFloor: Float = 0.02f,
    private val swayRange: Float = 0.12f,
    private val maxGapMs: Long = 1000,
) {
    private class Sample(val t: Long, val hx: Float, val hy: Float, val sx: Float, val sy: Float)
    private val window = ArrayDeque<Sample>()

    fun reset() = window.clear()

    fun add(f: BodyFeatures, timeMs: Long): StabilityResult {
        val torso = f.torsoLength
        val hip = f.midHipPoint
        val sh = f.midShoulderPoint
        val last = window.lastOrNull()
        if (last != null && timeMs - last.t > maxGapMs) window.clear()
        if (torso != null && hip != null && sh != null) {
            window.addLast(Sample(timeMs, hip.x / torso, hip.y / torso, sh.x / torso, sh.y / torso))
        }
        while (window.isNotEmpty() && window.first().t < timeMs - windowMs) window.removeFirst()
        return current()
    }

    fun current(): StabilityResult {
        val n = window.size
        if (n < minSamples || window.last().t - window.first().t < minSpanMs) return StabilityResult(null, null, n)
        val mhx = window.sumOf { it.hx.toDouble() } / n; val mhy = window.sumOf { it.hy.toDouble() } / n
        val msx = window.sumOf { it.sx.toDouble() } / n; val msy = window.sumOf { it.sy.toDouble() } / n
        val hipRms = sqrt(window.sumOf { sq(it.hx - mhx) + sq(it.hy - mhy) } / n)
        val shRms = sqrt(window.sumOf { sq(it.sx - msx) + sq(it.sy - msy) } / n)
        val sway = ((hipRms + shRms) / 2).toFloat()
        val score = Math.round(100f * (1f - ((sway - noiseFloor) / swayRange)).coerceIn(0f, 1f))
        return StabilityResult(score, sway, n)
    }

    private fun sq(d: Double) = d * d
}
