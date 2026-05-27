package com.example.demo.core

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val darkColorScheme = darkColorScheme(
        primary = Color(0xFFB0C4FF),
        onPrimary = Color(0xFF1A237E),
        primaryContainer = Color(0xFF3F51B5),
        onPrimaryContainer = Color(0xFFE8EAF6),
        secondary = Color(0xFF9FA8DA),
        onSecondary = Color(0xFF1A1A1A),
        secondaryContainer = Color(0xFF3F51B5),
        onSecondaryContainer = Color(0xFFE8EAF6),
        tertiary = Color(0xFF80CBC4),
        onTertiary = Color(0xFF003635),
        tertiaryContainer = Color(0xFF004D40),
        onTertiaryContainer = Color(0xFFE0F2F1),
        error = Color(0xFFF2B8B5),
        onError = Color(0xFF601410),
        errorContainer = Color(0xFF8C1D18),
        onErrorContainer = Color(0xFFF9DEDC),
        background = Color(0xFF121212),
        onBackground = Color(0xFFE1E1E1),
        surface = Color(0xFF121212),
        onSurface = Color(0xFFE1E1E1),
        surfaceVariant = Color(0xFF2C2C2C),
        onSurfaceVariant = Color(0xFFBDBDBD),
        outline = Color(0xFF8E9199),
        outlineVariant = Color(0xFF44474E),
    )

    MaterialTheme(
        colorScheme = darkColorScheme,
        content = content
    )
}