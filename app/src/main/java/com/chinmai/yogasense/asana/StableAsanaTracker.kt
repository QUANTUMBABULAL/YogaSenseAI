package com.chinmai.yogasense.asana

/**
 * Multi-frame confirmation with hysteresis. Time-based (not frame-count-based) because
 * inference FPS varies (5–9 FPS measured on the target phone).
 *
 * Enter: within the last [windowMs], the pose (same side) holds ≥ [enterShare] of frames,
 *        with ≥ [minFrames] votes spanning ≥ [minSpanMs] and mean confidence ≥ [minMeanConfidence].
 * Stay:  while its share stays ≥ [exitShare] (brief misclassifications don't drop it).
 * Reset: if no frame arrives for [staleGapMs].
 */
class StableAsanaTracker(
    private val windowMs: Long = 1500L,
    private val minFrames: Int = 4,
    private val minSpanMs: Long = 700L,
    private val enterShare: Float = 0.6f,
    private val exitShare: Float = 0.4f,
    private val minMeanConfidence: Float = 0.65f,
    private val staleGapMs: Long = 1000L,
) {
    private data class Vote(val ts: Long, val asana: Asana, val side: BodySide, val confidence: Float)

    private val votes = ArrayDeque<Vote>()
    private var stable: Pair<Asana, BodySide>? = null

    fun reset() { votes.clear(); stable = null }

    fun update(c: FrameClassification): Recognition {
        val last = votes.lastOrNull()
        if (last != null && (c.timestampMs - last.ts > staleGapMs || c.timestampMs < last.ts)) reset()
        votes.addLast(Vote(c.timestampMs, c.asana, c.side, c.confidence))
        while (votes.isNotEmpty() && votes.first().ts < c.timestampMs - windowMs) votes.removeFirst()

        val total = votes.size.toFloat()
        fun stats(key: Pair<Asana, BodySide>): Triple<Int, Float, Long> {
            val m = votes.filter { it.asana == key.first && it.side == key.second }
            val span = if (m.isEmpty()) 0L else m.last().ts - m.first().ts
            return Triple(m.size, if (m.isEmpty()) 0f else m.map { it.confidence }.average().toFloat(), span)
        }

        val leader = votes.filter { it.asana != Asana.UNKNOWN }
            .groupingBy { it.asana to it.side }.eachCount().maxByOrNull { it.value }?.key

        // Hysteresis: keep the current stable pose unless it falls below exitShare.
        stable?.let { cur ->
            val (n, _, _) = stats(cur)
            if (n / total < exitShare) stable = null
        }
        // Enter / switch.
        if (leader != null && leader != stable) {
            val (n, mean, span) = stats(leader)
            if (n / total >= enterShare && n >= minFrames && span >= minSpanMs && mean >= minMeanConfidence) {
                stable = leader
            }
        }

        val cur = stable
        return if (cur != null) {
            val (n, mean, _) = stats(cur)
            Recognition(cur.first, mean, true, cur.second, n / total, frame = c)
        } else {
            val (n, _, span) = leader?.let { stats(it) } ?: Triple(0, 0f, 0L)
            val progress = if (leader == null) 0f
            else minOf(1f, minOf(n.toFloat() / minFrames, span.toFloat() / minSpanMs, (n / total) / enterShare))
            Recognition(
                Asana.UNKNOWN, 0f, false, BodySide.NONE,
                stability = if (leader == null) 0f else n / total,
                pending = leader?.first ?: Asana.UNKNOWN,
                pendingProgress = progress,
                frame = c,
            )
        }
    }
}
