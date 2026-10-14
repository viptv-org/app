package org.viptv.app.features.settings

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.viptv.app.connection.Profile
import org.viptv.engine.wire.IntegrationConnection

@Composable
internal fun SettingsScreen(local: Boolean, addons: List<IntegrationConnection>, enabledAddonIds: Set<String>, busy: Boolean,
    error: String?, sessionNotice: String?, install: (String) -> Unit, toggle: (String, Boolean) -> Unit,
    remove: (String) -> Unit, profiles: () -> Unit, leave: () -> Unit, profile: Profile? = null,
    back: () -> Unit = {}, television: Boolean = false, initialSection: String? = null, initialLeave: Boolean = false, account: (@Composable () -> Unit)? = null, sourceIntegrations: (@Composable () -> Unit)? = null, preferences: (@Composable () -> Unit)? = null,
    devices: (@Composable (Boolean, () -> Unit) -> Unit)? = null,
    tvPreferences: TvSettingsPreferences? = null, changeTvPreferences: ((TvSettingsPreferences) -> Unit)? = null) {
    if (television) {
        TvSettingsScreen(local, addons, enabledAddonIds, busy, error, sessionNotice, install, toggle, profiles, leave, profile, sourceIntegrations = sourceIntegrations, prefs = tvPreferences, change = changeTvPreferences, actualPreferencesOnly = true)
        return
    }
    var section by remember { mutableStateOf(initialSection) }
    var leaving by remember { mutableStateOf(initialLeave) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact = maxWidth < 640.dp
        val webAccount = devices != null && !compact
        Row(Modifier.fillMaxSize().then(if (webAccount) Modifier.padding(horizontal = 57.dp, vertical = 32.dp) else Modifier)) {
            if (webAccount) { AccountSettingsSidebar(section ?: "Add-ons", profiles) { section = it }; Spacer(Modifier.width(48.dp)) }
            else if (!compact) SettingsSidebar(section ?: "Add-ons", profile, profiles) { section = it }
            Box(Modifier.weight(1f, fill = !webAccount).then(if (webAccount) Modifier.widthIn(max = 760.dp) else Modifier)) {
                when {
                    compact && section == null -> PhoneSettings(profile, addons.size, local, sessionNotice, profiles,
                        { section = "Add-ons" }, { leaving = true }, back, account = account?.let { { section = "Account" } }, devices = devices?.let { { section = "Devices" } }, preferences = preferences?.let { { section = it } })
                    (section ?: "Add-ons") == "Add-ons" -> AddonSettings(local, addons, enabledAddonIds, busy, error,
                        sessionNotice, install, toggle, remove, if (compact) ({ section = null }) else null, sourceIntegrations = sourceIntegrations)
                    section == "Account" && account != null -> SettingsDetailContent(compact, { section = null }, account)
                    section == "Devices" && devices != null -> devices(compact) { section = if (compact) null else "Account" }
                    section in listOf("Playback", "Subtitles & audio") && preferences != null -> SettingsDetailContent(compact, { section = null }, preferences)
                    else -> SettingsSection(section.orEmpty(), local, busy, error, sessionNotice, { leaving = true })
                }
            }
        }
        if (leaving) SettingsLeaveDialog(local, compact, busy, error, { leaving = false }, leave)
    }
}

@Composable
private fun SettingsDetailContent(compact: Boolean, back: () -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        if (compact) androidx.compose.material3.TextButton(back) { androidx.compose.material3.Text("Back") }
        Box(Modifier.weight(1f)) { content() }
    }
}
