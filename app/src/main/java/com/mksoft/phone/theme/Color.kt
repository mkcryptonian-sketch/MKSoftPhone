package com.mksoft.phone.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush

// ─────────────────────────────────────────────────────────────
//  Premium Dark Tech palette — Slate / Cyan / Indigo / Magenta
// ─────────────────────────────────────────────────────────────

// Dark Theme
val GeminiPrimaryDark          = Color(0xFF00E5FF) // Neon Cyan
val GeminiOnPrimaryDark        = Color(0xFF002B33)
val GeminiPrimaryContainerDark = Color(0xFF004D5C)
val GeminiOnPrimaryContainerDark = Color(0xFFB3F0FF)

val GeminiSecondaryDark          = Color(0xFF7986CB) // Soft Indigo
val GeminiOnSecondaryDark        = Color(0xFF1A1F6E)
val GeminiSecondaryContainerDark = Color(0xFF3D4FB5)
val GeminiOnSecondaryContainerDark = Color(0xFFDDE0FF)

val GeminiTertiaryDark          = Color(0xFFFF4081) // Vibrant Magenta-Pink
val GeminiOnTertiaryDark        = Color(0xFF5C0028)
val GeminiTertiaryContainerDark = Color(0xFF8C0044)
val GeminiOnTertiaryContainerDark = Color(0xFFFFD9E4)

val GeminiBackgroundDark      = Color(0xFF080C14) // Deep Navy Slate
val GeminiOnBackgroundDark    = Color(0xFFE8EDF5)
val GeminiSurfaceDark         = Color(0xFF0E1420) // Slightly lighter slate
val GeminiOnSurfaceDark       = Color(0xFFE8EDF5)
val GeminiSurfaceVariantDark  = Color(0xFF1C2438) // Card surface
val GeminiOnSurfaceVariantDark = Color(0xFF9BAAC4) // Muted text

// Light Theme (clean fallback — rarely shown)
val GeminiPrimaryLight          = Color(0xFF006D82)
val GeminiOnPrimaryLight        = Color(0xFFFFFFFF)
val GeminiPrimaryContainerLight = Color(0xFFB3F0FF)
val GeminiOnPrimaryContainerLight = Color(0xFF002B33)

val GeminiSecondaryLight          = Color(0xFF3D5AFE)
val GeminiOnSecondaryLight        = Color(0xFFFFFFFF)
val GeminiSecondaryContainerLight = Color(0xFFDDE0FF)
val GeminiOnSecondaryContainerLight = Color(0xFF1A1F6E)

val GeminiTertiaryLight          = Color(0xFFD81B60)
val GeminiOnTertiaryLight        = Color(0xFFFFFFFF)
val GeminiTertiaryContainerLight = Color(0xFFFFD9E4)
val GeminiOnTertiaryContainerLight = Color(0xFF5C0028)

val GeminiBackgroundLight      = Color(0xFFF4F6FB)
val GeminiOnBackgroundLight    = Color(0xFF0E1420)
val GeminiSurfaceLight         = Color(0xFFFFFFFF)
val GeminiOnSurfaceLight       = Color(0xFF0E1420)
val GeminiSurfaceVariantLight  = Color(0xFFDEE3EE)
val GeminiOnSurfaceVariantLight = Color(0xFF3D4A60)

// ─────────────────────────────────────────────────────────────
//  Signature Glow Gradient  (Cyan → Indigo → Magenta)
// ─────────────────────────────────────────────────────────────
val GeminiBlue   = Color(0xFF00E5FF) // Cyan
val GeminiPurple = Color(0xFF7986CB) // Indigo
val GeminiPink   = Color(0xFFFF4081) // Magenta
val GeminiGlowBrush = Brush.linearGradient(
    colors = listOf(GeminiBlue, GeminiPurple, GeminiPink)
)

// ─────────────────────────────────────────────────────────────
//  Semantic UI Colors
// ─────────────────────────────────────────────────────────────
val DialerCallGreen = Color(0xFF00E676) // Vivid green for "answer"
val DialerEndRed    = Color(0xFFFF1744) // Vivid red for "hang up"
val GlassBg         = Color(0x1AFFFFFF) // Frosted glass fill
val GlassBorder     = Color(0x33FFFFFF) // Frosted glass border

// ─────────────────────────────────────────────────────────────
//  Settings Redesign Accent
// ─────────────────────────────────────────────────────────────
val SettingsAccentPurple      = Color(0xFF534AB7)
val SettingsAccentPurpleLight = Color(0xFFEEEDFE)