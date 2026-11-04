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

fun ApplicationController.setGatewayEnabled(id: String, enabled: Boolean) {
    val before = mutable.value
    mutate(gatewayTarget(id, GATEWAY_ENABLE_ACTION),
        optimistic = { state -> state.copy(gateways = state.gateways?.let { snapshot ->
            snapshot.copy(items = snapshot.items.map { if (it.id == id) it.copy(enabled = enabled) else it }) }) },
        revert = { it.copy(gateways = before.gateways) }) {
        val remote = requireNotNull(connection)
        remote.updateGateway(id, enabled)
        loadGateways()
    }
}

fun ApplicationController.deleteGateway(id: String) {
    val before = mutable.value
    mutate(gatewayTarget(id, GATEWAY_DELETE_ACTION),
        optimistic = { state -> state.copy(gateways = state.gateways?.let { snapshot ->
            snapshot.copy(items = snapshot.items.filterNot { it.id == id }) }) },
        revert = { it.copy(gateways = before.gateways) }) {
        val remote = requireNotNull(connection)
        remote.deleteGateway(id)
        loadGateways()
    }
}

fun ApplicationController.checkGateway(id: String) = mutate(gatewayTarget(id, GATEWAY_CHECK_ACTION)) {
    val remote = requireNotNull(connection)
    val result = remote.checkGateway(id)
    updateState { next -> next.copy(gatewayChecks = next.gatewayChecks + (id to result)) }
}

fun ApplicationController.loadGatewayGrants(id: String) = run {
    val remote = requireNotNull(connection)
    publishGatewayGrants(remote, id)
}

fun ApplicationController.grantGatewayAccess(id: String, accountId: Long) =
    mutate(gatewayTarget(id, gatewayGrantTarget(accountId)),
        optimistic = { state -> state.copy(gatewayGrants = state.gatewayGrants + (id to (state.gatewayGrants[id].orEmpty() + accountId))) },
        revert = { it.copy(gatewayGrants = it.gatewayGrants + (id to it.gatewayGrants[id].orEmpty().filterNot { grant -> grant == accountId })) }) {
        val remote = requireNotNull(connection)
        remote.grantGateway(id, accountId)
        publishGatewayGrants(remote, id)
    }

fun ApplicationController.revokeGatewayAccess(id: String, accountId: Long) =
    mutate(gatewayTarget(id, gatewayGrantTarget(accountId)),
        optimistic = { state -> state.copy(gatewayGrants = state.gatewayGrants + (id to state.gatewayGrants[id].orEmpty().filterNot { it == accountId })) },
        revert = { it.copy(gatewayGrants = it.gatewayGrants + (id to it.gatewayGrants[id].orEmpty() + accountId)) }) {
        val remote = requireNotNull(connection)
        remote.revokeGateway(id, accountId)
        publishGatewayGrants(remote, id)
    }

internal const val GATEWAY_ENABLE_ACTION = "Enable"
internal const val GATEWAY_DELETE_ACTION = "Delete"
internal const val GATEWAY_CHECK_ACTION = "Check capacity"
internal fun gatewayTarget(id: String, action: String) = MutationTarget(MutationKind.Gateway, "$id $action")
internal fun gatewayGrantTarget(accountId: Long) = "Access $accountId"

private suspend fun ApplicationController.publishGatewayGrants(remote: RemoteConnection, id: String) {
    val grants = remote.gatewayGrants(id)
    updateState { next -> next.copy(gatewayGrants = next.gatewayGrants + (id to grants.items.map { it.account_id })) }
}

fun ApplicationController.openGatewaysSettings() {
    updateState { it.copy(settingsEntrySection = "Gateways", settingsPage = null) }
    navigate(Screen.Settings)
}