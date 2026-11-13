package com.chinmai.yogasense.ui.practice

import com.chinmai.yogasense.asana.Asana
import com.chinmai.yogasense.asana.AsanaRecognizer
import com.chinmai.yogasense.asana.BodySide
import com.chinmai.yogasense.asana.Recognition
import com.chinmai.yogasense.pose.PoseFrame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

data class LiveRecognitionState(
    val recognition: Recognition = Recognition.NONE,
    /** Mean time spent in AsanaRecognizer.process (smoothing + features + classify + track), ms. */
    val recognitionMs: Float = 0f,
    val framesProcessed: Long = 0,
)

/**
 * One instance per camera session (per PoseLandmarkerEngine). Consumes pose frames in order on a
 * background dispatcher; the UI only reads [state]. The MediaPipe result thread is never blocked:
 * frames arrive via a conflated StateFlow, so if recognition ever lags, stale frames are skipped.
 */
class LiveRecognitionController(
    private val recognizer: AsanaRecognizer = AsanaRecognizer(),
    private val log: ((String) -> Unit)? = null,
    private val clockMs: () -> Long = { System.nanoTime() / 1_000_000L },
) {
    private val _state = MutableStateFlow(LiveRecognitionState())
    val state: StateFlow<LiveRecognitionState> = _state.asStateFlow()

    private var lastTimestamp = Long.MIN_VALUE
    private var emaMs = 0f
    private var count = 0L
    private var lastStable: Pair<Asana, BodySide>? = null
    private var lastDebugLogMs = 0L

    suspend fun run(frames: Flow<PoseFrame?>) = withContext(Dispatchers.Default) {
        frames.collect { onFrame(it) }
    }

    /** Synchronous step; exposed for tests. Must be called from one thread at a time. */
    fun onFrame(frame: PoseFrame?) {
        if (frame == null) return
        if (frame.timestampMs <= lastTimestamp) return // preserve ordering, drop duplicates
        lastTimestamp = frame.timestampMs

        val t0 = System.nanoTime()
        val r = recognizer.process(frame)
        val ms = (System.nanoTime() - t0) / 1_000_000f
        emaMs = if (count == 0L) ms else emaMs + 0.1f * (ms - emaMs)
        count++
        _state.value = LiveRecognitionState(r, emaMs, count)
        log?.let { debugLog(it, r, frame.timestampMs) }
    }

    fun reset() {
        recognizer.reset()
        lastTimestamp = Long.MIN_VALUE
        lastStable = null
        count = 0L
        emaMs = 0f
        _state.value = LiveRecognitionState()
    }

    private fun debugLog(log: (String) -> Unit, r: Recognition, ts: Long) {
        val stable = if (r.isStable) r.asana to r.side else null
        if (stable != lastStable) {
            log("CONFIRMED ${stable?.let { "${it.first} ${it.second}" } ?: "none"} conf=%.2f stab=%.2f".format(r.confidence, r.stability))
            lastStable = stable
        }
        val now = clockMs()
        if (now - lastDebugLogMs < DEBUG_LOG_INTERVAL_MS) return
        lastDebugLogMs = now
        val f = r.frame ?: return
        val best = f.best
        val failing = best?.evidence
            ?.filter { it.score < 1f }
            ?.joinToString("; ") { e ->
                val v = e.value?.let { "%.2f".format(it) } ?: "missing"
                "${e.name}=$v [${e.min}..${e.max}] s=%.2f".format(e.score)
            }.orEmpty()
        log(
            "t=$ts recMs=%.2f frame=${f.asana} ${f.side} conf=%.2f | best=${best?.asana} ${best?.side} %.2f | %s | off-range: %s | lowConf: %s"
                .format(emaMs, f.confidence, best?.confidence ?: 0f, f.reason, failing.ifEmpty { "none" }, f.lowConfidenceLandmarks)
        )
    }

    private companion object {
        const val DEBUG_LOG_INTERVAL_MS = 1000L
    }
}
