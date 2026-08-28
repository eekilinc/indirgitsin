package com.indirgitsin.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val PremiumDarkColors = darkColorScheme(
    primary = PremiumRed,
    onPrimary = Color.White,
    primaryContainer = PremiumRedDark,
    onPrimaryContainer = Color.White,
    secondary = PremiumGold,
    onSecondary = Color(0xFF1A1A1A),
    background = BgDark,
    onBackground = TextPrimaryDark,
    surface = BgDarkSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = BgDarkCard,
    onSurfaceVariant = TextSecondaryDark,
    surfaceContainer = BgDarkCard,
    surfaceContainerHighest = BgDarkElevated,
    outline = BorderDark,
    outlineVariant = Color(0xFF3A3A44),
    error = Color(0xFFFF4D6A),
    errorContainer = Color(0xFF4A0F1A),
    scrim = Color.Black
)

private val PremiumLightColors = lightColorScheme(
    primary = PremiumRed,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDAD6),
    background = BgLight,
    onBackground = Color(0xFF1A1A1A),
    surface = SurfaceLight,
    onSurface = Color(0xFF1A1A1A),
    surfaceVariant = CardLight,
    onSurfaceVariant = Color(0xFF71717A),
    outline = BorderLight,
    outlineVariant = Color(0xFFE4E4E7),
    error = Color(0xFFBA1A1A),
    scrim = Color.Black
)

@Composable
fun IndirGitsinTheme(
    darkTheme: Boolean = true, // Premium: varsayılan koyu
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) PremiumDarkColors else PremiumLightColors
    MaterialTheme(
        colorScheme = colors,
        typography = Typography,
        content = content
    )
}
