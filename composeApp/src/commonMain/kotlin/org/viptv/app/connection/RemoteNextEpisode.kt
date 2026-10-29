package org.viptv.app.connection

import io.ktor.http.HttpMethod
import io.ktor.http.encodeURLPathPart
import kotlinx.serialization.json.*
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.ContinuationStatus
import org.viptv.engine.wire.NextEpisodeResult

internal fun Card.metadataOriginFields(): JsonObject = buildJsonObject {
    if (provider_id == "simkl" || provider_id == "stremio") put("metadata_provider_id", provider_id)
    else provider_id.takeIf { it.startsWith("addon:") }?.removePrefix("addon:")
        ?.toLongOrNull()?.takeIf { it > 0 }?.let {
            put("metadata_provider_id", "stremio"); put("metadata_addon_id", it)
        }
    metadata_origin?.let {
        put("metadata_origin", Json.encodeToJsonElement(it))
        put("card", Json.encodeToJsonElement(this@metadataOriginFields.lookupSelector()))
    }
}

internal suspend fun RemoteConnection.nextEpisode(profile: String, card: Card): NextEpisodeResult {
    if (card.identity.kind != "series" || card.identity.episode == null || card.parent_identity == null)
        return NextEpisodeResult(ContinuationStatus.UNAVAILABLE)
    val body = buildJsonObject {
        put("identity", Json.encodeToJsonElement(card.identity))
        put("parent_identity", Json.encodeToJsonElement(card.parent_identity))
        card.metadataOriginFields().forEach { (key, value) -> put(key, value) }
    }
    return Json { ignoreUnknownKeys = true }.decodeFromJsonElement(call(
        "/api/profiles/${profile.encodeURLPathPart()}/continue/next", HttpMethod.Post, body))
}
