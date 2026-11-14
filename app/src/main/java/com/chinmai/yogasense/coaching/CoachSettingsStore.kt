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

/**
 * Phase 7B: practice settings are stored per profile in Room (user_preferences). The old
 * device-wide SharedPreferences file `practice_settings` is now READ-ONLY: it is imported once
 * into the first profile and kept untouched as a fallback (never written, never deleted here).
 */
object LegacySettingsImport {
    /** Keys written by the Phase 5.2 CoachSettingsStore. */
    val KEYS = setOf("voice_mode", "voice_encouragement", "voice_countdown", "voice_corrections", "voice_rate", "vis_skeleton", "vis_highlights", "vis_reference", "vis_diagnostics")

    /** Null when the user never saved practice settings (then profile defaults apply). */
    fun fromMap(m: Map<String, *>): Pair<CoachSettings, VisualSettings>? =
        if (m.keys.none { it in KEYS }) null else SettingsCodec.decodeCoach(m) to SettingsCodec.decodeVisual(m)
}

class LegacyPracticeSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("practice_settings", Context.MODE_PRIVATE)
    fun read(): Pair<CoachSettings, VisualSettings>? = LegacySettingsImport.fromMap(prefs.all)
}

/** Device-level developer toggle (not personal, not per profile). Falls back to the legacy value once. */
class DeviceSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("device_settings", Context.MODE_PRIVATE)
    private val legacy = LegacyPracticeSettings(context)

    var diagnostics: Boolean
        get() = if (prefs.contains(KEY)) prefs.getBoolean(KEY, false) else legacy.read()?.second?.diagnostics ?: false
        set(v) { prefs.edit().putBoolean(KEY, v).apply() }

    private companion object { const val KEY = "vis_diagnostics" }
}
