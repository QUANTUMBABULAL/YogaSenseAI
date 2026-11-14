package com.chinmai.yogasense.ui.learning.player

import com.chinmai.yogasense.catalog.PoseFigure
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class PlaybackStatus { Loading, Ready, Ended, Error, Released }

data class PlaybackState(
    val status: PlaybackStatus = PlaybackStatus.Loading,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val error: String? = null,
) {
    val progress: Float get() = if (durationMs <= 0) 0f else (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
}

/**
 * Common control surface for every demonstration format (video, animated illustration, …),
 * so the learning screen and controls never depend on a concrete media type.
 */
interface DemoPlayback {
    val state: StateFlow<PlaybackState>
    fun play()
    fun pause()
    fun replay()
    fun release()
    fun togglePlayPause() = if (state.value.isPlaying) pause() else play()
}

// ---- Animated illustration ----------------------------------------------------------------

/**
 * Deterministic keyframe timeline: hold each frame, ease into the next, hold the final pose.
 * Pure Kotlin so it is unit-testable.
 */
class FigureTimeline(
    val frames: List<PoseFigure>,
    private val holdMs: Long = 700,
    private val transitionMs: Long = 1400,
    private val finalHoldMs: Long = 3000,
) {
    init { require(frames.isNotEmpty()) { "At least one keyframe required" } }

    val durationMs: Long = (frames.size - 1) * (holdMs + transitionMs) + finalHoldMs

    fun frameAt(positionMs: Long): PoseFigure {
        var t = positionMs.coerceIn(0, durationMs)
        for (i in 0 until frames.size - 1) {
            if (t < holdMs) return frames[i]
            t -= holdMs
            if (t < transitionMs) return lerp(frames[i], frames[i + 1], easeInOut(t.toFloat() / transitionMs))
            t -= transitionMs
        }
        return frames.last()
    }

    private fun easeInOut(x: Float) = if (x < 0.5f) 4 * x * x * x else 1 - Math.pow((-2.0 * x + 2), 3.0).toFloat() / 2

    companion object {
        private fun l(a: Pair<Float, Float>, b: Pair<Float, Float>, f: Float) =
            (a.first + (b.first - a.first) * f) to (a.second + (b.second - a.second) * f)

        fun lerp(a: PoseFigure, b: PoseFigure, f: Float) = PoseFigure(
            head = l(a.head, b.head, f),
            shoulderL = l(a.shoulderL, b.shoulderL, f), shoulderR = l(a.shoulderR, b.shoulderR, f),
            elbowL = l(a.elbowL, b.elbowL, f), elbowR = l(a.elbowR, b.elbowR, f),
            wristL = l(a.wristL, b.wristL, f), wristR = l(a.wristR, b.wristR, f),
            hipL = l(a.hipL, b.hipL, f), hipR = l(a.hipR, b.hipR, f),
            kneeL = l(a.kneeL, b.kneeL, f), kneeR = l(a.kneeR, b.kneeR, f),
            ankleL = l(a.ankleL, b.ankleL, f), ankleR = l(a.ankleR, b.ankleR, f),
        )
    }
}

/**
 * Playback for the animated illustration. The UI calls [tick] with frame timestamps while playing;
 * no threads or timers are owned here, so release is trivial and tests can drive time directly.
 */
class FigurePlayback(
    val timeline: FigureTimeline,
    autoPlay: Boolean,
    private val loop: Boolean,
) : DemoPlayback {
    private val _state = MutableStateFlow(
        PlaybackState(PlaybackStatus.Ready, isPlaying = autoPlay, durationMs = timeline.durationMs),
    )
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()
    private var lastTickMs: Long? = null

    fun tick(frameTimeMs: Long) {
        val s = _state.value
        val last = lastTickMs
        lastTickMs = frameTimeMs
        if (!s.isPlaying || last == null) return
        val dt = (frameTimeMs - last).coerceIn(0, 250) // ignore long gaps (e.g. app paused)
        var pos = s.positionMs + dt
        if (pos >= s.durationMs) {
            if (loop) pos %= s.durationMs
            else { _state.value = s.copy(positionMs = s.durationMs, isPlaying = false, status = PlaybackStatus.Ended); return }
        }
        _state.value = s.copy(positionMs = pos)
    }

    override fun play() {
        if (_state.value.status == PlaybackStatus.Released) return
        lastTickMs = null
        _state.update {
            if (it.status == PlaybackStatus.Ended) it.copy(positionMs = 0, isPlaying = true, status = PlaybackStatus.Ready)
            else it.copy(isPlaying = true)
        }
    }

    override fun pause() = _state.update { it.copy(isPlaying = false) }

    override fun replay() {
        if (_state.value.status == PlaybackStatus.Released) return
        lastTickMs = null
        _state.update { it.copy(positionMs = 0, isPlaying = true, status = PlaybackStatus.Ready) }
    }

    override fun release() = _state.update { it.copy(isPlaying = false, status = PlaybackStatus.Released) }

    fun currentFrame(): PoseFigure = timeline.frameAt(_state.value.positionMs)
}

/** Static illustration: no timeline, never plays. */
object NoPlayback : DemoPlayback {
    override val state: StateFlow<PlaybackState> = MutableStateFlow(PlaybackState(PlaybackStatus.Ready))
    override fun play() {}
    override fun pause() {}
    override fun replay() {}
    override fun release() {}
}

fun formatTime(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}
