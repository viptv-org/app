package org.viptv.app.connection

import io.ktor.http.*
import kotlinx.serialization.json.*
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.CatalogPage

internal suspend fun RemoteConnection.library(profile: String): List<Card> {
    val codec = Json { ignoreUnknownKeys = true }
    return codec.decodeFromJsonElement<CatalogPage>(call("/api/v3/profiles/${profile.encodeURLPathPart()}/library?offset=0&limit=100")).cards
}

internal suspend fun RemoteConnection.saveFavorite(profile: String, card: Card) {
    call("/api/v3/profiles/${profile.encodeURLPathPart()}/library", HttpMethod.Put,
        buildJsonObject { put("card", Json.encodeToJsonElement(card)) })
}

internal suspend fun RemoteConnection.removeFavorite(profile: String, card: Card) {
    call("/api/v3/profiles/${profile.encodeURLPathPart()}/library/${card.identity.kind.encodeURLPathPart()}/${card.identity.id.encodeURLPathPart()}", HttpMethod.Delete)
}

internal suspend fun RemoteConnection.libraryMembership(profile: String): org.viptv.engine.wire.LibraryMembershipSnapshot =
    Json { ignoreUnknownKeys = true }.decodeFromJsonElement(call("/api/v3/profiles/${profile.encodeURLPathPart()}/library/membership"))

internal suspend fun RemoteConnection.libraryImportStatus(profile: String): org.viptv.engine.wire.LibraryImportStatus =
    Json { ignoreUnknownKeys = true }.decodeFromJsonElement(call("/api/v3/profiles/${profile.encodeURLPathPart()}/imports/simkl"))
