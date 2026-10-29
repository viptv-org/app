package org.viptv.app.state

import org.viptv.app.connection.PlaybackEvent
import org.viptv.app.connection.ProfilePreferences
import org.viptv.app.connection.subtitleMode

const val PLAYER_SETTINGS_SECTION = "Subtitles & audio"

fun ApplicationController.openPlayerSettings() {
    val state = mutable.value
    if (state.screen != Screen.Player || state.playback == null) return
    backgroundPlayback = null
    resumeAfterRenewal = null
    if (!state.paused) {
        mutable.value = state.copy(paused = true)
        // The renderer unmounts with Settings and never reports this pause itself.
        if (firstFrameSeen) tracking?.transition(PlaybackEvent.Pause, lastProgress)
        if (!completionSeen) saveProgress(false)
    }
    playerVisitTracks = mutable.value.preferences?.preferences?.serverTracks()
    mutable.value = mutable.value.copy(settingsReturnsToPlayer = true, settingsEntrySection = PLAYER_SETTINGS_SECTION)
    navigate(Screen.Settings, record = false)
}

internal fun ApplicationState.inPlayerSettings() = screen == Screen.Settings && settingsReturnsToPlayer

internal fun ApplicationController.returnToPlayer() {
    val state = mutable.value
    val input = state.playback
    val preferences = state.preferences?.preferences ?: input?.preferences
    val source = state.playingSource
    val reopen = input != null && preferences != null && source != null && remotePlayback?.gateway == true &&
        preferences.serverTracks() != (playerVisitTracks ?: input.preferences.serverTracks())
    playerVisitTracks = null
    if (!reopen && input != null && preferences != null) input.reopenAt(lastProgress?.positionMillis, preferences)
    mutable.value = state.copy(paused = true)
    navigate(Screen.Player, record = false)
    if (reopen && input != null && source != null) {
        val position = if (input.live) null else lastProgress?.positionMillis ?: (input.positionMillis + input.timelineOffsetMillis)
        if (!completionSeen) saveProgress(false)
        openSource(source, forceGateway = true, positionMillis = position, pausedAfterOpen = true)
    }
}

internal data class ServerTracks(val audioLanguage: String?, val subtitleLanguage: String?, val subtitleMode: String)

private fun ProfilePreferences.serverTracks() = ServerTracks(preferred_audio_language, preferred_subtitle_language, subtitleMode)
