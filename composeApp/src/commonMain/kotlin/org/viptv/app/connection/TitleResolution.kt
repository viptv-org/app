package org.viptv.app.connection

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.viptv.engine.wire.DetailSnapshot

data class DetailFocus(val season: Long? = null, val episode: Long? = null)

data class DetailRead(val detail: DetailSnapshot, val focus: DetailFocus? = null)

data class TitleSearchRequest(val query: String, val type: String? = null, val season: Long? = null)

internal val RemoteFailure.titleNeedsSearch: TitleSearchRequest?
    get() {
        if (status != 404 || code != "title_needs_search") return null
        val value = body?.let(::jsonObjectOrNull) ?: return null
        val query = value.text("query")?.takeIf { it.isNotBlank() } ?: return null
        return TitleSearchRequest(query, value.text("type"), value.number("season"))
    }

internal fun detailFocus(value: JsonObject): DetailFocus? =
    value.number("focus_season")?.let { DetailFocus(it, value.number("focus_episode")) }

private fun jsonObjectOrNull(body: String): JsonObject? =
    runCatching { Json.parseToJsonElement(body).jsonObject }.getOrNull()

private fun JsonObject.text(name: String): String? =
    runCatching { get(name)?.jsonPrimitive?.contentOrNull }.getOrNull()

private fun JsonObject.number(name: String): Long? =
    runCatching { get(name)?.jsonPrimitive?.longOrNull }.getOrNull()
