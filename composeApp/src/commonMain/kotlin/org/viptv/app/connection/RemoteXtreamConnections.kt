package org.viptv.app.connection

import io.ktor.http.*
import kotlinx.serialization.json.*

data class XtreamScopes(val enabled: Boolean = true, val live: Boolean = true, val movies: Boolean = true,
    val series: Boolean = true) {
    val contentEnabled: Boolean get() = live || movies || series
}

data class XtreamRefresh(val state: String, val error: String?) {
    val running: Boolean get() = state == "queued" || state == "running"
}

data class XtreamConnection(val id: Long, val name: String, val scopes: XtreamScopes,
    val credentialsEncrypted: Boolean, val refresh: XtreamRefresh)

internal const val xtreamManagedElsewhere = "Manage this from a signed-in phone, computer or the web dashboard."

internal fun xtreamFailureMessage(code: String): String? = when (code) {
    "provider_credentials_rejected" -> "The provider rejected this login. Check the username, password and subscription."
    "provider_already_configured" -> "This provider login is already in your account."
    "too_many_providers" -> "This account has reached its limit of 64 connections."
    "invalid_provider_configuration" -> "Check the name, server address, username and password."
    "invalid_provider_endpoint", "provider_private_destination" -> "Use a public HTTP or HTTPS server address."
    "provider_not_found" -> "This connection is no longer in your account."
    "provider_encryption_required" -> "This connection needs the server operator's migration before it can be changed here."
    "provider_checks_busy" -> "Another connection check is running. Try again shortly."
    "provider_rate_limited" -> "The provider is limiting requests. Wait and try again."
    "provider_timeout" -> "The provider took too long to respond. Try again."
    "provider_connection_limit" -> "The provider's connection limit is reached. Close a stream and try again."
    "provider_unavailable", "provider_response_interrupted" -> "The provider could not be reached. Try again."
    "provider_protocol_invalid", "provider_response_too_large" -> "The provider returned an unexpected response."
    "secret_store_not_configured" -> "This server cannot store credentials, so connections cannot be changed here."
    "account_session_required" -> xtreamManagedElsewhere
    "catalog_unavailable" -> "This connection has no enabled content to sync."
    "source_configuration_changed" -> "This connection changed while saving. Refresh the list and try again."
    "invalid_catalog_query", "invalid_cursor" -> "Refresh the list and try again."
    else -> null
}

private fun xtreamInvalid(): RemoteFailure =
    RemoteFailure(502, "The server returned invalid connection settings. Try again or update the server.")

private fun xtreamFailure(failure: RemoteFailure): RemoteFailure =
    failure.code?.let { xtreamFailureMessage(it) }?.let { RemoteFailure(failure.status, it, failure.code) } ?: failure

private suspend fun RemoteConnection.xtreamCall(path: String, method: HttpMethod = HttpMethod.Get,
    body: JsonElement? = null): JsonElement = try { call(path, method, body) }
    catch (failure: RemoteFailure) { throw xtreamFailure(failure) }

private fun xtreamConnection(value: JsonElement): XtreamConnection {
    val row = value as? JsonObject ?: throw xtreamInvalid()
    val id = row["id"]?.jsonPrimitive?.longOrNull ?: throw xtreamInvalid()
    val name = row["name"]?.jsonPrimitive?.contentOrNull ?: throw xtreamInvalid()
    fun flag(key: String) = row[key]?.jsonPrimitive?.booleanOrNull ?: throw xtreamInvalid()
    val refresh = row["refresh"]?.jsonObject ?: throw xtreamInvalid()
    return XtreamConnection(id, name,
        XtreamScopes(flag("enabled"), flag("enable_live"), flag("enable_movies"), flag("enable_series")),
        flag("credentials_encrypted"),
        XtreamRefresh(refresh["state"]?.jsonPrimitive?.contentOrNull ?: throw xtreamInvalid(),
            refresh["error"]?.jsonPrimitive?.contentOrNull))
}

private fun xtreamRefresh(value: JsonElement): XtreamRefresh {
    val row = value as? JsonObject ?: throw xtreamInvalid()
    return XtreamRefresh(row["state"]?.jsonPrimitive?.contentOrNull ?: throw xtreamInvalid(),
        row["error"]?.jsonPrimitive?.contentOrNull)
}

private fun XtreamScopes.body(): JsonObject = buildJsonObject {
    put("enabled", enabled)
    put("enable_live", live)
    put("enable_movies", movies)
    put("enable_series", series)
}

internal suspend fun RemoteConnection.xtreamConnections(): List<XtreamConnection> {
    val items = xtreamCall("/api/iptv/connections?limit=200").jsonObject["items"]?.jsonArray ?: throw xtreamInvalid()
    return items.map(::xtreamConnection)
}

internal suspend fun RemoteConnection.xtreamDefaultLive(): Long? =
    xtreamCall("/api/iptv/live-default").jsonObject["catalog_id"]?.jsonPrimitive?.longOrNull

internal suspend fun RemoteConnection.createXtreamConnection(name: String, url: String, username: String,
    password: String, scopes: XtreamScopes): XtreamConnection = xtreamConnection(xtreamCall("/api/iptv/connections",
    HttpMethod.Post, buildJsonObject {
        put("name", name)
        put("url", url)
        put("username", username)
        put("password", password)
        scopes.body().forEach { (key, value) -> put(key, value) }
    }))

internal suspend fun RemoteConnection.updateXtreamConnection(id: Long, name: String,
    scopes: XtreamScopes): XtreamConnection = xtreamConnection(xtreamCall("/api/iptv/connections/$id",
    HttpMethod.Patch, buildJsonObject {
        put("name", name)
        scopes.body().forEach { (key, value) -> put(key, value) }
    }))

internal suspend fun RemoteConnection.replaceXtreamPassword(id: Long, password: String): XtreamConnection =
    xtreamConnection(xtreamCall("/api/iptv/connections/$id/credentials", HttpMethod.Put,
        buildJsonObject { put("password", password) }))

internal suspend fun RemoteConnection.syncXtreamConnection(id: Long): XtreamRefresh =
    xtreamRefresh(xtreamCall("/api/iptv/connections/$id/refresh", HttpMethod.Post))

internal suspend fun RemoteConnection.setXtreamDefaultLive(id: Long): Long =
    xtreamCall("/api/iptv/live-default", HttpMethod.Put, buildJsonObject { put("catalog_id", id) })
        .jsonObject["catalog_id"]?.jsonPrimitive?.longOrNull ?: throw xtreamInvalid()

internal suspend fun RemoteConnection.deleteXtreamConnection(id: Long) {
    xtreamCall("/api/iptv/connections/$id", HttpMethod.Delete)
}
