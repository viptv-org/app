package org.viptv.app.features.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
internal fun AccountSettingsSidebar(selected: String, profile: Profile?, sections: List<SettingsSection>, local: Boolean,
    profiles: () -> Unit, select: (String) -> Unit, signOut: () -> Unit) {
    Column(Modifier.width(240.dp).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("Settings", Modifier.padding(bottom = 14.dp), fontSize = 26.sp, lineHeight = 30.sp,
            letterSpacing = (-.52).sp, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth().pointerClickable(onClick = profiles, shape = RoundedCornerShape(8.dp))
            .padding(end = 12.dp, top = 8.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically) {
            ProfileAvatar(profile, 40.dp, 10.dp)
            Column { Text(profile?.name ?: "Profile", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text("Switch profile", fontSize = 12.sp, color = Palette.secondary) }
        }
        sections.forEach { section ->
            SettingsGroupLabel(section.group.label, SettingsLayout.Web)
            section.rows.forEach { row ->
                val label = if (row == SettingsRow.SignOut) signOutLabel(local) else row.label
                Surface(color = if (row.key == selected) Palette.text.copy(alpha = .1f) else Palette.background, shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.offset(x = (-12).dp)) {
                    Row(Modifier.fillMaxWidth().heightIn(min = 40.dp).pointerClickable(shape = RoundedCornerShape(8.dp)) {
                        if (row == SettingsRow.SignOut) signOut() else select(row.key)
                    }.padding(start = 12.dp, end = 16.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SettingsRailIcon(row, Modifier.size(19.dp))
                        Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (row.dangerous) Palette.danger else Palette.text)
                    }
                }
            }
        }
    }
}