package org.viptv.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
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
    imageDiagnostics: ((String) -> Unit)? = null,
    entryBackChanged: ((() -> Unit)?) -> Unit = {},
) {
    org.viptv.app.images.ConfigureImageLoading(imageDiagnostics)
    val scope = rememberCoroutineScope()
    val controller = providedController ?: remember { ApplicationController(scope) }
    val state by controller.state.collectAsState()
    val routes = rememberSaveableStateHolder()
    LaunchedEffect(controller) { controller.restore() }
    DisposableEffect(controller) { onDispose { controller.disconnect() } }
    ViptvTheme(television) {
        BoxWithConstraints(Modifier.fillMaxSize().background(if (browser && state.screen == Screen.Player) androidx.compose.ui.graphics.Color.Transparent else Palette.background).windowInsetsPadding(WindowInsets.safeDrawing)) {
            if (television && state.starting) { TvSplash(); return@BoxWithConstraints }
            val compact = maxWidth < 640.dp && !television
            val tablet = !compact && !television && !browser && !desktopChrome
            when (state.screen) {
                Screen.Connect -> ConnectScreen(browser, browserOrigin() ?: "", state.busy, state.error, localDatabase?.let { { controller.useLocal(it) } }, controller::connect, desktopChrome, compact && !television && !browser && !desktopChrome, television, entryBackChanged,
                    pairing = state.devicePairing, startPairing = controller::startDevicePairing, cancelPairing = controller::cancelDevicePairing)
                Screen.Profiles -> ProfilesScreen(state.profiles, state.busy, state.error, state.localMode || state.profileManagement?.canManage == true, state.managingProfiles, compact, desktopChrome,
                    controller::selectProfile, controller::editProfile, controller::manageProfiles, controller::stopManagingProfiles, controller::createProfile, television)
                Screen.ProfilePin -> ProfilePinScreen(state.profiles.find { it.id == state.profileToOpen }, compact,
                    state.busy, state.error, controller::submitProfilePin, { controller.navigate(Screen.Profiles) }, television)
                Screen.ProfileEdit -> ProfileEditScreen(state.profiles.find { it.id == state.profileToEdit }, state.profiles.size,
                    state.busy, state.error, compact, { controller.navigate(Screen.Profiles) }, controller::renameProfile,
                    controller::setProfilePin, controller::setProfileAvatar, controller::deleteProfile, television, entryBackChanged, pinAvailable = state.localMode)
                Screen.Player -> PlayerRoute(state, controller, television, !browser && !television && !desktopChrome, browser, entryBackChanged)
                else -> Box(Modifier.fillMaxSize()) {
                  Column(Modifier.fillMaxSize()) {
                    if (browser || (television && state.screen !in listOf(Screen.Home, Screen.Details, Screen.Sources, Screen.Settings))) TopNavigation(state.screen, television, controller::navigate, state.profiles.find { it.id == state.selectedProfile })
                    else if (!desktopChrome && !browser && !television && compact && state.screen !in listOf(Screen.Details, Screen.Sources, Screen.History, Screen.Search, Screen.Settings)) PhoneHeader(state.screen, state.profiles.find { it.id == state.selectedProfile }, controller::navigate)

                    Row(Modifier.weight(1f)) {
                        if (tablet && state.screen !in listOf(Screen.Details, Screen.Sources)) TabletNavigation(state.screen, state.profiles.find { it.id == state.selectedProfile }, controller::navigate)
                        else if (desktopChrome && (!compact || desktopMenuOpen)) SideNavigation(state.screen, controller::navigate)
                        when (state.screen) {
                            Screen.Home -> HomeScreen(state.home, television, controller::open, controller::refresh, desktopChrome,
                                play = controller::playTitle, tablet = tablet, navigate = controller::navigate, browser = browser, profile = state.profiles.find { it.id == state.selectedProfile }, backChanged = entryBackChanged, save = controller::toggleFavorite, saved = { card -> state.isSaved(card) })
                            Screen.Discover, Screen.Movies, Screen.Series -> BrowseScreen(state.screen, state.home, television, controller::open, state.browseCards, state.browseControls, state.browseQuery, state.busy, state.error, controller::browseFilter, if (state.browseNext != null) controller::browseMore else null)
                            Screen.Search -> routes.SaveableStateProvider("${state.selectedProfile}:search") {
                                SearchScreen(state.searchTerm, state.results, state.addons, state.busy, state.error, desktopChrome,
                                    controller::search, controller::open, television, controller::editSearch)
                            }
                            Screen.Details -> Box(Modifier.fillMaxSize()) {
                                if (browser) HomeScreen(state.home, false, controller::open, controller::refresh, browser = true)
                                DetailsScreen(state.detail, state.busy, state.error ?: state.detailPageError, controller::backDetails, controller::chooseSource, television, tablet, browser, save = controller::toggleFavorite, saved = state.isSaved(state.detail?.item), open = controller::open, loadPage = controller::detailPage)
                            }
                            Screen.Sources -> SourcesScreen(state.sourceTitle, state.sources, state.busy && !state.findingSources, state.error, controller::playSource, controller::backSources, state.detail, television, tablet, loadingMore = state.findingSources, selectedSource = state.playingSource?.id)
                            Screen.Calendar -> CalendarScreen(state.calendarDays, state.calendarEntries, television, controller::open, { controller.navigate(Screen.Settings) }, available = !state.localMode, busy = state.busy, error = state.error)
                            Screen.Live -> org.viptv.app.features.live.LiveRoute(state, controller, television)
                            Screen.Library -> org.viptv.app.features.library.LibraryRoute(state, controller, television)
                            Screen.History -> HistoryScreen(state.history, state.busy, state.error, controller::open, controller::clearHistory, { controller.navigate(Screen.Home) })
                            Screen.Settings -> org.viptv.app.features.settings.SettingsRoute(state, controller, browser, television)
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
