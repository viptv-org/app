package org.viptv.app.state

import org.viptv.app.connection.*

fun ApplicationController.refreshLinkedServices() = run {
    val profile = mutable.value.selectedProfile ?: throw LocalFailure("Choose a profile first.")
    val remote = connection ?: throw LocalFailure("Connect to your account to link services.")
    val owner = generation
    val status = remote.simklStatus(profile)
    if (connection === remote && generation == owner && mutable.value.selectedProfile == profile)
        updateState { next -> next.copy(simkl = status, linkedServicesProfile = profile) }
}

fun ApplicationController.linkLinkedService() = run {
    val profile = mutable.value.selectedProfile ?: throw LocalFailure("Choose a profile first.")
    val remote = connection ?: throw LocalFailure("Connect to your account to link services.")
    openAccountAuthorization(remote.authorizeSimkl(profile))
}

fun ApplicationController.unlinkLinkedService() {
    val before = mutable.value
    mutate(preferenceTarget(UNLINK_SERVICE_TARGET),
        optimistic = { state -> state.copy(simkl = state.simkl?.copy(linked = false)) },
        revert = { it.copy(simkl = before.simkl) },
        ownsControllerJob = true) {
        val profile = mutable.value.selectedProfile ?: throw LocalFailure("Choose a profile first.")
        val remote = connection ?: throw LocalFailure("Connect to your account to link services.")
        val owner = generation
        remote.unlinkSimkl(profile)
        val status = remote.simklStatus(profile)
        if (connection === remote && generation == owner && mutable.value.selectedProfile == profile)
            updateState { next -> next.copy(simkl = status, linkedServicesProfile = profile) }
        profileConfigurationChanged()
    }
}

internal const val UNLINK_SERVICE_TARGET = "Linked services"
