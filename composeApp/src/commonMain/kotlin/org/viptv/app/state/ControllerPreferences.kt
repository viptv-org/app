package org.viptv.app.state

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.viptv.app.connection.*
import org.viptv.app.player.PlaybackTrackKind
import org.viptv.app.player.canonicalLanguageTag

internal suspend fun ApplicationController.loadPreferences(owner: AddonOperationOwner? = null) {
    owner?.requireCurrent(this)
    val profile = requireNotNull(mutable.value.selectedProfile)
    val remote = connection
    val device = local
    try {
        val snapshot = device?.profilePreferences(profile) ?: requireNotNull(remote).profilePreferences(profile)
        owner?.requireCurrent(this)
        if (!ownsProfile(profile, remote, device)) return
        updateState { it.copy(preferences = snapshot, preferencesError = null) }
    } catch (error: CancellationException) { throw error }
    catch (error: Exception) {
        owner?.requireCurrent(this)
        if (error is RemoteFailure && error.status == 401) expireSession(remote, device)
        else if (ownsProfile(profile, remote, device))
            updateState { it.copy(preferencesError = if (error is RemoteFailure && error.status == 403) error.message
                else "Could not load profile preferences. Try again.") }
    }
}

internal fun ApplicationController.ownsProfile(profile: String, remote: RemoteConnection?, device: LocalConnection?): Boolean =
    mutable.value.selectedProfile == profile && connection === remote && local === device

fun ApplicationController.refreshPreferences() = run { loadPreferences() }

fun ApplicationController.updatePreferences(preferences: ProfilePreferences) = run {
    val profile = requireNotNull(mutable.value.selectedProfile)
    val remote = connection
    val device = local
    val snapshot = mutable.value.preferences ?: throw LocalFailure("Refresh profile preferences first.")
    val updated = device?.updateProfilePreferences(profile, snapshot.revision, preferences)
        ?: requireNotNull(remote).updateProfilePreferences(profile, snapshot.revision, preferences)
    if (ownsProfile(profile, remote, device)) {
        cancelNextEpisodePreparation()
        updateState { it.copy(preferences = updated, preferencesError = null) }
    }
}

// Not `run {}`: it cancels the controller job and sets `busy`, which must not happen while a player is open.
fun ApplicationController.rememberTrackChoice(kind: PlaybackTrackKind, language: String?) {
    val snapshot = mutable.value.preferences ?: return
    val current = snapshot.preferences
    val tag = language?.takeIf(String::isNotBlank)?.let(::canonicalLanguageTag)
    val updated = when (kind) {
        PlaybackTrackKind.Audio -> current.copy(preferred_audio_language = tag)
        PlaybackTrackKind.Subtitle -> when {
            current.subtitleMode == "foreign" -> if (tag == null) current else current.copy(preferred_subtitle_language = tag)
            tag == null -> if (current.subtitle_mode == null) current.copy(subtitles_enabled = false) else current.withSubtitleMode("never")
            else -> current.copy(preferred_subtitle_language = tag).let {
                if (it.subtitle_mode == null) it.copy(subtitles_enabled = true) else it.withSubtitleMode("always") }
        }
    }
    if (updated == current) return
    val profile = mutable.value.selectedProfile ?: return
    val remote = connection
    val device = local
    scope.launch {
        try {
            val saved = device?.updateProfilePreferences(profile, snapshot.revision, updated)
                ?: remote?.updateProfilePreferences(profile, snapshot.revision, updated) ?: return@launch
            if (ownsProfile(profile, remote, device)) {
                cancelNextEpisodePreparation()
                updateState { it.copy(preferences = saved, preferencesError = null) }
            }
        } catch (error: CancellationException) { throw error }
        catch (_: Exception) {
            if (ownsProfile(profile, remote, device))
                updateState { it.copy(preferencesError = "Could not save this track choice to the profile.") }
        }
    }
}
