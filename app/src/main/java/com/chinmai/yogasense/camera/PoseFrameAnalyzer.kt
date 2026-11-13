package com.chinmai.yogasense.camera

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Rect
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.chinmai.yogasense.pose.PoseLandmarkerEngine

/**
 * Converts RGBA_8888 [ImageProxy] frames into an upright, reusable Bitmap and hands it to
 * [PoseLandmarkerEngine]. Runs on the single CameraX analysis thread.
 *
 * - Frames are skipped (no copy) while MediaPipe is busy.
 * - Bitmaps are allocated only when the frame size or rotation changes.
 * - The ImageProxy is always closed in `finally`.
 */
class PoseFrameAnalyzer(private val engine: PoseLandmarkerEngine) : ImageAnalysis.Analyzer {

    private var rawBitmap: Bitmap? = null      // width = rowStride / 4, may include row padding
    private var uprightBitmap: Bitmap? = null  // only used if CameraX did not pre-rotate
    private var uprightCanvas: Canvas? = null
    private var lastRotation = -1
    private val matrix = Matrix()
    private val srcRect = Rect()

    override fun analyze(image: ImageProxy) {
        try {
            if (!engine.canAccept()) return
            val plane = image.planes[0]
            val width = image.width
            val height = image.height
            val strideWidth = plane.rowStride / plane.pixelStride

            val raw = rawBitmap?.takeIf { it.width == strideWidth && it.height == height }
                ?: Bitmap.createBitmap(strideWidth, height, Bitmap.Config.ARGB_8888).also { rawBitmap = it }
            val buffer = plane.buffer
            buffer.rewind()
            raw.copyPixelsFromBuffer(buffer)

            val rotation = image.imageInfo.rotationDegrees
            if (rotation == 0 && strideWidth == width) {
                engine.submit(raw, width, height)
                return
            }

            // Slow path: crop row padding and/or rotate into a reusable upright bitmap.
            val outW = if (rotation % 180 == 0) width else height
            val outH = if (rotation % 180 == 0) height else width
            val upright = uprightBitmap?.takeIf { it.width == outW && it.height == outH && rotation == lastRotation }
                ?: Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888).also {
                    uprightBitmap = it
                    uprightCanvas = Canvas(it)
                    lastRotation = rotation
                    matrix.reset()
                    matrix.postRotate(rotation.toFloat())
                    when (rotation) {
                        90 -> matrix.postTranslate(height.toFloat(), 0f)
                        180 -> matrix.postTranslate(width.toFloat(), height.toFloat())
                        270 -> matrix.postTranslate(0f, width.toFloat())
                    }
                }
            val canvas = uprightCanvas ?: return
            srcRect.set(0, 0, width, height)
            canvas.save()
            canvas.concat(matrix)
            canvas.drawBitmap(raw, srcRect, srcRect, null)
            canvas.restore()
            engine.submit(upright, outW, outH)
        } catch (e: Exception) {
            Log.e(TAG, "Frame analysis failed", e)
        } finally {
            image.close()
        }
    }

    companion object {
        private const val TAG = "PoseFrameAnalyzer"
    }
}
