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
    devices: (@Composable (Boolean, () -> Unit) -> Unit)? = null, addonsRequested: (() -> Unit)? = null,
    tvPreferences: TvSettingsPreferences? = null, changeTvPreferences: ((TvSettingsPreferences) -> Unit)? = null,
    serverAddress: String? = null,
    tvAccount: TvAccountInfo? = null, accountRequested: (() -> Unit)? = null, accountDashboard: (() -> Unit)? = null,
    parentalBack: (() -> Unit)? = null, parental: (@Composable () -> Unit)? = null, tvParental: (@Composable (androidx.compose.ui.focus.FocusRequester, androidx.compose.ui.focus.FocusRequester, Float) -> Unit)? = null,
    preferencesError: String? = null, retryPreferences: (() -> Unit)? = null,
    sectionPreferences: (@Composable (String, Boolean) -> Unit)? = null,
    installWithCompletion: ((String, (Boolean) -> Unit) -> Unit)? = null,
    stremioImport: (@Composable (Boolean, () -> Unit) -> Unit)? = null) {
    if (television) {
        TvSettingsScreen(local, addons, enabledAddonIds, busy, error, sessionNotice, install, toggle, profiles, leave, profile, initialSection = TvSettingsSection.entries.firstOrNull { it.label == initialSection } ?: TvSettingsSection.Playback, sourceIntegrations = sourceIntegrations, prefs = tvPreferences, change = changeTvPreferences, actualPreferencesOnly = true, parental = tvParental, account = tvAccount, accountRequested = accountRequested, accountDashboard = accountDashboard, dashboardAddress = serverAddress, preferencesError = preferencesError, retryPreferences = retryPreferences, installWithCompletion = installWithCompletion)
        return
    }
    var section by remember { mutableStateOf(initialSection) }
    var leaving by remember { mutableStateOf(initialLeave) }
    // The addon count must be known before the section opens; one eager
    // load on composition, and section changes only refresh, not duplicate,
    // the initial request.
    var addonsLoaded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { addonsRequested?.invoke(); addonsLoaded = true }
    LaunchedEffect(section) { if (section == "Add-ons" && addonsLoaded) addonsRequested?.invoke() }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact = maxWidth < 640.dp
        val webAccount = devices != null && !compact
        Row(Modifier.fillMaxSize().then(if (webAccount) Modifier.padding(horizontal = 57.dp, vertical = 32.dp) else Modifier)) {
            if (webAccount) { AccountSettingsSidebar(section ?: "Add-ons", profiles, stremioImport != null) { section = it }; Spacer(Modifier.width(48.dp)) }
            else if (!compact) SettingsSidebar(section ?: "Add-ons", profile, profiles, stremioImport != null) { section = it }
            Box(Modifier.weight(1f, fill = !webAccount).then(if (webAccount) Modifier.widthIn(max = 760.dp) else Modifier)) {
                when {
                    compact && section == null -> PhoneSettings(profile, addons.size, local, sessionNotice, profiles,
                        { section = "Add-ons" }, { leaving = true }, back, account = account?.let { { section = "Account" } }, devices = devices?.let { { section = "Devices" } }, stremioImport = stremioImport?.let { { section = stremioImportSection } }, preferences = if (preferences != null || sectionPreferences != null) ({ section = it }) else null, parental = parental?.let { { section = "Parental controls" } }, information = { section = it })
                    (section ?: "Add-ons") == "Add-ons" -> AddonSettings(local, addons, enabledAddonIds, busy, error,
                        sessionNotice, install, toggle, remove, if (compact) ({ section = null }) else null, sourceIntegrations = sourceIntegrations,
                        installWithCompletion = installWithCompletion)
                    section == "Parental controls" && parental != null -> SettingsDetailContent(compact, parentalBack ?: { section = null }, parental)
                    section == "Account" && account != null -> SettingsDetailContent(compact, { section = null }, account)
                    section == "Devices" && devices != null -> devices(compact) { section = if (compact) null else "Account" }
                    section == stremioImportSection && stremioImport != null -> Box(if (!compact && !webAccount) Modifier.padding(32.dp) else Modifier) {
                        stremioImport(compact) { section = if (compact) null else "Account" }
                    }
                    section in listOf("Playback", "Subtitles & audio") && (preferences != null || sectionPreferences != null) -> SettingsDetailContent(compact, { section = null }) {
                        if (sectionPreferences != null) sectionPreferences(requireNotNull(section), compact) else preferences?.invoke()
                    }
                    section in listOf("General", "Help", "About", "Watch on TV") -> SettingsDetailContent(compact, { section = null }) {
                        SettingsInformation(requireNotNull(section), local, serverAddress, profile?.name, profiles) { leaving = true }
                    }
                    else -> SettingsSection(section.orEmpty(), local, busy, error, sessionNotice, { leaving = true })
                }
            }
        }
        if (leaving) SettingsLeaveDialog(local, compact, busy, error, { leaving = false }, leave)
    }
}

internal const val stremioImportSection = "Import from Stremio"

@Composable
private fun SettingsDetailContent(compact: Boolean, back: () -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        if (compact) androidx.compose.material3.TextButton(back) { androidx.compose.material3.Text("Back") }
        Box(Modifier.weight(1f)) { content() }
    }
}
