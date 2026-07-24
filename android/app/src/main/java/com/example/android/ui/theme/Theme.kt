package com.example.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColors = darkColorScheme(
    primary = BrandGreen,
    onPrimary = DarkBackground,
    primaryContainer = DarkGreenContainer,
    onPrimaryContainer = DarkOnGreenContainer,
    secondary = DarkOnGreenContainer,
    onSecondary = DarkBackground,
    tertiary = PremiumGold,
    onTertiary = DarkBackground,
    tertiaryContainer = DarkPremiumContainer,
    onTertiaryContainer = DarkOnPremiumContainer,
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    error = ErrorColor
)

private val LightColors = lightColorScheme(
    primary = BrandGreenPressed,
    onPrimary = White,
    primaryContainer = LightGreenContainer,
    onPrimaryContainer = LightOnGreenContainer,
    secondary = BrandGreenPressed,
    onSecondary = White,
    tertiary = PremiumGold,
    onTertiary = LightOnSurface,
    tertiaryContainer = LightPremiumContainer,
    onTertiaryContainer = LightOnPremiumContainer,
    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
    error = ErrorColor
)

enum class ThemeMode { System, Light, Dark }

@Composable
fun SpotifyTheme(
    themeMode: ThemeMode = ThemeMode.System,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
