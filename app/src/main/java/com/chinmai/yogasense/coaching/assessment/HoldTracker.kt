package com.chinmai.yogasense.coaching.assessment

import com.chinmai.yogasense.asana.Asana

data class HoldStats(val totalMs: Long = 0, val longestMs: Long = 0, val segments: Int = 0)

/**
 * Stable-hold time per asana. Time is counted only between consecutive QUALIFYING frames
 * (caller decides: confirmed recognition + alignment available + stability ≥ threshold).
 * - Non-qualifying frames pause counting; the segment survives a dropout ≤ [maxDropoutMs].
 * - A longer dropout, leaving the frame, or a different asana ends the segment.
 * - Gaps between qualifying frames > [maxFrameGapMs] are not counted (e.g. app paused).
 */
class HoldTracker(
    val stabilityThreshold: Int = 40,
    private val maxDropoutMs: Long = 1500,
    private val maxFrameGapMs: Long = 500,
) {
    private var segAsana: Asana? = null
    private var segMs = 0L
    private var lastQualifiedAt: Long? = null
    private val stats = linkedMapOf<Asana, HoldStats>()

    val totals: Map<Asana, HoldStats> get() = stats

    fun reset() { closeSegment(); stats.clear() }

    fun update(asana: Asana?, qualifies: Boolean, timeMs: Long, notCountingReason: String? = null): HoldStatus {
        if (asana != segAsana && segAsana != null) closeSegment()
        val last = lastQualifiedAt
        if (asana != null && qualifies) {
            when {
                segAsana == asana && last != null && timeMs - last <= maxFrameGapMs -> segMs += timeMs - last
                segAsana == asana && last != null && timeMs - last <= maxDropoutMs -> Unit // resume, dropout not counted
                else -> { closeSegment(); segAsana = asana; segMs = 0 }
            }
            lastQualifiedAt = timeMs
            record()
            return HoldStatus(asana, segMs, true, null)
        }
        if (segAsana != null && (last == null || timeMs - last > maxDropoutMs)) closeSegment()
        return HoldStatus(asana, if (segAsana == asana) segMs else 0, false, notCountingReason)
    }

    private fun record() {
        val a = segAsana ?: return
        val prev = stats[a] ?: HoldStats()
        stats[a] = prev.copy(longestMs = maxOf(prev.longestMs, segMs))
    }

    private fun closeSegment() {
        val a = segAsana
        if (a != null && segMs > 0) {
            val prev = stats[a] ?: HoldStats()
            stats[a] = HoldStats(prev.totalMs + segMs, maxOf(prev.longestMs, segMs), prev.segments + 1)
        }
        segAsana = null; segMs = 0; lastQualifiedAt = null
    }

    /** Totals including the open segment (for summaries). */
    fun snapshotTotals(): Map<Asana, HoldStats> {
        val out = LinkedHashMap(stats)
        val a = segAsana
        if (a != null && segMs > 0) {
            val p = out[a] ?: HoldStats()
            out[a] = HoldStats(p.totalMs + segMs, maxOf(p.longestMs, segMs), p.segments + 1)
        }
        return out
    }
}
