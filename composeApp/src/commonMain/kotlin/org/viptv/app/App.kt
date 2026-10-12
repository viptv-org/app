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
import org.viptv.app.features.player.PlayerRoute
import org.viptv.app.features.history.HistoryScreen
import org.viptv.app.features.browse.BrowseScreen
import org.viptv.app.features.library.LibraryScreen
import org.viptv.app.features.calendar.CalendarScreen
import org.viptv.app.features.live.LiveScreen
import org.viptv.app.features.live.LiveChannel
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
    entryBackChanged: ((() -> Unit)?) -> Unit = {},
) {
    org.viptv.app.images.ConfigureImageLoading()
    val scope = rememberCoroutineScope()
    val controller = providedController ?: remember { ApplicationController(scope) }
    val state by controller.state.collectAsState()
    LaunchedEffect(controller) { controller.restore() }
    DisposableEffect(controller) { onDispose { controller.disconnect() } }
    ViptvTheme(television) {
        BoxWithConstraints(Modifier.fillMaxSize().background(if (browser && state.screen == Screen.Player) androidx.compose.ui.graphics.Color.Transparent else Palette.background).windowInsetsPadding(WindowInsets.safeDrawing)) {
            val compact = maxWidth < 640.dp && !television
            val tablet = !compact && !television && !browser && !desktopChrome
            when (state.screen) {
                Screen.Connect -> ConnectScreen(browser, browserOrigin() ?: "", state.busy, state.error, localDatabase?.let { { controller.useLocal(it) } }, controller::connect, desktopChrome, compact && !television && !browser && !desktopChrome, television, entryBackChanged)
                Screen.Profiles -> ProfilesScreen(state.profiles, state.busy, state.error, state.localMode, state.managingProfiles, compact, desktopChrome,
                    controller::selectProfile, controller::editProfile, controller::manageProfiles, controller::stopManagingProfiles, controller::createProfile, television)
                Screen.ProfilePin -> ProfilePinScreen(state.profiles.find { it.id == state.profileToOpen }, compact,
                    state.busy, state.error, controller::submitProfilePin, { controller.navigate(Screen.Profiles) }, television)
                Screen.ProfileEdit -> ProfileEditScreen(state.profiles.find { it.id == state.profileToEdit }, state.profiles.size,
                    state.busy, state.error, compact, { controller.navigate(Screen.Profiles) }, controller::renameProfile,
                    controller::setProfilePin, controller::setProfileAvatar, controller::deleteProfile)
                Screen.Player -> PlayerRoute(state, controller, television, !browser && !television && !desktopChrome, browser, entryBackChanged)
                else -> Box(Modifier.fillMaxSize()) {
                  Column(Modifier.fillMaxSize()) {
                    if (browser || (television && state.screen !in listOf(Screen.Home, Screen.Details, Screen.Sources, Screen.Settings))) TopNavigation(state.screen, television, controller::navigate, state.profiles.find { it.id == state.selectedProfile })
                    else if (!desktopChrome && !browser && !television && compact && state.screen !in listOf(Screen.Details, Screen.Sources, Screen.History, Screen.Search, Screen.Settings)) PhoneHeader(state.screen, state.profiles.find { it.id == state.selectedProfile }, controller::navigate)

                    Row(Modifier.weight(1f)) {
                        if (tablet) TabletNavigation(state.screen, state.profiles.find { it.id == state.selectedProfile }, controller::navigate)
                        else if (desktopChrome && (!compact || desktopMenuOpen)) SideNavigation(state.screen, controller::navigate)
                        when (state.screen) {
                            Screen.Home -> HomeScreen(state.home, television, controller::open, controller::refresh, desktopChrome,
                                play = { card -> if (state.localMode) controller.chooseSource(card) else controller.open(card) }, tablet = tablet, navigate = controller::navigate, browser = browser, profile = state.profiles.find { it.id == state.selectedProfile })
                            Screen.Discover, Screen.Movies, Screen.Series -> BrowseScreen(state.screen, state.home, television, controller::open)
                            Screen.Search -> SearchScreen(state.searchTerm, state.results, state.addons, state.busy, state.error, desktopChrome, controller::search, controller::open, television)
                            Screen.Details -> DetailsScreen(state.detail, state.busy, state.error, { controller.navigate(Screen.Home) }, controller::chooseSource, television)
                            Screen.Sources -> SourcesScreen(state.sourceTitle, state.sources, state.busy, state.error, controller::playSource, { controller.navigate(if (state.detail == null) Screen.Home else Screen.Details) }, state.detail, television)
                            Screen.Calendar -> CalendarScreen(emptyList(), emptyList(), television, controller::open, { controller.navigate(Screen.Settings) })
                            Screen.Live -> LiveScreen(state.home?.rows.orEmpty().flatMap { it.cards }.filter { it.identity.kind in listOf("channel", "tv") }
                                .distinctBy { it.provider_id + ":" + it.identity.id }.map { LiveChannel(it) }, television, null, null,
                                { if (state.localMode) controller.chooseSource(it.card) else controller.open(it.card) }, { controller.navigate(Screen.Settings) })
                            Screen.Library -> LibraryScreen(emptyList(), false, television, controller::open, { controller.navigate(Screen.History) }, { controller.navigate(Screen.Home) })
                            Screen.History -> if (state.localMode) HistoryScreen(state.history, state.busy, state.error, controller::open, controller::clearHistory, { controller.navigate(Screen.Home) }) else Text("History is unavailable for this connection.", Modifier.padding(32.dp))
                            Screen.Settings -> SettingsScreen(state.localMode, state.addons, state.enabledAddonIds, state.busy, state.error, state.sessionNotice, controller::installAddon, controller::toggleAddon, controller::removeAddon, { controller.navigate(Screen.Profiles) }, { if (state.localMode) controller.disconnect() else controller.signOut() }, state.profiles.find { it.id == state.selectedProfile }, { controller.navigate(Screen.Home) }, television)
                            else -> Column(Modifier.padding(32.dp)) { Text(state.screen.name, style = MaterialTheme.typography.headlineLarge) }
                        }
                    }
                  }
                  if (compact && !browser && !desktopChrome && state.screen !in listOf(Screen.Details, Screen.Sources, Screen.Search, Screen.Settings)) Box(Modifier.align(androidx.compose.ui.Alignment.BottomCenter)) {
                      BottomNavigation(state.screen, controller::navigate)
                  }
                }
            }
        }
    }
}
