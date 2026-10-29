package org.viptv.app.connection

import io.ktor.http.encodeURLParameter
import io.ktor.http.encodeURLPathPart
import kotlinx.serialization.json.*
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.SubtitleSnapshot

internal suspend fun RemoteConnection.subtitlesFor(card: Card): SubtitleSnapshot {
    val path = "/api/subtitles/${card.identity.kind.encodeURLPathPart()}/${card.identity.id.encodeURLPathPart()}"
    val origin = card.metadataOriginFields()
    val query = origin.entries.joinToString("&") { "${it.key}=${(if (it.value is JsonPrimitive) it.value.jsonPrimitive.content else it.value.toString()).encodeURLParameter()}" }
    val value = try { call(path + if (query.isEmpty()) "" else "?$query") }
    catch (error: RemoteFailure) {
        if (error.status != 400 || query.isEmpty()) throw error
        if (card.metadata_origin != null) throw RemoteFailure(422, "This server cannot preserve this subtitle title’s origin. Update the server and try again.")
        // An older server's plain lookup is equivalent, except for a Stremio opaque id that resembles a Simkl id.
        if (origin["metadata_provider_id"]?.jsonPrimitive?.content == "stremio" && card.identity.id.startsWith("simkl:"))
            throw RemoteFailure(422, "This server cannot use this subtitle metadata origin.")
        call(path)
    }
    return Json { ignoreUnknownKeys = true }.decodeFromJsonElement(value)
}
