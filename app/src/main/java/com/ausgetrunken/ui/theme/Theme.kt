package com.ausgetrunken.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = WineRed,
    secondary = DarkBrownishTan,
    tertiary = Gold,
    background = SlateBlack,
    surface = Surface,
    surfaceVariant = SurfaceVariant,
    onPrimary = OnSurface,
    onSecondary = OnSurface,
    onTertiary = SlateBlack,
    onBackground = OnSurface,
    onSurface = OnSurface,
    onSurfaceVariant = OnSurfaceVariant
)

private val LightColorScheme = lightColorScheme(
    primary = WineRed,
    secondary = DarkBrownishTan,
    tertiary = Gold,
    background = OnSurface,
    surface = Color.White,
    surfaceVariant = Color(0xFFF5F5F5),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = SlateBlack,
    onBackground = SlateBlack,
    onSurface = SlateBlack,
    onSurfaceVariant = Color(0xFF666666)
)

@Composable
fun AusgetrunkenTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) {
        DarkColorScheme
    } else {
        LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}