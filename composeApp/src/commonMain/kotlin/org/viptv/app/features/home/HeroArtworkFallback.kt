package org.viptv.app.features.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import org.viptv.app.theme.Palette

@Composable
internal fun HeroArtworkFallback(modifier: Modifier) {
    Box(modifier.background(Brush.verticalGradient(listOf(Palette.raised, Palette.background)))) {
        Box(Modifier.fillMaxSize().background(Brush.radialGradient(
            colors = listOf(Palette.accent.copy(alpha = .16f), Color.Transparent),
            center = Offset(0.72f, 0.30f),
            radius = 1400f,
        )))
        Box(Modifier.fillMaxSize().background(Brush.radialGradient(
            colors = listOf(Palette.text.copy(alpha = .06f), Color.Transparent),
            center = Offset(0.18f, 0.85f),
            radius = 900f,
        )))
    }
}
