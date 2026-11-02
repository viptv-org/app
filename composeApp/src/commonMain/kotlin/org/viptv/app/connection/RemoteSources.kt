package org.viptv.app.connection

import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.SourceCandidate
import org.viptv.engine.wire.SourcePresentation

// The host's SDK producer spends up to 10s on metadata plus 45s on providers: poll through that whole interval.
internal suspend fun RemoteConnection.sources(card: Card, publish: (SourcesOutcome) -> Unit = {}): SourcesOutcome = withTimeout(60_000) {
    val started = call("/api/streams", HttpMethod.Post, buildJsonObject {
        put("type", card.identity.kind); put("id", card.identity.id)
        card.parent_identity?.takeIf { card.identity.kind == "series" }?.let { put("series_id", it.id) }
        card.metadataOriginFields().forEach { (key, value) -> put(key, value) }
        card.streamRequestFields().forEach { (key, value) -> put(key, value) }
        card.identity.episode?.let { coordinates ->
            coordinates.season?.let { put("season", it) }; put("episode", coordinates.episode)
        }
    }).jsonObject
    val job = started.getValue("id").jsonPrimitive.content.encodeURLPathPart()
    var completed = false
    try {
        val presentationCodec = Json { ignoreUnknownKeys = true }
        val found = linkedMapOf<String, SourceCandidate>()
        val producers = linkedMapOf<String, SourceProducerOutcome>()
        var after = 0
        while (true) {
            val page = call("/api/streams/$job?after=$after&wait_ms=10000").jsonObject
            val events = page.getValue("events").jsonArray
            events.forEach { raw ->
                val event = raw.jsonObject
                event["source"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }?.let { id ->
                    val prior = producers[id]
                    producers[id] = SourceProducerOutcome(id, prior?.label.orEmpty(),
                        event["error_code"]?.jsonPrimitive?.contentOrNull ?: prior?.errorCode,
                        event["error"]?.jsonPrimitive?.contentOrNull ?: prior?.errorMessage)
                }
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
            if (events.isNotEmpty()) publish(SourcesOutcome(found.values.toList(), producers = producers.values.toList()))
            if (page["done"]?.jsonPrimitive?.boolean == true) { completed = true; break }
            if (events.isEmpty()) delay(500)
        }
        SourcesOutcome(found.values.toList(), producers = producers.values.toList())
    } finally {
        if (!completed) withContext(NonCancellable) {
            withTimeoutOrNull(2000) { try { call("/api/streams/$job", HttpMethod.Delete) }
                catch (error: CancellationException) { throw error }
                catch (_: Exception) { } }
        }
    }
}

// The host rejects the whole request when a field it validates is malformed, so send only accepted values.
private fun Card.streamRequestFields(): JsonObject = buildJsonObject {
    val show = if (identity.episode == null) identity else parent_identity ?: identity
    show.ids.firstOrNull { it.namespace == "imdb" }?.value.requestImdbId()?.let { put("imdb_id", it) }
    show.ids.firstOrNull { it.namespace == "tmdb" }?.value.requestTmdbId()?.let { put("tmdb_id", it) }
    if (identity.episode == null) title.requestText(512)?.let { put("name", it) }
    metadata?.year?.takeIf { it in 1870..2200 }?.let { put("year", it) }
}

private fun String?.requestText(limit: Int): String? = this?.trim()
    ?.takeIf { it.isNotEmpty() && it.length <= limit && it.none { char -> char.isISOControl() } }

private fun String?.requestImdbId(): String? = this?.trim()?.removePrefix("imdb:")
    ?.takeIf { it.startsWith("tt") && it.length in 7..14 && it.drop(2).all { char -> char in '0'..'9' } }

private fun String?.requestTmdbId(): String? = this?.trim()?.removePrefix("tmdb:")
    ?.takeIf { it.length in 1..12 && it.all { char -> char in '0'..'9' } }
    ?.toLongOrNull()?.takeIf { it in 1..2_147_483_647L }?.toString()

private fun JsonObject.text(key: String): String? = (get(key) as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }
private fun JsonObject.texts(key: String): List<String> = (get(key) as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.takeIf { value -> value.isString }?.content }

internal fun SourceCandidate.withReleaseFacts(card: JsonObject): SourceCandidate = copy(
    release_type = card.text("release_type")?.takeUnless { it == "unknown" },
    three_d = (card["three_d"] as? JsonPrimitive)?.booleanOrNull ?: false,
    dynamic_range = card.texts("dynamic_range"),
    audio_flags = card.texts("audio_flags"),
    seeders = (card["seeders"] as? JsonPrimitive)?.takeUnless { it.isString }?.longOrNull,
    delivery = card.text("delivery"),
    subbed = (card["subbed"] as? JsonPrimitive)?.booleanOrNull ?: false,
    subtitle_languages = card.texts("subtitle_languages"),
)
