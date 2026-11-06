package org.viptv.app.connection

import io.ktor.http.HttpMethod
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

data class AddonConfigureLink(val url: String, val expiresInSeconds: Long)

// The server keeps the manifest token and answers a short-lived ticketed path; the client only ever sees that path.
suspend fun RemoteConnection.addonConfigureLink(id: String): AddonConfigureLink {
    val path = "/api/addons/${addonNumericId(id)}/configure-link"
    val response = call(path, HttpMethod.Post, buildJsonObject {}).jsonObject
    val ticket = response["configure_url"]?.jsonPrimitive?.contentOrNull
        ?: throw RemoteFailure(502, "The server sent no configure link.")
    if (!ticket.startsWith("/api/addons/configure/")) throw RemoteFailure(502, "The server sent an unusable configure link.")
    return AddonConfigureLink(origin + ticket, response["expires_in"]?.jsonPrimitive?.longOrNull ?: 300L)
}
