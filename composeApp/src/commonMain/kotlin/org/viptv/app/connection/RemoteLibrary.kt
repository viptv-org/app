package org.viptv.app.connection

import io.ktor.http.*
import kotlinx.serialization.json.*
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.CatalogPage

internal suspend fun RemoteConnection.library(profile: String): List<Card> {
    val codec = Json { ignoreUnknownKeys = true }
    val cards = mutableListOf<Card>()
    var offset = 0
    do {
        val page = codec.decodeFromJsonElement<CatalogPage>(call("/api/v3/profiles/${profile.encodeURLPathPart()}/library?offset=$offset&limit=100"))
        cards += page.cards
        offset += page.cards.size
        if (page.complete || page.cards.isEmpty()) break
    } while (true)
    return cards
}

internal suspend fun RemoteConnection.saveFavorite(profile: String, card: Card) {
    call("/api/v3/profiles/${profile.encodeURLPathPart()}/library", HttpMethod.Put,
        buildJsonObject { put("card", Json.encodeToJsonElement(card)) })
}

internal suspend fun RemoteConnection.removeFavorite(profile: String, card: Card) {
    call("/api/v3/profiles/${profile.encodeURLPathPart()}/library/${card.identity.kind.encodeURLPathPart()}/${card.identity.id.encodeURLPathPart()}", HttpMethod.Delete)
}
