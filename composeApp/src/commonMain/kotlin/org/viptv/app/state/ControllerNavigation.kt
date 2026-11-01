package org.viptv.app.state

import org.viptv.app.connection.*
import kotlinx.coroutines.CancellationException

fun ApplicationController.navigate(screen: Screen, record: Boolean = true, browse: BrowseQuery? = null) {
    val previousScreen = mutable.value.screen
    val navigation = ++navigationGeneration
    pageRequestJob?.let { pending ->
        pending.cancel()
        if (job === pending) {
            generation++
            updateState { it.copy(busy = false) }
        }
    }
    if (screen != Screen.Settings && mutable.value.parentTitles != null) closeParentTitles()
    if (mutable.value.screen == Screen.Calendar && screen != Screen.Calendar) {
        generation++
        job?.cancel()
        updateState { it.copy(busy = false) }
    }
    if (screen != Screen.Details) cancelDetailPaging()
    if (screen !in listOf(Screen.Details, Screen.Sources, Screen.Player)) {
        cancelSourceDetail()
        if (mutable.value.playTapTitle != null || mutable.value.detailFailure != null)
            updateState { it.copy(playTapTitle = null, detailFailure = null) }
    }
    if (screen != Screen.Sources && mutable.value.findingSources) {
        generation++
        job?.cancel()
        updateState { it.copy(findingSources = false, busy = false) }
    }
    val playerVisit = screen == Screen.Settings && mutable.value.settingsReturnsToPlayer &&
        previousScreen in listOf(Screen.Player, Screen.Settings)
    if (screen != Screen.Player && !playerVisit && mutable.value.playback != null) {
        val live = mutable.value.playback?.live == true
        if (!completionSeen) saveProgress(false, final = true); job?.cancel(); releaseRemotePlayback(); updateState { it.copy(playback = null) }
        afterPlayback(live)
    }
    val recorded = when (previousScreen) {
        Screen.Sources -> mutable.value.sourceReturnScreen
        Screen.Details -> mutable.value.detailReturnScreen
        Screen.Player -> mutable.value.detailReturnScreen
        else -> previousScreen
    }
    updateState { state ->
        val history = if (record && screen != previousScreen)
            state.copy(navBack = (state.navBack + recorded).takeLast(32), navForward = emptyList())
        else state
        history.copy(screen = screen, error = null, errorCode = null, detailRetryable = true,
            searchFocusResults = state.searchFocusResults && screen == Screen.Search,
            pendingDetailSeason = state.pendingDetailSeason.takeIf { screen == Screen.Search },
            leavePinFrom = history.leavePinFrom.takeIf { screen == Screen.ProfilePin },
            settingsEntrySection = if (screen == Screen.Settings) state.settingsEntrySection else null,
            liveSetupReturnFocus = state.liveSetupReturnFocus && screen in listOf(Screen.Settings, Screen.Live),
            settingsReturnsToPlayer = playerVisit,
            settingsPage = if (screen == Screen.Settings && previousScreen == Screen.Settings) state.settingsPage else null,
            passwordChangeNotice = if (screen == Screen.Settings) state.passwordChangeNotice else null)
    }
    if (screen in listOf(Screen.Discover, Screen.Movies, Screen.Series, Screen.Anime)) run {
        loadBrowse(browse ?: BrowseQuery(when (screen) { Screen.Series -> "series"; Screen.Anime -> "anime"; else -> "movie" }))
    }
    if (screen == Screen.Home && mutable.value.policyRefreshNeeded) refresh()
    else if (screen == Screen.Home) freshen()
    if (screen != Screen.Live) liveGuideJob?.cancel()
    if (screen == Screen.Live) run { loadLive(LiveQuery()) }
    if (screen == Screen.Settings && connection?.browserSession != true) refreshAddons()
    if (screen == Screen.Library) run { loadLibraryPage() }
    if (screen == Screen.ContinueWatching) loadContinueWatching(navigation)
    if (screen == Screen.Calendar && connection != null) run { loadCalendar() }
    if (screen == Screen.History) runPage {
        progressJob?.join()
        val profile = requireNotNull(mutable.value.selectedProfile)
        val history = readCurrentPage(navigation) { local?.history(profile) ?: requireNotNull(connection).history(profile) } ?: return@runPage
        updateState { it.copy(history = history) }
    }
    rememberNavigation()
}

