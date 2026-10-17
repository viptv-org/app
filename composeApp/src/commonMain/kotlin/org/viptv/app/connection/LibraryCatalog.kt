package org.viptv.app.connection

import io.ktor.http.*
import kotlinx.serialization.json.*
import org.viptv.engine.wire.LibraryQuery
import org.viptv.engine.wire.LibrarySnapshot

fun defaultLibraryQuery() = LibraryQuery(source = "favorites", offset = 0, limit = 100,
    sort = "last_watched", show = "all", type = "all")
private val libraryCodec = Json { ignoreUnknownKeys = true; encodeDefaults = true }

suspend fun RemoteConnection.libraryPage(profile: String, query: LibraryQuery): LibrarySnapshot {
    val parameters = libraryCodec.encodeToJsonElement(query).jsonObject.entries
        .filter { it.value !is JsonNull }.joinToString("&") {
            "${it.key.encodeURLParameter()}=${it.value.jsonPrimitive.content.encodeURLParameter()}"
        }
    return libraryCodec.decodeFromJsonElement(call("/api/profiles/${profile.encodeURLPathPart()}/library?$parameters"))
}
