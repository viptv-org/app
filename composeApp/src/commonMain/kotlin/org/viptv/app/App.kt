package org.viptv.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.viptv.app.theme.*
import org.viptv.app.state.*
import org.viptv.app.connection.browserOrigin
import org.viptv.app.features.entry.*
import org.viptv.app.features.home.HomeScreen
import org.viptv.app.features.search.SearchScreen
import org.viptv.app.features.details.DetailsScreen
import org.viptv.app.features.settings.SettingsScreen
import org.viptv.app.features.sources.SourcesScreen
import org.viptv.app.features.player.PlayerScreen
import org.viptv.app.features.history.HistoryScreen
import org.viptv.app.features.browse.BrowseScreen
import org.viptv.app.components.*
import org.viptv.engine.wire.Card

@Composable
fun App(
    television: Boolean = false,
    browser: Boolean = false,
    localDatabase: String? = null,
    desktopChrome: Boolean = false,
    desktopMenuOpen: Boolean = false,
    providedController: ApplicationController? = null,
) {
    org.viptv.app.images.ConfigureImageLoading()
    val scope = rememberCoroutineScope()
    val controller = providedController ?: remember { ApplicationController(scope) }
    val state by controller.state.collectAsState()
    LaunchedEffect(controller) { controller.restore() }
    DisposableEffect(controller) { onDispose { controller.disconnect() } }
    ViptvTheme {
        BoxWithConstraints(Modifier.fillMaxSize().background(Palette.background).windowInsetsPadding(WindowInsets.safeDrawing)) {
            val compact = maxWidth < 640.dp && !television
            when (state.screen) {
                Screen.Connect -> ConnectScreen(browser, browserOrigin() ?: "", state.busy, state.error, localDatabase?.let { { controller.useLocal(it) } }, controller::connect, desktopChrome)
                Screen.Profiles -> ProfilesScreen(state.profiles, state.busy, state.error, state.localMode, state.managingProfiles, compact, desktopChrome,
                    controller::selectProfile, controller::editProfile, controller::manageProfiles, controller::stopManagingProfiles, controller::createProfile)
                Screen.ProfilePin -> ProfilePinScreen(state.profiles.find { it.id == state.profileToOpen }, compact,
                    state.busy, state.error, controller::submitProfilePin) { controller.navigate(Screen.Profiles) }
                Screen.ProfileEdit -> ProfileEditScreen(state.profiles.find { it.id == state.profileToEdit }, state.profiles.size,
                    state.busy, state.error, compact, { controller.navigate(Screen.Profiles) }, controller::renameProfile,
                    controller::setProfilePin, controller::setProfileAvatar, controller::deleteProfile)
                Screen.Player -> PlayerScreen(state.playback, state.paused, state.error, controller::pausePlayback, controller::observePlayback, controller::closePlayer)
                else -> Box(Modifier.fillMaxSize()) {
                  Column(Modifier.fillMaxSize()) {
                    if (browser || television) TopNavigation(state.screen, television, controller::navigate)
                    else if (!desktopChrome) Row(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 20.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Brand(); Spacer(Modifier.weight(1f)); TextButton(onClick = { controller.navigate(Screen.Settings) }) { Text("Settings") }
                    }
                    Row(Modifier.weight(1f)) {
                        if (!browser && !television && (!compact || (desktopChrome && desktopMenuOpen))) SideNavigation(state.screen, controller::navigate)
                        when (state.screen) {
                            Screen.Home -> HomeScreen(state.home, television, controller::open, controller::refresh)
                            Screen.Discover, Screen.Movies, Screen.Series -> BrowseScreen(state.screen, state.home, television, controller::open)
                            Screen.Search -> SearchScreen(state.searchTerm, state.results, state.addons, state.busy, state.error, desktopChrome, controller::search, controller::open)
                            Screen.Details -> DetailsScreen(state.detail, state.busy, state.error, { controller.navigate(Screen.Home) }, controller::chooseSource)
                            Screen.Sources -> SourcesScreen(state.sourceTitle, state.sources, state.busy, state.error, controller::playSource) { controller.navigate(Screen.Details) }
                            Screen.History -> if (state.localMode) HistoryScreen(state.history, state.busy, state.error, controller::open, controller::clearHistory) else Text("History is unavailable for this connection.", Modifier.padding(32.dp))
                            Screen.Settings -> SettingsScreen(state.localMode, state.addons, state.enabledAddonIds, state.busy, state.error, state.sessionNotice, controller::installAddon, controller::toggleAddon, controller::removeAddon, { controller.navigate(Screen.Profiles) }, { if (state.localMode) controller.disconnect() else controller.signOut() })
                            else -> Column(Modifier.padding(32.dp)) { Text(state.screen.name, style = MaterialTheme.typography.headlineLarge) }
                        }
                    }
                  }
                  if (compact && !browser && !desktopChrome) Box(Modifier.align(androidx.compose.ui.Alignment.BottomCenter)) {
                      BottomNavigation(state.screen, controller::navigate)
                  }
                }
            }
        }
    }
}
