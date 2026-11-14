package com.chinmai.yogasense.profile

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Ruler scales for the height and weight selectors (pure, JVM-tested).
 *
 * A ruler position is an integer [index] in [min]..[max]; one index = one increment:
 *  - height cm : 1 cm,    100–250 cm
 *  - height in : 1 inch,  40–98 in  (3′ 4″ – 8′ 2″), within the 100–250 cm limits
 *  - weight kg : 0.1 kg,  25.0–300.0 kg
 *  - weight lb : 1 lb,    56–661 lb (25.4–299.8 kg)
 *
 * Canonical values stay in cm and kg. Changing the unit never changes the canonical value: the
 * ruler for the new unit is positioned at the nearest increment for display only. The canonical
 * value changes only when the user moves a ruler, and then it is the exact value of that increment
 * (e.g. 67 in = 170.18 cm, 143 lb = 64.8637 kg), so repeated unit switches never drift.
 */
enum class TickLevel { MINOR, MEDIUM, MAJOR }

data class RulerScale(
    val id: String,
    val min: Int,
    val max: Int,
    /** Index that a fresh ruler starts at when nothing is stored (display only; nothing is saved from it). */
    val defaultIndex: Int,
    val majorEvery: Int,
    val mediumEvery: Int,
    val unitLabel: String,
    /** Canonical value (cm or kg) of an index. */
    val toCanonical: (Int) -> Float,
    /** Nearest index for a canonical value, clamped to the range. */
    val fromCanonical: (Float) -> Int,
    /** Large selected-value text, e.g. "170", "5′ 7″", "64.9". */
    val format: (Int) -> String,
    /** Tick label at major ticks, e.g. "170", "5 ft", "65". */
    val label: (Int) -> String,
) {
    val count: Int get() = max - min + 1
    fun clamp(i: Int) = i.coerceIn(min, max)
    fun tickLevel(i: Int): TickLevel = when {
        i % majorEvery == 0 -> TickLevel.MAJOR
        i % mediumEvery == 0 -> TickLevel.MEDIUM
        else -> TickLevel.MINOR
    }
}

object MeasurementScales {
    const val CM_PER_INCH = 2.54f
    const val LB_PER_KG = 2.20462262f

    fun feetInches(totalInches: Int): Pair<Int, Int> = totalInches / 12 to totalInches % 12
    fun formatFeetInches(totalInches: Int): String = feetInches(totalInches).let { (f, i) -> "$f′ $i″" }

    val HEIGHT_CM = RulerScale(
        id = "height-cm", min = 100, max = 250, defaultIndex = 170, majorEvery = 10, mediumEvery = 5, unitLabel = "cm",
        toCanonical = { it.toFloat() },
        fromCanonical = { cm -> cm.roundToInt().coerceIn(100, 250) },
        format = { "$it" }, label = { "$it" },
    )

    val HEIGHT_IN = RulerScale(
        id = "height-in", min = 40, max = 98, defaultIndex = 67, majorEvery = 12, mediumEvery = 6, unitLabel = "",
        toCanonical = { it * CM_PER_INCH },
        fromCanonical = { cm -> (cm / CM_PER_INCH).roundToInt().coerceIn(40, 98) },
        format = ::formatFeetInches, label = { "${it / 12} ft" },
    )

    val WEIGHT_KG = RulerScale(
        id = "weight-kg", min = 250, max = 3000, defaultIndex = 700, majorEvery = 10, mediumEvery = 5, unitLabel = "kg",
        toCanonical = { it / 10f },
        fromCanonical = { kg -> (kg * 10f).roundToInt().coerceIn(250, 3000) },
        format = { i -> if (i % 10 == 0) "${i / 10}" else "${i / 10}.${i % 10}" }, label = { "${it / 10}" },
    )

    val WEIGHT_LB = RulerScale(
        id = "weight-lb", min = 56, max = 661, defaultIndex = 154, majorEvery = 10, mediumEvery = 5, unitLabel = "lbs",
        toCanonical = { it / LB_PER_KG },
        fromCanonical = { kg -> (kg * LB_PER_KG).roundToInt().coerceIn(56, 661) },
        format = { "$it" }, label = { "$it" },
    )

    fun height(u: HeightUnit) = if (u == HeightUnit.CM) HEIGHT_CM else HEIGHT_IN
    fun weight(u: WeightUnit) = if (u == WeightUnit.KG) WEIGHT_KG else WEIGHT_LB

    /** "170 cm" / "5′ 7″" for a canonical height in the given unit. */
    fun heightText(cm: Float, u: HeightUnit): String = height(u).let { s -> s.format(s.fromCanonical(cm)) + if (u == HeightUnit.CM) " cm" else "" }
    fun weightText(kg: Float, u: WeightUnit): String = weight(u).let { s -> "${s.format(s.fromCanonical(kg))} ${s.unitLabel}" }

    /** Secondary line showing the other unit, e.g. "≈ 5′ 7″" under "170 cm". */
    fun heightConverted(cm: Float, shown: HeightUnit): String = "≈ " + heightText(cm, if (shown == HeightUnit.CM) HeightUnit.FT_IN else HeightUnit.CM)
    fun weightConverted(kg: Float, shown: WeightUnit): String = "≈ " + weightText(kg, if (shown == WeightUnit.KG) WeightUnit.LB else WeightUnit.KG)
}

/** Pure ruler geometry: scroll offset (px) ↔ index, and where a fling settles. */
object RulerMath {
    /** Offset 0 = [RulerScale.min] under the centre indicator. */
    fun offsetFor(index: Int, scale: RulerScale, spacingPx: Float): Float = (scale.clamp(index) - scale.min) * spacingPx

    fun indexAt(offsetPx: Float, scale: RulerScale, spacingPx: Float): Int = scale.clamp(scale.min + (offsetPx / spacingPx).roundToInt())

    fun maxOffset(scale: RulerScale, spacingPx: Float): Float = (scale.max - scale.min) * spacingPx

    /** A fling/drag always settles exactly on an increment inside the range. */
    fun snap(targetOffsetPx: Float, scale: RulerScale, spacingPx: Float): Float = offsetFor(indexAt(targetOffsetPx, scale, spacingPx), scale, spacingPx)

    /** Fades ticks toward the edges: 1 at the centre, [minAlpha] at the edge. */
    fun edgeAlpha(distanceFromCentre: Float, halfWidth: Float, minAlpha: Float = 0.12f): Float {
        if (halfWidth <= 0f) return 1f
        val t = (abs(distanceFromCentre) / halfWidth).coerceIn(0f, 1f)
        return 1f - (1f - minAlpha) * t * t
    }

    /** Haptic only when a medium/major tick is crossed, and at most once per [minIntervalMs]. */
    fun shouldTick(level: TickLevel, nowMs: Long, lastTickMs: Long, minIntervalMs: Long = 45): Boolean =
        level != TickLevel.MINOR && nowMs - lastTickMs >= minIntervalMs
}
