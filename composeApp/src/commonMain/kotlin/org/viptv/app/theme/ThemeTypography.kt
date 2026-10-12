package org.viptv.app.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import org.jetbrains.compose.resources.Font
import org.viptv.app.resources.*

@Composable
internal fun viptvTypography(): Typography {
    val font = FontFamily(Font(Res.font.onest_400, FontWeight.Normal), Font(Res.font.onest_500, FontWeight.Medium),
        Font(Res.font.onest_600, FontWeight.SemiBold), Font(Res.font.onest_700, FontWeight.Bold))
    val base = Typography()
    return Typography(
        displayLarge = base.displayLarge.copy(fontFamily = font), displayMedium = base.displayMedium.copy(fontFamily = font), displaySmall = base.displaySmall.copy(fontFamily = font),
        headlineLarge = base.headlineLarge.copy(fontFamily = font), headlineMedium = base.headlineMedium.copy(fontFamily = font), headlineSmall = base.headlineSmall.copy(fontFamily = font),
        titleLarge = base.titleLarge.copy(fontFamily = font), titleMedium = base.titleMedium.copy(fontFamily = font), titleSmall = base.titleSmall.copy(fontFamily = font),
        bodyLarge = base.bodyLarge.copy(fontFamily = font), bodyMedium = base.bodyMedium.copy(fontFamily = font), bodySmall = base.bodySmall.copy(fontFamily = font),
        labelLarge = base.labelLarge.copy(fontFamily = font), labelMedium = base.labelMedium.copy(fontFamily = font), labelSmall = base.labelSmall.copy(fontFamily = font),
    )
}
