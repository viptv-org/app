package org.viptv.app.state

import org.viptv.app.connection.PlaybackEvent

/** The Settings section the player's Audio & subtitles menu links to. */
const val PLAYER_SETTINGS_SECTION = "Subtitles & audio"

/*
 * app#44: a visit to Settings from the player.
 * - The player's Audio & subtitles menu opens Settings at Subtitles & audio and pauses playback.
 * - Playback and its server lease stay open behind Settings; the lease keeps renewing
 *   (ControllerPlaybackLease) and is re-acquired there, not in the player, if it lapses.
 * - Back from the visit, by any input (in-page arrow, Android and TV remote Back, the
 *   desktop Back chevron or Escape), returns to the player: same position, paused, with the
 *   profile's current preferences, so a size or colour change shows at once.
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
    mutable.value = mutable.value.copy(settingsReturnsToPlayer = true, settingsEntrySection = PLAYER_SETTINGS_SECTION)
    // Not a history entry: Back from the visit returns to the player, never to Sources.
    navigate(Screen.Settings, record = false)
}

/** Settings is a visit from the player that Back returns from. */
internal fun ApplicationState.inPlayerSettings() = screen == Screen.Settings && settingsReturnsToPlayer

/** Back from a player visit: the player again, where it was, paused, with the profile's current preferences. */
internal fun ApplicationController.returnToPlayer() {
    val state = mutable.value
    state.playback?.let { input -> input.reopenAt(lastProgress?.positionMillis, state.preferences?.preferences ?: input.preferences) }
    mutable.value = state.copy(paused = true)
    navigate(Screen.Player, record = false)
}
