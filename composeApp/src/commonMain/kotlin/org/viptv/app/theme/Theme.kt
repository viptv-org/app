package org.viptv.app.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import org.jetbrains.compose.resources.Font
import org.viptv.app.resources.Res
import org.viptv.app.resources.onest_400
import org.viptv.app.resources.onest_500
import org.viptv.app.resources.onest_600
import org.viptv.app.resources.onest_700

object Palette {
    val background = Color(0xFF0B0B0C)
    val surface = Color(0xFF161618)
    val raised = Color(0xFF212124)
    val text = Color(0xFFF4F2EE)
    val secondary = Color(0xFFB6B4AF)
    val accent = Color(0xFFF5C542)
    val danger = Color(0xFFFF7A6E)
}

@Composable
fun ViptvTheme(content: @Composable () -> Unit) {
    val font = FontFamily(Font(Res.font.onest_400, FontWeight.Normal), Font(Res.font.onest_500, FontWeight.Medium), Font(Res.font.onest_600, FontWeight.SemiBold), Font(Res.font.onest_700, FontWeight.Bold))
    val base = Typography()
    MaterialTheme(
        colorScheme = darkColorScheme(primary = Palette.text, onPrimary = Palette.background, background = Palette.background, surface = Palette.surface, onSurface = Palette.text, secondary = Palette.accent, error = Palette.danger),
        typography = Typography(
            bodyLarge = base.bodyLarge.copy(fontFamily = font), bodyMedium = base.bodyMedium.copy(fontFamily = font), bodySmall = base.bodySmall.copy(fontFamily = font),
            titleLarge = base.titleLarge.copy(fontFamily = font), titleMedium = base.titleMedium.copy(fontFamily = font), titleSmall = base.titleSmall.copy(fontFamily = font),
            headlineLarge = base.headlineLarge.copy(fontFamily = font), headlineMedium = base.headlineMedium.copy(fontFamily = font),
            labelLarge = base.labelLarge.copy(fontFamily = font), labelMedium = base.labelMedium.copy(fontFamily = font), labelSmall = base.labelSmall.copy(fontFamily = font),
        ), content = { CompositionLocalProvider(LocalContentColor provides Palette.text, content = content) },
    )
}
