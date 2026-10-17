package org.viptv.app.features.settings

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.unit.dp
import org.viptv.app.state.*
import org.viptv.app.connection.ProfilePreferences

@Composable
internal fun SettingsRoute(state: ApplicationState, controller: ApplicationController, browser: Boolean, television: Boolean, compact: Boolean = false) {
    val leave = { if (state.localMode) controller.leaveLocal() else controller.signOut() }
    SettingsScreen(state.localMode, state.addons, state.enabledAddonIds, state.busy, state.error, state.sessionNotice,
        controller::installAddon, controller::toggleAddon, controller::removeAddon,
        { controller.navigate(Screen.Profiles) }, leave,
        state.profiles.find { it.id == state.selectedProfile }, { controller.navigate(Screen.Home) }, television,
        initialSection = state.settingsEntrySection ?: if (browser && !compact) "Devices" else null,
        installWithCompletion = controller::installAddon,
        addonsRequested = if (browser) controller::refreshAddonConfiguration else null,
        account = { LinkedAccounts(state.localMode, browser, state.simkl, state.busy, state.error,
            controller::refreshAccounts, controller::linkSimkl, controller::unlinkSimkl, leave,
            identity = state.accountIdentity ?: state.accountDevices?.account,
            dashboardAddress = controller.connection?.accountDashboardUrl) },
        sourceIntegrations = { Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
            BrowsingProviderSettings(state.browsingConfiguration, state.plugins, state.busy, controller::selectBrowsingProvider)
            SourceIntegrationSettings(state.sourceIntegrations, state.busy, state.sourceIntegrationError,
            controller::refreshSourceIntegrations, controller::configureSourceIntegration, controller::removeSourceIntegration) } },
        preferences = { ProfilePreferencesSettings(state.preferences, state.busy, state.preferencesError ?: state.error, controller::refreshPreferences, controller::updatePreferences) },
        preferencesError = state.preferencesError, retryPreferences = controller::refreshPreferences,
        sectionPreferences = { section, compact ->
            if (compact && !browser) PhoneProfilePreferences(section, state.preferences, state.busy, state.preferencesError ?: state.error,
                controller::refreshPreferences, controller::updatePreferences)
            else ProfilePreferencesSettings(state.preferences, state.busy, state.preferencesError ?: state.error,
                controller::refreshPreferences, controller::updatePreferences)
        },
        devices = if (browser && !state.localMode) ({ compact, back -> AccountDevicesScreen(state.accountDevices, state.busy, state.error,
            controller::refreshAccountDevices, controller::signOutDevice, controller::signOutAllDevices, compact, if (compact) back else null) }) else null,
        serverAddress = controller.connection?.accountDashboardUrl,
        tvAccount = (state.accountIdentity ?: state.accountDevices?.account)?.let { TvAccountInfo(username = it.username) },
        accountRequested = if (!state.localMode) controller::refreshAccountIdentity else null,
        accountDashboard = controller.connection?.accountDashboardUrl?.let { url -> { org.viptv.app.connection.openAccountAuthorization(url) } },
        tvPreferences = state.preferences?.preferences?.tvSettings(),
        changeTvPreferences = { controller.updatePreferences(it.profilePreferences(state.preferences?.preferences ?: ProfilePreferences())) },
        parentalBack = if (state.parentTitles != null) controller::closeParentTitles else null,
        parental = {
            if (browser && !state.localMode && state.parentTitles != null) ParentTitlesSettings(state.parentTitles, state.busy, state.error,
                controller::closeParentTitles, controller::searchParentTitles, controller::refreshApprovedTitles,
                controller::approveParentTitle, controller::revokeParentTitle)
            else if (browser && !state.localMode) ParentControlsSettings(state.parentSettings, state.busy, state.error,
                state.parentSettingsNotice, state.parentSettingsSaved, controller::refreshParentControls,
                controller::unlockParentControls, controller::changeParentPin, controller::changeKidsPolicy, controller::openParentTitles)
            else ParentControlsNative(state.localMode, controller.connection?.accountDashboardUrl,
                { controller.navigate(Screen.Profiles) })
        },
        tvParental = { first, sidebar, scale -> ParentControlsNative(state.localMode, controller.connection?.accountDashboardUrl,
            { controller.navigate(Screen.Profiles) }, first, sidebar, scale) })
}
