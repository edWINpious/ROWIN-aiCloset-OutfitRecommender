package com.example.rowin_adlproject.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * ROWIN light theme. The app is light-mode by design (matching the Stitch
 * screens); a matching dark scheme is provided for system dark mode.
 */
private val RowinLight = lightColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = OnPrimaryContainer,
    secondary = Secondary,
    secondaryContainer = SecondaryContainer,
    error = Error,
    errorContainer = ErrorContainer,
    onErrorContainer = OnErrorContainer,
    background = Surface,
    onBackground = OnSurface,
    surface = Surface,
    onSurface = OnSurface,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = OnSurfaceVariant,
    surfaceContainerLowest = SurfaceContainerLowest,
    surfaceContainerLow = SurfaceContainerLow,
    surfaceContainer = SurfaceContainer,
    surfaceContainerHigh = SurfaceContainerHigh,
    surfaceContainerHighest = SurfaceContainerHighest,
    outline = Outline,
    outlineVariant = OutlineVariant,
)

private val RowinDark = darkColorScheme(
    primary = PrimaryFixedDim,
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = OnPrimaryContainer,
    background = Color(0xFF121613),
    onBackground = Color(0xFFE0E5DF),
    surface = Color(0xFF121613),
    onSurface = Color(0xFFE0E5DF),
    surfaceVariant = Color(0xFF414944),
    onSurfaceVariant = Color(0xFFC0C8C3),
    outline = Color(0xFF8A938D),
    outlineVariant = Color(0xFF414944),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

@Composable
fun RowinTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = RowinLight,
        typography = RowinTypography,
        content = content,
    )
}
