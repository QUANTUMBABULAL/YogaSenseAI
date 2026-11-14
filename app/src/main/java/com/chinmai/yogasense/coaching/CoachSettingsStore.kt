package com.chinmai.yogasense.coaching

import android.content.Context

/** Pure key/value codec so persistence logic is unit-testable without Android. */
object SettingsCodec {
    fun encode(c: CoachSettings, v: VisualSettings): Map<String, Any> = mapOf(
        "voice_mode" to c.mode.name,
        "voice_encouragement" to c.encouragement,
        "voice_countdown" to c.countdown,
        "voice_corrections" to c.corrections,
        "voice_rate" to c.speechRate,
        "vis_skeleton" to v.liveSkeleton,
        "vis_highlights" to v.correctionHighlights,
        "vis_reference" to v.referenceFigure,
        "vis_diagnostics" to v.diagnostics,
    )

    fun decodeCoach(m: Map<String, *>): CoachSettings {
        val d = CoachSettings()
        return CoachSettings(
            mode = (m["voice_mode"] as? String)?.let { runCatching { VoiceMode.valueOf(it) }.getOrNull() } ?: d.mode,
            encouragement = m["voice_encouragement"] as? Boolean ?: d.encouragement,
            countdown = m["voice_countdown"] as? Boolean ?: d.countdown,
            corrections = m["voice_corrections"] as? Boolean ?: d.corrections,
            speechRate = ((m["voice_rate"] as? Float) ?: d.speechRate).coerceIn(0.8f, 1.2f),
        )
    }

    fun decodeVisual(m: Map<String, *>): VisualSettings {
        val d = VisualSettings()
        return VisualSettings(
            liveSkeleton = m["vis_skeleton"] as? Boolean ?: d.liveSkeleton,
            correctionHighlights = m["vis_highlights"] as? Boolean ?: d.correctionHighlights,
            referenceFigure = m["vis_reference"] as? Boolean ?: d.referenceFigure,
            diagnostics = m["vis_diagnostics"] as? Boolean ?: d.diagnostics,
        )
    }
}

/** Local persistence (SharedPreferences; no extra dependency). */
class CoachSettingsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("practice_settings", Context.MODE_PRIVATE)

    fun loadCoach(): CoachSettings = SettingsCodec.decodeCoach(prefs.all)
    fun loadVisual(): VisualSettings = SettingsCodec.decodeVisual(prefs.all)

    fun save(c: CoachSettings, v: VisualSettings) {
        val e = prefs.edit()
        SettingsCodec.encode(c, v).forEach { (k, value) ->
            when (value) {
                is Boolean -> e.putBoolean(k, value)
                is Float -> e.putFloat(k, value)
                is String -> e.putString(k, value)
            }
        }
        e.apply()
    }
}
