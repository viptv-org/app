package org.viptv.app.connection

import io.ktor.http.*
import kotlinx.serialization.json.*

private val gatewaysCodec = Json { ignoreUnknownKeys = true }

suspend fun RemoteConnection.gateways(): GatewaySnapshot =
    gatewaysCodec.decodeFromJsonElement(call("/api/gateways"))

suspend fun RemoteConnection.registerGateway(name: String, endpoint: String, namespace: String, priority: Int?, integrationKey: String): Gateway =
    gatewaysCodec.decodeFromJsonElement(call("/api/gateways", HttpMethod.Post,
        gatewayRegistration(name, endpoint, namespace, priority, integrationKey)))

suspend fun RemoteConnection.replaceGateway(id: String, name: String, endpoint: String, namespace: String, priority: Int?, integrationKey: String): Gateway =
    gatewaysCodec.decodeFromJsonElement(call("/api/gateways/${id.encodeURLPathPart()}", HttpMethod.Put,
        gatewayRegistration(name, endpoint, namespace, priority, integrationKey)))

suspend fun RemoteConnection.updateGateway(id: String, enabled: Boolean) {
    call("/api/gateways/${id.encodeURLPathPart()}", HttpMethod.Patch, buildJsonObject { put("enabled", enabled) })
}

suspend fun RemoteConnection.deleteGateway(id: String) {
    call("/api/gateways/${id.encodeURLPathPart()}", HttpMethod.Delete)
}

suspend fun RemoteConnection.checkGateway(id: String): GatewayCheck =
    gatewaysCodec.decodeFromJsonElement(call("/api/gateways/${id.encodeURLPathPart()}/check", HttpMethod.Post, buildJsonObject { }))

suspend fun RemoteConnection.gatewayGrants(id: String): GatewayGrantList =
    gatewaysCodec.decodeFromJsonElement(call("/api/gateways/${id.encodeURLPathPart()}/grants"))

suspend fun RemoteConnection.grantGateway(id: String, accountId: Long) {
    call("/api/gateways/${id.encodeURLPathPart()}/grants", HttpMethod.Put, gatewayGrantBody(accountId, true))
}

suspend fun RemoteConnection.revokeGateway(id: String, accountId: Long) {
    call("/api/gateways/${id.encodeURLPathPart()}/grants", HttpMethod.Put, gatewayGrantBody(accountId, false))
}

private fun gatewayRegistration(name: String, endpoint: String, namespace: String, priority: Int?, integrationKey: String): JsonObject =
    buildJsonObject {
        put("name", name.trim())
        put("endpoint", endpoint.trim())
        put("namespace", namespace.trim())
        priority?.let { put("priority", it) }
        put("integration_key", integrationKey)
    }

private fun gatewayGrantBody(accountId: Long, enabled: Boolean): JsonObject = buildJsonObject {
    put("account_id", accountId)
    put("enabled", enabled)
}