package com.chinmai.yogasense.ui.learning.player

import com.chinmai.yogasense.catalog.AsanaInfo
import com.chinmai.yogasense.catalog.PoseFigure

/**
 * What the demonstration player can show for an asana. New formats (e.g. Lottie, 3D) are added as
 * new subtypes plus one renderer branch in AsanaAnimationPlayer; the learning screen is unchanged.
 */
sealed interface LessonMedia {
    /** Bundled human demonstration video at assets/[assetPath]. */
    data class Video(val assetPath: String) : LessonMedia

    /** Programmatic stick-figure animation from catalogue keyframes. Not a human demonstration. */
    data class AnimatedIllustration(val keyframes: List<PoseFigure>) : LessonMedia

    /** Single static illustration. */
    data class StaticIllustration(val figure: PoseFigure) : LessonMedia
}

object LessonMediaResolver {
    /**
     * Prefer a real bundled video; otherwise fall back to the animated illustration, then to the
     * static figure. [assetExists] is injected so this is testable without Android.
     */
    fun resolve(info: AsanaInfo, assetExists: (String) -> Boolean): LessonMedia {
        // A real video always wins over the illustration (canonical name first, then the provided file).
        com.chinmai.yogasense.catalog.AsanaVideoAssets.candidates(info).firstOrNull(assetExists)?.let { return LessonMedia.Video(it) }
        return illustration(info)
    }

    private fun illustration(info: AsanaInfo): LessonMedia = when {
        info.demoSequence.size >= 2 -> LessonMedia.AnimatedIllustration(info.demoSequence)
        else -> LessonMedia.StaticIllustration(info.figure)
    }

    /** Fallback used if a video exists but fails to play. */
    fun fallbackFor(info: AsanaInfo): LessonMedia =
        if (info.demoSequence.size >= 2) LessonMedia.AnimatedIllustration(info.demoSequence)
        else LessonMedia.StaticIllustration(info.figure)
}
