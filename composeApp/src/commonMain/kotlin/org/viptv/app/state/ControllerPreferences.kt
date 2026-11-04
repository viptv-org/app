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
        if (error is RemoteFailure && error.sessionExpired) expireSession(remote, device)
        else if (ownsProfile(profile, remote, device))
            updateState { it.copy(preferencesError = if (error is RemoteFailure && error.status == 403) error.message
                else "Could not load profile preferences. Try again.") }
    }
}

internal fun ApplicationController.ownsProfile(profile: String, remote: RemoteConnection?, device: LocalConnection?): Boolean =
    mutable.value.selectedProfile == profile && connection === remote && local === device

fun ApplicationController.refreshPreferences() = run { loadPreferences() }

internal const val PREFERENCES_TARGET = "Profile preferences"
private const val PREFERENCES_FAILED = "Could not save profile preferences. Try again."

// The row that was changed names the write, so only it draws the pending state and only it drops a repeat press.
fun ApplicationController.updatePreferences(preferences: ProfilePreferences, target: String = PREFERENCES_TARGET) {
    val profile = mutable.value.selectedProfile ?: return
    val remote = connection
    val device = local
    val snapshot = mutable.value.preferences ?: return
    mutate(preferenceTarget(target),
        optimistic = { it.copy(preferences = it.preferences?.copy(preferences = preferences), preferencesError = null) },
        revert = { it.withPreferences(snapshot).copy(preferencesError = PREFERENCES_FAILED) },
        message = { PREFERENCES_FAILED },
        ownsControllerJob = true) {
        val updated = device?.updateProfilePreferences(profile, snapshot.revision, preferences)
            ?: requireNotNull(remote).updateProfilePreferences(profile, snapshot.revision, preferences)
        if (ownsProfile(profile, remote, device)) {
            cancelNextEpisodePreparation()
            updateState { it.copy(preferences = updated, preferencesError = null) }
        }
    }
}

internal fun preferenceTarget(target: String) = MutationTarget(MutationKind.Preference, target)

// The last good snapshot is what a refused write puts back, so no rejected value stays pinned in the row.
internal fun ApplicationState.withPreferences(snapshot: PreferencesSnapshot): ApplicationState =
    copy(preferences = snapshot.copy(preferences = snapshot.preferences.copy()))

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
    val remote = connection ?: return
    val device = local
    val failed = "Could not save this track choice to the profile."
    mutate(preferenceTarget(if (kind == PlaybackTrackKind.Audio) AUDIO_LANGUAGE_TARGET else SUBTITLE_LANGUAGE_TARGET),
        optimistic = { it.copy(preferences = it.preferences?.copy(preferences = updated), preferencesError = null) },
        revert = { it.withPreferences(snapshot).copy(preferencesError = failed) },
        message = { failed }) {
        val saved = device?.updateProfilePreferences(profile, snapshot.revision, updated)
            ?: remote.updateProfilePreferences(profile, snapshot.revision, updated)
        if (ownsProfile(profile, remote, device)) {
            cancelNextEpisodePreparation()
            updateState { it.copy(preferences = saved, preferencesError = null) }
        }
    }
}

internal const val AUDIO_LANGUAGE_TARGET = "Audio language"
internal const val SUBTITLE_LANGUAGE_TARGET = "Subtitle language"
internal const val PLAYER_SETTINGS_TARGET = "Player settings"

// Inline player settings save to the profile without closing or reopening the player, like a track choice.
fun ApplicationController.savePlaybackPreferences(preferences: ProfilePreferences) {
    val snapshot = mutable.value.preferences ?: return
    if (preferences == snapshot.preferences) return
    val profile = mutable.value.selectedProfile ?: return
    val remote = connection ?: return
    val device = local
    val failed = "Could not save this subtitle setting to the profile."
    mutate(preferenceTarget(PLAYER_SETTINGS_TARGET),
        optimistic = { it.copy(preferences = it.preferences?.copy(preferences = preferences), preferencesError = null) },
        revert = { it.withPreferences(snapshot).copy(preferencesError = failed) },
        message = { failed }) {
        val saved = device?.updateProfilePreferences(profile, snapshot.revision, preferences)
            ?: remote.updateProfilePreferences(profile, snapshot.revision, preferences)
        if (ownsProfile(profile, remote, device)) {
            cancelNextEpisodePreparation()
            updateState { it.copy(preferences = saved, preferencesError = null) }
        }
    }
}
