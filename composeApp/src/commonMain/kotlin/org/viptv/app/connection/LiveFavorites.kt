package org.viptv.app.connection

import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

@Serializable
private data class LiveFavoritesSnapshot(val channel_ids: List<String> = emptyList())
private val favoritesCodec = Json { ignoreUnknownKeys = true }
internal fun decodeLiveFavorites(raw: String): Set<String> = liveFavoriteIds(favoritesCodec.decodeFromString<LiveFavoritesSnapshot>(raw))
private fun liveFavoriteIds(snapshot: LiveFavoritesSnapshot): Set<String> {
    if (snapshot.channel_ids.size > 10_000 || snapshot.channel_ids.any { it.isBlank() || it.length > 512 || it.any(Char::isISOControl) })
        throw LocalFailure("Could not load channel favourites.")
    return snapshot.channel_ids.toSet()
}
suspend fun RemoteConnection.liveFavorites(profile: String): Set<String> =
    liveFavoriteIds(favoritesCodec.decodeFromJsonElement(call("/api/profiles/${profile.encodeURLPathPart()}/live/favorites")))
suspend fun RemoteConnection.setLiveFavorite(profile: String, channel: String, saved: Boolean): Set<String> =
    liveFavoriteIds(favoritesCodec.decodeFromJsonElement(call("/api/profiles/${profile.encodeURLPathPart()}/live/${channel.encodeURLPathPart()}/favorite", if (saved) HttpMethod.Put else HttpMethod.Delete)))
