package org.viptv.app.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Copy over artwork keeps 4.5:1 against a white frame, so every stop that can sit under text leaves the ground at .7 or more.
internal object Scrim {
    private fun ground(alpha: Float) = Palette.background.copy(alpha = alpha)

    val tvHeroWide = Brush.horizontalGradient(0f to ground(.95f), .46f to ground(.9f), .62f to ground(.55f), 1f to Color.Transparent)
    val tvHeroTall = Brush.verticalGradient(0f to ground(.3f), .45f to Color.Transparent, .8f to ground(.9f), 1f to Palette.background)
    val tvTopBar = Brush.verticalGradient(0f to ground(.94f), .72f to ground(.82f), 1f to Color.Transparent)

    val phoneHero = Brush.verticalGradient(0f to Color.Transparent, .45f to Color.Transparent, .72f to ground(.84f), 1f to ground(.98f))

    val tabletHeroWide = Brush.horizontalGradient(0f to ground(.93f), .5f to ground(.84f), .72f to Color.Transparent)
    val tabletHeroTall = Brush.verticalGradient(0f to Color.Transparent, .5f to Color.Transparent, .76f to ground(.84f), 1f to ground(.98f))

    val pageBackdrop = Brush.verticalGradient(0f to ground(.3f), .55f to ground(.78f), 1f to Palette.background)
}