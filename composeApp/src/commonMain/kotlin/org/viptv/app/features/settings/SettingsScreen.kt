package org.viptv.app.features.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.viptv.app.connection.Profile
import org.viptv.app.state.SettingsPage
import org.viptv.engine.wire.IntegrationConnection
import org.viptv.app.components.TextButton

@Composable
internal fun SettingsScreen(local: Boolean, addons: List<IntegrationConnection>, enabledAddonIds: Set<String>, busy: Boolean,
    error: String?, sessionNotice: String?, install: (String) -> Unit, toggle: (String, Boolean) -> Unit,
    remove: (String) -> Unit, profiles: () -> Unit, leave: () -> Unit, profile: Profile? = null,
    leaveSettings: () -> Unit = {}, television: Boolean = false, initialSection: String? = null, initialLeave: Boolean = false, account: (@Composable () -> Unit)? = null, sourceIntegrations: (@Composable (androidx.compose.ui.focus.FocusRequester, androidx.compose.ui.focus.FocusRequester, Float) -> Unit)? = null, preferences: (@Composable () -> Unit)? = null,
    devices: (@Composable (Boolean, () -> Unit) -> Unit)? = null, addonsRequested: (() -> Unit)? = null,
    tvPreferences: TvSettingsPreferences? = null, changeTvPreferences: ((TvSettingsPreferences) -> Unit)? = null,
    serverAddress: String? = null,
    tvAccount: TvAccountInfo? = null, accountRequested: (() -> Unit)? = null, accountDashboard: (() -> Unit)? = null,
    householdPin: (@Composable () -> Unit)? = null,
    password: (@Composable (() -> Unit) -> Unit)? = null, accountAccessRequested: (() -> Unit)? = null,
    deleteAccount: (@Composable (() -> Unit) -> Unit)? = null,
    parentalBack: (() -> Unit)? = null, parental: (@Composable () -> Unit)? = null, tvParental: (@Composable (androidx.compose.ui.focus.FocusRequester, androidx.compose.ui.focus.FocusRequester, Float) -> Unit)? = null,
    preferencesError: String? = null, retryPreferences: (() -> Unit)? = null,
    sectionPreferences: (@Composable (String, Boolean) -> Unit)? = null,
    installWithCompletion: ((String, (Boolean) -> Unit) -> Unit)? = null,
    tvParentalMaturity: String? = null, tvParentalPinAvailable: Boolean = true,
    stremioImport: (@Composable (Boolean, () -> Unit, () -> Unit) -> Unit)? = null,
    addDevice: (@Composable (Boolean) -> Unit)? = null,
    page: SettingsPage? = null, pageChanged: ((SettingsPage) -> Unit)? = null,
    returnToPlayer: (() -> Unit)? = null,
    tvDevicePlayback: DevicePlaybackState? = null,
    desktop: Boolean = false, profilePage: (@Composable (SettingsLayout) -> Unit)? = null,
    profileTitles: (@Composable (Boolean) -> Unit)? = null, profileTitlesBack: (() -> Unit)? = null,
    linkedServices: (@Composable (Boolean) -> Unit)? = null,
    tvProfilePage: (@Composable (androidx.compose.ui.focus.FocusRequester, androidx.compose.ui.focus.FocusRequester, Float, (TvSettingsSection) -> Unit) -> Unit)? = null,
    tvProfileTitles: (@Composable (androidx.compose.ui.focus.FocusRequester, androidx.compose.ui.focus.FocusRequester, Float) -> Unit)? = null,
    tvDevices: (@Composable (androidx.compose.ui.focus.FocusRequester, androidx.compose.ui.focus.FocusRequester, Float, () -> Unit) -> Unit)? = null,
    browser: Boolean = false) {
    if (television) {
        TvSettingsScreen(local, addons, enabledAddonIds, busy, error, sessionNotice, install, toggle, profiles, leave, profile, initialSection = TvSettingsSection.entries.firstOrNull { it.label == initialSection && !it.leavesSettings } ?: TvSettingsSection.Playback, sourceIntegrations = sourceIntegrations, prefs = tvPreferences, change = changeTvPreferences, actualPreferencesOnly = true, parental = tvParental, account = tvAccount, accountRequested = accountRequested, accountDashboard = accountDashboard, dashboardAddress = serverAddress, preferencesError = preferencesError, retryPreferences = retryPreferences, installWithCompletion = installWithCompletion, maturity = tvParentalMaturity, pinAvailable = tvParentalPinAvailable, devicePlayback = tvDevicePlayback,
            profilePage = tvProfilePage, profileTitles = tvProfileTitles, tvDevices = tvDevices)
        return
    }
    val unusedRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    val phoneSourceIntegrations: (@Composable () -> Unit)? = sourceIntegrations?.let { render -> { render(unusedRequester, unusedRequester, 1f) } }
    var localPage by remember { mutableStateOf<SettingsPage?>(null) }
    val savedPage = if (pageChanged != null) page else localPage
    val current = savedPage ?: SettingsPage(initialSection)
    val section = current.section
    val show: (SettingsPage) -> Unit = pageChanged ?: { localPage = it }
    var leaving by remember { mutableStateOf(initialLeave) }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    var addonsLoaded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { accountAccessRequested?.invoke() }
    LaunchedEffect(Unit) { addonsRequested?.invoke(); addonsLoaded = true }
    LaunchedEffect(section) { if (section == "Add-ons" && addonsLoaded) addonsRequested?.invoke() }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact = maxWidth < 640.dp
        val webAccount = browser && !compact && !local
        val layout = when {
            compact -> SettingsLayout.Phone
            webAccount -> SettingsLayout.Web
            desktop -> SettingsLayout.Desktop
            else -> SettingsLayout.Tablet
        }
        val rows = availableRows(parental != null, stremioImport != null, preferences != null || sectionPreferences != null,
            account != null, devices != null, addDevice != null, watchOnTv = layout == SettingsLayout.Tablet, information = true,
            householdPin = householdPin != null, sourceIntegrations = sourceIntegrations != null,
            deleteAccount = deleteAccount != null, linkedServices = linkedServices != null, password = password != null)
        val root = initialSection.takeIf { returnToPlayer != null }
        val shown = current.laidOut(compact, root)
        SideEffect { if (savedPage != shown) show(shown) }
        val open: (String?) -> Unit = { show(SettingsPage(it).laidOut(compact, root)) }
        val back: () -> Unit = {
            val parent = shown.parent()
            when {
                parent != null -> show(parent)
                returnToPlayer != null && section == root -> returnToPlayer()
                else -> show(SettingsPage())
            }
        }
        val backLabel = if (returnToPlayer != null && section == root) "Back to player" else null
        Row(Modifier.fillMaxSize().then(if (webAccount) Modifier.padding(horizontal = 57.dp, vertical = 32.dp) else Modifier)) {
            if (webAccount) {
                AccountSettingsSidebar(section ?: SettingsRow.Addons.key, profile, settingsSections(rows), local, profiles, { open(it) }, { leaving = true }); Spacer(Modifier.width(48.dp))
            } else if (!compact) SettingsSidebar(section ?: SettingsRow.Addons.key, profile, layout, settingsSections(rows), local, profiles, { open(it) }, { leaving = true })
            Box(Modifier.weight(1f, fill = !webAccount).then(if (webAccount) Modifier.widthIn(max = 760.dp) else Modifier)) {
                when {
                    compact && section == null -> PhoneSettings(profile, addons.size, local, sessionNotice, profiles,
                        { open("Add-ons") }, { leaving = true }, back = leaveSettings, account = account?.let { { open("Account") } }, devices = devices?.let { { open("Devices") } }, stremioImport = stremioImport?.let { { open(stremioImportSection) } }, preferences = if (preferences != null || sectionPreferences != null) ({ open(it) }) else null, parental = parental?.let { { open("Parental controls") } }, information = { open(it) }, addDevice = addDevice?.let { { open("Add a device") } }, householdPin = householdPin?.let { { open("Household PIN") } }, sourceIntegrations = if (sourceIntegrations != null) ({ open("Source integrations") }) else null, password = password?.let { { open("Password") } }, deleteAccount = deleteAccount?.let { { open("Delete account") } }, profilePage = profilePage?.let { { open("Profile") } }, linkedServices = linkedServices?.let { { open("Linked services") } }, listState = listState)
                    (section ?: "Add-ons") == "Add-ons" -> AddonSettings(local, addons, enabledAddonIds, busy, error,
                        sessionNotice, install, toggle, remove, if (compact) back else null,
                        installWithCompletion = installWithCompletion)
                    section == "Profile" && profileTitles != null -> SettingsDetailContent(compact, profileTitlesBack ?: back) { profileTitles(compact) }
                    section == "Profile" && profilePage != null -> SettingsDetailContent(compact, back) { profilePage(layout) }
                    section == "Linked services" && linkedServices != null -> SettingsDetailContent(compact, back) { linkedServices(compact) }
                    section == "Parental controls" && parental != null -> SettingsDetailContent(compact, parentalBack ?: back, content = parental)
                    section == "Account" && account != null -> SettingsDetailContent(compact, back, content = account)
                    section == "Password" && password != null -> SettingsDetailContent(compact, back) {
                        password(if (compact) back else ({ open("Account") }))
                    }
                    section == "Household PIN" && householdPin != null -> SettingsDetailContent(compact, back, content = householdPin)
                    section == "Delete account" && deleteAccount != null -> SettingsDetailContent(compact, back) {
                        val cancel: () -> Unit = if (compact) back else ({ open("Account") })
                        deleteAccount(cancel)
                    }
                    section == "Source integrations" && phoneSourceIntegrations != null -> SettingsDetailContent(compact, back) {
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(if (compact) 16.dp else 32.dp)) {
                            phoneSourceIntegrations.invoke()
                        }
                    }
                    section == "Devices" && devices != null -> devices(compact) { if (compact) back() else open("Account") }
                    section == stremioImportSection && stremioImport != null -> Box(if (!compact && !webAccount) Modifier.padding(32.dp) else Modifier) {
                        stremioImport(compact, { if (compact) back() else open("Account") }) { open("Add-ons") }
                    }
                    section in listOf("Playback", "Subtitles & audio") && (preferences != null || sectionPreferences != null) -> SettingsDetailContent(compact, back, backLabel) {
                        if (sectionPreferences != null) sectionPreferences(requireNotNull(section), compact) else preferences?.invoke()
                    }
                    section == "Add a device" && addDevice != null -> SettingsDetailContent(compact, back) { addDevice(compact) }
                    section in listOf("Help", "About", "Watch on TV") -> SettingsDetailContent(compact, back) {
                        SettingsInformation(requireNotNull(section), local, serverAddress) { leaving = true }
                    }
                }
            }
        }
        if (leaving) SettingsLeaveDialog(local, compact, busy, error, { leaving = false }, leave)
    }
}

internal const val stremioImportSection = "Import from Stremio"

@Composable
private fun SettingsDetailContent(compact: Boolean, back: () -> Unit, backLabel: String? = null, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        if (compact || backLabel != null) TextButton(back) { androidx.compose.material3.Text(backLabel ?: "Back") }
        Box(Modifier.weight(1f)) { content() }
    }
}
