package com.chinmai.yogasense.camera

import androidx.camera.core.CameraSelector

/** The two cameras a user can pick before (or during) practice. */
enum class CameraChoice(val lensFacing: Int, val label: String) {
    FRONT(CameraSelector.LENS_FACING_FRONT, "Front camera"),
    BACK(CameraSelector.LENS_FACING_BACK, "Back camera");

    companion object {
        fun of(lensFacing: Int): CameraChoice? = entries.firstOrNull { it.lensFacing == lensFacing }
    }
}

/** What was actually bound for a requested lens; [available] holds the lenses the device has. */
data class LensResolution(val requested: Int, val bound: Int, val available: Set<Int>) {
    val fellBack: Boolean get() = bound != requested
}

/** Pure camera-selection rules (no CameraX calls), so they can be unit tested on the JVM. */
object CameraLensPolicy {
    /** Guided sessions face the user (front camera); free practice keeps its existing back-camera default. */
    fun defaultLens(guided: Boolean): Int = (if (guided) CameraChoice.FRONT else CameraChoice.BACK).lensFacing

    /**
     * The requested lens if the device has it, otherwise the other front/back camera, otherwise any
     * camera it has. Null when there is no camera at all.
     */
    fun resolve(requested: Int, available: Set<Int>): LensResolution? {
        val bound = when {
            requested in available -> requested
            else -> CameraChoice.entries.map { it.lensFacing }.firstOrNull { it != requested && it in available }
                ?: available.minOrNull()
        } ?: return null
        return LensResolution(requested, bound, available)
    }

    /** User-facing explanation of a fallback, or null when the requested camera was used. */
    fun fallbackMessage(r: LensResolution): String? {
        if (!r.fellBack) return null
        val wanted = CameraChoice.of(r.requested)?.label ?: "The selected camera"
        val using = CameraChoice.of(r.bound)?.label?.lowercase() ?: "the available camera"
        return "$wanted isn't available on this device — using the $using instead."
    }
}
