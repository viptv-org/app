package org.viptv.app.connection

import io.ktor.http.*
import kotlinx.serialization.json.*
import org.viptv.engine.wire.LiveGuide
import org.viptv.engine.wire.LivePage
import org.viptv.engine.wire.LiveQuery
import org.viptv.engine.wire.SourceCandidate

private val liveCodec = Json { ignoreUnknownKeys = true }

suspend fun RemoteConnection.liveChannels(profile: String, query: LiveQuery): LivePage {
    val parameters = buildList {
        add("offset=${query.offset}")
        query.limit?.let { add("limit=$it") }
        query.connection_id?.let { add("connection_id=${it.encodeURLParameter()}") }
        query.group_id?.let { add("group_id=${it.encodeURLParameter()}") }
        query.search?.let { add("search=${it.encodeURLParameter()}") }
    }
    return liveCodec.decodeFromJsonElement(call("/api/profiles/${profile.encodeURLPathPart()}/live/channels?${parameters.joinToString("&")}"))
}

suspend fun RemoteConnection.liveGuide(profile: String, channel: String): LiveGuide =
    liveCodec.decodeFromJsonElement(call("/api/profiles/${profile.encodeURLPathPart()}/live/${channel.encodeURLPathPart()}/guide"))

suspend fun RemoteConnection.liveSources(profile: String, channel: String): List<SourceCandidate> {
    val page = call("/api/profiles/${profile.encodeURLPathPart()}/live/${channel.encodeURLPathPart()}/sources",
        HttpMethod.Post, buildJsonObject {}).jsonObject
    return page.getValue("streams").jsonArray.map { element ->
        val row = element.jsonObject
        SourceCandidate(row.getValue("id").jsonPrimitive.content, row["provider_id"]?.jsonPrimitive?.contentOrNull ?: "server",
            row["label"]?.jsonPrimitive?.contentOrNull ?: row["name"]?.jsonPrimitive?.contentOrNull ?: "Live source",
            detail = row["detail"]?.jsonPrimitive?.contentOrNull, quality_height = row["quality_height"]?.jsonPrimitive?.longOrNull,
            audio_languages = row["audio_languages"]?.jsonArray.orEmpty().mapNotNull { it.jsonPrimitive.contentOrNull })
            .withReleaseFacts(row)
    }
}
