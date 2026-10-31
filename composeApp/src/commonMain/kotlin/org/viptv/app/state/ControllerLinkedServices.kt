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

fun ApplicationController.unlinkLinkedService() = run {
    val profile = mutable.value.selectedProfile ?: throw LocalFailure("Choose a profile first.")
    val remote = connection ?: throw LocalFailure("Connect to your account to link services.")
    val owner = generation
    remote.unlinkSimkl(profile)
    val status = remote.simklStatus(profile)
    if (connection === remote && generation == owner && mutable.value.selectedProfile == profile)
        updateState { next -> next.copy(simkl = status, linkedServicesProfile = profile) }
}