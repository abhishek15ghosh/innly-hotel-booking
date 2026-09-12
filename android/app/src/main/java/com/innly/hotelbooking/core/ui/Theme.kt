package com.innly.hotelbooking.core.ui

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = EmeraldPrimary,
    onPrimary = EmeraldOnPrimary,
    primaryContainer = EmeraldContainer,
    onPrimaryContainer = EmeraldOnContainer,
    secondary = EmeraldLight,
    onSecondary = EmeraldOnPrimary,
    secondaryContainer = WarmIvorySurfaceVariant,
    onSecondaryContainer = EmeraldPrimary,
    tertiary = ChampagneGold,
    onTertiary = ChampagneGoldOnContainer,
    tertiaryContainer = ChampagneGoldContainer,
    onTertiaryContainer = ChampagneGoldOnContainer,
    background = WarmIvoryBackground,
    onBackground = CharcoalText,
    surface = WarmIvorySurface,
    onSurface = CharcoalText,
    surfaceVariant = WarmIvorySurfaceVariant,
    onSurfaceVariant = CharcoalTextSecondary,
    outline = WarmIvoryBorder,
    error = StatusErrorRed,
    errorContainer = StatusErrorContainer,
    onErrorContainer = StatusErrorRed,
)

private val DarkColorScheme = darkColorScheme(
    primary = EmeraldDarkPrimary,
    onPrimary = EmeraldDarkOnPrimary,
    primaryContainer = EmeraldDarkContainer,
    onPrimaryContainer = EmeraldDarkOnContainer,
    secondary = EmeraldDarkPrimary,
    onSecondary = EmeraldDarkOnPrimary,
    secondaryContainer = CharcoalDarkSurfaceVariant,
    onSecondaryContainer = EmeraldDarkPrimary,
    tertiary = ChampagneGoldDark,
    onTertiary = ChampagneGoldDarkContainer,
    tertiaryContainer = ChampagneGoldDarkContainer,
    onTertiaryContainer = ChampagneGoldDark,
    background = CharcoalDarkBackground,
    onBackground = CharcoalTextDark,
    surface = CharcoalDarkSurface,
    onSurface = CharcoalTextDark,
    surfaceVariant = CharcoalDarkSurfaceVariant,
    onSurfaceVariant = CharcoalTextSecondaryDark,
    outline = CharcoalDarkBorder,
    error = StatusErrorRed,
    errorContainer = androidx.compose.ui.graphics.Color(0xFF3B1818),
    onErrorContainer = androidx.compose.ui.graphics.Color(0xFFFFB4AB),
)

@Composable
fun InnlyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = colorScheme.surface.toArgb()
                window.navigationBarColor = colorScheme.surface.toArgb()
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = !darkTheme
                insetsController.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(
        LocalSpacing provides Spacing(),
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = Shapes,
            content = content,
        )
    }
}
