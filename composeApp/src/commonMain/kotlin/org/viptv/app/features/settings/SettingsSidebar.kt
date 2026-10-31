package org.viptv.app.features.settings

import org.viptv.app.components.FocusLazyColumn as LazyColumn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.viptv.app.connection.Profile
import org.viptv.app.features.entry.ProfileAvatar
import org.viptv.app.theme.Palette
import org.viptv.app.components.pointerClickable

@Composable
internal fun SettingsSidebar(selected: String, profile: Profile?, layout: SettingsLayout, sections: List<SettingsSection>,
    local: Boolean, profiles: () -> Unit, select: (String) -> Unit, signOut: () -> Unit) {
    LazyColumn(Modifier.width(240.dp).fillMaxHeight().background(Palette.surface), contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("Settings", Modifier.padding(vertical = 12.dp), fontSize = 24.sp, fontWeight = FontWeight.Bold) }
        item {
            Row(Modifier.fillMaxWidth().pointerClickable(onClick = profiles, shape = RoundedCornerShape(10.dp)).padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                ProfileAvatar(profile, 40.dp, 10.dp)
                Column { Text(profile?.name ?: "Profile", fontWeight = FontWeight.SemiBold); Text("Switch profile", fontSize = 12.sp, color = Palette.secondary) }
            }
        }
        sections.forEach { section ->
            item(key = section.key) { SettingsGroupLabel(section.group.label, layout) }
            section.rows.forEach { row ->
                val label = if (row == SettingsRow.SignOut) signOutLabel(local) else row.label
                item(key = row.key) {
                    Surface(color = if (row.key == selected) Palette.raised else Palette.surface, shape = RoundedCornerShape(10.dp)) {
                        Row(Modifier.fillMaxWidth().pointerClickable(shape = RoundedCornerShape(10.dp)) {
                            if (row == SettingsRow.SignOut) signOut() else select(row.key)
                        }.padding(horizontal = 12.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            SettingsRailIcon(row, Modifier.size(20.dp))
                            Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (row.dangerous) Palette.danger else Palette.text)
                        }
                    }
                }
            }
        }
    }
}
