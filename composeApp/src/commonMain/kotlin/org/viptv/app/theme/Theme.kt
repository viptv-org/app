package org.viptv.app.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp

object Palette {
    val background = Color(0xFF0B0B0C)
    val surface = Color(0xFF161618)
    val raised = Color(0xFF212124)
    val text = Color(0xFFF4F2EE)
    val body = Color(0xFFDAD8D3)
    val secondary = Color(0xFFB6B4AF)
    val tertiary = Color(0xFF8F8D89)
    val accent = Color(0xFFF5C542)
    val danger = Color(0xFFFF7A6E)
}

@Composable
fun ViptvTheme(television: Boolean = false,
    /** Desktop and web: hover visuals follow the mouse (see `LocalPointerHover`). Never on TV. */
    pointer: Boolean = false, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ViptvColors,
        typography = viptvTypography(), content = {
            org.viptv.app.components.TvFocusEnvironment(television) {
                CompositionLocalProvider(LocalContentColor provides Palette.text,
                    org.viptv.app.components.LocalPointerHover provides (pointer && !television)) {
                    if (television) CompositionLocalProvider(LocalTextStyle provides LocalTextStyle.current.copy(letterSpacing = 0.sp), content = content)
                    else content()
                }
            }
        },
    )
}
