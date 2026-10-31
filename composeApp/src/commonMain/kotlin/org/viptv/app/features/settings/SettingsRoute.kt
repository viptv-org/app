package org.viptv.app.features.settings

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.dp
import org.viptv.app.state.*
import org.viptv.app.connection.ProfilePreferences
import org.viptv.app.connection.addonLogo

@Composable
internal fun SettingsRoute(state: ApplicationState, controller: ApplicationController, browser: Boolean, television: Boolean, compact: Boolean = false,
    desktop: Boolean = false, entryBackChanged: ((() -> Unit)?) -> Unit = {}) {
    val returnsToPlayer = state.settingsReturnsToPlayer
    val backStaysInSettings = state.parentTitles != null || state.settingsPage?.subpage == true || returnsToPlayer
    DisposableEffect(backStaysInSettings) {
        entryBackChanged(if (backStaysInSettings) controller::navigateBack else null)
        onDispose { entryBackChanged(null) }
    }
    val deviceLink by controller.deviceLink.collectAsState()
    val devicePlayback = rememberDevicePlaybackState()
    val leave = { if (state.localMode) controller.leaveLocal() else controller.signOut() }
    val remote = controller.connection.takeUnless { state.localMode }
    val logos = remember(remote) { remote?.let { connection -> AddonLogoSource { connection.addonLogo(it) } } }
    CompositionLocalProvider(LocalAddonLogos provides logos) {
    val escapeReturns = returnsToPlayer && (desktop || browser) && !television
    val keyboard = remember { FocusRequester() }
    var pageFocused by remember { mutableStateOf(false) }
    if (escapeReturns) LaunchedEffect(Unit) { keyboard.requestFocus() }
    Box(if (escapeReturns) Modifier.focusRequester(keyboard).onFocusChanged { pageFocused = it.isFocused }.focusable().onKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown || event.key != Key.Escape) false
        else { if (pageFocused) controller.navigateBack() else keyboard.requestFocus(); true }
    } else Modifier) {
    SettingsScreen(state.localMode, state.addons, state.enabledAddonIds, state.busy, state.error, state.sessionNotice,
        controller::installAddon, controller::toggleAddon, controller::removeAddon,
        { controller.navigate(Screen.Profiles) }, leave,
        state.profiles.find { it.id == state.selectedProfile }, controller::navigateBack, television,
        initialSection = state.settingsEntrySection ?: if (browser && !compact) "Devices" else null,
        page = state.settingsPage, pageChanged = controller::showSettingsPage,
        desktop = desktop,
        profilePage = { layout -> ProfilePageRoute(state, controller, layout) },
        profileTitles = if (state.parentTitles != null) ({ compact ->
            ParentTitlesSettings(requireNotNull(state.parentTitles), state.busy, state.error, controller::closeParentTitles,
                controller::searchParentTitles, controller::refreshApprovedTitles, controller::approveParentTitle,
                controller::revokeParentTitle, backLabel = "Back to Profile")
        }) else null,
        profileTitlesBack = controller::closeParentTitles,
        linkedServices = if (!state.localMode) ({ compact ->
            LinkedServicesSettings(state.simkl.takeIf { state.linkedServicesProfile == state.selectedProfile },
                state.profiles.find { it.id == state.selectedProfile }?.name, compact, state.busy, state.error,
                controller::refreshLinkedServices, controller::linkLinkedService, controller::unlinkLinkedService)
        }) else null,
        tvProfilePage = ({ first, sidebar, scale, openSection -> TvProfilePageRoute(state, controller, scale, first, sidebar, openSection) }),
        tvProfileTitles = if (state.parentTitles != null) ({ first, sidebar, scale ->
            ParentTitlesSettings(requireNotNull(state.parentTitles), state.busy, state.error, controller::closeParentTitles,
                controller::searchParentTitles, controller::refreshApprovedTitles, controller::approveParentTitle,
                controller::revokeParentTitle, first, backLabel = "Back to Profile")
        }) else null,
        installWithCompletion = controller::installAddon,
        addonsRequested = if (browser) controller::refreshAddonConfiguration else null,
        householdPin = if (!state.localMode) ({ HouseholdPinSettings(state.parentSettings, state.busy, state.error,
            state.parentSettingsNotice, state.parentSettingsSaved, controller::refreshParentControls,
            controller::unlockParentControls, controller::changeParentPin) }) else null,
        deleteAccount = controller.connection.takeUnless { state.localMode }?.let { _ -> { cancel: () -> Unit ->
            DeleteAccountScreen(state.accountIdentity, state.parentSettings?.parent?.pin_configured,
                state.busy, state.error, controller::refreshAccountDeletion, cancel,
                controller::deleteAccount)
        } },
        account = if (state.localMode) null else ({ AccountIdentityScreen(state.accountIdentity ?: state.accountDevices?.account,
            state.busy, state.error, controller::refreshAccounts) }),
        sourceIntegrations = { first, sidebar, scale -> Column(verticalArrangement = Arrangement.spacedBy(24.dp * scale)) {
            BrowsingProviderSettings(state.browsingConfiguration, state.plugins, state.busy, controller::selectBrowsingProvider, tv = television, scale = scale)
            SourceIntegrationSettings(state.sourceIntegrations, state.busy, state.sourceIntegrationError,
            controller::refreshSourceIntegrations, controller::configureSourceIntegration, controller::removeSourceIntegration, television = television,
            profileName = state.profiles.find { it.id == state.selectedProfile }?.name,
            origin = controller.connection?.accountDashboardUrl?.trimEnd('/'), tvScale = scale, first = first, left = sidebar) } },
        preferences = { ProfilePreferencesSettings(state.preferences, state.busy, state.preferencesError ?: state.error, controller::refreshPreferences, controller::updatePreferences,
            devicePlayback) },
        preferencesError = state.preferencesError, retryPreferences = controller::refreshPreferences,
        sectionPreferences = { section, compact ->
            if (compact && !browser) PhoneProfilePreferences(section, state.preferences, state.busy, state.preferencesError ?: state.error,
                controller::refreshPreferences, controller::updatePreferences, devicePlayback)
            else ProfilePreferencesSettings(state.preferences, state.busy, state.preferencesError ?: state.error,
                controller::refreshPreferences, controller::updatePreferences, devicePlayback)
        },
        devices = if (browser && !state.localMode) ({ compact, back -> AccountDevicesScreen(state.accountDevices, state.busy, state.error,
            controller::refreshAccountDevices, controller::signOutDevice, controller::signOutAllDevices, compact, if (compact) back else null) }) else null,
        addDevice = controller.connection.takeUnless { state.localMode }?.let { remote -> { compact: Boolean -> AddDeviceScreen(remote, compact, deviceLink?.takeIf { it.delivered }?.code, controller::consumeDeviceCode) } },
        stremioImport = if (stremioImportAvailable(state, television, desktop)) ({ compact, back, manageAddons ->
            StremioImportRoute(state, controller, compact, if (compact) back else null, manageAddons) }) else null,
        serverAddress = controller.connection?.accountDashboardUrl,
        tvAccount = (state.accountIdentity ?: state.accountDevices?.account)?.let { TvAccountInfo(it.name, it.username, it.role) },
        accountRequested = if (!state.localMode) controller::refreshAccountIdentity else null,
        accountDashboard = controller.connection?.accountDashboardUrl?.let { url -> { org.viptv.app.connection.openAccountAuthorization(url) } },
        tvPreferences = state.preferences?.preferences?.tvSettings(),
        changeTvPreferences = { controller.updatePreferences(it.profilePreferences(state.preferences?.preferences ?: ProfilePreferences())) },
        parentalBack = if (state.parentTitles != null) controller::closeParentTitles else null,
        parental = {
            if (!state.localMode && state.parentTitles != null) ParentTitlesSettings(state.parentTitles, state.busy, state.error,
                controller::closeParentTitles, controller::searchParentTitles, controller::refreshApprovedTitles,
                controller::approveParentTitle, controller::revokeParentTitle)
            else if (!state.localMode) ParentControlsSettings(state.parentSettings, state.busy, state.error,
                state.parentSettingsNotice, state.parentSettingsSaved, controller::refreshParentControls,
                controller::unlockParentControls, controller::changeParentPin, controller::changeKidsPolicy, controller::openParentTitles)
            else ParentControlsNative { controller.navigate(Screen.Profiles) }
        },
        tvParental = if (state.localMode) null else ({ first: FocusRequester, sidebar: FocusRequester, scale: Float ->
            if (state.parentTitles != null) ParentTitlesSettings(state.parentTitles, state.busy, state.error,
                controller::closeParentTitles, controller::searchParentTitles, controller::refreshApprovedTitles,
                controller::approveParentTitle, controller::revokeParentTitle, first)
            else TvParentControlsSettings(state.parentSettings, state.busy, state.error, state.parentSettingsNotice,
                state.parentSettingsSaved, controller::refreshParentControls, controller::unlockParentControls,
                controller::changeParentPin, controller::changeKidsPolicy, controller::openParentTitles, first, sidebar, scale)
        }),
        tvParentalMaturity = state.profileManagement?.profiles?.find { it.id == state.selectedProfile }?.let {
            if (it.kids) it.max_age?.let { age -> "Up to age $age" } ?: "Kids restrictions" else "No profile restriction"
        },
        tvParentalPinAvailable = state.localMode,
        returnToPlayer = if (returnsToPlayer) controller::navigateBack else null, tvDevicePlayback = devicePlayback)
    }
    }
}

internal fun stremioImportAvailable(state: ApplicationState, television: Boolean, desktop: Boolean) =
    !television && !desktop && !state.localMode

@Composable
private fun StremioImportRoute(state: ApplicationState, controller: ApplicationController, compact: Boolean, back: (() -> Unit)?,
    manageAddons: () -> Unit) {
    val profile = state.profiles.find { it.id == state.selectedProfile }
    val restricted = state.profileManagement?.profiles?.find { it.id == state.selectedProfile }?.kids == true
    val remote = controller.connection
    val session = remember(controller, remote, state.selectedProfile, restricted) { controller.openStremioImport(restricted) }
    DisposableEffect(session) { onDispose { session?.let(controller::closeStremioImport) } }
    StremioImportScreen(session, profile?.name, compact, back, manageAddons)
}
