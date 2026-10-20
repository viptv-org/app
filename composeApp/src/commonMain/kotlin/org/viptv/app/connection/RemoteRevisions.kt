package org.viptv.app.connection

import io.ktor.http.HttpMethod
import io.ktor.http.encodeURLPathPart
import kotlinx.serialization.json.*
import org.viptv.app.state.Revisions

/** One cheap DB-only read of the profile's change tokens. Null when the server predates them. */
internal suspend fun RemoteConnection.revisions(profile: String): Revisions? {
    val (status, body) = exchange("/api/profiles/${profile.encodeURLPathPart()}/revisions", HttpMethod.Get)
    if (status == 404) return null
    if (status !in 200..299) throw RemoteFailure(status, if (status == 401) "Sign in to continue." else "The server could not complete this request.")
    return parseRevisions(Json.parseToJsonElement(body)) ?: throw RemoteFailure(502, "The server returned an incompatible response.")
}

internal fun parseRevisions(value: JsonElement?): Revisions? = (value as? JsonObject)?.entries
    ?.mapNotNull { (key, token) -> (token as? JsonPrimitive)?.takeUnless { it is JsonNull }?.let { key to it.content } }?.toMap()
