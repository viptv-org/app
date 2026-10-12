package org.viptv.app.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

internal val ViptvColors = darkColorScheme(
    primary = Palette.text, onPrimary = Palette.background,
    primaryContainer = Palette.raised, onPrimaryContainer = Palette.text,
    secondary = Palette.accent, onSecondary = Palette.background,
    secondaryContainer = Palette.raised, onSecondaryContainer = Palette.text,
    tertiary = Palette.text, onTertiary = Palette.background,
    tertiaryContainer = Palette.raised, onTertiaryContainer = Palette.text,
    background = Palette.background, onBackground = Palette.text,
    surface = Palette.surface, onSurface = Palette.text,
    surfaceVariant = Palette.raised, onSurfaceVariant = Palette.secondary,
    surfaceTint = Color.Transparent,
    surfaceBright = Palette.raised, surfaceDim = Palette.background,
    surfaceContainerLowest = Palette.background, surfaceContainerLow = Palette.surface,
    surfaceContainer = Palette.surface, surfaceContainerHigh = Palette.raised,
    surfaceContainerHighest = Color(0xFF303033),
    error = Palette.danger, onError = Palette.background,
    errorContainer = Color(0xFF382321), onErrorContainer = Palette.text,
    outline = Color(0xFF5C5B5A), outlineVariant = Color(0xFF303033),
    inverseSurface = Palette.text, inverseOnSurface = Palette.background, inversePrimary = Palette.background,
)
