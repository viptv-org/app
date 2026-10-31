package org.viptv.app.features.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.viptv.app.theme.Palette

@Composable
internal fun SettingsGroupLabel(label: String, layout: SettingsLayout) {
    Text(label.uppercase(), Modifier.padding(start = layout.groupLabelHorizontal, end = layout.groupLabelHorizontal,
            top = layout.groupLabelAbove, bottom = layout.groupLabelBelow)
            .semantics(mergeDescendants = true) { heading() },
        color = Palette.tertiary, fontSize = layout.groupLabelSize, lineHeight = (layout.groupLabelSize.value * 1.4f).sp,
        letterSpacing = (layout.groupLabelSize.value * layout.groupLabelTracking).sp, fontWeight = FontWeight.Bold)
}

@Composable
internal fun SettingsRailIcon(row: SettingsRow, modifier: Modifier = Modifier) {
    Icon(row.icon, null, modifier, tint = if (row.dangerous) Palette.danger else Palette.text)
}
