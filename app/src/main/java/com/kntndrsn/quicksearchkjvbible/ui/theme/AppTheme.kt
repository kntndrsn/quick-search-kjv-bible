package com.rdp.biblequicksearchkjv.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary = Color(0xFF1E3A5F),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD6E3F8),
    onPrimaryContainer = Color(0xFF0A1E36),
    secondary = Color(0xFF516071),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD5E4F7),
    onSecondaryContainer = Color(0xFF0E1D2A),
    background = Color(0xFFFAF8F5),
    onBackground = Color(0xFF1B1B1D),
    surface = Color(0xFFFAF8F5),
    onSurface = Color(0xFF1B1B1D),
    surfaceVariant = Color(0xFFEAE7E1),
    onSurfaceVariant = Color(0xFF45474C),
    error = Color(0xFFB3261E)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA5C8FF),
    onPrimary = Color(0xFF00315F),
    primaryContainer = Color(0xFF1B4978),
    onPrimaryContainer = Color(0xFFD4E3FF),
    secondary = Color(0xFFBBC7DB),
    onSecondary = Color(0xFF253140),
    secondaryContainer = Color(0xFF2B3B4E),
    onSecondaryContainer = Color(0xFFD7E3F8),
    background = Color(0xFF121316),
    onBackground = Color(0xFFE4E2E6),
    surface = Color(0xFF121316),
    onSurface = Color(0xFFE4E2E6),
    surfaceVariant = Color(0xFF23262C),
    onSurfaceVariant = Color(0xFFC4C6D0),
    error = Color(0xFFFF897D)
)

@Composable
fun BibleQuickSearchTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !darkTheme
            insetsController.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}



