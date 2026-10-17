package org.viptv.app.connection

import io.ktor.http.HttpMethod
import io.ktor.http.encodeURLPathPart
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import org.viptv.app.player.PlaybackInput
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.MediaIdentity

internal enum class PlaybackEvent(val wire: String) { Start("start"), Pause("pause"), Stop("stop") }

internal data class TrackingReceipt(
    val state: String,
    val providerId: String?,
    val reason: String?,
    val action: String?,
    val completed: Boolean,
    val deduplicated: Boolean = false,
)

/** One ordered event stream per actual playback attempt; provider selection stays on the server. */
internal class RemotePlaybackEvents(
    private val remote: RemoteConnection,
    private val input: PlaybackInput,
    private val card: Card,
    private val parent: MediaIdentity? = card.parent_identity,
) {
    private val gate = Mutex()
    private var sequence = 0L

    suspend fun emit(event: PlaybackEvent, positionMillis: Long, durationMillis: Long?, ended: Boolean = false): TrackingReceipt = gate.withLock {
        val profile = requireNotNull(input.profileId) { "Playback is not bound to a profile." }
        check(sequence < Long.MAX_VALUE)
        sequence++
        val result = remote.call("/api/profiles/${profile.encodeURLPathPart()}/playback-events", HttpMethod.Post,
            buildJsonObject {
                put("attempt_id", input.id); put("sequence", sequence)
                put("identity", Json.encodeToJsonElement(card.identity))
                parent?.let { put("parent_identity", Json.encodeToJsonElement(it)) }
                put("event", event.wire)
                put("position_millis", positionMillis.coerceAtLeast(0))
                put("duration_millis", (durationMillis ?: 0).coerceAtLeast(0))
                put("ended", ended)
            }).jsonObject
        val state = result["state"]?.jsonPrimitive?.content
        if (state !in setOf("recorded", "ignored", "unavailable"))
            throw RemoteFailure(502, "The server did not confirm the playback event.")
        val deduplicated = result["deduplicated"]?.jsonPrimitive?.booleanOrNull == true
        val completed = result["completed"]?.jsonPrimitive?.booleanOrNull
        val receiptOnly = deduplicated && completed == true && result["action"] == JsonNull
        if (state == "recorded" && ((!receiptOnly && result["action"]?.jsonPrimitive?.content !in listOf("start", "pause", "stop"))
                    || result["completed"]?.jsonPrimitive?.booleanOrNull == null))
            throw RemoteFailure(502, "The server returned an incomplete tracking receipt.")
        TrackingReceipt(requireNotNull(state), result["provider_id"]?.jsonPrimitive?.contentOrNull,
            result["reason"]?.jsonPrimitive?.contentOrNull, result["action"]?.jsonPrimitive?.contentOrNull,
            completed ?: false, deduplicated)
    }
}
