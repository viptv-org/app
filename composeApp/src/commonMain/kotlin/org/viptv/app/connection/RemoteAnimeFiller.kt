package org.viptv.app.connection

import io.ktor.http.encodeURLParameter
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

enum class FillerKind { Filler, Mixed }

@Serializable
private data class AnimeFillerBody(val episodes: List<AnimeFillerEpisode> = emptyList())

@Serializable
private data class AnimeFillerEpisode(val number: Long = 0, val kind: String = "")

private val animeFillerCodec = Json { ignoreUnknownKeys = true }

internal suspend fun RemoteConnection.animeFiller(titles: List<String>): Map<Long, FillerKind> {
    val query = titles.joinToString("&") { "title=${it.encodeURLParameter()}" }
    if (query.isEmpty()) return emptyMap()
    val body = animeFillerCodec.decodeFromJsonElement(AnimeFillerBody.serializer(), call("/api/anime/filler?$query"))
    return body.episodes.mapNotNull { episode ->
        val kind = when (episode.kind) {
            "filler" -> FillerKind.Filler
            "mixed" -> FillerKind.Mixed
            else -> return@mapNotNull null
        }
        (episode.number to kind).takeIf { episode.number > 0 }
    }.toMap()
}
