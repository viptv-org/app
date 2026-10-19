package org.viptv.app.state

import kotlinx.coroutines.CancellationException
import org.viptv.app.connection.*

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
