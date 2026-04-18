package com.basitce.hapticbeats.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = Copper,
    secondary = Amber,
    tertiary = Slate,
    background = Graphite950,
    surface = Graphite900,
    surfaceVariant = Graphite800,
    onPrimary = Graphite950,
    onSecondary = Graphite950,
    onTertiary = White,
    onBackground = Mist,
    onSurface = Mist,
    onSurfaceVariant = Bone,
    error = SignalRed,
    onError = White
)

private val LightColorScheme = lightColorScheme(
    primary = Copper,
    secondary = Amber,
    tertiary = Slate,
    background = Mist,
    surface = White,
    surfaceVariant = Bone.copy(alpha = 0.42f),
    onPrimary = White,
    onSecondary = Graphite950,
    onTertiary = White,
    onBackground = Graphite950,
    onSurface = Graphite900,
    onSurfaceVariant = Graphite700,
    error = SignalRed,
    onError = White
)

private val AmoledColorScheme = darkColorScheme(
    primary = Copper,
    secondary = Amber,
    tertiary = Slate,
    background = Black,
    surface = Black,
    surfaceVariant = Graphite900,
    onPrimary = Black,
    onSecondary = Graphite950,
    onTertiary = White,
    onBackground = Mist,
    onSurface = Mist,
    onSurfaceVariant = Bone,
    error = SignalRed,
    onError = White
)

enum class ThemeMode {
    SYSTEM, LIGHT, DARK, AMOLED
}

@Composable
fun HapticBeatsTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK, ThemeMode.AMOLED -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val colorScheme = when {
        themeMode == ThemeMode.AMOLED -> AmoledColorScheme
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
