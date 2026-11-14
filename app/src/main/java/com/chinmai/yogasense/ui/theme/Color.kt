package com.chinmai.yogasense.ui.theme

import androidx.compose.ui.graphics.Color

// ---- Brand ------------------------------------------------------------------------------
val Midnight = Color(0xFF171C28)
val Coral = Color(0xFFFF6B4A)
val Lime = Color(0xFFB7F36B)
val WarmWhite = Color(0xFFF5F3EE)

// ---- Light app theme (Home, Explore, Detail, Progress, Profile) -------------------------
val CardWhite = Color(0xFFFFFFFF)
val SurfaceMuted = Color(0xFFECE9E2)    // chips, placeholders
val Hairline = Color(0xFFE3DFD6)         // borders/dividers
val Ink = Midnight                        // primary text
val InkMuted = Color(0xFF5E6472)          // secondary text, 5.5:1 on WarmWhite
val InkSubtle = Color(0xFF8A8F99)         // tertiary/disabled text
val CoralDeep = Color(0xFFC2412A)         // coral text on light surfaces (AA)
val CoralTint = Color(0xFFFFE6DF)
val LimeTint = Color(0xFFEAF9D6)
val LimeDeep = Color(0xFF4E7A14)          // lime-family text on light surfaces (AA)
val ErrorRed = Color(0xFFB3261E)
val WarningAmber = Color(0xFF9A5B00)

// ---- Dark immersive practice theme ------------------------------------------------------
// These names are imported by the existing practice screen (LiveTrackingScreen, AsanaCard);
// keep them stable.
val Background = Midnight
val Surface1 = Color(0xFF222838)
val Surface2 = Color(0xFF2C3346)
val Accent = Lime
val AccentDim = Color(0xFF6E9440)
val TextMuted = Color(0xFFA6ACB8)
val Warn = Color(0xFFFF9A80)              // coral-tinted warning, readable on Midnight
