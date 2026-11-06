package org.viptv.app.features.settings

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import org.viptv.app.theme.Palette

internal const val settingsValueWeight = 0.75f

@Composable
internal fun RowScope.SettingsRowValue(value: String) {
    Text(value, Modifier.weight(settingsValueWeight, fill = false), color = Palette.secondary, fontSize = 14.sp,
        maxLines = 1, overflow = TextOverflow.Ellipsis)
}