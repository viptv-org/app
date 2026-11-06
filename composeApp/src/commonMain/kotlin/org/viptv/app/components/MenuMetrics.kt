package org.viptv.app.components

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// TV menus are drawn at the TV geometry (MENU-TV-001) whatever their pointer layout size is.
@Immutable
internal class MenuMetrics(val television: Boolean, val scale: Float) {
    val width: Dp = if (television) TvMenuWidth * scale else PopoverMenuWidth * scale
    val rowMinHeight: Dp = if (television) 72.dp * scale else 44.dp * scale
    val rowPaddingH: Dp = if (television) 24.dp * scale else 10.dp * scale
    val rowPaddingV: Dp = if (television) 12.dp * scale else 10.dp * scale
    val iconSize: Dp = if (television) 26.dp * scale else 18.dp * scale
    val iconGap: Dp = if (television) 16.dp * scale else 12.dp * scale
    val textSize: TextUnit = if (television) (26 * scale).sp else (15 * scale).sp
    val lineHeight: TextUnit = if (television) (32 * scale).sp else TextUnit.Unspecified
    val rowRadius: Dp = if (television) 14.dp * scale else 10.dp * scale
    val menuRadius: Dp = if (television) 20.dp * scale else 16.dp * scale
    val outerPadding: Dp = if (television) 8.dp * scale else 6.dp * scale
    val dividerHorizontal: Dp = if (television) 16.dp * scale else 12.dp * scale
    val dividerVertical: Dp = if (television) 8.dp * scale else 6.dp * scale
    val shadow: Dp = if (television) 40.dp * scale else 20.dp * scale
    // tvFocusOutline scales these itself.
    val focusRing: Dp = 2.dp
    val focusGap: Dp = if (television) 3.dp else 2.dp
}

internal val TvMenuWidth = 600.dp

internal val LocalMenuMetrics = staticCompositionLocalOf { MenuMetrics(television = false, scale = 1f) }