package org.viptv.app.features.settings

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.unit.dp
import org.viptv.app.state.*

@Composable
internal fun SettingsRoute(state: ApplicationState, controller: ApplicationController, browser: Boolean, television: Boolean) {
    val leave = { if (state.localMode) controller.disconnect() else controller.signOut() }
    SettingsScreen(state.localMode, state.addons, state.enabledAddonIds, state.busy, state.error, state.sessionNotice,
        controller::installAddon, controller::toggleAddon, controller::removeAddon,
        { controller.navigate(Screen.Profiles) }, leave,
        state.profiles.find { it.id == state.selectedProfile }, { controller.navigate(Screen.Home) }, television,
        account = { LinkedAccounts(state.localMode, browser, state.simkl, state.busy, state.error,
            controller::refreshAccounts, controller::linkSimkl, controller::unlinkSimkl, leave) },
        sourceIntegrations = { Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
            BrowsingProviderSettings(state.browsingConfiguration, state.plugins, state.busy, controller::selectBrowsingProvider)
            SourceIntegrationSettings(state.sourceIntegrations, state.busy, state.sourceIntegrationError,
            controller::refreshSourceIntegrations, controller::configureSourceIntegration, controller::removeSourceIntegration) } },
        preferences = { ProfilePreferencesSettings(state.preferences, state.busy, state.preferencesError ?: state.error, controller::refreshPreferences, controller::updatePreferences) },
        devices = if (browser && !state.localMode) ({ compact, back -> AccountDevicesScreen(state.accountDevices, state.busy, state.error,
            controller::refreshAccountDevices, controller::signOutDevice, controller::signOutAllDevices, compact, if (compact) back else null) }) else null,
        tvPreferences = state.preferences?.preferences?.tvSettings(),
        changeTvPreferences = { controller.updatePreferences(it.profilePreferences()) })
}
