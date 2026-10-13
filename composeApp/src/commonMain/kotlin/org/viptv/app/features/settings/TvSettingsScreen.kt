package org.viptv.app.features.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.viptv.app.connection.Profile
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.IntegrationConnection

@Composable
internal fun TvSettingsScreen(local: Boolean, addons: List<IntegrationConnection>, enabled: Set<String>, busy: Boolean, error: String?,
    notice: String?, install: (String) -> Unit, toggle: (String, Boolean) -> Unit, profiles: () -> Unit, leave: () -> Unit, profile: Profile?,
    initialSection: TvSettingsSection = TvSettingsSection.Playback, prefs: TvSettingsPreferences? = null,
    change: ((TvSettingsPreferences) -> Unit)? = null, addonInfo: Map<String, TvAddonInfo> = emptyMap(), account: TvAccountInfo? = null,
    initialLeave: Boolean = false, placeholderQr: Boolean = false, sourceIntegrations: (@Composable () -> Unit)? = null, actualPreferencesOnly: Boolean = false) {
    var section by remember { mutableStateOf(initialSection) }
    var leaving by remember { mutableStateOf(initialLeave) }
    var choice by remember { mutableStateOf<TvSettingsChoice?>(null) }
    var unavailable by remember { mutableStateOf<String?>(null) }
    var installing by remember { mutableStateOf(false) }
    var address by remember { mutableStateOf("") }
    val sidebar = remember { FocusRequester() }; val first = remember { FocusRequester() }
    CompositionLocalProvider(LocalTextStyle provides LocalTextStyle.current.copy(lineHeightStyle =
        LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None, LineHeightStyle.Mode.Fixed))) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val scale = maxWidth / 1920.dp
        LaunchedEffect(section, leaving) { if (!leaving) first.requestFocus() }
        Row(Modifier.fillMaxSize()) {
            TvSettingsSidebar(section, scale, sidebar, first, modal = leaving || choice != null || installing || unavailable != null) {
                if (it == TvSettingsSection.Profiles) profiles() else section = it
            }
            Column(Modifier.weight(1f).padding(start = 80.dp * scale, end = 96.dp * scale, top = 96.dp * scale)) {
                when (section) {
                    TvSettingsSection.Playback -> TvPlaybackSettings(profile?.name, prefs, scale, first, sidebar, { change?.invoke(it) },
                        { title, values, select -> choice = TvSettingsChoice(title, values, select) }, { unavailable = "Playback preferences" }, actualPreferencesOnly)
                    TvSettingsSection.Subtitles -> TvSubtitleSettings(prefs, scale, first, sidebar, { change?.invoke(it) },
                        { title, values, select -> choice = TvSettingsChoice(title, values, select) }, { unavailable = "Subtitle preferences" }, actualPreferencesOnly)
                    TvSettingsSection.Addons -> TvAddonSettings(addons, enabled, local, busy, scale, first, sidebar, addonInfo, toggle,
                        { installing = true }, { unavailable = "Add-on management" }, placeholderQr)
                    TvSettingsSection.Account -> TvSettingsAccount(local, account, scale, first, sidebar, { leaving = true }, { unavailable = "Account settings" })
                    else -> {
                        Text(section.label, fontSize = (40 * scale).sp)
                        Spacer(Modifier.height(48.dp * scale))
                        TvSettingsRow(if (section == TvSettingsSection.About) "VIPTV · Experimental build" else "Open Profiles", scale = scale, requester = first, left = sidebar) {
                            if (section == TvSettingsSection.Parental) profiles() else unavailable = section.label }
                    }
                }
                notice?.let { Text(it, Modifier.padding(top = 20.dp * scale), color = Palette.danger, fontSize = (22 * scale).sp) }
                error?.let { Text(it, Modifier.padding(top = 20.dp * scale), color = Palette.danger, fontSize = (22 * scale).sp) }
            }
        }
        if (section == TvSettingsSection.Addons && sourceIntegrations != null) {
            var showingSources by remember { mutableStateOf(false) }
            Box(Modifier.fillMaxSize().padding(32.dp * scale), contentAlignment = androidx.compose.ui.Alignment.BottomEnd) {
                Button({ showingSources = true }) { Text("Source integrations") }
            }
            if (showingSources) AlertDialog(onDismissRequest = { showingSources = false },
                text = { sourceIntegrations() }, confirmButton = { TextButton({ showingSources = false }) { Text("Close") } })
        }
        if (leaving) TvLeaveConfirmation(local, scale, { leaving = false; leave() }, { leaving = false })
        choice?.let { TvSettingsChoiceDialog(it, scale) { choice = null } }
        unavailable?.let { label -> AlertDialog(onDismissRequest = { unavailable = null }, title = { Text(label) },
            text = { Text("$label is not available on this connection yet.") }, confirmButton = { TextButton(onClick = { unavailable = null }) { Text("OK") } }) }
        if (installing) AlertDialog(onDismissRequest = { installing = false }, title = { Text("Add an add-on") },
            text = { OutlinedTextField(address, { address = it }, singleLine = true, label = { Text("Add-on address") }, placeholder = { Text("https://addon.example/manifest.json") }) },
            confirmButton = { TextButton(onClick = { install(address); address = ""; installing = false }, enabled = address.isNotBlank() && !busy) { Text("Add") } },
            dismissButton = { TextButton(onClick = { installing = false }) { Text("Cancel") } })
    }
    }
}
