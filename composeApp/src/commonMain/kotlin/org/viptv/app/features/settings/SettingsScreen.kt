package org.viptv.app.features.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import org.viptv.app.components.pageBottomClearance
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.viptv.app.connection.Profile
import org.viptv.app.state.SettingsPage
import org.viptv.engine.wire.IntegrationConnection
import org.viptv.app.components.TextButton
import org.viptv.app.components.BackControl
import org.viptv.app.components.LocalBarsFadeContent
import org.viptv.app.components.ScrollEdge
import org.viptv.app.components.ScrollEdgeFade
import org.viptv.app.components.pageGutter

@Composable
internal fun SettingsScreen(local: Boolean, addons: List<IntegrationConnection>, enabledAddonIds: Set<String>, busy: Boolean,
    error: String?, sessionNotice: String?, install: (String) -> Unit, toggle: (String, Boolean) -> Unit,
    remove: (String) -> Unit, profiles: () -> Unit, leave: () -> Unit, profile: Profile? = null,
    leaveSettings: () -> Unit = {}, television: Boolean = false, initialSection: String? = null, initialLeave: Boolean = false, account: (@Composable () -> Unit)? = null, sourceIntegrations: (@Composable (androidx.compose.ui.focus.FocusRequester, androidx.compose.ui.focus.FocusRequester, Float) -> Unit)? = null, preferences: (@Composable () -> Unit)? = null, gateways: (@Composable (androidx.compose.ui.focus.FocusRequester, androidx.compose.ui.focus.FocusRequester, Float) -> Unit)? = null,
    devices: (@Composable (Boolean, () -> Unit) -> Unit)? = null, addonsRequested: (() -> Unit)? = null,
    tvPreferences: TvSettingsPreferences? = null, changeTvPreferences: ((TvSettingsPreferences, String) -> Unit)? = null,
    serverAddress: String? = null,
    tvAccount: TvAccountInfo? = null, accountRequested: (() -> Unit)? = null, accountDashboard: (() -> Unit)? = null,
    householdPin: (@Composable () -> Unit)? = null,
    password: (@Composable (() -> Unit) -> Unit)? = null, accountAccessRequested: (() -> Unit)? = null,
    gatewaysRequested: (() -> Unit)? = null,
    deleteAccount: (@Composable (() -> Unit) -> Unit)? = null,
    parentalBack: (() -> Unit)? = null, parental: (@Composable () -> Unit)? = null, tvParental: (@Composable (androidx.compose.ui.focus.FocusRequester, androidx.compose.ui.focus.FocusRequester, Float) -> Unit)? = null,
    preferencesError: String? = null, retryPreferences: (() -> Unit)? = null,
    sectionPreferences: (@Composable (String, Boolean) -> Unit)? = null,
    installWithCompletion: ((String, (Boolean) -> Unit) -> Unit)? = null,
    stremioImport: (@Composable (Boolean, () -> Unit, () -> Unit) -> Unit)? = null,
    addDevice: (@Composable (Boolean, () -> Unit) -> Unit)? = null,
    page: SettingsPage? = null, pageChanged: ((SettingsPage) -> Unit)? = null,
    returnToPlayer: (() -> Unit)? = null,
    tvDevicePlayback: DevicePlaybackState? = null,
    desktop: Boolean = false, profilePage: (@Composable (SettingsLayout) -> Unit)? = null,
    profileTitles: (@Composable (Boolean) -> Unit)? = null, profileTitlesBack: (() -> Unit)? = null,
    linkedServices: (@Composable (Boolean) -> Unit)? = null,
    tvProfilePage: (@Composable (androidx.compose.ui.focus.FocusRequester, androidx.compose.ui.focus.FocusRequester, Float, (TvSettingsSection) -> Unit) -> Unit)? = null,
    tvProfileTitles: (@Composable (androidx.compose.ui.focus.FocusRequester, androidx.compose.ui.focus.FocusRequester, Float) -> Unit)? = null,
    tvDevices: (@Composable (androidx.compose.ui.focus.FocusRequester, androidx.compose.ui.focus.FocusRequester, Float, () -> Unit) -> Unit)? = null,
    browser: Boolean = false,
    xtreamConnections: (@Composable (Boolean, (() -> Unit)?) -> Unit)? = null,
    tvXtreamConnections: (@Composable (androidx.compose.ui.focus.FocusRequester, androidx.compose.ui.focus.FocusRequester, Float) -> Unit)? = null,
    vodMatches: (@Composable (Boolean) -> Unit)? = null,
    tvVodMatches: (@Composable (androidx.compose.ui.focus.FocusRequester, androidx.compose.ui.focus.FocusRequester, Float) -> Unit)? = null,
    watchOnTvValue: String = "Off",
    watchOnTvSettings: (@Composable (Boolean, () -> Unit) -> Unit)? = null,
    addonsManageable: Boolean = true,
    addonLogos: Map<String, String> = emptyMap()) {
    if (television) {
        TvSettingsScreen(local, addons, enabledAddonIds, busy, error, sessionNotice, install, toggle, profiles, leave, profile, initialSection = TvSettingsSection.entries.firstOrNull { it.label == initialSection && !it.leavesSettings } ?: TvSettingsSection.Playback, sourceIntegrations = sourceIntegrations, gateways = gateways, prefs = tvPreferences, change = changeTvPreferences, actualPreferencesOnly = true, parental = tvParental, account = tvAccount, accountRequested = accountRequested, accountDashboard = accountDashboard, dashboardAddress = serverAddress, preferencesError = preferencesError, retryPreferences = retryPreferences, installWithCompletion = installWithCompletion, devicePlayback = tvDevicePlayback,
            profilePage = tvProfilePage, profileTitles = tvProfileTitles, tvDevices = tvDevices, xtream = tvXtreamConnections, vodMatches = tvVodMatches,
            addonLogos = addonLogos,
            addonsManageable = addonsManageable)
        return
    }
    val unusedRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    val phoneSourceIntegrations: (@Composable () -> Unit)? = sourceIntegrations?.let { render -> { render(unusedRequester, unusedRequester, 1f) } }
    val phoneGateways: (@Composable () -> Unit)? = gateways?.let { render -> { render(unusedRequester, unusedRequester, 1f) } }
    var localPage by remember { mutableStateOf<SettingsPage?>(null) }
    val watchOnTv = rememberWatchOnTvAvailable()
    val savedPage = if (pageChanged != null) page else localPage
    // A session that cannot approve a device keeps no page for it, so a sign-in link that arrived before the role was known lands on the list.
    val current = (savedPage ?: SettingsPage(initialSection)).takeUnless { it.section == addDeviceSection && addDevice == null } ?: SettingsPage()
    val section = current.section
    val show: (SettingsPage) -> Unit = pageChanged ?: { localPage = it }
    var leaving by remember { mutableStateOf(initialLeave) }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    var addonsLoaded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { accountAccessRequested?.invoke() }
    LaunchedEffect(Unit) { addonsRequested?.invoke(); addonsLoaded = true }
    LaunchedEffect(section) { if (section == "Add-ons" && addonsLoaded) addonsRequested?.invoke() }
    LaunchedEffect(section) { if (section == "Gateways") gatewaysRequested?.invoke() }
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
            account != null, devices != null, addDevice != null, information = true,
            householdPin = householdPin != null, sourceIntegrations = sourceIntegrations != null,
            deleteAccount = deleteAccount != null, linkedServices = linkedServices != null, password = password != null,
            xtreamConnections = xtreamConnections != null, gateways = gateways != null, vodMatches = vodMatches != null,
            watchOnTv = watchOnTv)
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
        Row(Modifier.fillMaxSize().then(if (webAccount) Modifier.padding(horizontal = pageGutter(television = false, tablet = false, browser = browser, maxWidth), vertical = 32.dp) else Modifier)) {
            if (webAccount) {
                AccountSettingsSidebar(section ?: SettingsRow.Addons.key, profile, settingsSections(rows), local, profiles, { open(it) }, { leaving = true }); Spacer(Modifier.width(48.dp))
            } else if (!compact) SettingsSidebar(section ?: SettingsRow.Addons.key, profile, layout, settingsSections(rows), local, profiles, { open(it) }, { leaving = true })
            Box(Modifier.weight(1f, fill = !webAccount).then(if (webAccount) Modifier.widthIn(max = 760.dp) else Modifier)) {
                when {
                    compact && section == null -> PhoneSettings(profile, addons.size, local, sessionNotice, profiles,
                        { open("Add-ons") }, { leaving = true }, back = leaveSettings, account = account?.let { { open("Account") } }, devices = devices?.let { { open("Devices") } }, stremioImport = stremioImport?.let { { open(stremioImportSection) } }, preferences = if (preferences != null || sectionPreferences != null) ({ open(it) }) else null, parental = parental?.let { { open("Parental controls") } }, information = { open(it) }, addDevice = addDevice?.let { { open(addDeviceSection) } }, householdPin = householdPin?.let { { open("Household PIN") } }, sourceIntegrations = if (sourceIntegrations != null) ({ open("Source integrations") }) else null, xtreamConnections = if (xtreamConnections != null) ({ open(xtreamConnectionsSection) }) else null, gateways = if (gateways != null) ({ open("Gateways") }) else null, password = password?.let { { open("Password") } }, deleteAccount = deleteAccount?.let { { open("Delete account") } }, profilePage = profilePage?.let { { open("Profile") } }, linkedServices = linkedServices?.let { { open("Linked services") } }, vodMatches = vodMatches?.let { { open("VOD matches") } }, watchOnTv = watchOnTv, watchOnTvValue = watchOnTvValue, openWatchOnTv = if (watchOnTvSettings != null) ({ open("Watch on TV") }) else null, listState = listState)
                    (section ?: "Add-ons") == "Add-ons" -> AddonSettings(local, addons, enabledAddonIds, busy, error,
                        sessionNotice, install, toggle, remove, if (compact) back else null,
                        managementAvailable = addonsManageable,
                        logos = addonLogos,
                        installWithCompletion = installWithCompletion)
                    section == "Profile" && profileTitles != null -> SettingsDetailContent(compact, profileTitlesBack ?: back) { profileTitles(compact) }
                    section == "Profile" && profilePage != null -> SettingsDetailContent(compact, back, title = "Profile") { profilePage(layout) }
                    section == "Linked services" && linkedServices != null -> SettingsDetailContent(compact, back, title = "Linked services") { linkedServices(compact) }
                    section == "Parental controls" && parental != null -> SettingsDetailContent(compact, parentalBack ?: back, title = if (parentalBack == null) "Parental controls" else null, content = parental)
                    section == "Account" && account != null -> SettingsDetailContent(compact, back, title = "Account", content = account)
                    section == "Password" && password != null -> SettingsDetailContent(compact, back, title = "Change password") {
                        password(if (compact) back else ({ open("Account") }))
                    }
                    section == "Household PIN" && householdPin != null -> SettingsDetailContent(compact, back, title = "Household PIN", content = householdPin)
                    section == "Delete account" && deleteAccount != null -> SettingsDetailContent(compact, back, title = "Delete account") {
                        val cancel: () -> Unit = if (compact) back else ({ open("Account") })
                        deleteAccount(cancel)
                    }
                    section == "Source integrations" && phoneSourceIntegrations != null -> SettingsDetailContent(compact, back, title = "Source integrations") {
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(if (compact) 16.dp else 32.dp).pageBottomClearance()) {
                            phoneSourceIntegrations.invoke()
                        }
                    }
                    section == xtreamConnectionsSection && xtreamConnections != null -> xtreamConnections(compact, if (compact) back else null)
                    section == "VOD matches" && vodMatches != null -> SettingsDetailContent(compact, back, title = "VOD matches") { vodMatches(compact) }
                    section == "Gateways" && phoneGateways != null -> SettingsDetailContent(compact, back, title = "Playback gateways") {
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(if (compact) 16.dp else 32.dp)) {
                            phoneGateways.invoke()
                        }
                    }
                    section == "Devices" && devices != null -> devices(compact) { if (compact) back() else open("Account") }
                    section == stremioImportSection && stremioImport != null -> Box(if (!compact && !webAccount) Modifier.padding(32.dp) else Modifier) {
                        stremioImport(compact, { if (compact) back() else open("Account") }) { open("Add-ons") }
                    }
                    section in listOf("Playback", "Subtitles & audio") && (preferences != null || sectionPreferences != null) -> SettingsDetailContent(compact, back, title = requireNotNull(section), backLabel = backLabel) {
                        if (sectionPreferences != null) sectionPreferences(requireNotNull(section), compact) else preferences?.invoke()
                    }
                    section == addDeviceSection && addDevice != null -> SettingsDetailContent(compact, back) { addDevice(compact) { leaving = true } }
                    section == "Watch on TV" && watchOnTvSettings != null -> SettingsDetailContent(compact, back, title = "Watch on TV") { watchOnTvSettings.invoke(compact, back) }
                    section in listOf("Help", "About") -> SettingsDetailContent(compact, back, title = if (section == "About") "About VIPTV" else "Help") {
                        SettingsInformation(requireNotNull(section), local, serverAddress, compact)
                    }
                }
            }
        }
        if (leaving) SettingsLeaveDialog(local, compact, busy, error, { leaving = false }, leave)
    }
}

