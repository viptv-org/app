package org.viptv.app.connection

import io.ktor.http.encodeURLParameter
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.contentOrNull
import org.viptv.engine.wire.Card

@Serializable
data class SearchResultRow(
    val id: String,
    val title: String,
    val provider_id: String,
    val addon_id: Long? = null,
    val state: String = "Ready",
    val cards: List<Card> = emptyList(),
    val message: String? = null,
)

@Serializable
data class SearchSnapshot(val rows: List<SearchResultRow> = emptyList(), val complete: Boolean = true)

@Serializable
internal data class SearchStreamPlan(val rows: List<SearchResultRow> = emptyList())

internal sealed interface SearchStreamEvent {
    data class Plan(val rows: List<SearchResultRow>) : SearchStreamEvent
    data class Row(val row: SearchResultRow) : SearchStreamEvent
}

private val searchCodec = Json { ignoreUnknownKeys = true }

internal suspend fun RemoteConnection.searchResults(term: String): SearchSnapshot? = try {
    searchCodec.decodeFromJsonElement<SearchSnapshot>(
        call("/api/search?query=${term.trim().encodeURLParameter()}"))
} catch (failure: RemoteFailure) {
    // Older QA servers predate grouped search: a 404 means no results, not a failure.
    if (failure.status == 404) null else throw failure
}

internal suspend fun RemoteConnection.searchStreamResults(term: String, publish: suspend (SearchStreamEvent) -> Unit): Boolean {
    var failed: RemoteFailure? = null
    try {
        searchEventStream("/api/search/stream?query=${term.trim().encodeURLParameter()}") { name, data ->
            when (name) {
                "plan" -> publish(SearchStreamEvent.Plan(decodePlan(data)))
                "row" -> decodeRow(data)?.let { publish(SearchStreamEvent.Row(it)) }
                "error" -> failed = decodeStreamError(data)
            }
        }
    } catch (_: SearchStreamUnavailable) {
        return false
    }
    failed?.let { throw it }
    return true
}

private fun decodePlan(data: String?): List<SearchResultRow> = data?.let {
    runCatching { searchCodec.decodeFromJsonElement<SearchStreamPlan>(searchCodec.parseToJsonElement(it)).rows }
        .getOrElse { emptyList() }
} ?: emptyList()

private fun decodeRow(data: String?): SearchResultRow? = data?.let {
    runCatching { searchCodec.decodeFromJsonElement<SearchResultRow>(searchCodec.parseToJsonElement(it)) }.getOrNull()
}

private fun decodeStreamError(data: String?): RemoteFailure {
    val value = data?.let { runCatching { searchCodec.parseToJsonElement(it) }.getOrNull() }?.jsonObject ?: JsonObject(emptyMap())
    val status = value["status"]?.jsonPrimitive?.intOrNull ?: 502
    val message = value["error"]?.jsonPrimitive?.contentOrNull ?: "The search could not finish. Try again."
    return RemoteFailure(status, message)
}