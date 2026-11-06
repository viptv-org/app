package org.viptv.app.features.settings

import org.viptv.app.components.FocusLazyColumn as LazyColumn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.viptv.app.connection.Profile
import org.viptv.app.features.entry.ProfileAvatar
import org.viptv.app.components.textChromeWidth
import org.viptv.app.theme.Palette
import org.viptv.app.components.pointerClickable

// T3Settings (tablet) and D3SetGeneral (desktop) draw different rails: 360 / 40 with 15 labels against 260 / 28 with 14.
internal data class SettingsRailMetrics(
    val width: Dp, val horizontal: Dp, val vertical: Dp, val titleSize: TextUnit, val avatar: Dp,
    val rowHorizontal: Dp, val rowVertical: Dp, val rowRadius: Dp, val rowLabelSize: TextUnit,
    val profileHorizontal: Dp, val profileVertical: Dp, val profileRadius: Dp,
) {
    val currentRow = Color.White.copy(alpha = .10f)
}

internal fun SettingsLayout.railMetrics(): SettingsRailMetrics = when (this) {
    SettingsLayout.Tablet -> SettingsRailMetrics(width = 360.dp, horizontal = 40.dp, vertical = 16.dp, titleSize = 26.sp,
        avatar = 44.dp, rowHorizontal = 10.dp, rowVertical = 12.dp, rowRadius = 14.dp, rowLabelSize = 15.sp,
        profileHorizontal = 8.dp, profileVertical = 16.dp, profileRadius = 12.dp)
    else -> SettingsRailMetrics(width = 260.dp, horizontal = 28.dp, vertical = 16.dp, titleSize = 24.sp,
        avatar = 40.dp, rowHorizontal = 8.dp, rowVertical = 12.dp, rowRadius = 8.dp, rowLabelSize = 14.sp,
        profileHorizontal = 8.dp, profileVertical = 12.dp, profileRadius = 8.dp)
}

@Composable
internal fun SettingsSidebar(selected: String, profile: Profile?, layout: SettingsLayout, sections: List<SettingsSection>,
    local: Boolean, profiles: () -> Unit, select: (String) -> Unit, signOut: () -> Unit) {
    val metrics = layout.railMetrics()
    LazyColumn(Modifier.width(textChromeWidth(metrics.width)).fillMaxHeight().background(Palette.surface),
        contentPadding = PaddingValues(horizontal = metrics.horizontal, vertical = metrics.vertical),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text("Settings", Modifier.padding(vertical = 12.dp), fontSize = metrics.titleSize, fontWeight = FontWeight.Bold)
        }
        item {
            Row(Modifier.fillMaxWidth().pointerClickable(onClick = profiles, shape = RoundedCornerShape(metrics.profileRadius))
                .padding(horizontal = metrics.profileHorizontal, vertical = metrics.profileVertical),
                horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                ProfileAvatar(profile, metrics.avatar, 10.dp)
                Column {
                    Text(profile?.name ?: "Profile", fontSize = metrics.rowLabelSize, fontWeight = FontWeight.SemiBold)
                    Text("Switch profile", fontSize = 12.sp, color = Palette.secondary)
                }
            }
        }
        sections.forEach { section ->
            item(key = section.key) { SettingsGroupLabel(section.group.label, layout) }
            section.rows.forEach { row ->
                val label = if (row == SettingsRow.SignOut) signOutLabel(local) else row.label
                item(key = row.key) {
                    Surface(color = if (row.key == selected) metrics.currentRow else Palette.surface,
                        shape = RoundedCornerShape(metrics.rowRadius)) {
                        Row(Modifier.fillMaxWidth().pointerClickable(shape = RoundedCornerShape(metrics.rowRadius)) {
                            if (row == SettingsRow.SignOut) signOut() else select(row.key)
                        }.padding(horizontal = metrics.rowHorizontal, vertical = metrics.rowVertical),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            SettingsRailIcon(row, Modifier.size(20.dp))
                            Text(label, fontSize = metrics.rowLabelSize, fontWeight = FontWeight.SemiBold,
                                color = if (row.dangerous) Palette.danger else Palette.text)
                        }
                    }
                }
            }
        }
    }
}