internal const val stremioImportSection = "Import from Stremio"
internal const val addDeviceSection = "Add a device"
internal const val xtreamConnectionsSection = "Xtream connections"

@Composable
internal fun SettingsPageHeader(compact: Boolean, back: () -> Unit, title: String, backLabel: String? = null) {
    if (!compact && backLabel == null) return
    Box(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = if (compact) 16.dp else 0.dp)) {
        BackControl(back, Modifier.align(Alignment.CenterStart), label = backLabel, desktop = !compact)
        if (compact) Text(title, Modifier.align(Alignment.Center), fontSize = 17.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SettingsDetailContent(compact: Boolean, back: () -> Unit, title: String? = null, backLabel: String? = null, content: @Composable () -> Unit) {
    val scrolled = remember { mutableStateOf(false) }
    val connection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < 0f) scrolled.value = true
                return Offset.Zero
            }
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y > 0f) scrolled.value = false
                return Offset.Zero
            }
        }
    }
    Column(Modifier.fillMaxSize()) {
        if (title != null) SettingsPageHeader(compact, back, title, backLabel)
        else if (compact || backLabel != null) Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = if (compact) 16.dp else 0.dp),
            verticalAlignment = Alignment.CenterVertically) {
            BackControl(back, label = backLabel, desktop = !compact)
        }
        Box(Modifier.weight(1f).nestedScroll(connection)) {
            content()
            if (LocalBarsFadeContent.current && scrolled.value) ScrollEdgeFade(ScrollEdge.Top, Modifier.align(Alignment.TopCenter))
        }
    }
}
