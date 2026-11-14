package com.chinmai.yogasense.coaching

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

enum class VoiceStatus { Initializing, Ready, Unavailable, Released }

data class VoiceState(
    val status: VoiceStatus = VoiceStatus.Initializing,
    val speaking: Boolean = false,
    val lastText: String? = null,
    val error: String? = null,
)

/**
 * Thin wrapper over Android [TextToSpeech]. Create once per practice session (not per
 * recomposition) and call [release] when the session screen goes away.
 *
 * - Urgent cues (SAFETY/FRAMING) flush the queue; others queue behind at most one pending item,
 *   so speech never overlaps and never backs up.
 * - Language: device locale if supported, else US English, else any English.
 */
class VoiceCoachManager(context: Context) {
    private val _state = MutableStateFlow(VoiceState())
    val state: StateFlow<VoiceState> = _state.asStateFlow()

    private val pending = AtomicInteger(0)
    private var rate = 1.0f
    private var tts: TextToSpeech? = null

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            val engine = tts
            if (status != TextToSpeech.SUCCESS || engine == null) {
                _state.update { it.copy(status = VoiceStatus.Unavailable, error = "Text-to-speech engine unavailable") }
                return@TextToSpeech
            }
            val locale = listOf(Locale.getDefault(), Locale.US, Locale.UK, Locale.ENGLISH).firstOrNull {
                engine.isLanguageAvailable(it) >= TextToSpeech.LANG_AVAILABLE
            }
            if (locale == null || engine.setLanguage(locale) < TextToSpeech.LANG_AVAILABLE) {
                _state.update { it.copy(status = VoiceStatus.Unavailable, error = "No supported English voice installed") }
                return@TextToSpeech
            }
            engine.setSpeechRate(rate)
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = _state.update { it.copy(speaking = true) }
                override fun onDone(utteranceId: String?) {
                    val left = pending.decrementAndGet().coerceAtLeast(0)
                    pending.set(left)
                    _state.update { it.copy(speaking = left > 0) }
                }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) = onDone(utteranceId)
                override fun onError(utteranceId: String?, errorCode: Int) {
                    Log.w(TAG, "TTS error $errorCode")
                    onDone(utteranceId)
                }
                override fun onStop(utteranceId: String?, interrupted: Boolean) {
                    pending.set(0); _state.update { it.copy(speaking = false) }
                }
            })
            _state.update { it.copy(status = VoiceStatus.Ready, error = null) }
        }
    }

    fun setSpeechRate(r: Float) {
        rate = r.coerceIn(0.6f, 1.5f)
        tts?.setSpeechRate(rate)
    }

    /** Speaks [cue] if ready. Returns false if dropped (not ready, or busy with a pending item). */
    fun speak(cue: Cue): Boolean {
        val engine = tts ?: return false
        if (_state.value.status != VoiceStatus.Ready) return false
        val urgent = cue.priority.interrupts
        if (!urgent && pending.get() >= 2) return false // one speaking + one queued at most
        val mode = if (urgent) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        if (urgent) pending.set(0)
        val result = engine.speak(cue.text, mode, null, "cue-${cue.key}-${System.nanoTime()}")
        if (result == TextToSpeech.SUCCESS) {
            pending.incrementAndGet()
            _state.update { it.copy(lastText = cue.text) }
            return true
        }
        _state.update { it.copy(error = "Speech failed") }
        return false
    }

    fun stop() {
        tts?.stop()
        pending.set(0)
        _state.update { it.copy(speaking = false) }
    }

    fun release() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        pending.set(0)
        _state.update { it.copy(status = VoiceStatus.Released, speaking = false) }
    }

    private companion object { const val TAG = "VoiceCoach" }
}
