package com.example.vehicare.ui.theme

import androidx.compose.ui.graphics.Color

// ---------------------------------------------------------------------------------------------
// Brand: deep navy for headings/primary elements, white surfaces, soft gray cards.
// Status semantics: teal/green = healthy, amber = warning, red = critical/safety.
// Colour is NEVER the only carrier of meaning (see AGENTS.md, Section 5).
// ---------------------------------------------------------------------------------------------

val Navy900 = Color(0xFF0B2545)
val Navy800 = Color(0xFF123A6B)
val Navy700 = Color(0xFF1B4B8F)
val Navy100 = Color(0xFFDDE7F5)
val Navy50 = Color(0xFFEFF4FB)

val Slate700 = Color(0xFF44536B)
val Slate500 = Color(0xFF5A6577)
val Slate200 = Color(0xFFC7D0DC)
val Slate100 = Color(0xFFE3E9F2)
val Slate50 = Color(0xFFF1F4F8)

val Teal600 = Color(0xFF0F766E)
val Teal100 = Color(0xFFD6F1EE)

val Charcoal900 = Color(0xFF141A22)
val Charcoal600 = Color(0xFF3A4353)

val White = Color(0xFFFFFFFF)

// Semantic status colours (paired with icons and text everywhere they are used).
val HealthyGreen = Color(0xFF12734F)
val HealthyGreenContainer = Color(0xFFD8F1E4)
val WarningAmber = Color(0xFF9A5B00)
val WarningAmberContainer = Color(0xFFFFEED2)
val CriticalRed = Color(0xFFB3261E)
val CriticalRedContainer = Color(0xFFFBDCDA)
val InfoBlue = Color(0xFF1565C0)
val InfoBlueContainer = Color(0xFFDCE9FB)

// Gentle gradient used only for intelligent-diagnostic accents.
val DiagnosticGradientStart = Navy700
val DiagnosticGradientEnd = Teal600
