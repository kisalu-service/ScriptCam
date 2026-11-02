package com.scriptcam.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Palette « studio nuit » : fonds bleu nuit, texte blanc, accent menthe discret.
val Ink900 = Color(0xFF070B14)
val Ink800 = Color(0xFF0C1220)
val Ink700 = Color(0xFF141C2E)
val Ink600 = Color(0xFF1E283D)
val Accent = Color(0xFF3DDC97)
val AccentDark = Color(0xFF1F8F63)
val RecRed = Color(0xFFFF4D5E)
val TextPrimary = Color(0xFFF5F7FA)
val TextSecondary = Color(0xFF9AA6BD)
val Warning = Color(0xFFFFB020)

private val ScriptCamColors = darkColorScheme(
    primary = Accent,
    onPrimary = Ink900,
    primaryContainer = AccentDark,
    onPrimaryContainer = TextPrimary,
    secondary = Color(0xFF6FA8FF),
    onSecondary = Ink900,
    tertiary = Warning,
    background = Ink900,
    onBackground = TextPrimary,
    surface = Ink800,
    onSurface = TextPrimary,
    surfaceVariant = Ink700,
    onSurfaceVariant = TextSecondary,
    outline = Ink600,
    error = RecRed,
    onError = Color.White
)

@Composable
fun ScriptCamTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
        }
    }
    MaterialTheme(
        colorScheme = ScriptCamColors,
        typography = ScriptCamTypography,
        content = content
    )
}
