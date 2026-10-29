package org.viptv.app.connection

import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.SourceCandidate
import org.viptv.engine.wire.SourcePresentation

// The host's SDK producer spends up to 10s on metadata plus 45s on providers: poll through that whole interval.
internal suspend fun RemoteConnection.sources(card: Card, publish: (List<SourceCandidate>) -> Unit = {}): List<SourceCandidate> = withTimeout(60_000) {
    val started = call("/api/streams", HttpMethod.Post, buildJsonObject {
        put("type", card.identity.kind); put("id", card.identity.id)
        card.parent_identity?.takeIf { card.identity.kind == "series" }?.let { put("series_id", it.id) }
        card.metadataOriginFields().forEach { (key, value) -> put(key, value) }
        card.identity.ids.firstOrNull { it.namespace == "imdb" }?.let { put("imdb_id", it.value) }
        card.identity.episode?.let { coordinates ->
            coordinates.season?.let { put("season", it) }; put("episode", coordinates.episode)
        }
    }).jsonObject
    val job = started.getValue("id").jsonPrimitive.content.encodeURLPathPart()
    var completed = false
    try {
        val presentationCodec = Json { ignoreUnknownKeys = true }
        val found = linkedMapOf<String, SourceCandidate>()
        var after = 0
        while (true) {
            val page = call("/api/streams/$job?after=$after&wait_ms=10000").jsonObject
            val events = page.getValue("events").jsonArray
            events.forEach { raw ->
                val event = raw.jsonObject
                event["streams"]?.jsonArray.orEmpty().forEach { stream ->
                    val value = stream.jsonObject
                    val id = value.getValue("id").jsonPrimitive.content
                    found[id] = SourceCandidate(id, event["source"]?.jsonPrimitive?.content ?: "server",
                        value["name"]?.jsonPrimitive?.contentOrNull ?: value["title"]?.jsonPrimitive?.contentOrNull ?: "Source",
                        detail = value["detail"]?.jsonPrimitive?.contentOrNull
                            ?: value["description"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                            ?: value["title"]?.jsonPrimitive?.contentOrNull,
                        quality_height = value["quality_height"]?.jsonPrimitive?.longOrNull,
                        audio_languages = value["audio_languages"]?.jsonArray.orEmpty().mapNotNull { it.jsonPrimitive.contentOrNull },
                        presentation = value["presentation"]?.takeUnless { it is JsonNull }?.let { presentationCodec.decodeFromJsonElement<SourcePresentation>(it) })
                        .withReleaseFacts(value)
                }
            }
            after += events.size
            if (events.isNotEmpty()) publish(found.values.toList())
            if (page["done"]?.jsonPrimitive?.boolean == true) { completed = true; break }
            if (events.isEmpty()) delay(500)
        }
        found.values.toList()
    } finally {
        if (!completed) withContext(NonCancellable) {
            withTimeoutOrNull(2000) { try { call("/api/streams/$job", HttpMethod.Delete) }
                catch (error: CancellationException) { throw error }
                catch (_: Exception) { } }
        }
    }
}

private fun JsonObject.text(key: String): String? = (get(key) as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }
private fun JsonObject.texts(key: String): List<String> = (get(key) as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.takeIf { value -> value.isString }?.content }

internal fun SourceCandidate.withReleaseFacts(card: JsonObject): SourceCandidate = copy(
    release_type = card.text("release_type")?.takeUnless { it == "unknown" },
    three_d = (card["three_d"] as? JsonPrimitive)?.booleanOrNull ?: false,
    dynamic_range = card.texts("dynamic_range"),
    audio_flags = card.texts("audio_flags"),
    seeders = (card["seeders"] as? JsonPrimitive)?.takeUnless { it.isString }?.longOrNull,
    delivery = card.text("delivery"),
)
