package org.viptv.app.state

import org.viptv.app.connection.*
import kotlinx.coroutines.CancellationException

fun ApplicationController.navigate(screen: Screen, record: Boolean = true) {
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
    if (screen != Screen.Sources && mutable.value.findingSources) {
        generation++
        job?.cancel()
        mutable.value = mutable.value.copy(findingSources = false, busy = false)
    }
    if (screen != Screen.Player && mutable.value.playback != null) { if (!completionSeen) saveProgress(false, final = true); job?.cancel(); releaseRemotePlayback(); mutable.value = mutable.value.copy(playback = null) }
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
    mutable.value = history.copy(screen = screen, error = null, settingsEntrySection = if (screen == Screen.Settings) mutable.value.settingsEntrySection else null)
    if (screen in listOf(Screen.Discover, Screen.Movies, Screen.Series, Screen.Anime)) run {
        loadBrowse(BrowseQuery(when (screen) { Screen.Series -> "series"; Screen.Anime -> "anime"; else -> "movie" }))
    }
    if (screen == Screen.Home && mutable.value.policyRefreshNeeded) refresh()
    if (screen != Screen.Live) liveGuideJob?.cancel()
    if (screen == Screen.Live) run { loadLive(LiveQuery()) }
    if (screen == Screen.Settings && connection?.accountManagementEnabled != true) refreshAddons()
    if (screen == Screen.Library) run { loadLibraryPage() }
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
    generation++; job?.cancel()
    mutable.value = mutable.value.copy(screen = mutable.value.sourceReturnScreen,
        busy = false, findingSources = false, error = null)
}

/** Chrome and other global Back controls use the same route-specific return state. */
fun ApplicationController.navigateBack() {
    val state = mutable.value
    when (state.screen) {
        Screen.Sources -> backSources()
        Screen.Player -> closePlayer()
        Screen.Details -> backDetails()
        else -> if (state.navBack.isNotEmpty()) popHistory() else when (state.screen) {
            Screen.ProfilePin, Screen.ProfileEdit -> navigate(Screen.Profiles)
            Screen.Connect, Screen.RecoveryCodes, Screen.Profiles, Screen.Home -> Unit
            else -> navigate(Screen.Home)
        }
    }
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
