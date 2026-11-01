package org.viptv.app.features.settings

import org.viptv.app.components.FocusLazyColumn as LazyColumn
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
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
import org.viptv.app.components.IconButton

@Composable
internal fun PhoneSettings(profile: Profile?, addonCount: Int, local: Boolean, notice: String?,
    profiles: () -> Unit, addons: () -> Unit, leave: () -> Unit, back: () -> Unit, account: (() -> Unit)? = null, devices: (() -> Unit)? = null, stremioImport: (() -> Unit)? = null, preferences: ((String) -> Unit)? = null, parental: (() -> Unit)? = null, information: ((String) -> Unit)? = null, addDevice: (() -> Unit)? = null,
    householdPin: (() -> Unit)? = null, sourceIntegrations: (() -> Unit)? = null, xtreamConnections: (() -> Unit)? = null, password: (() -> Unit)? = null, deleteAccount: (() -> Unit)? = null,
    profilePage: (() -> Unit)? = null, linkedServices: (() -> Unit)? = null, gateways: (() -> Unit)? = null,
    listState: LazyListState = rememberLazyListState()) {
    val available = availableRows(parental != null, stremioImport != null, preferences != null, account != null,
        devices != null, addDevice != null, information = information != null,
        householdPin = householdPin != null, sourceIntegrations = sourceIntegrations != null,
        deleteAccount = deleteAccount != null, linkedServices = linkedServices != null, password = password != null,
        xtreamConnections = xtreamConnections != null, gateways = gateways != null)
    val open: (SettingsRow) -> Unit = { row ->
        when (row) {
            SettingsRow.Profile -> profilePage?.invoke()
            SettingsRow.LinkedServices -> linkedServices?.invoke()
            SettingsRow.ParentalControls -> parental?.invoke()
            SettingsRow.ImportFromStremio -> stremioImport?.invoke()
            SettingsRow.Playback, SettingsRow.SubtitlesAudio -> preferences?.invoke(row.key)
            SettingsRow.About, SettingsRow.Help -> information?.invoke(row.key)
            SettingsRow.Account -> account?.invoke()
            SettingsRow.Password -> password?.invoke()
            SettingsRow.HouseholdPin -> householdPin?.invoke()
            SettingsRow.Devices -> devices?.invoke()
            SettingsRow.AddDevice -> addDevice?.invoke()
            SettingsRow.Addons -> addons()
            SettingsRow.SourceIntegrations -> sourceIntegrations?.invoke()
            SettingsRow.XtreamConnections -> xtreamConnections?.invoke()
            SettingsRow.Gateways -> gateways?.invoke()
            SettingsRow.SignOut -> leave()
            SettingsRow.DeleteAccount -> deleteAccount?.invoke()
            else -> Unit
        }
    }
    LazyColumn(Modifier.fillMaxSize(), listState, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
        item {
            Box(Modifier.fillMaxWidth().height(48.dp)) {
                IconButton(onClick = back, modifier = Modifier.align(Alignment.CenterStart).size(44.dp)) {
                    Icon(Lucide.ChevronLeft, "Back", Modifier.size(26.dp))
                }
                Text("Settings", Modifier.align(Alignment.Center), fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
        }
        item {
            Spacer(Modifier.height(18.dp))
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
        notice?.let { item { Text(it, Modifier.padding(top = 18.dp), color = Palette.danger) } }
        settingsSections(available).forEach { section ->
            item(key = section.key) {
                Column {
                    SettingsGroupLabel(section.group.label, SettingsLayout.Phone)
                    Surface(shape = RoundedCornerShape(14.dp), color = Palette.surface) {
                        Column {
                            section.rows.forEachIndexed { index, row ->
                                if (index > 0) SettingsDivider()
                                val label = if (row == SettingsRow.SignOut) signOutLabel(local) else row.label
                                if (row.dangerous) SettingsDangerItem(label, row.icon) { open(row) }
                                else SettingsItem(label, row.icon, subtitle = row.subtitle(addonCount, local), action = { open(row) })
                            }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

private fun SettingsRow.subtitle(addonCount: Int, local: Boolean): String? = when (this) {
    SettingsRow.Account -> if (local) "Using this device locally" else "Connected to a server"
    SettingsRow.Addons -> "$addonCount installed"
    else -> null
}

@Composable
private fun SettingsDivider() { HorizontalDivider(color = Palette.raised, thickness = 1.dp) }
