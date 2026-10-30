package org.viptv.app.state

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import org.viptv.app.connection.*

fun ApplicationController.refreshAccountIdentity() = run { loadAccountIdentity() }

fun ApplicationController.refreshAccountAccess() {
    val remote = connection ?: return
    if (remote.accountAccessRead) return
    scope.launch(start = CoroutineStart.UNDISPATCHED) {
        try { loadAccountIdentity() }
        catch (error: CancellationException) { throw error }
        catch (_: Exception) { }
    }
}

private suspend fun ApplicationController.loadAccountIdentity() {
    val remote = requireNotNull(connection)
    val owner = generation
    val identity = remote.accountIdentity()
    if (connection === remote && generation == owner) updateState { next -> next.copy(accountIdentity = identity,
        manageAccount = remote.accountManagementEnabled, accountOwner = remote.accountOwner) }
}

fun ApplicationController.refreshAccounts() = run {
    val remote = requireNotNull(connection)
    val owner = generation
    loadAccountIdentity()
    if (remote.browserSession) {
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
