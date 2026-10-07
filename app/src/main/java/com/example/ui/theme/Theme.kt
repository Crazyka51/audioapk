package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ForensicDarkColorScheme = darkColorScheme(
    primary = ForensicCyan,
    onPrimary = Color(0xFF00363D),
    primaryContainer = Color(0xFF004F59),
    onPrimaryContainer = ForensicCyan,
    
    secondary = ForensicNeonGreen,
    onSecondary = Color(0xFF00391C),
    secondaryContainer = Color(0xFF00532B),
    onSecondaryContainer = ForensicNeonGreen,
    
    tertiary = ForensicOrange,
    onTertiary = Color(0xFF5D1200),
    tertiaryContainer = Color(0xFF812000),
    onTertiaryContainer = ForensicOrange,
    
    background = ForensicDarkBackground,
    onBackground = ForensicTextPrimary,
    surface = ForensicSurface,
    onSurface = ForensicTextPrimary,
    surfaceVariant = ForensicSurfaceVariant,
    onSurfaceVariant = ForensicTextSecondary,
    
    outline = ForensicCardBorder,
    outlineVariant = Color(0xFF202430),
    error = ForensicRed,
    onError = Color.White
)

@Composable
fun AudioDetectiveTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // Forensic app strictly uses high-contrast Dark theme for optimal spectrogram visibility
    MaterialTheme(
        colorScheme = ForensicDarkColorScheme,
        typography = Typography,
        content = content
    )
}
