package com.chinmai.yogasense.wellness

/**
 * Support content shown when PHQ-9 item 9 is answered with anything other than "Not at all".
 * Numbers verified 2026-11-14 (Tele-MANAS: MoHFW / NIMHANS, 24×7, free; 112: India's national
 * emergency number). The app never contacts anyone itself; "Call" buttons only open the dialer.
 */
object CrisisSupport {
    data class Line(val name: String, val number: String, val dial: String, val detail: String)

    val TELE_MANAS = Line("Tele-MANAS", "14416", "14416", "Free, confidential mental health support, 24×7, in many Indian languages")
    val TELE_MANAS_TOLL_FREE = Line("Tele-MANAS (toll-free)", "1-800-891-4416", "18008914416", "Same service, alternative number")
    val EMERGENCY = Line("Emergency services", "112", "112", "If you may act on these thoughts or cannot stay safe right now")

    const val TITLE = "Thank you for sharing this"
    const val INTRO =
        "You mentioned having thoughts that you would be better off dead or of hurting yourself. " +
            "Many people have thoughts like these at difficult times, and you deserve support."
    const val IMMEDIATE_HEADER = "If you might act on these thoughts or can't stay safe right now"
    const val IMMEDIATE =
        "Call 112 or go to the nearest hospital emergency department. If you can, stay with someone you trust until help arrives."
    const val NOT_IMMEDIATE_HEADER = "If you are safe right now"
    const val NOT_IMMEDIATE =
        "Consider talking to someone you trust today, and reach out to a mental health professional. Tele-MANAS offers free, confidential support at any time."
    const val LIMITS =
        "YogaSense AI cannot provide emergency care or crisis support, and it has not contacted anyone on your behalf."
}
