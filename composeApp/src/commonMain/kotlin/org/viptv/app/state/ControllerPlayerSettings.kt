package org.viptv.app.state

import org.viptv.app.connection.PlaybackEvent
import org.viptv.app.connection.ProfilePreferences
import org.viptv.app.connection.subtitleMode

const val PLAYER_SETTINGS_SECTION = "Subtitles & audio"
const val PLAYER_ADDONS_SECTION = "Add-ons"

fun ApplicationController.openPlayerSettings() = visitSettingsFromPlayer(PLAYER_SETTINGS_SECTION)

// The error-slate notice's Configure source: the add-on that served this source is listed there, focused.
fun ApplicationController.openPlayerAddons() = visitSettingsFromPlayer(PLAYER_ADDONS_SECTION, addonTarget = mutable.value.playerAddonTarget())

internal fun ApplicationState.playerAddonTarget(): String? = playingSource?.provider_id?.takeIf { it.startsWith("addon:") }

private fun ApplicationController.visitSettingsFromPlayer(section: String, addonTarget: String? = null) {
    val state = mutable.value
    if (state.screen != Screen.Player || state.playback == null) return
    backgroundPlayback = null
    resumeAfterRenewal = null
    if (!state.paused) {
        updateState { state -> state.copy(paused = true) }
        // The renderer unmounts with Settings and never reports this pause itself.
        if (firstFrameSeen) tracking?.transition(PlaybackEvent.Pause, lastProgress)
        if (!completionSeen) saveProgress(false)
    }
    playerVisitTracks = mutable.value.preferences?.preferences?.serverTracks()
    updateState { next -> next.copy(settingsReturnsToPlayer = true, settingsEntrySection = section, settingsAddonTarget = addonTarget) }
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
    // Before the player recomposes: its renderer opens with these.
    if (!reopen && input != null && preferences != null) input.reopenAt(lastProgress?.positionMillis, preferences)
    updateState { state -> state.copy(paused = true) }
    navigate(Screen.Player, record = false)
    if (reopen && input != null && source != null) {
        val position = if (input.live) null else lastProgress?.positionMillis ?: (input.positionMillis + input.timelineOffsetMillis)
        if (!completionSeen) saveProgress(false)
        openSource(source, forceGateway = true, positionMillis = position, pausedAfterOpen = true)
    }
}

internal data class ServerTracks(val audioLanguage: String?, val subtitleLanguage: String?, val subtitleMode: String)

private fun ProfilePreferences.serverTracks() = ServerTracks(preferred_audio_language, preferred_subtitle_language, subtitleMode)
