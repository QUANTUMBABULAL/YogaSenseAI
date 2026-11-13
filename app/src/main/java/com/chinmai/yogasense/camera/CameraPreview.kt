package com.chinmai.yogasense.camera

import android.util.Log
import android.util.Size
import android.view.Surface
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraState
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.chinmai.yogasense.pose.PoseLandmarkerEngine
import java.util.concurrent.Executors

sealed interface CameraStatus {
    data object Starting : CameraStatus
    data object Active : CameraStatus
    data class Error(val message: String) : CameraStatus
}

/**
 * Binds CameraX Preview + ImageAnalysis to the current lifecycle and owns the
 * [PoseLandmarkerEngine] lifetime. Everything is released when this leaves composition.
 *
 * Preview uses FIT_CENTER and both use cases request 4:3, so the visible preview matches the
 * analysed image exactly (no hidden crop) and the overlay can map coordinates 1:1.
 */
@Composable
fun CameraPreview(
    lensFacing: Int,
    onEngineReady: (PoseLandmarkerEngine?) -> Unit,
    onStatus: (CameraStatus) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val rotation = LocalView.current.display?.rotation ?: Surface.ROTATION_0
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FIT_CENTER
            implementationMode = PreviewView.ImplementationMode.PERFORMANCE
        }
    }

    DisposableEffect(lensFacing, lifecycleOwner, rotation) {
        onStatus(CameraStatus.Starting)
        val executor = Executors.newSingleThreadExecutor()
        val mainExecutor = ContextCompat.getMainExecutor(context)
        var engine: PoseLandmarkerEngine? = null
        var provider: ProcessCameraProvider? = null
        var analysis: ImageAnalysis? = null
        var cameraInfo: CameraInfo? = null
        val stateObserver = Observer<CameraState> { state ->
            val err = state.error
            when {
                err != null -> onStatus(CameraStatus.Error(cameraErrorText(err.code)))
                state.type == CameraState.Type.OPEN -> onStatus(CameraStatus.Active)
                else -> onStatus(CameraStatus.Starting)
            }
        }
        var disposed = false // main thread only

        // Model load + provider wait run on the analysis thread; binding hops to main.
        executor.execute {
            val created = PoseLandmarkerEngine(context.applicationContext)
            engine = created
            val cameraProvider = try {
                ProcessCameraProvider.getInstance(context).get()
            } catch (e: Exception) {
                Log.e(TAG, "Camera provider unavailable", e)
                mainExecutor.execute { onStatus(CameraStatus.Error("Camera unavailable: ${e.message}")) }
                return@execute
            }
            mainExecutor.execute {
                if (disposed) return@execute
                onEngineReady(created)
                provider = cameraProvider
                try {
                    val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
                    if (!cameraProvider.hasCamera(selector)) {
                        onStatus(CameraStatus.Error("Requested camera not available on this device"))
                        return@execute
                    }
                    val aspect = AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY
                    val preview = Preview.Builder()
                        .setResolutionSelector(ResolutionSelector.Builder().setAspectRatioStrategy(aspect).build())
                        .setTargetRotation(rotation)
                        .build()
                        .also { it.surfaceProvider = previewView.surfaceProvider }
                    val imageAnalysis = ImageAnalysis.Builder()
                        .setResolutionSelector(
                            ResolutionSelector.Builder()
                                .setAspectRatioStrategy(aspect)
                                .setResolutionStrategy(
                                    ResolutionStrategy(
                                        Size(640, 480),
                                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                                    )
                                )
                                .build()
                        )
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                        .setOutputImageRotationEnabled(true)
                        .setTargetRotation(rotation)
                        .build()
                        .also { it.setAnalyzer(executor, PoseFrameAnalyzer(created)) }
                    analysis = imageAnalysis

                    cameraProvider.unbindAll()
                    val camera = cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview, imageAnalysis)
                    cameraInfo = camera.cameraInfo
                    camera.cameraInfo.cameraState.observe(lifecycleOwner, stateObserver)
                } catch (e: Exception) {
                    Log.e(TAG, "Camera bind failed", e)
                    onStatus(CameraStatus.Error("Camera failed to start: ${e.message}"))
                }
            }
        }

        onDispose {
            disposed = true
            cameraInfo?.cameraState?.removeObserver(stateObserver)
            analysis?.clearAnalyzer()
            provider?.unbindAll()
            onEngineReady(null)
            // Close MediaPipe on the analysis thread so it never races an in-progress analyze().
            executor.execute { engine?.close() }
            executor.shutdown()
        }
    }

    AndroidView(factory = { previewView }, modifier = modifier)
}

private fun cameraErrorText(code: Int): String = when (code) {
    CameraState.ERROR_CAMERA_IN_USE, CameraState.ERROR_MAX_CAMERAS_IN_USE -> "Camera is in use by another app"
    CameraState.ERROR_CAMERA_DISABLED -> "Camera is disabled by device policy"
    CameraState.ERROR_CAMERA_FATAL_ERROR -> "Camera encountered a fatal error — restart the app"
    CameraState.ERROR_DO_NOT_DISTURB_MODE_ENABLED -> "Turn off Do Not Disturb to use the camera"
    else -> "Camera error ($code)"
}

private const val TAG = "CameraPreview"
