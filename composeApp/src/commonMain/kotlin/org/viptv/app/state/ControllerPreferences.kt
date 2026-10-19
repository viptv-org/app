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
        mutable.value = mutable.value.copy(preferences = snapshot, preferencesError = null)
    } catch (error: CancellationException) { throw error }
    catch (error: Exception) {
        owner?.requireCurrent(this)
        // An expired session is not a preferences failure: never offer a retry that cannot work.
        if (error is RemoteFailure && error.status == 401) expireSession(remote, device)
        else if (ownsProfile(profile, remote, device))
            mutable.value = mutable.value.copy(preferencesError = if (error is RemoteFailure && error.status == 403) error.message
                else "Could not load profile preferences. Try again.")
    }
}

/** Preferences belong to one profile on one connection; a switch invalidates both. */
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
    // A switch that landed while the write was in flight owns the visible preferences.
    if (ownsProfile(profile, remote, device)) mutable.value = mutable.value.copy(preferences = updated, preferencesError = null)
}

/**
 * A picker choice is a profile preference, not a session setting: store it with
 * the same revision-checked write the settings screen uses. Read-side matching
 * canonicalises tags (`player/SubtitlePreference.kt:7`), so a stored tag
 * round-trips even when a renderer reports it in another form. Deliberately not
 * `run { }`: that cancels the current controller job and sets `busy`, which must
 * not happen while a player is open.
 */
fun ApplicationController.rememberTrackChoice(kind: PlaybackTrackKind, language: String?) {
    val snapshot = mutable.value.preferences ?: return
    val current = snapshot.preferences
    val tag = language?.takeIf(String::isNotBlank)?.let(::canonicalLanguageTag)
    val updated = when (kind) {
        PlaybackTrackKind.Audio -> current.copy(preferred_audio_language = tag)
        PlaybackTrackKind.Subtitle ->
            if (tag == null) current.copy(subtitles_enabled = false)
            else current.copy(preferred_subtitle_language = tag, subtitles_enabled = true)
    }
    if (updated == current) return
    val profile = mutable.value.selectedProfile ?: return
    val remote = connection
    val device = local
    scope.launch {
        try {
            val saved = device?.updateProfilePreferences(profile, snapshot.revision, updated)
                ?: remote?.updateProfilePreferences(profile, snapshot.revision, updated) ?: return@launch
            if (ownsProfile(profile, remote, device)) mutable.value = mutable.value.copy(preferences = saved, preferencesError = null)
        } catch (error: CancellationException) { throw error }
        catch (_: Exception) {
            if (ownsProfile(profile, remote, device))
                mutable.value = mutable.value.copy(preferencesError = "Could not save this track choice to the profile.")
        }
    }
}
