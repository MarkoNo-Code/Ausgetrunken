package com.ausgetrunken.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = WineRed,
    onPrimary = Color.White,
    primaryContainer = WineRedLight,
    onPrimaryContainer = Color.White,
    
    secondary = WineAccent,
    onSecondary = Color.White,
    secondaryContainer = WineGold,
    onSecondaryContainer = Color.Black,
    
    tertiary = WineGold,
    onTertiary = Color.Black,
    
    background = DarkBackground,
    onBackground = DarkOnSurface,
    
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    
    outline = DarkOutline,
    error = ErrorRed,
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = WineRed,
    onPrimary = Color.White,
    primaryContainer = WineRedLight,
    onPrimaryContainer = Color.White,
    
    secondary = WineAccent,
    onSecondary = Color.White,
    secondaryContainer = WineGold,
    onSecondaryContainer = Color.Black,
    
    tertiary = WineGold,
    onTertiary = Color.Black,
    
    background = LightBackground,
    onBackground = LightOnSurface,
    
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    
    outline = LightOutline,
    error = ErrorRed,
    onError = Color.White
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