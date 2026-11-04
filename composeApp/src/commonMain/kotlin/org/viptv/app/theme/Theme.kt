package org.viptv.app.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp

object Palette {
    val background = Color(0xFF0B0B0C)
    val surface = Color(0xFF161618)
    val raised = Color(0xFF212124)
    val text = Color(0xFFF4F2EE)
    val body = Color(0xFFDAD8D3)
    val secondary = Color(0xFFB6B4AF)
    val tertiary = Color(0xFF8F8D89)
    val accent: Color
        @Composable get() = LocalAccent.current
    val danger = Color(0xFFFF7A6E)
    val segmentIntro = Color(0xFF9AC7FF)
    val segmentRecap = Color(0xFFB79CFF)
    val segmentCredits = Color(0xFF8CE0A8)
}

@Composable
fun ViptvTheme(television: Boolean = false,
    // Desktop and web only: hover visuals follow the mouse, never on TV.
    pointer: Boolean = false, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = viptvColorScheme(),
        typography = viptvTypography(), content = {
            org.viptv.app.components.TvFocusEnvironment(television) {
                val density = LocalDensity.current
                val textScale = LocalTvTextScale.current
                val scaledDensity = if (television && textScale != 1f) Density(density.density, density.fontScale * textScale) else density
                CompositionLocalProvider(LocalContentColor provides Palette.text,
                    LocalDensity provides scaledDensity,
                    org.viptv.app.components.LocalPointerHover provides (pointer && !television),
                    org.viptv.app.components.LocalCardOverflow provides (!pointer && !television)) {
                    if (television) CompositionLocalProvider(LocalTextStyle provides LocalTextStyle.current.copy(letterSpacing = 0.sp), content = content)
                    else content()
                }
            }
        },
    )
}
