package org.viptv.app.state

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.Job
import org.viptv.app.connection.SourceGroups
import org.viptv.app.connection.SourceProducerOutcome
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.SourceCandidate

internal const val SOURCES_EMPTY_LABEL = "No sources found"
internal const val SOURCES_UNAVAILABLE_LABEL = "Sources unavailable"

@Immutable
data class DetailSourcePreview(
    val rows: List<SourceCandidate> = emptyList(),
    val groups: SourceGroups = SourceGroups(),
    val producers: List<SourceProducerOutcome> = emptyList(),
    val torrentOnly: Int = 0,
    val settled: Boolean = false,
    val failed: Boolean = false,
) {
    val best: SourceCandidate? get() = groups.bestMatchId?.let { id -> rows.firstOrNull { it.id == id } } ?: rows.firstOrNull()
    val sourceCountLabel: String get() = "${rows.size} ${if (rows.size == 1) "source" else "sources"}"
}

internal class DetailSourcePreviewSession(val key: String) {
    var job: Job? = null
    var adopted = false
}

internal fun detailSourcePreviewKey(card: Card, profile: String): String = listOf(profile, card.provider_id,
    card.identity.kind, card.identity.id, card.identity.episode?.order.orEmpty(), card.identity.edition.orEmpty())
    .joinToString("|")