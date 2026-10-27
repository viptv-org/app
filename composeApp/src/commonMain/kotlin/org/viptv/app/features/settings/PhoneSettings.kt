package org.viptv.app.features.settings

import org.viptv.app.components.FocusLazyColumn as LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
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
import org.viptv.app.components.pointerClickable
import org.viptv.app.components.TextButton
import org.viptv.app.components.IconButton

@Composable
internal fun PhoneSettings(profile: Profile?, addonCount: Int, local: Boolean, notice: String?,
    profiles: () -> Unit, addons: () -> Unit, leave: () -> Unit, back: () -> Unit, account: (() -> Unit)? = null, devices: (() -> Unit)? = null, stremioImport: (() -> Unit)? = null, preferences: ((String) -> Unit)? = null, parental: (() -> Unit)? = null, information: ((String) -> Unit)? = null, addDevice: Boolean = false,
    /** Owned by Settings so Back from a sub-page lands on the list where the row was opened. */
    listState: LazyListState = rememberLazyListState()) {
    LazyColumn(Modifier.fillMaxSize(), listState, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
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
                Row(Modifier.fillMaxWidth().pointerClickable(onClick = profiles).padding(14.dp), verticalAlignment = Alignment.CenterVertically,
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
        // A row appears only when it has somewhere to go; SettingsRoute supplies every handler.
        settingsGroup(listOfNotNull(
            preferences?.let { open -> { SettingsItem("Playback", Lucide.CirclePlay) { open("Playback") } } },
            preferences?.let { open -> { SettingsItem("Subtitles & audio", Lucide.Captions) { open("Subtitles & audio") } } }))
        settingsGroup(listOfNotNull(
            information?.let { open -> { SettingsItem("Watch on TV", Lucide.Airplay, if (addDevice) "Sign in a TV or another device" else null) { open("Watch on TV") } } },
            { SettingsItem("Add-ons", Lucide.Puzzle, "$addonCount installed", addons) }))
        settingsGroup(listOfNotNull(
            account?.let { { SettingsItem("Account", Lucide.UserRound, if (local) "Using this device locally" else "Connected to a server", action = it) } },
            devices?.let { { SettingsItem("Devices", Lucide.Monitor, action = it) } },
            stremioImport?.let { { SettingsItem(stremioImportSection, Lucide.Import, action = it) } },
            parental?.let { { SettingsItem("Parental controls", Lucide.Lock, action = it) } },
            information?.let { open -> { SettingsItem("Help", Lucide.Info) { open("Help") } } },
            information?.let { open -> { SettingsItem("About VIPTV", Lucide.Info) { open("About") } } }))
        item {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TextButton(onClick = leave) { Text(if (local) "Change connection" else "Sign out", color = Palette.danger, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

/** One rounded group of [rows] with hairlines between them; an empty group is left out. */
private fun LazyListScope.settingsGroup(rows: List<@Composable () -> Unit>) {
    if (rows.isNotEmpty()) item {
        Surface(shape = RoundedCornerShape(14.dp), color = Palette.surface) {
            Column { rows.forEachIndexed { index, row -> if (index > 0) SettingsDivider(); row() } }
        }
    }
}

@Composable
private fun SettingsDivider() { HorizontalDivider(color = Palette.raised, thickness = 1.dp) }
