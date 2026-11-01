package org.viptv.app.connection

import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

private val vodMatchCodec = Json { ignoreUnknownKeys = true }

@Serializable
internal data class VodMatch(val vod_id: String, val provider_id: Long, val type: String, val name: String,
    val year: Int? = null, val poster: String? = null)

@Serializable
internal data class VodMatchPage(val items: List<VodMatch> = emptyList(), val next_cursor: String? = null,
    val previous_cursor: String? = null)

@Serializable
internal data class IptvConnection(val id: Long, val name: String, val enabled: Boolean = true,
    val enable_movies: Boolean = true, val enable_series: Boolean = true)

@Serializable
private data class IptvConnectionPage(val items: List<IptvConnection> = emptyList(), val next_cursor: String? = null)

internal data class VodMatchFilter(val providerId: Long? = null, val type: String? = null, val query: String = "")

internal suspend fun RemoteConnection.vodMatches(filter: VodMatchFilter, cursor: String? = null): VodMatchPage {
    val query = buildList {
        add("limit=50")
        filter.providerId?.let { add("provider_id=$it") }
        filter.type?.let { add("kind=${it.encodeURLParameter()}") }
        if (filter.query.isNotBlank()) add("search=${filter.query.encodeURLParameter()}")
        cursor?.let { add("cursor=${it.encodeURLParameter()}") }
    }.joinToString("&")
    return vodMatchCodec.decodeFromJsonElement(call("/api/iptv/matches?$query"))
}

internal suspend fun RemoteConnection.iptvConnections(): List<IptvConnection> {
    val connections = mutableListOf<IptvConnection>()
    val visited = mutableSetOf<String>()
    var cursor: String? = null
    do {
        val suffix = cursor?.let { "&cursor=${it.encodeURLParameter()}" }.orEmpty()
        val page: IptvConnectionPage = vodMatchCodec.decodeFromJsonElement(call("/api/iptv/connections?limit=200$suffix"))
        connections += page.items
        cursor = page.next_cursor
        if (cursor != null && !visited.add(cursor)) throw RemoteFailure(502, "The server repeated an Xtream connection page.")
    } while (cursor != null)
    return connections
}

internal suspend fun RemoteConnection.overrideVodMatch(vodId: String, metadataId: String, type: String) {
    call("/api/iptv/matches", HttpMethod.Put, buildJsonObject {
        put("vod_id", vodId)
        put("metadata_id", metadataId)
        put("type", type)
    })
}