fun ApplicationController.backDetails() {
    cancelDetailPaging()
    generation++
    job?.cancel()
    val previous = mutable.value.detailHistory.lastOrNull()
    if (previous != null) updateState { it.copy(detail = previous, detailHistory = it.detailHistory.dropLast(1), busy = false, error = null, detailRetryable = true) }
    else { updateState { it.copy(busy = false) }; returnTo(mutable.value.detailReturnScreen) }
    rememberNavigation()
}

fun ApplicationController.backSources() {
    val awaitingTitle = mutable.value.let { it.playTapTitle != null && it.detail == null && it.sourceReturnScreen == Screen.Details }
    generation++; job?.cancel()
    if (!awaitingTitle) cancelSourceDetail()
    updateState { state ->
        state.copy(screen = state.sourceReturnScreen, busy = false, findingSources = false,
            error = state.error.takeIf { awaitingTitle }, detailRetryable = if (awaitingTitle) state.detailRetryable else true)
    }
    rememberNavigation()
}

fun ApplicationController.navigateBack() {
    if (backWithinSettings()) return
    val state = mutable.value
    if (state.inPlayerSettings()) { returnToPlayer(); return }
    when (state.backStep()) {
        BackStep.Root -> Unit
        BackStep.Sources -> backSources()
        BackStep.Player -> closePlayer()
        BackStep.Details -> backDetails()
        BackStep.Profiles -> navigate(Screen.Profiles, record = false)
        BackStep.ManageProfiles -> stopManagingProfiles()
        BackStep.PopHistory -> popHistory()
        BackStep.Home -> navigate(Screen.Home, record = false)
    }
}

fun ApplicationController.navigateSystemBack() {
    if (mutable.value.screen == Screen.Home) {
        navigate(Screen.Profiles, record = false)
        updateState { it.copy(profilesFromHome = true) }
        return
    }
    navigateBack()
}

fun ApplicationState.canNavigateBack(): Boolean =
    settingsLevelOpen() || inPlayerSettings() || backStep() != BackStep.Root

fun ApplicationState.systemBackStaysInApp(): Boolean = when (screen) {
    Screen.RecoveryCodes -> true
    Screen.Home -> true
    else -> canNavigateBack()
}

private enum class BackStep { Root, Sources, Player, Details, Profiles, ManageProfiles, PopHistory, Home }

private fun ApplicationState.backStep(): BackStep = when (screen) {
    Screen.Sources -> BackStep.Sources
    Screen.Player -> BackStep.Player
    Screen.Details -> BackStep.Details
    Screen.Connect, Screen.RecoveryCodes -> BackStep.Root
    Screen.ProfilePin, Screen.ProfileEdit -> BackStep.Profiles
    Screen.Profiles -> when {
        managingProfiles -> BackStep.ManageProfiles
        profilesFromHome -> BackStep.Root
        selectedProfile == null -> BackStep.Root
        navBack.isNotEmpty() -> BackStep.PopHistory
        else -> BackStep.Home
    }
    else -> when {
        navBack.isNotEmpty() -> BackStep.PopHistory
        screen == Screen.Home -> BackStep.Root
        else -> BackStep.Home
    }
}

private fun ApplicationState.settingsLevelOpen() =
    screen == Screen.Settings && (parentTitles != null || settingsPage?.parent() != null)

fun ApplicationController.showSettingsPage(page: SettingsPage) {
    if (mutable.value.screen == Screen.Settings && mutable.value.settingsPage != page) updateState { it.copy(settingsPage = page) }
}

private fun ApplicationController.backWithinSettings(): Boolean {
    val state = mutable.value
    if (!state.settingsLevelOpen()) return false
    if (state.parentTitles != null) { closeParentTitles(); return true }
    val parent = state.settingsPage?.parent() ?: return false
    updateState { it.copy(settingsPage = parent) }
    return true
}

fun ApplicationController.navigateForward() {
    val state = mutable.value
    val target = state.navForward.lastOrNull() ?: return
    updateState { it.copy(navForward = it.navForward.dropLast(1), navBack = it.navBack + it.screen) }
    navigate(target, record = false)
}

private fun ApplicationController.returnTo(screen: Screen) {
    val state = mutable.value
    updateState { current -> current.copy(navBack = if (current.navBack.lastOrNull() == screen) current.navBack.dropLast(1) else current.navBack) }
    navigate(screen, record = false)
}

private fun ApplicationController.popHistory() {
    val state = mutable.value
    val target = state.navBack.lastOrNull() ?: return
    updateState { it.copy(navBack = it.navBack.dropLast(1), navForward = it.navForward + it.screen) }
    navigate(target, record = false)
}
