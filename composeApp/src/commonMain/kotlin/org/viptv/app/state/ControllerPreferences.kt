package org.viptv.app.state

import kotlinx.coroutines.CancellationException
import org.viptv.app.connection.*

internal suspend fun ApplicationController.loadPreferences(owner: AddonOperationOwner? = null) {
    owner?.requireCurrent(this)
    val profile = requireNotNull(mutable.value.selectedProfile)
    try {
        val snapshot = local?.profilePreferences(profile) ?: requireNotNull(connection).profilePreferences(profile)
        owner?.requireCurrent(this)
        mutable.value = mutable.value.copy(preferences = snapshot, preferencesError = null)
    } catch (error: CancellationException) { throw error }
    catch (_: Exception) {
        owner?.requireCurrent(this)
        mutable.value = mutable.value.copy(preferencesError = "Could not load profile preferences. Try again.")
    }
}

fun ApplicationController.refreshPreferences() = run { loadPreferences() }

fun ApplicationController.updatePreferences(preferences: ProfilePreferences) = run {
    val profile = requireNotNull(mutable.value.selectedProfile)
    val snapshot = mutable.value.preferences ?: throw LocalFailure("Refresh profile preferences first.")
    val updated = local?.updateProfilePreferences(profile, snapshot.revision, preferences)
        ?: requireNotNull(connection).updateProfilePreferences(profile, snapshot.revision, preferences)
    mutable.value = mutable.value.copy(preferences = updated, preferencesError = null)
}
