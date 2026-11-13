package com.chinmai.yogasense.asana

import com.chinmai.yogasense.pose.LandmarkSmoother
import com.chinmai.yogasense.pose.PoseFrame

/**
 * Facade: smoothing → features → per-frame classification → temporal confirmation.
 * Not thread-safe; call [process] from one thread (e.g. a collector of PoseLandmarkerEngine.frames).
 */
class AsanaRecognizer(
    private val smoother: LandmarkSmoother = LandmarkSmoother(),
    private val classifier: AsanaClassifier = AsanaClassifier(),
    private val tracker: StableAsanaTracker = StableAsanaTracker(),
) {
    fun process(frame: PoseFrame): Recognition =
        tracker.update(classifier.classify(smoother.smooth(frame)))

    fun reset() { smoother.reset(); tracker.reset() }
}
