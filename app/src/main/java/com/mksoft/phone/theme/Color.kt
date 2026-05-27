package com.mksoft.phone.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush

// Forest / Autumn Expressive color palette
// Combination of Red, Green, Olive Green, Orange, and White

// Dark Theme Colors
val GeminiPrimaryDark = Color(0xFF9CCC65) // Light Olive Green
val GeminiOnPrimaryDark = Color(0xFF1B3D00)
val GeminiPrimaryContainerDark = Color(0xFF33691E) // Dark Olive Green
val GeminiOnPrimaryContainerDark = Color(0xFFDCEDC8)

val GeminiSecondaryDark = Color(0xFFFFB74D) // Orange
val GeminiOnSecondaryDark = Color(0xFF4E2C00)
val GeminiSecondaryContainerDark = Color(0xFFE65100) // Deep Orange
val GeminiOnSecondaryContainerDark = Color(0xFFFFE0B2)

val GeminiTertiaryDark = Color(0xFFEF5350) // Red
val GeminiOnTertiaryDark = Color(0xFF4A0002)
val GeminiTertiaryContainerDark = Color(0xFFB71C1C) // Dark Red
val GeminiOnTertiaryContainerDark = Color(0xFFFFEBEE)

val GeminiBackgroundDark = Color(0xFF0F150E) // Very Dark Olive/Charcoal
val GeminiOnBackgroundDark = Color(0xFFFFFFFF) // White
val GeminiSurfaceDark = Color(0xFF162015) // Dark Olive Slate
val GeminiOnSurfaceDark = Color(0xFFFFFFFF) // White
val GeminiSurfaceVariantDark = Color(0xFF283626) // Olive Slate
val GeminiOnSurfaceVariantDark = Color(0xFFC7D3C5) // Light Olive Gray

// Light Theme Colors (Clean autumn fallback)
val GeminiPrimaryLight = Color(0xFF558B2F) // Olive Green
val GeminiOnPrimaryLight = Color(0xFFFFFFFF) // White
val GeminiPrimaryContainerLight = Color(0xFFDCEDC8)
val GeminiOnPrimaryContainerLight = Color(0xFF1B3D00)

val GeminiSecondaryLight = Color(0xFFF57C00) // Orange
val GeminiOnSecondaryLight = Color(0xFFFFFFFF) // White
val GeminiSecondaryContainerLight = Color(0xFFFFE0B2)
val GeminiOnSecondaryContainerLight = Color(0xFF4E2C00)

val GeminiTertiaryLight = Color(0xFFD32F2F) // Red
val GeminiOnTertiaryLight = Color(0xFFFFFFFF) // White
val GeminiTertiaryContainerLight = Color(0xFFFFEBEE)
val GeminiOnTertiaryContainerLight = Color(0xFF4A0002)

val GeminiBackgroundLight = Color(0xFFFAFDFA) // Off-white green
val GeminiOnBackgroundLight = Color(0xFF1A1C19)
val GeminiSurfaceLight = Color(0xFFFAFDFA)
val GeminiOnSurfaceLight = Color(0xFF1A1C19)
val GeminiSurfaceVariantLight = Color(0xFFDFE4DD)
val GeminiOnSurfaceVariantLight = Color(0xFF434840)

// Signature glow gradient brush (Olive, Orange, Red)
val GeminiBlue = Color(0xFF9CCC65) // Olive
val GeminiPurple = Color(0xFFFF9800) // Orange
val GeminiPink = Color(0xFFF44336) // Red
val GeminiGlowBrush = Brush.linearGradient(
    colors = listOf(GeminiBlue, GeminiPurple, GeminiPink)
)

// Premium UI Specific Colors
val DialerCallGreen = Color(0xFF4CAF50) // Bright Green
val DialerEndRed = Color(0xFFF44336) // Bright Red
val GlassBg = Color(0x1AFFFFFF) // White glass
val GlassBorder = Color(0x33FFFFFF) // White border