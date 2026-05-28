package com.mksoft.phone.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.foundation.isSystemInDarkTheme


// ─────────────────────────────────────────────────────────────
//  Dark color scheme — the primary app experience
// ─────────────────────────────────────────────────────────────
private val DarkColorScheme = darkColorScheme(
    primary              = GeminiPrimaryDark,
    onPrimary            = GeminiOnPrimaryDark,
    primaryContainer     = GeminiPrimaryContainerDark,
    onPrimaryContainer   = GeminiOnPrimaryContainerDark,
    secondary            = GeminiSecondaryDark,
    onSecondary          = GeminiOnSecondaryDark,
    secondaryContainer   = GeminiSecondaryContainerDark,
    onSecondaryContainer = GeminiOnSecondaryContainerDark,
    tertiary             = GeminiTertiaryDark,
    onTertiary           = GeminiOnTertiaryDark,
    tertiaryContainer    = GeminiTertiaryContainerDark,
    onTertiaryContainer  = GeminiOnTertiaryContainerDark,
    background           = GeminiBackgroundDark,
    onBackground         = GeminiOnBackgroundDark,
    surface              = GeminiSurfaceDark,
    onSurface            = GeminiOnSurfaceDark,
    surfaceVariant       = GeminiSurfaceVariantDark,
    onSurfaceVariant     = GeminiOnSurfaceVariantDark,
    error                = GeminiTertiaryDark,
    onError              = GeminiOnTertiaryDark
)

// ─────────────────────────────────────────────────────────────
//  Light color scheme — available as fallback
// ─────────────────────────────────────────────────────────────
private val LightColorScheme = lightColorScheme(
    primary              = GeminiPrimaryLight,
    onPrimary            = GeminiOnPrimaryLight,
    primaryContainer     = GeminiPrimaryContainerLight,
    onPrimaryContainer   = GeminiOnPrimaryContainerLight,
    secondary            = GeminiSecondaryLight,
    onSecondary          = GeminiOnSecondaryLight,
    secondaryContainer   = GeminiSecondaryContainerLight,
    onSecondaryContainer = GeminiOnSecondaryContainerLight,
    tertiary             = GeminiTertiaryLight,
    onTertiary           = GeminiOnTertiaryLight,
    tertiaryContainer    = GeminiTertiaryContainerLight,
    onTertiaryContainer  = GeminiOnTertiaryContainerLight,
    background           = GeminiBackgroundLight,
    onBackground         = GeminiOnBackgroundLight,
    surface              = GeminiSurfaceLight,
    onSurface            = GeminiOnSurfaceLight,
    surfaceVariant       = GeminiSurfaceVariantLight,
    onSurfaceVariant     = GeminiOnSurfaceVariantLight
)

@Composable
fun VoIPAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                // Transparent status bar — content draws edge-to-edge
                window.statusBarColor = android.graphics.Color.TRANSPARENT
                window.navigationBarColor = colorScheme.background.toArgb()
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = !darkTheme
                insetsController.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = Typography,
        content     = content
    )
}
