package org.viptv.app.state

import org.viptv.app.connection.*
import kotlinx.coroutines.CancellationException

/** [browse] replaces the default first Browse query of a browse screen (a Home genre, for example). */
fun ApplicationController.navigate(screen: Screen, record: Boolean = true, browse: BrowseQuery? = null) {
    val previousScreen = mutable.value.screen
    val navigation = ++navigationGeneration
    pageRequestJob?.let { pending ->
        pending.cancel()
        if (job === pending) {
            generation++
            mutable.value = mutable.value.copy(busy = false)
        }
    }
    if (screen != Screen.Settings && mutable.value.parentTitles != null) closeParentTitles()
    if (mutable.value.screen == Screen.Calendar && screen != Screen.Calendar) {
        generation++
        job?.cancel()
        mutable.value = mutable.value.copy(busy = false)
    }
    if (screen != Screen.Details) cancelDetailPaging()
    // A play tap's pending Details belong to the overlay group the viewer is now leaving (app#140).
    if (screen !in listOf(Screen.Details, Screen.Sources, Screen.Player)) {
        cancelSourceDetail()
        if (mutable.value.playTapTitle != null || mutable.value.detailFailure != null)
            mutable.value = mutable.value.copy(playTapTitle = null, detailFailure = null)
    }
    if (screen != Screen.Sources && mutable.value.findingSources) {
        generation++
        job?.cancel()
        mutable.value = mutable.value.copy(findingSources = false, busy = false)
    }
    // A player visit to Settings (app#44) keeps playback behind it; anywhere else ends it.
    val playerVisit = screen == Screen.Settings && mutable.value.settingsReturnsToPlayer &&
        previousScreen in listOf(Screen.Player, Screen.Settings)
    if (screen != Screen.Player && !playerVisit && mutable.value.playback != null) {
        val live = mutable.value.playback?.live == true
        if (!completionSeen) saveProgress(false, final = true); job?.cancel(); releaseRemotePlayback(); mutable.value = mutable.value.copy(playback = null)
        afterPlayback(live)
    }
    // Overlays (Details/Sources/Player) are not browser destinations; record
    // the page they return to instead so Back never lands on a stale sheet.
    val recorded = when (previousScreen) {
        Screen.Sources -> mutable.value.sourceReturnScreen
        Screen.Details -> mutable.value.detailReturnScreen
        Screen.Player -> mutable.value.detailReturnScreen
        else -> previousScreen
    }
    val history = if (record && screen != previousScreen)
        // Browser-style history: a new destination drops the forward stack.
        mutable.value.copy(navBack = (mutable.value.navBack + recorded).takeLast(32), navForward = emptyList())
    else mutable.value
    mutable.value = history.copy(screen = screen, error = null, settingsEntrySection = if (screen == Screen.Settings) mutable.value.settingsEntrySection else null,
        settingsReturnsToPlayer = playerVisit,
        // A page belongs to one visit: arriving from elsewhere starts at settingsEntrySection.
        settingsPage = if (screen == Screen.Settings && previousScreen == Screen.Settings) mutable.value.settingsPage else null)
    if (screen in listOf(Screen.Discover, Screen.Movies, Screen.Series, Screen.Anime)) run {
        loadBrowse(browse ?: BrowseQuery(when (screen) { Screen.Series -> "series"; Screen.Anime -> "anime"; else -> "movie" }))
    }
    if (screen == Screen.Home && mutable.value.policyRefreshNeeded) refresh()
    else if (screen == Screen.Home) freshen()
    if (screen != Screen.Live) liveGuideJob?.cancel()
    if (screen == Screen.Live) run { loadLive(LiveQuery()) }
    if (screen == Screen.Settings && connection?.accountManagementEnabled != true) refreshAddons()
    if (screen == Screen.Library) run { loadLibraryPage() }
    if (screen == Screen.ContinueWatching) loadContinueWatching(navigation)
    if (screen == Screen.Calendar && connection != null) run { loadCalendar() }
    if (screen == Screen.History) runPage {
        progressJob?.join()
        val profile = requireNotNull(mutable.value.selectedProfile)
        val history = readCurrentPage(navigation) { local?.history(profile) ?: requireNotNull(connection).history(profile) } ?: return@runPage
        mutable.value = mutable.value.copy(history = history)
    }
}

fun ApplicationController.backDetails() {
    cancelDetailPaging()
    generation++
    job?.cancel()
    val previous = mutable.value.detailHistory.lastOrNull()
    if (previous != null) mutable.value = mutable.value.copy(detail = previous, detailHistory = mutable.value.detailHistory.dropLast(1), busy = false, error = null)
    else { mutable.value = mutable.value.copy(busy = false); returnTo(mutable.value.detailReturnScreen) }
}

