package org.viptv.app.connection

import kotlinx.serialization.Serializable

@Serializable
data class Gateway(
    val id: String,
    val name: String,
    val endpoint: String,
    val namespace: String,
    val priority: Int,
    val enabled: Boolean,
    val revision: Long,
    val can_manage: Boolean,
)

@Serializable
data class GatewaySnapshot(
    val items: List<Gateway> = emptyList(),
    val secret_storage_configured: Boolean = true,
)

@Serializable
data class GatewayCapacity(val inputs: Int, val outputs: Int, val viewers: Int)

@Serializable
data class GatewayCheck(
    val ready: Boolean = false,
    val version: Int = 0,
    val available: GatewayCapacity? = null,
)

@Serializable
data class GatewayGrant(val account_id: Long, val enabled: Boolean = true)

@Serializable
data class GatewayGrantList(val items: List<GatewayGrant> = emptyList(), val next_cursor: String? = null)

internal fun gatewayCodeMessage(code: String?): String? = when (code) {
    "secret_store_not_configured" -> "The server's encryption keyring is not configured, so gateways can't be saved. Ask the server operator."
    "invalid_gateway_key" -> "Use a scoped gateway integration key starting with pgk_."
    "invalid_gateway_endpoint", "gateway_private_destination" -> "Use a public HTTPS gateway endpoint."
    "invalid_gateway_configuration" -> "Check the gateway name, namespace and priority."
    "gateway_already_configured" -> "This gateway and namespace are already configured in your account."
    "gateway_not_found" -> "This gateway is unavailable in your account."
    "gateway_not_ready", "gateway_unavailable", "gateway_dns_unavailable" -> "The gateway is not ready or could not be reached securely. Try again."
    "gateway_key_rejected" -> "The gateway rejected this integration key."
    "gateway_scope_missing" -> "The key must allow this namespace and all required playback operations."
    "too_many_gateways" -> "This account has reached its gateway configuration limit."
    "gateway_checks_busy" -> "Gateway checks are busy. Try again shortly."
    "gateway_account_unavailable" -> "The account selected for this grant is unavailable."
    else -> null
}