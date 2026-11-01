package org.viptv.app.state

import kotlinx.coroutines.CancellationException
import org.viptv.app.connection.*

fun ApplicationController.refreshGateways() = run { loadGateways() }

private suspend fun ApplicationController.loadGateways() {
    val remote = requireNotNull(connection)
    val owner = generation
    try {
        val snapshot = remote.gateways()
        if (connection === remote && generation == owner)
            updateState { next -> next.copy(gateways = snapshot, gatewaysError = null) }
    } catch (error: CancellationException) { throw error }
    catch (error: Exception) {
        if (error is RemoteFailure && error.status == 401) throw error
        if (connection === remote && generation == owner)
            updateState { next -> next.copy(gatewaysError =
                if (error is RemoteFailure && error.status == 403) error.message else "Could not load gateways. Try again.") }
    }
}

fun ApplicationController.setGatewayEnabled(id: String, enabled: Boolean) = run {
    val remote = requireNotNull(connection)
    remote.updateGateway(id, enabled)
    loadGateways()
}

fun ApplicationController.deleteGateway(id: String) = run {
    val remote = requireNotNull(connection)
    remote.deleteGateway(id)
    loadGateways()
}

fun ApplicationController.checkGateway(id: String) = run {
    val remote = requireNotNull(connection)
    val result = remote.checkGateway(id)
    updateState { next -> next.copy(gatewayChecks = next.gatewayChecks + (id to result)) }
}

fun ApplicationController.loadGatewayGrants(id: String) = run {
    val remote = requireNotNull(connection)
    publishGatewayGrants(remote, id)
}

fun ApplicationController.grantGatewayAccess(id: String, accountId: Long) = run {
    val remote = requireNotNull(connection)
    remote.grantGateway(id, accountId)
    publishGatewayGrants(remote, id)
}

fun ApplicationController.revokeGatewayAccess(id: String, accountId: Long) = run {
    val remote = requireNotNull(connection)
    remote.revokeGateway(id, accountId)
    publishGatewayGrants(remote, id)
}

private suspend fun ApplicationController.publishGatewayGrants(remote: RemoteConnection, id: String) {
    val grants = remote.gatewayGrants(id)
    updateState { next -> next.copy(gatewayGrants = next.gatewayGrants + (id to grants.items.map { it.account_id })) }
}

fun ApplicationController.openGatewaysSettings() {
    updateState { it.copy(settingsEntrySection = "Gateways", settingsPage = null) }
    navigate(Screen.Settings)
}