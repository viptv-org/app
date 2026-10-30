package org.viptv.app.state

import org.viptv.app.connection.*

fun ApplicationController.refreshAccountIdentity() = run { loadAccountIdentity() }

private suspend fun ApplicationController.loadAccountIdentity() {
    val remote = requireNotNull(connection)
    val owner = generation
    val identity = remote.accountIdentity()
    if (connection === remote && generation == owner) updateState { next -> next.copy(accountIdentity = identity) }
}

fun ApplicationController.refreshAccounts() = run {
    val remote = requireNotNull(connection)
    val owner = generation
    loadAccountIdentity()
    if (remote.accountManagementEnabled) {
        val profile = requireNotNull(mutable.value.selectedProfile)
        val status = remote.simklStatus(profile)
        if (connection === remote && generation == owner && mutable.value.selectedProfile == profile)
            updateState { next -> next.copy(simkl = status) }
    }
}
fun ApplicationController.linkSimkl() = run {
    if (browserOrigin() == null) throw LocalFailure("Link accounts through the server web dashboard.")
    openAccountAuthorization(requireNotNull(connection).authorizeSimkl(requireNotNull(mutable.value.selectedProfile)))
}
fun ApplicationController.unlinkSimkl() = run {
    if (browserOrigin() == null) throw LocalFailure("Manage linked accounts through the server web dashboard.")
    val profile = requireNotNull(mutable.value.selectedProfile)
    requireNotNull(connection).unlinkSimkl(profile)
    val status = requireNotNull(connection).simklStatus(profile)
    updateState { next -> next.copy(simkl = status) }
}
