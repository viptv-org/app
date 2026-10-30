package org.viptv.app.connection

import io.ktor.http.HttpMethod
import io.ktor.http.encodeURLPathPart
import kotlinx.serialization.json.*
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.ContinuationPage
import org.viptv.engine.wire.HomeBrowseTarget
import org.viptv.engine.wire.HomeRow
import org.viptv.engine.wire.MediaIdentity
import org.viptv.engine.wire.QueueVisibilityReceipt

internal suspend fun RemoteConnection.continueWatching(profile: String, offset: Int, limit: Int): ContinuationPage =
    Json { ignoreUnknownKeys = true }.decodeFromJsonElement(
        call("/api/profiles/${profile.encodeURLPathPart()}/continue?offset=$offset&limit=$limit"))

internal fun HomeRow.opensContinueWatching(): Boolean = browse?.target == HomeBrowseTarget.CONTINUE

private const val CONTINUE_VISIBILITY = "/continue/visibility"

// Hiding resolves the entry to its title identity, which Undo then shows again on its own.
internal suspend fun RemoteConnection.hideFromContinueWatching(profile: String, card: Card): MediaIdentity =
    Json { ignoreUnknownKeys = true }.decodeFromJsonElement<QueueVisibilityReceipt>(call(
        "/api/profiles/${profile.encodeURLPathPart()}$CONTINUE_VISIBILITY", HttpMethod.Put, buildJsonObject {
            put("identity", Json.encodeToJsonElement(card.identity))
            card.parent_identity?.let { put("parent_identity", Json.encodeToJsonElement(it)) }
            put("hidden", true)
            card.metadata_origin?.let { origin ->
                put("metadata_origin", Json.encodeToJsonElement(origin))
                put("card", Json.encodeToJsonElement(card.lookupSelector()))
            }
        })).identity

internal suspend fun RemoteConnection.showInContinueWatching(profile: String, identity: MediaIdentity) {
    call("/api/profiles/${profile.encodeURLPathPart()}$CONTINUE_VISIBILITY", HttpMethod.Put, buildJsonObject {
        put("identity", Json.encodeToJsonElement(identity))
        put("hidden", false)
    })
}
