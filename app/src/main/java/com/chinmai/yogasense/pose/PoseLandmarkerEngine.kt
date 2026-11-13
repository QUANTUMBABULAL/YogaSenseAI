package com.chinmai.yogasense.pose

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/** Live metrics measured from actual MediaPipe results (not camera frames). */
data class InferenceStats(
    val inferenceFps: Float = 0f,
    val latencyMs: Long = 0L,
    val error: String? = null,
    val ready: Boolean = false,
)

/**
 * Wraps MediaPipe Pose Landmarker (Lite, LIVE_STREAM).
 *
 * Threading: [submit] and [close] must be called from the same single analysis thread.
 * Results arrive on MediaPipe's internal thread and are published via StateFlow.
 *
 * Back-pressure: at most one frame is in flight. While busy, [submit] returns false and the
 * caller drops the frame, so no queue can build up and the caller's reusable bitmap is never
 * overwritten while MediaPipe might still read it.
 */
class PoseLandmarkerEngine(context: Context) {

    private val _frames = MutableStateFlow<PoseFrame?>(null)
    val frames: StateFlow<PoseFrame?> = _frames.asStateFlow()

    private val _stats = MutableStateFlow(InferenceStats())
    val stats: StateFlow<InferenceStats> = _stats.asStateFlow()

    private val busy = AtomicBoolean(false)
    private val busySinceMs = AtomicLong(0)
    private var lastTimestampMs = 0L
    @Volatile private var closed = false
    @Volatile private var imageWidth = 0
    @Volatile private var imageHeight = 0

    // FPS window, touched only on the MediaPipe result thread.
    private var windowStartMs = 0L
    private var windowCount = 0

    private val landmarker: PoseLandmarker? = try {
        val base = BaseOptions.builder()
            .setModelAssetPath(MODEL_ASSET)
            .setDelegate(Delegate.CPU)
            .build()
        val options = PoseLandmarker.PoseLandmarkerOptions.builder()
            .setBaseOptions(base)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setNumPoses(1)
            .setMinPoseDetectionConfidence(0.5f)
            .setMinPosePresenceConfidence(0.5f)
            .setMinTrackingConfidence(0.5f) // enables landmark tracking between frames
            .setOutputSegmentationMasks(false)
            .setResultListener { result, _ -> onResult(result) }
            .setErrorListener { e ->
                Log.e(TAG, "Pose inference error", e)
                busy.set(false)
                _stats.value = _stats.value.copy(error = e.message ?: "Inference error")
            }
            .build()
        PoseLandmarker.createFromOptions(context, options).also {
            _stats.value = InferenceStats(ready = true)
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to initialise Pose Landmarker", e)
        _stats.value = InferenceStats(error = "Pose model failed to load: ${e.message}")
        null
    }

    /** True if MediaPipe can accept a frame now. Lets callers skip conversion work when busy. */
    fun canAccept(): Boolean {
        if (closed || landmarker == null) return false
        if (!busy.get()) return true
        // Safety valve: if a result was never delivered, don't stall forever.
        return SystemClock.uptimeMillis() - busySinceMs.get() > STALL_TIMEOUT_MS
    }

    /**
     * Submits an upright RGBA bitmap. Returns false if the frame was dropped.
     * MediaPipe copies bitmap pixels into its input packet inside detectAsync, and the
     * single-in-flight gate prevents reuse while a frame is pending.
     */
    fun submit(bitmap: Bitmap, imageWidth: Int, imageHeight: Int): Boolean {
        val lm = landmarker ?: return false
        if (!canAccept()) return false
        var ts = SystemClock.uptimeMillis()
        if (ts <= lastTimestampMs) ts = lastTimestampMs + 1 // MediaPipe requires strictly increasing timestamps
        lastTimestampMs = ts
        this.imageWidth = imageWidth
        this.imageHeight = imageHeight
        busy.set(true)
        busySinceMs.set(ts)
        return try {
            lm.detectAsync(BitmapImageBuilder(bitmap).build(), ts)
            true
        } catch (e: Exception) {
            busy.set(false)
            Log.e(TAG, "detectAsync failed", e)
            _stats.value = _stats.value.copy(error = e.message ?: "detectAsync failed")
            false
        }
    }

    private fun onResult(result: PoseLandmarkerResult) {
        busy.set(false)
        if (closed) return
        val now = SystemClock.uptimeMillis()
        val pose = result.landmarks().firstOrNull()
        val landmarks = pose?.map { n ->
            Landmark(
                x = n.x(),
                y = n.y(),
                z = n.z(),
                visibility = n.visibility().orElse(0f),
                presence = n.presence().orElse(0f),
            )
        }.orEmpty()
        _frames.value = PoseFrame(landmarks, result.timestampMs(), imageWidth, imageHeight)

        if (windowStartMs == 0L) windowStartMs = now
        windowCount++
        val elapsed = now - windowStartMs
        val current = _stats.value
        if (elapsed >= FPS_WINDOW_MS) {
            _stats.value = current.copy(
                inferenceFps = windowCount * 1000f / elapsed,
                latencyMs = now - result.timestampMs(),
                error = null,
            )
            windowStartMs = now
            windowCount = 0
        }
    }

    /** Call from the analysis thread after the analyzer has been detached. */
    fun close() {
        if (closed) return
        closed = true
        try {
            landmarker?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing Pose Landmarker", e)
        }
    }

    companion object {
        private const val TAG = "PoseLandmarkerEngine"
        const val MODEL_ASSET = "pose_landmarker_lite.task"
        private const val STALL_TIMEOUT_MS = 500L
        private const val FPS_WINDOW_MS = 1000L
    }
}
