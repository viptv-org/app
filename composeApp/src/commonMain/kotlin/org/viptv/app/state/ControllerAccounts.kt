package org.viptv.app.state

import org.viptv.app.connection.*

fun ApplicationController.refreshAccountIdentity() = run { loadAccountIdentity() }

private suspend fun ApplicationController.loadAccountIdentity() {
    val remote = requireNotNull(connection)
    val owner = generation
    val identity = remote.accountIdentity()
    if (connection === remote && generation == owner) updateState { next -> next.copy(accountIdentity = identity) }
}

fun ApplicationController.refreshAccounts() = run { loadAccountIdentity() }