fun ApplicationController.backSources() {
    val state = mutable.value
    // A play tap still resolving its title's Details keeps that read: Back opens the title's
    // page in its loading state, and the read fills it in with no second request (app#140).
    // The selector's own work always stops here.
    val awaitingTitle = state.playTapTitle != null && state.detail == null && state.sourceReturnScreen == Screen.Details
    generation++; job?.cancel()
    if (!awaitingTitle) cancelSourceDetail()
    mutable.value = state.copy(screen = state.sourceReturnScreen, busy = false, findingSources = false,
        // The title's read is what failed, or may still: its page is where that error belongs.
        error = state.error.takeIf { awaitingTitle })
}

/**
 * The app's one Back step (docs/BACK_MATRIX.md). Every Back input ends here: the in-page arrows,
 * Android and TV system Back, the desktop chevron, Escape and mouse button, and browser history.
 * Nested levels go up one at a time, overlays return to their own origin, and other pages pop the
 * screen history ([ApplicationState.navBack]) or, without history, go to their parent.
 */
fun ApplicationController.navigateBack() {
    if (backWithinSettings()) return
    val state = mutable.value
    // app#44: Settings opened from the player's Audio & subtitles menu goes back to the paused player.
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

/**
 * Android system Back and the TV remote's Back, once no route took it (docs/BACK_MATRIX.md).
 * MainActivity calls this instead of [navigateBack] so Back on Home opens the profile picker first
 * (app#109); desktop and browser keep [navigateBack]'s history pop on Home. The picker reached this
 * way records no history and is marked [ApplicationState.profilesFromHome] so Back there leaves the
 * app instead of bouncing back to Home.
 */
fun ApplicationController.navigateSystemBack() {
    if (mutable.value.screen == Screen.Home) {
        navigate(Screen.Profiles, record = false)
        mutable.value = mutable.value.copy(profilesFromHome = true)
        return
    }
    navigateBack()
}

/** Whether [navigateBack] has a step to take here; false on a root page, where Back belongs to the platform. */
fun ApplicationState.canNavigateBack(): Boolean =
    settingsLevelOpen() || inPlayerSettings() || backStep() != BackStep.Root

/**
 * Android system Back and the TV remote's Back, once no route took it: true when the app takes the
 * step ([navigateSystemBack]), false when the activity leaves the app. Back on Home opens the
 * profile picker first (app#109); Back on that picker leaves the app. The recovery codes are shown
 * once, so Back there stays on the page.
 */
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
    // The PIN pad and the profile editor belong to Profiles, whatever was open before them.
    Screen.ProfilePin, Screen.ProfileEdit -> BackStep.Profiles
    Screen.Profiles -> when {
        managingProfiles -> BackStep.ManageProfiles
        // Reached from Home by Back (app#109): Back leaves the app — no Home<->Profiles bounce.
        profilesFromHome -> BackStep.Root
        // The first choice after sign-in has nowhere to go back to.
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

/** Parental controls' Titles or a Settings sub-page is open: [backWithinSettings] goes up one level. */
private fun ApplicationState.settingsLevelOpen() =
    screen == Screen.Settings && (parentTitles != null || settingsPage?.parent() != null)

/** Settings reports the page it shows, so global Back can leave a sub-page before leaving Settings. */
fun ApplicationController.showSettingsPage(page: SettingsPage) {
    if (mutable.value.screen == Screen.Settings && mutable.value.settingsPage != page) mutable.value = mutable.value.copy(settingsPage = page)
}

/** Back inside Settings, one level at a time: Parental controls' Titles, then a sub-page back to the Settings list. */
private fun ApplicationController.backWithinSettings(): Boolean {
    val state = mutable.value
    if (!state.settingsLevelOpen()) return false
    if (state.parentTitles != null) { closeParentTitles(); return true }
    val parent = state.settingsPage?.parent() ?: return false
    mutable.value = state.copy(settingsPage = parent)
    return true
}

/** The desktop chrome's Forward chevron. */
fun ApplicationController.navigateForward() {
    val state = mutable.value
    val target = state.navForward.lastOrNull() ?: return
    mutable.value = state.copy(navForward = state.navForward.dropLast(1), navBack = state.navBack + state.screen)
    navigate(target, record = false)
}

/** Return to a screen that is already at the top of the back stack. */
private fun ApplicationController.returnTo(screen: Screen) {
    val state = mutable.value
    val back = if (state.navBack.lastOrNull() == screen) state.navBack.dropLast(1) else state.navBack
    mutable.value = state.copy(navBack = back)
    navigate(screen, record = false)
}

private fun ApplicationController.popHistory() {
    val state = mutable.value
    val target = state.navBack.lastOrNull() ?: return
    mutable.value = state.copy(navBack = state.navBack.dropLast(1), navForward = state.navForward + state.screen)
    navigate(target, record = false)
}
