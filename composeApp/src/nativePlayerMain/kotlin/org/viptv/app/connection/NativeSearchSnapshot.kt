package org.viptv.app.connection

import org.viptv.engine.wire.HomeSnapshot
import org.viptv.engine.wire.RowState

/** One carousel per add-on catalog: a Stremio search answers per catalog, so
 * merging a provider's catalogs into one row loses the Movies/Series split and
 * a single slow catalog marked the whole provider unavailable. */
internal fun nativeSearchSnapshot(snapshot: HomeSnapshot): SearchSnapshot = SearchSnapshot(
    rows = snapshot.rows.map { catalog ->
        SearchResultRow(id = "local:${catalog.id}", title = catalog.title, provider_id = catalog.provider_id,
            state = when (catalog.state) {
                RowState.READY -> "Ready"
                RowState.PENDING -> "Pending"
                RowState.FAILED -> "Failed"
                else -> "Empty"
            }, cards = catalog.cards,
            message = catalog.message ?: if (catalog.state == RowState.FAILED) "This search catalog could not respond." else null)
    }, complete = snapshot.complete,
)
