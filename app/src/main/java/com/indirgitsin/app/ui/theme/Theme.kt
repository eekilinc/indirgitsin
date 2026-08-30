package com.indirgitsin.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private fun ytDarkColors(primary: Color, primaryDark: Color) = darkColorScheme(
    primary = primary,
    onPrimary = Color.White,
    primaryContainer = primaryDark,
    background = YtBackground,
    onBackground = YtTextPrimary,
    surface = YtBackground,
    onSurface = YtTextPrimary,
    surfaceVariant = YtSurfaceVariant,
    onSurfaceVariant = YtTextSecondary,
    surfaceContainer = YtSurface,
    surfaceContainerHighest = YtElevated,
    outline = YtBorder,
    outlineVariant = Color(0xFF3F3F3F),
    secondary = YtBlue,
    onSecondary = Color.White,
    error = Color(0xFFFF4D6A),
    scrim = Color.Black
)

private fun ytLightColors(primary: Color) = lightColorScheme(
    primary = primary,
    onPrimary = Color.White,
    background = Color(0xFFF9F9F9),
    onBackground = Color(0xFF0F0F0F),
    surface = Color.White,
    onSurface = Color(0xFF0F0F0F),
    surfaceVariant = Color(0xFFF2F2F2),
    onSurfaceVariant = Color(0xFF606060),
    outline = Color(0xFFE5E5E5),
    secondary = YtBlue
)

@Composable
fun IndirGitsinTheme(
    darkTheme: Boolean = true,
    appColor: String = "red",
    content: @Composable () -> Unit
) {
    val c = AppColor.fromKey(appColor)
    val colors = if (darkTheme) ytDarkColors(c.primary, c.primaryDark) else ytLightColors(c.primary)
    MaterialTheme(
        colorScheme = colors,
        typography = Typography,
        content = content
    )
}
