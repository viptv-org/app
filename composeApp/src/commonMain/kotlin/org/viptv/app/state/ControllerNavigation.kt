package org.viptv.app.state

import org.viptv.app.connection.*

fun ApplicationController.navigate(screen: Screen) {
    if (screen != Screen.Player && mutable.value.playback != null) { if (!completionSeen) saveProgress(false, final = true); job?.cancel(); releaseRemotePlayback(); mutable.value = mutable.value.copy(playback = null) }
    mutable.value = mutable.value.copy(screen = screen, error = null)
    if (screen in listOf(Screen.Discover, Screen.Movies, Screen.Series)) run {
        loadBrowse(BrowseQuery(if (screen == Screen.Series) "series" else "movie"))
    }
    if (screen == Screen.Settings) refreshAddons()
    if (screen == Screen.Library) run { loadLibrary() }
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
