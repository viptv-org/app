package org.viptv.app.features.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.viptv.app.theme.Palette

// The two-pane detail pane: T3Settings draws 40 padding under a 26/700 title, D3SetGeneral 28 / 48 under a 24/700 one.
internal data class SettingsPaneStyle(val horizontal: Dp, val vertical: Dp, val titleSize: TextUnit, val titleLineHeight: TextUnit)

internal val LocalSettingsPaneStyle = compositionLocalOf<SettingsPaneStyle?> { null }

internal fun SettingsLayout.paneStyle(): SettingsPaneStyle = when (this) {
    SettingsLayout.Tablet -> SettingsPaneStyle(horizontal = 40.dp, vertical = 40.dp, titleSize = 26.sp, titleLineHeight = 31.sp)
    else -> SettingsPaneStyle(horizontal = 28.dp, vertical = 48.dp, titleSize = 24.sp, titleLineHeight = 28.sp)
}

@Composable
internal fun settingsPanePadding(compact: Boolean, fallback: Dp = if (compact) 16.dp else 32.dp): PaddingValues =
    LocalSettingsPaneStyle.current?.let { PaddingValues(horizontal = it.horizontal, vertical = it.vertical) } ?: PaddingValues(fallback)

@Composable
internal fun settingsPaneHorizontalPadding(compact: Boolean, fallback: Dp = if (compact) 16.dp else 32.dp): Dp =
    LocalSettingsPaneStyle.current?.horizontal ?: fallback

@Composable
internal fun settingsPaneVerticalPadding(compact: Boolean, fallback: Dp = if (compact) 16.dp else 32.dp): Dp =
    LocalSettingsPaneStyle.current?.vertical ?: fallback

@Composable
internal fun SettingsPaneTitle(text: String, modifier: Modifier = Modifier, color: Color = Palette.text) {
    val pane = LocalSettingsPaneStyle.current
    Text(text, modifier, style = if (pane == null) TextStyle(fontSize = 28.sp, lineHeight = 34.sp) else
        TextStyle(fontSize = pane.titleSize, lineHeight = pane.titleLineHeight), fontWeight = FontWeight.Bold, color = color)
}
