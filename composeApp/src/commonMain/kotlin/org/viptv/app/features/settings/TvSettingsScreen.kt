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
import androidx.compose.foundation.text.selection.SelectionContainer

@Composable
internal fun TvSettingsScreen(local: Boolean, addons: List<IntegrationConnection>, enabled: Set<String>, busy: Boolean, error: String?,
    notice: String?, install: (String) -> Unit, toggle: (String, Boolean) -> Unit, profiles: () -> Unit, leave: () -> Unit, profile: Profile?,
    initialSection: TvSettingsSection = TvSettingsSection.Playback, prefs: TvSettingsPreferences? = null,
    change: ((TvSettingsPreferences) -> Unit)? = null, addonInfo: Map<String, TvAddonInfo> = emptyMap(), account: TvAccountInfo? = null,
    initialLeave: Boolean = false, placeholderQr: Boolean = false, sourceIntegrations: (@Composable () -> Unit)? = null, actualPreferencesOnly: Boolean = false,
    parental: (@Composable (FocusRequester, FocusRequester, Float) -> Unit)? = null,
    accountRequested: (() -> Unit)? = null, accountDashboard: (() -> Unit)? = null,
    dashboardAddress: String? = null, preferencesError: String? = null, retryPreferences: (() -> Unit)? = null,
    installWithCompletion: ((String, (Boolean) -> Unit) -> Unit)? = null) {
    var section by remember { mutableStateOf(initialSection) }
    var leaving by remember { mutableStateOf(initialLeave) }
    var choice by remember { mutableStateOf<TvSettingsChoice?>(null) }
    var unavailable by remember { mutableStateOf<String?>(null) }
    var browserFallback by remember { mutableStateOf(false) }
    var installing by remember { mutableStateOf(false) }
    var installPending by remember { mutableStateOf(false) }
    var installGeneration by remember { mutableIntStateOf(0) }
    DisposableEffect(Unit) { onDispose { installGeneration++ } }
    val installBusy = busy || installPending
    var address by remember { mutableStateOf("") }
    val preferenceSection = section in listOf(TvSettingsSection.Playback, TvSettingsSection.Subtitles)
    val failedPreferences = preferenceSection && prefs == null && preferencesError != null && retryPreferences != null
    val missingAccount = section == TvSettingsSection.Account && !local && account == null && accountRequested != null
    LaunchedEffect(section, local) { if (section == TvSettingsSection.Account && !local) accountRequested?.invoke() }
    val sidebar = remember { FocusRequester() }; val first = remember { FocusRequester() }
    CompositionLocalProvider(LocalTextStyle provides LocalTextStyle.current.copy(lineHeightStyle =
        LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None, LineHeightStyle.Mode.Fixed))) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val scale = maxWidth / 1920.dp
        org.viptv.app.components.TvRequestedFocus(first, listOf(section, failedPreferences, missingAccount),
            enabled = !leaving && (!(failedPreferences || missingAccount) || !busy))
        Row(Modifier.fillMaxSize()) {
            TvSettingsSidebar(section, scale, sidebar, first, modal = leaving || choice != null || installing || unavailable != null || browserFallback) {
                if (it == TvSettingsSection.Profiles) profiles() else section = it
            }
            Column(Modifier.weight(1f).padding(start = 80.dp * scale, end = 96.dp * scale, top = 96.dp * scale)) {
                if (failedPreferences) TvPreferencesFailure(section.label, requireNotNull(preferencesError), busy, scale, first, sidebar, retryPreferences)
                else when (section) {
                    TvSettingsSection.Playback -> TvPlaybackSettings(profile?.name, prefs, scale, first, sidebar, { change?.invoke(it) },
                        { title, values, select -> choice = TvSettingsChoice(title, values, select) }, { unavailable = "Playback preferences" }, actualPreferencesOnly)
                    TvSettingsSection.Subtitles -> TvSubtitleSettings(prefs, scale, first, sidebar, { change?.invoke(it) },
                        { title, values, select -> choice = TvSettingsChoice(title, values, select) }, { unavailable = "Subtitle preferences" }, actualPreferencesOnly)
                    TvSettingsSection.Addons -> TvAddonSettings(addons, enabled, local, busy, scale, first, sidebar, addonInfo, toggle,
                        { installing = true }, { unavailable = "Add-on management" }, placeholderQr)
                    TvSettingsSection.Parental -> if (parental != null) parental(first, sidebar, scale) else {
                        Text("Parental controls", fontSize = (40 * scale).sp)
                        TvSettingsRow("Open Profiles", scale = scale, requester = first, left = sidebar, activate = profiles)
                    }
                    TvSettingsSection.Account -> TvSettingsAccount(local, account, scale, first, sidebar, { leaving = true }, {
                        if (local) leaving = true
                        else if (accountDashboard != null) try { accountDashboard() } catch (_: Exception) {
                            if (dashboardAddress != null) browserFallback = true else unavailable = "Account settings"
                        } else if (dashboardAddress != null) browserFallback = true else unavailable = "Account settings"
                    }, busy = busy, error = error.takeIf { accountRequested != null }, retry = accountRequested)
                    else -> {
                        Text(section.label, fontSize = (40 * scale).sp)
                        Spacer(Modifier.height(48.dp * scale))
                        TvSettingsRow(if (section == TvSettingsSection.About) "VIPTV · Experimental build" else "Open Profiles", scale = scale, requester = first, left = sidebar) {
                            if (section == TvSettingsSection.Parental) profiles() else unavailable = section.label }
                    }
                }
                if (preferenceSection && preferencesError != null && !failedPreferences) {
                    Text(preferencesError, Modifier.padding(top = 20.dp * scale), color = Palette.danger, fontSize = (22 * scale).sp)
                    TvPreferencesRetry(busy, scale, null, sidebar, retryPreferences)
                }
                notice?.let { Text(it, Modifier.padding(top = 20.dp * scale), color = Palette.danger, fontSize = (22 * scale).sp) }
                if (section != TvSettingsSection.Account || local || accountRequested == null)
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
        if (browserFallback) AlertDialog(onDismissRequest = { browserFallback = false }, title = { Text("Open server dashboard") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Open this address in a browser on your phone or computer.")
                dashboardAddress?.let { SelectionContainer { Text(it, color = Palette.secondary) } }
            } }, confirmButton = { TextButton({ browserFallback = false }) { Text("Close") } })
        if (installing) AlertDialog(onDismissRequest = { if (!busy && !installPending) { installGeneration++; installing = false } }, title = { Text("Add an add-on") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(address, { address = it }, enabled = !installBusy, singleLine = true,
                    label = { Text("Add-on address") }, placeholder = { Text("https://addon.example/manifest.json") })
                if (installBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
                error?.let { Text(it, color = Palette.danger) }
            } },
            confirmButton = { TextButton(onClick = {
                if (!busy && !installPending && address.isNotBlank()) {
                    val complete = installWithCompletion
                    if (complete == null) install(address.trim())
                    else {
                        installPending = true
                        val attempt = ++installGeneration
                        complete(address.trim()) { installed ->
                            if (attempt == installGeneration && installing) {
                                installPending = false
                                if (installed) { installGeneration++; address = ""; installing = false }
                            }
                        }
                    }
                }
            }, enabled = address.isNotBlank() && !installBusy) { Text("Add") } },
            dismissButton = { TextButton(onClick = { if (!busy && !installPending) { installGeneration++; installing = false } }, enabled = !installBusy) { Text("Cancel") } })
    }
    }
}
