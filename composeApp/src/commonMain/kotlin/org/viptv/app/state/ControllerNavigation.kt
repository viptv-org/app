package org.viptv.app.state

import org.viptv.app.connection.*

fun ApplicationController.navigate(screen: Screen) {
    if (screen != Screen.Details) cancelDetailPaging()
    if (screen != Screen.Sources && mutable.value.findingSources) {
        generation++
        job?.cancel()
        mutable.value = mutable.value.copy(findingSources = false, busy = false)
    }
    if (screen != Screen.Player && mutable.value.playback != null) { if (!completionSeen) saveProgress(false, final = true); job?.cancel(); releaseRemotePlayback(); mutable.value = mutable.value.copy(playback = null) }
    mutable.value = mutable.value.copy(screen = screen, error = null)
    if (screen in listOf(Screen.Discover, Screen.Movies, Screen.Series)) run {
        loadBrowse(BrowseQuery(if (screen == Screen.Series) "series" else "movie"))
    }
    if (screen != Screen.Live) liveGuideJob?.cancel()
    if (screen == Screen.Live) run { loadLive(LiveQuery()) }
    if (screen == Screen.Settings && connection?.accountManagementEnabled != true) refreshAddons()
    if (screen == Screen.Library) run { loadLibraryPage() }
    if (screen == Screen.Calendar && connection != null) run {
        val window = calendarWindow()
        mutable.value = mutable.value.copy(calendarDays = window.days)
        mutable.value = mutable.value.copy(calendarEntries = requireNotNull(connection).calendar(window))
    }
    if (screen == Screen.History) run {
        progressJob?.join()
        val profile=requireNotNull(mutable.value.selectedProfile)
        mutable.value=mutable.value.copy(history=local?.history(profile) ?: requireNotNull(connection).history(profile))
    }
}

fun ApplicationController.backDetails() {
    cancelDetailPaging()
    generation++
    job?.cancel()
    val previous = mutable.value.detailHistory.lastOrNull()
    if (previous != null) mutable.value = mutable.value.copy(detail = previous, detailHistory = mutable.value.detailHistory.dropLast(1), busy = false, error = null)
    else { mutable.value = mutable.value.copy(busy = false); navigate(mutable.value.detailReturnScreen) }
}

fun ApplicationController.backSources() {
    generation++; job?.cancel()
    mutable.value = mutable.value.copy(screen = mutable.value.sourceReturnScreen,
        busy = false, findingSources = false, error = null)
}

/** Chrome and other global Back controls use the same route-specific return state. */
fun ApplicationController.navigateBack() {
    when (mutable.value.screen) {
        Screen.Details -> backDetails()
        Screen.Sources -> backSources()
        Screen.Player -> closePlayer()
        Screen.ProfilePin, Screen.ProfileEdit -> navigate(Screen.Profiles)
        Screen.Connect, Screen.Profiles, Screen.Home -> Unit
        else -> navigate(Screen.Home)
    }
}
