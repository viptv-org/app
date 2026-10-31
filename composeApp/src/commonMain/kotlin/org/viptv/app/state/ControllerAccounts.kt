package org.viptv.app.state

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.viptv.app.connection.*

fun ApplicationController.refreshAccountIdentity() = run { loadAccountIdentity() }

fun ApplicationController.refreshAccountAccess() {
    val remote = connection ?: return
    if (remote.accountAccessRead) return
    scope.launch {
        try { loadAccountIdentity() }
        catch (error: CancellationException) { throw error }
        catch (failure: RemoteFailure) { if (failure.status == 401) expireSession(remote, null) }
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

fun ApplicationController.refreshAccounts() = run { loadAccountIdentity() }
