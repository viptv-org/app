package org.viptv.app

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import org.viptv.app.features.home.CardActions
import org.viptv.app.features.home.LocalCardActions
import org.viptv.app.state.*
import org.viptv.app.theme.ViptvTheme

internal class DesktopWindowControls(
    val minimize: () -> Unit,
    val maximize: () -> Unit,
    val close: () -> Unit,
    val maximized: Boolean = false,
)

@Composable
internal fun DesktopShell(
    controller: ApplicationController,
    globalBack: GlobalBack,
    mac: Boolean,
    controls: DesktopWindowControls,
    localDatabase: String? = null,
    searchFocus: FocusRequester? = null,
    manageControllerLifecycle: Boolean = true,
    titleBarArea: @Composable (@Composable () -> Unit) -> Unit = { it() },
) {
    val state by controller.state.collectAsState()
    var menuOpen by remember { mutableStateOf(false) }
    var offlineDismissed by remember { mutableStateOf(false) }
    // A reconnect (or a different failure) clears the flag, so the next outage shows the bar again.
    LaunchedEffect(state.error) { if (state.error != ServerUnreachableMessage) offlineDismissed = false }
    ViptvTheme(pointer = true) {
        DesktopBackInputs(globalBack, state) {
            Column(Modifier.fillMaxSize()) {
                if (state.screen == Screen.Player) {
                    Box(Modifier.fillMaxSize()) {
                        App(localDatabase = localDatabase, desktopChrome = true, desktopMenuOpen = menuOpen, providedController = controller,
                            manageControllerLifecycle = manageControllerLifecycle, entryBackChanged = globalBack.routeBackChanged)
                        DesktopWindowButtons(mac, controls.minimize, controls.maximize, controls.close, Modifier.align(Alignment.TopEnd), maximized = controls.maximized)
                    }
                } else {
                    titleBarArea {
                        DesktopTitleBar(state, mac, controller::navigate, controller::search,
                            { menuOpen = !menuOpen }, controls.minimize, controls.maximize, controls.close, Modifier,
                            back = if (globalBack.canGoBack(state)) globalBack::back else null,
                            forward = if (globalBack.canGoForward(state)) globalBack::forward else null,
                            searchFocus = searchFocus,
                            maximized = controls.maximized)
                    }
                    Box(Modifier.weight(1f)) {
                        CompositionLocalProvider(LocalCardActions provides CardActions(
                            play = controller::playTitle,
                            toggleSave = controller::toggleFavorite,
                            isSaved = { state.isSaved(it) })) {
                            App(localDatabase = localDatabase, desktopChrome = true, desktopMenuOpen = menuOpen, providedController = controller,
                                manageControllerLifecycle = manageControllerLifecycle, entryBackChanged = globalBack.routeBackChanged)
                        }
                        if (state.screen == Screen.Home && state.error == ServerUnreachableMessage && !offlineDismissed) DesktopOfflineBar(
                            retry = controller::refresh,
                            dismiss = { offlineDismissed = true },
                            modifier = Modifier.align(Alignment.TopStart).padding(start = 72.dp))
                    }
                }
            }
        }
    }
}
