package com.chinmai.yogasense.coaching.assessment

import com.chinmai.yogasense.asana.Asana

data class PoseSummary(
    val asana: Asana,
    val stableHoldTotalMs: Long,
    val longestHoldMs: Long,
    val meanAlignment: Int?,       // null = not enough assessed samples
    val meanStability: Int?,       // null = not enough measured samples
    val corrections: Map<String, Int>,
    val improvements: List<String>,
)

data class SessionSummary(
    val durationMs: Long,
    val recognisedPoses: List<Asana>,
    val poses: List<PoseSummary>,
)

/**
 * Aggregates real measurements for the end-of-session summary. Averages are only reported with
 * at least [minSamples] samples, and only from frames where the hold was counting.
 */
class SessionStats(private val minSamples: Int = 10) {
    private val recognised = linkedSetOf<Asana>()
    private val alignment = HashMap<Asana, MutableList<Int>>()
    private val stability = HashMap<Asana, MutableList<Int>>()
    private val corrections = HashMap<Asana, LinkedHashMap<String, Int>>()
    private val improvements = HashMap<Asana, MutableList<String>>()

    fun recognised(a: Asana) { recognised += a }

    fun sample(a: Asana, alignmentScore: Int?, stabilityScore: Int?) {
        alignmentScore?.let { alignment.getOrPut(a) { mutableListOf() } += it }
        stabilityScore?.let { stability.getOrPut(a) { mutableListOf() } += it }
    }

    fun correction(a: Asana, text: String) {
        val m = corrections.getOrPut(a) { LinkedHashMap() }
        m[text] = (m[text] ?: 0) + 1
    }

    fun improvement(a: Asana, label: String) { improvements.getOrPut(a) { mutableListOf() } += label }

    fun summary(durationMs: Long, holds: Map<Asana, HoldStats>): SessionSummary {
        val poses = (recognised + holds.keys).distinct().map { a ->
            val h = holds[a] ?: HoldStats()
            PoseSummary(
                a, h.totalMs, h.longestMs,
                alignment[a]?.takeIf { it.size >= minSamples }?.average()?.let { Math.round(it).toInt() },
                stability[a]?.takeIf { it.size >= minSamples }?.average()?.let { Math.round(it).toInt() },
                corrections[a].orEmpty(),
                improvements[a].orEmpty(),
            )
        }
        return SessionSummary(durationMs, recognised.toList(), poses)
    }
}
