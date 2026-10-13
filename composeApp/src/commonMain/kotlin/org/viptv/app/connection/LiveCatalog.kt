package org.viptv.app.connection

import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.SourceCandidate

@Serializable
data class LiveQuery(val connection_id: String? = null, val group_id: String? = null, val search: String? = null,
    val offset: Int = 0, val limit: Int = 100)
@Serializable
data class LiveCatalogChannel(val id: String, val connection_id: String, val card: Card,
    val group_id: String? = null, val group_name: String? = null, val group_ids: List<String> = emptyList())
@Serializable
data class LiveCatalogGroup(val id: String, val label: String)
@Serializable
data class LiveCatalogPage(val channels: List<LiveCatalogChannel> = emptyList(), val groups: List<LiveCatalogGroup> = emptyList(),
    val next_cursor: String? = null, val complete: Boolean = true, val availability: String = "Unsupported", val reason: String? = null)
@Serializable
data class LiveGuideProgram(val id: String, val title: String, val start_millis: Long, val end_millis: Long, val description: String? = null)
@Serializable
data class LiveGuideSnapshot(val channel_id: String, val programs: List<LiveGuideProgram> = emptyList(),
    val availability: String = "Unsupported", val reason: String? = null)

private val liveCodec = Json { ignoreUnknownKeys = true }

suspend fun RemoteConnection.liveChannels(profile: String, query: LiveQuery): LiveCatalogPage {
    val parameters = buildList {
        add("offset=${query.offset}"); add("limit=${query.limit}")
        query.connection_id?.let { add("connection_id=${it.encodeURLParameter()}") }
        query.group_id?.let { add("group_id=${it.encodeURLParameter()}") }
        query.search?.let { add("search=${it.encodeURLParameter()}") }
    }
    return liveCodec.decodeFromJsonElement(call("/api/v3/profiles/${profile.encodeURLPathPart()}/live/channels?${parameters.joinToString("&")}"))
}

suspend fun RemoteConnection.liveGuide(profile: String, channel: String): LiveGuideSnapshot =
    liveCodec.decodeFromJsonElement(call("/api/v3/profiles/${profile.encodeURLPathPart()}/live/${channel.encodeURLPathPart()}/guide"))

suspend fun RemoteConnection.liveSources(profile: String, channel: String): List<SourceCandidate> {
    val page = call("/api/v3/profiles/${profile.encodeURLPathPart()}/live/${channel.encodeURLPathPart()}/sources").jsonObject
    return page.getValue("streams").jsonArray.map { element ->
        val row = element.jsonObject
        SourceCandidate(row.getValue("id").jsonPrimitive.content, row["provider_id"]?.jsonPrimitive?.contentOrNull ?: "server",
            row["label"]?.jsonPrimitive?.contentOrNull ?: row["name"]?.jsonPrimitive?.contentOrNull ?: "Live source",
            detail = row["detail"]?.jsonPrimitive?.contentOrNull, quality_height = row["quality_height"]?.jsonPrimitive?.longOrNull,
            audio_languages = row["audio_languages"]?.jsonArray.orEmpty().mapNotNull { it.jsonPrimitive.contentOrNull })
    }
}
