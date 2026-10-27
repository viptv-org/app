package org.viptv.app.state

import org.viptv.app.connection.PlaybackEvent
import org.viptv.app.connection.ProfilePreferences
import org.viptv.app.connection.subtitleMode

/** The Settings section the player's Audio & subtitles menu links to. */
const val PLAYER_SETTINGS_SECTION = "Subtitles & audio"

/*
 * app#44: a visit to Settings from the player.
 * - The player's Audio & subtitles menu opens Settings at Subtitles & audio and pauses playback.
 * - Playback and its server lease stay open behind Settings; the lease keeps renewing
 *   (ControllerPlaybackLease) and is re-acquired there, not in the player, if it lapses.
 * - Back from the visit, by any input (in-page arrow, Android and TV remote Back, the
 *   desktop Back chevron or Escape), returns to the player: same position, paused, with the
 *   profile's current preferences, so every change in the section applies on return.
 * - Leaving Settings for any other page ends playback as leaving the player always did.
 */

/** Opens Settings at Subtitles & audio over the paused player. */
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
    // Not a history entry: Back from the visit returns to the player, never to Sources.
    navigate(Screen.Settings, record = false)
}

/** Settings is a visit from the player that Back returns from. */
internal fun ApplicationState.inPlayerSettings() = screen == Screen.Settings && settingsReturnsToPlayer

/**
 * Back from a player visit: the player again, where it was, paused, with the profile's current
 * preferences. The renderer applies them when it reopens. A server gateway chose the audio and
 * subtitle tracks when its lease opened, so a language or subtitle-mode change there reopens the
 * source at the same position, as a seek on that stream does.
 */
internal fun ApplicationController.returnToPlayer() {
    val state = mutable.value
    val input = state.playback
    val preferences = state.preferences?.preferences ?: input?.preferences
    val source = state.playingSource
    // Only a change made during this visit: a track picked in the player is already playing.
    val reopen = input != null && preferences != null && source != null && remotePlayback?.gateway == true &&
        preferences.serverTracks() != (playerVisitTracks ?: input.preferences.serverTracks())
    playerVisitTracks = null
    // Before the player recomposes: its renderer opens with these.
    if (!reopen && input != null && preferences != null) input.reopenAt(lastProgress?.positionMillis, preferences)
    mutable.value = state.copy(paused = true)
    navigate(Screen.Player, record = false)
    if (reopen && input != null && source != null) {
        val position = if (input.live) null else lastProgress?.positionMillis ?: (input.positionMillis + input.timelineOffsetMillis)
        if (!completionSeen) saveProgress(false)
        openSource(source, forceGateway = true, positionMillis = position, pausedAfterOpen = true)
    }
}

/** What a server gateway reads from the profile when it opens a lease: the tracks it outputs. */
internal data class ServerTracks(val audioLanguage: String?, val subtitleLanguage: String?, val subtitleMode: String)

private fun ProfilePreferences.serverTracks() = ServerTracks(preferred_audio_language, preferred_subtitle_language, subtitleMode)
