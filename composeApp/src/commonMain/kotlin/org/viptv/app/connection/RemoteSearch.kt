package org.viptv.app.connection

import io.ktor.http.encodeURLParameter
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
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

internal suspend fun RemoteConnection.searchResults(term: String): SearchSnapshot? = try {
    Json { ignoreUnknownKeys = true }.decodeFromJsonElement<SearchSnapshot>(
        call("/api/search?query=${term.trim().encodeURLParameter()}"))
} catch (failure: RemoteFailure) {
    // Older QA servers predate grouped search: a 404 means no results, not a failure.
    if (failure.status == 404) null else throw failure
}
