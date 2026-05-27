package com.mksoft.phone.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = GeminiPrimaryDark,
    onPrimary = GeminiOnPrimaryDark,
    primaryContainer = GeminiPrimaryContainerDark,
    onPrimaryContainer = GeminiOnPrimaryContainerDark,
    secondary = GeminiSecondaryDark,
    onSecondary = GeminiOnSecondaryDark,
    tertiary = GeminiTertiaryDark,
    onTertiary = GeminiOnTertiaryDark,
    background = GeminiBackgroundDark,
    onBackground = GeminiOnBackgroundDark,
    surface = GeminiSurfaceDark,
    onSurface = GeminiOnSurfaceDark,
    surfaceVariant = GeminiSurfaceVariantDark,
    onSurfaceVariant = GeminiOnSurfaceVariantDark
)

private val LightColorScheme = lightColorScheme(
    primary = GeminiPrimaryLight,
    onPrimary = GeminiOnPrimaryLight,
    primaryContainer = GeminiPrimaryContainerLight,
    onPrimaryContainer = GeminiOnPrimaryContainerLight,
    secondary = GeminiSecondaryLight,
    onSecondary = GeminiOnSecondaryLight,
    tertiary = GeminiTertiaryLight,
    onTertiary = GeminiOnTertiaryLight,
    background = GeminiBackgroundLight,
    onBackground = GeminiOnBackgroundLight,
    surface = GeminiSurfaceLight,
    onSurface = GeminiOnSurfaceLight,
    surfaceVariant = GeminiSurfaceVariantLight,
    onSurfaceVariant = GeminiOnSurfaceVariantLight
)

@Composable
fun VoIPAppTheme(
    darkTheme: Boolean = false, // Force light theme by default
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = !darkTheme
                insetsController.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

