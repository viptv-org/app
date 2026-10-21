package org.viptv.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
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
    manageControllerLifecycle: Boolean = true,
    imageDiagnostics: ((String) -> Unit)? = null,
    entryBackChanged: ((() -> Unit)?) -> Unit = {},
    /** Prefilled server address for native sign-in; browsers always use their own origin. */
    defaultServer: String = "",
) {
    org.viptv.app.images.ConfigureImageLoading(imageDiagnostics)
    val scope = rememberCoroutineScope()
    val controller = providedController ?: remember { ApplicationController(scope) }
    val state by controller.state.collectAsState()
    val deviceLink by controller.deviceLink.collectAsState()
    val routes = rememberSaveableStateHolder()
    LaunchedEffect(controller, manageControllerLifecycle, localDatabase) { if (manageControllerLifecycle) controller.restore(localDatabase) }
    // A device sign-in link opens Add a device only once signed in with a profile chosen.
    LaunchedEffect(deviceLink, state.screen, state.starting) { controller.deliverDeviceCode() }
    DisposableEffect(controller, manageControllerLifecycle) { onDispose { if (manageControllerLifecycle) controller.disconnect() } }
    // Android start/stop, desktop minimise/restore and window focus, browser tab visibility.
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(controller, lifecycle, manageControllerLifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_START -> controller.appForeground(true)
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> controller.freshen()
                androidx.lifecycle.Lifecycle.Event.ON_STOP -> controller.appForeground(false)
                else -> Unit
            }
        }
        if (manageControllerLifecycle) lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); if (manageControllerLifecycle) controller.appForeground(false) }
    }
    ViptvTheme(television) {
        BoxWithConstraints(Modifier.fillMaxSize()
            // Observed, never consumed: an aged Home waits until the viewer stops interacting.
            .onPreviewKeyEvent { controller.userActivity(); false }
            .pointerInput(controller) { awaitPointerEventScope { while (true) { awaitPointerEvent(PointerEventPass.Initial); controller.userActivity() } } }
            .background(if (browser && state.screen == Screen.Player) androidx.compose.ui.graphics.Color.Transparent else Palette.background).windowInsetsPadding(WindowInsets.safeDrawing)) {
            if (state.starting) {
                if (television) {
                    DisposableEffect(controller) { entryBackChanged(controller::disconnect); onDispose { entryBackChanged(null) } }
                    TvSplash(controller::disconnect)
                } else {
                    // Restore-in-progress placeholder: never flash the login
                    // screen while the browser session is being validated.
                    Box(Modifier.fillMaxSize().background(Palette.background))
                }
                return@BoxWithConstraints
            }
            val compact = maxWidth < 640.dp && !television
            val tablet = !compact && !television && !browser && !desktopChrome
            when (state.screen) {
                Screen.Connect -> ConnectScreen(browser, browserOrigin() ?: defaultServer, state.busy, state.error, localDatabase?.let { { controller.useLocal(it) } }, controller::connect, desktopChrome, compact && !television && !browser && !desktopChrome, television, entryBackChanged,
                    pairing = state.devicePairing, startPairing = controller::startDevicePairing, cancelPairing = controller::cancelDevicePairing,
                    register = if (browser) ({ username, name, password -> controller.registerAccount(browserOrigin().orEmpty(), username, name, password) }) else null,
                    recover = if (browser) ({ username, code, password -> controller.recoverAccount(browserOrigin().orEmpty(), username, code, password) }) else null)
                Screen.RecoveryCodes -> RecoveryCodesScreen(state.recoveryKit, state.busy, state.error, controller::finishAccountEntry, controller::disconnect, television = television)
                Screen.Profiles -> Box(Modifier.fillMaxSize()) {
                    ProfilesScreen(state.profiles, state.busy, state.error, state.localMode || state.profileManagement?.canManage == true, state.managingProfiles, compact, desktopChrome,
                        controller::selectProfile, controller::editProfile, controller::manageProfiles, controller::stopManagingProfiles, controller::createProfile, television,
                        back = if (state.selectedProfile != null) ({ controller.navigate(Screen.Home) }) else null)
                }
                Screen.ProfilePin -> ProfilePinScreen(state.profiles.find { it.id == state.profileToOpen }, compact,
                    state.busy, state.error, controller::submitProfilePin, { controller.navigate(Screen.Profiles) }, television)
                Screen.ProfileEdit -> ProfileEditScreen(state.profiles.find { it.id == state.profileToEdit }, state.profiles.size,
                    state.busy, state.error, compact, { controller.navigate(Screen.Profiles) }, controller::renameProfile,
                    controller::setProfilePin, controller::setProfileAvatar, controller::deleteProfile, television, entryBackChanged, pinAvailable = state.localMode,
                    editingCurrent = state.profileToEdit == state.selectedProfile, openSettings = controller::profileSettings,
                    language = state.preferences?.takeIf { state.profileToEdit == state.selectedProfile }?.preferences?.let { it.preferred_audio_language ?: "Original audio" },
                    maturity = state.profileManagement?.profiles?.find { it.id == state.profileToEdit }?.let { if(it.kids)it.max_age?.let { age -> "Up to age $age" } ?: "Kids restrictions" else "No profile restriction" })
                Screen.Player -> PlayerRoute(state, controller, television, !browser && !television && !desktopChrome, browser, entryBackChanged)
                else -> Box(Modifier.fillMaxSize()) {
                  Column(Modifier.fillMaxSize()) {
                    if (browser || (television && state.screen !in listOf(Screen.Home, Screen.Details, Screen.Sources, Screen.Settings))) TopNavigation(state.screen, television, controller::navigate, state.profiles.find { it.id == state.selectedProfile })
                    else if (!desktopChrome && !browser && !television && compact && state.screen !in listOf(Screen.Details, Screen.Sources, Screen.History, Screen.Search, Screen.Settings)) PhoneHeader(state.screen, state.profiles.find { it.id == state.selectedProfile }, controller::navigate)

                    Row(Modifier.weight(1f)) {
                        if (tablet && state.screen !in listOf(Screen.Details, Screen.Sources)) TabletNavigation(state.screen, state.profiles.find { it.id == state.selectedProfile }, controller::navigate)
                        else if (desktopChrome && (!compact || desktopMenuOpen)) SideNavigation(
                            if (state.screen == Screen.Details || state.screen == Screen.Sources) state.detailReturnScreen else state.screen, controller::navigate)
                        when (state.screen) {
                            Screen.Home -> HomeScreen(state.home, television, controller::open, controller::refresh, desktopChrome, busy = state.busy, error = state.error,
                                play = controller::playTitle, tablet = tablet, navigate = controller::navigate, browser = browser, profile = state.profiles.find { it.id == state.selectedProfile }, backChanged = entryBackChanged, save = controller::toggleFavorite, saved = { card -> state.isSaved(card) })
                            // W3Browse: the browser renders the rail-and-content
                            // page; phone and desktop keep the shared inline chips.
                            Screen.Discover, Screen.Movies, Screen.Series, Screen.Anime ->
                                if (browser) org.viptv.app.features.browse.BrowserBrowseRoute(state, controller)
                                else BrowseScreen(state.screen, state.home, television, controller::open, state.browseCards, state.browseControls, state.browseQuery, state.busy, state.error, controller::browseFilter, if (state.browseNext != null) controller::browseMore else null,
                                    multipleChanged = controller::browseSelections, browser = browser, applyQuery = controller::applyBrowse)
                            Screen.Search -> routes.SaveableStateProvider("${state.selectedProfile}:search") {
                                SearchScreen(state.searchTerm, state.results, state.addons, state.busy, state.error, desktopChrome,
                                    controller::search, controller::open, television, controller::editSearch, searchRows = state.searchRows)
                            }
                            // The details page stays composed while the sources sheet is
                            // open, so nothing reloads or flashes behind it.
                            Screen.Details, Screen.Sources -> routes.SaveableStateProvider("detail:${state.selectedProfile}") {
                                Box(Modifier.fillMaxSize()) {
                                    if (browser && state.screen == Screen.Details) HomeScreen(state.home, false, controller::open, controller::refresh, browser = true)
                                    DetailsScreen(state.detail, state.busy && state.screen == Screen.Details,
                                        // A failed first load lands in state.error; the details page
                                        // must show it instead of rendering blank (NAV-04).
                                        state.detailPageError ?: state.error.takeIf { state.detail == null }, controller::backDetails, controller::chooseSource, television, tablet, browser, save = controller::toggleFavorite, saved = state.isSaved(state.detail?.item), open = controller::open, loadPage = controller::detailPage, playFromStart = controller::playTitleFromStart,
                                        defaultSourceLabel = state.preferences?.preferences?.source_selection?.let { if (it == "best") "Best match" else "Always ask" })
                                    if (state.screen == Screen.Sources) SourcesScreen(state.sourceTitle, state.sources, state.busy && !state.findingSources, state.error, controller::playSource, controller::backSources, state.detail, television, tablet, loadingMore = state.findingSources, selectedSource = state.playingSource?.id, torrentOnlyProviders = state.torrentOnlySources, openingStage = state.openingStage)
                                }
                            }
                            Screen.Calendar -> CalendarScreen(state.calendarDays, state.calendarEntries, television, controller::open, { controller.navigate(Screen.Settings) }, available = !state.localMode && state.calendarAvailable, busy = state.busy, error = state.error,
                                type = state.calendarType, week = state.calendarWeek, controls = true, categoryChanged = controller::calendarCategory, weekChanged = controller::calendarWeek, watch = controller::watchCalendar)
                            Screen.Live -> org.viptv.app.features.live.LiveRoute(state, controller, television)
                            Screen.Library -> org.viptv.app.features.library.LibraryRoute(state, controller, television)
                            Screen.History -> if (television) org.viptv.app.features.history.TvHistoryScreen(state.history, state.busy, state.error,
                                controller::open, controller::clearHistory, { controller.navigate(Screen.Home) })
                            else HistoryScreen(state.history, state.busy, state.error, controller::open, controller::clearHistory, { controller.navigate(Screen.Home) })
                            Screen.Settings -> org.viptv.app.features.settings.SettingsRoute(state, controller, browser, television, compact, desktop = desktopChrome)
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
