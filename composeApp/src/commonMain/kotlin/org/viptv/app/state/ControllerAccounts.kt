package org.viptv.app.state

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.viptv.app.connection.*

fun ApplicationController.refreshAccountIdentity() = run { loadAccountIdentity() }

fun ApplicationController.refreshAccountAccess() {
    val remote = connection ?: return
    val owner = generation
    val profile = mutable.value.selectedProfile
    fun unavailable() {
        if (connection === remote && generation == owner && mutable.value.selectedProfile == profile) updateState { next ->
            next.copy(manageIntegrations = false, accountAccess = AccountAccess(next.accountRole, next.manageAccount, false)) }
    }
    scope.launch {
        try { loadAccountIdentity() }
        catch (error: CancellationException) { throw error }
        catch (failure: RemoteFailure) { if (failure.sessionExpired) expireSession(remote, null) else unavailable() }
        catch (_: Exception) { unavailable() }
    }
}

private suspend fun ApplicationController.loadAccountIdentity() {
    val remote = requireNotNull(connection)
    val owner = generation
    val profile = mutable.value.selectedProfile
    val identity = remote.accountIdentity()
    if (connection === remote && generation == owner && mutable.value.selectedProfile == profile) updateState { next -> next.copy(accountIdentity = identity,
        manageAccount = remote.accountManagementEnabled, manageIntegrations = remote.integrationManagementEnabled,
        accountOwner = remote.accountOwner, accountRole = remote.accountRole,
        accountAccess = AccountAccess(remote.accountRole, remote.accountManagementEnabled, remote.integrationManagementEnabled)) }
}

fun ApplicationController.refreshAccounts() = run { loadAccountIdentity() }
