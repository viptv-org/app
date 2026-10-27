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

/** The desktop window's controls that belong to the window, not to the app. */
internal class DesktopWindowControls(
    val minimize: () -> Unit,
    val maximize: () -> Unit,
    val close: () -> Unit,
    val maximized: Boolean = false,
)

/**
 * Everything inside the desktop window: the title bar, the rail and the app,
 * with the window-wide Back inputs and the offline bar. The window itself
 * ([DesktopAppWindow]) only frames it, so a test can run this exact chrome.
 *
 * [titleBarArea] wraps the title bar; the window passes its drag area there.
 */
@Composable
internal fun DesktopShell(
    controller: ApplicationController,
    globalBack: GlobalBack,
    mac: Boolean,
    window: DesktopWindowControls,
    localDatabase: String? = null,
    searchFocus: FocusRequester? = null,
    manageControllerLifecycle: Boolean = true,
    titleBarArea: @Composable (@Composable () -> Unit) -> Unit = { it() },
) {
    val state by controller.state.collectAsState()
    var menuOpen by remember { mutableStateOf(false) }
    var offlineDismissed by remember { mutableStateOf(false) }
    // A reconnect (or a different failure) clears the flag, so the next outage
    // shows the bar again.
    LaunchedEffect(state.error) { if (state.error != ServerUnreachableMessage) offlineDismissed = false }
    ViptvTheme(pointer = true) {
        DesktopBackInputs(globalBack, state) {
            Column(Modifier.fillMaxSize()) {
                if (state.screen == Screen.Player) {
                    Box(Modifier.fillMaxSize()) {
                        App(localDatabase = localDatabase, desktopChrome = true, desktopMenuOpen = menuOpen, providedController = controller,
                            manageControllerLifecycle = manageControllerLifecycle, entryBackChanged = globalBack.routeBackChanged)
                        DesktopWindowButtons(mac, window.minimize, window.maximize, window.close, Modifier.align(Alignment.TopEnd), maximized = window.maximized)
                    }
                } else {
                    titleBarArea {
                        DesktopTitleBar(state, mac, controller::navigate, controller::search,
                            { menuOpen = !menuOpen }, window.minimize, window.maximize, window.close, Modifier,
                            back = if (globalBack.canGoBack(state)) globalBack::back else null,
                            forward = if (globalBack.canGoForward(state)) globalBack::forward else null,
                            searchFocus = searchFocus,
                            maximized = window.maximized)
                    }
                    Box(Modifier.weight(1f)) {
                        // DUI-04: the card hover preview and right-click menu need
                        // Play / My List; publishing them once here keeps
                        // HomeCatalogRow and MediaCard signatures untouched.
                        CompositionLocalProvider(LocalCardActions provides CardActions(
                            play = controller::playTitle,
                            toggleSave = controller::toggleFavorite,
                            isSaved = { state.isSaved(it) })) {
                            App(localDatabase = localDatabase, desktopChrome = true, desktopMenuOpen = menuOpen, providedController = controller,
                                manageControllerLifecycle = manageControllerLifecycle, entryBackChanged = globalBack.routeBackChanged)
                        }
                        // Board D3Offline: overlays the content area below the title
                        // bar and leaves the 72 dp rail visible. Home only — that is
                        // the page whose cached rows the copy promises.
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
