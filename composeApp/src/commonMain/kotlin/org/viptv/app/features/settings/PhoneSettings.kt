package org.viptv.app.features.settings

import org.viptv.app.components.FocusLazyColumn as LazyColumn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.*
import org.viptv.app.connection.Profile
import org.viptv.app.features.entry.ProfileAvatar
import org.viptv.app.theme.Palette

@Composable
internal fun PhoneSettings(profile: Profile?, addonCount: Int, local: Boolean, notice: String?,
    profiles: () -> Unit, addons: () -> Unit, leave: () -> Unit, back: () -> Unit, account: (() -> Unit)? = null, devices: (() -> Unit)? = null, stremioImport: (() -> Unit)? = null, preferences: ((String) -> Unit)? = null, parental: (() -> Unit)? = null, information: ((String) -> Unit)? = null, addDevice: Boolean = false) {
    var unavailable by remember { mutableStateOf<String?>(null) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item {
            Box(Modifier.fillMaxWidth().height(48.dp)) {
                IconButton(onClick = back, modifier = Modifier.align(Alignment.CenterStart).size(44.dp)) {
                    Icon(Lucide.ChevronLeft, "Back", Modifier.size(26.dp))
                }
                Text("Settings", Modifier.align(Alignment.Center), fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
        }
        item {
            Surface(shape = RoundedCornerShape(16.dp), color = Palette.surface) {
                Row(Modifier.fillMaxWidth().clickable(onClick = profiles).padding(14.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    ProfileAvatar(profile, 56.dp, 12.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(profile?.name ?: "Profile", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("Switch profile", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Palette.secondary)
                    }
                    Icon(Lucide.ChevronRight, null, Modifier.size(20.dp), tint = Palette.tertiary)
                }
            }
        }
        notice?.let { item { Text(it, color = Palette.danger) } }
        item {
            SettingsGroup {
                SettingsItem("Playback", Lucide.CirclePlay) { if (preferences != null) preferences("Playback") else unavailable = "Playback preferences" }
                SettingsDivider()
                SettingsItem("Subtitles & audio", Lucide.Captions) { if (preferences != null) preferences("Subtitles & audio") else unavailable = "Subtitle and audio preferences" }
            }
        }
        item {
            SettingsGroup {
                SettingsItem("Watch on TV", Lucide.Airplay, if (addDevice) "Sign in a TV or another device" else null) { if (information != null) information("Watch on TV") else unavailable = "TV pairing" }
                SettingsDivider()
                SettingsItem("Add-ons", Lucide.Puzzle, "$addonCount installed", addons)
            }
        }
        item {
            SettingsGroup {
                SettingsItem("Account", Lucide.UserRound, if (local) "Using this device locally" else "Connected to a server") { if (account != null) account() else unavailable = "Account settings" }
                SettingsDivider()
                if (devices != null) {
                    SettingsItem("Devices", Lucide.Monitor, action = devices)
                    SettingsDivider()
                }
                if (stremioImport != null) {
                    SettingsItem(stremioImportSection, Lucide.Import, action = stremioImport)
                    SettingsDivider()
                }
                if (parental != null) {
                    SettingsItem("Parental controls", Lucide.Lock, action = parental)
                    SettingsDivider()
                }
                SettingsItem("Help", Lucide.Info) { if (information != null) information("Help") else unavailable = "Help" }
                SettingsDivider()
                SettingsItem("About VIPTV", Lucide.Info) { if (information != null) information("About") else unavailable = "About VIPTV" }
            }
        }
        item {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TextButton(onClick = leave) { Text(if (local) "Change connection" else "Sign out", color = Palette.danger, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
    unavailable?.let { label ->
        AlertDialog(onDismissRequest = { unavailable = null }, title = { Text(label) },
            text = { Text(if (label == "About VIPTV") "VIPTV · Experimental build" else "$label is not available in this experiment yet.") },
            confirmButton = { TextButton(onClick = { unavailable = null }) { Text("OK") } })
    }
}

@Composable
private fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(14.dp), color = Palette.surface) { Column(content = content) }
}

@Composable
private fun SettingsDivider() { HorizontalDivider(color = Palette.raised, thickness = 1.dp) }
