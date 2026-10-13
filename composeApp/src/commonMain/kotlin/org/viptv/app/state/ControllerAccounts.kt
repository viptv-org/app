package org.viptv.app.state

import org.viptv.app.connection.*

fun ApplicationController.refreshAccounts() = run {
    val profile = requireNotNull(mutable.value.selectedProfile)
    mutable.value = mutable.value.copy(simkl = requireNotNull(connection).simklStatus(profile))
}
fun ApplicationController.linkSimkl() = run {
    if (browserOrigin() == null) throw LocalFailure("Link accounts through the server web dashboard.")
    openAccountAuthorization(requireNotNull(connection).authorizeSimkl(requireNotNull(mutable.value.selectedProfile)))
}
fun ApplicationController.unlinkSimkl() = run {
    if (browserOrigin() == null) throw LocalFailure("Manage linked accounts through the server web dashboard.")
    val profile = requireNotNull(mutable.value.selectedProfile)
    requireNotNull(connection).unlinkSimkl(profile)
    mutable.value = mutable.value.copy(simkl = requireNotNull(connection).simklStatus(profile))
}
