package org.viptv.app.features.settings

import androidx.compose.runtime.Composable
import org.viptv.app.state.*

@Composable
internal fun SettingsRoute(state: ApplicationState, controller: ApplicationController, browser: Boolean, television: Boolean) {
    val leave = { if (state.localMode) controller.disconnect() else controller.signOut() }
    SettingsScreen(state.localMode, state.addons, state.enabledAddonIds, state.busy, state.error, state.sessionNotice,
        controller::installAddon, controller::toggleAddon, controller::removeAddon,
        { controller.navigate(Screen.Profiles) }, leave,
        state.profiles.find { it.id == state.selectedProfile }, { controller.navigate(Screen.Home) }, television,
        account = { LinkedAccounts(state.localMode, browser, state.simkl, state.busy, state.error,
            controller::refreshAccounts, controller::linkSimkl, controller::unlinkSimkl, leave) })
}
