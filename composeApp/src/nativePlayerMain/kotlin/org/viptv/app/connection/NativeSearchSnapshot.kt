package org.viptv.app.connection

import org.viptv.engine.wire.HomeSnapshot
import org.viptv.engine.wire.RowState

/** Keep one carousel per add-on without dropping failed or empty catalogs. */
internal fun nativeSearchSnapshot(snapshot: HomeSnapshot): SearchSnapshot = SearchSnapshot(
    rows = snapshot.rows.groupBy { it.provider_id }.map { (provider, catalogs) ->
        val cards = catalogs.flatMap { it.cards }
        val failed = catalogs.any { it.state == RowState.FAILED }
        val pending = catalogs.any { it.state == RowState.PENDING }
        SearchResultRow(id = "local:$provider", title = catalogs.first().title, provider_id = provider,
            state = when {
                cards.isNotEmpty() -> "Ready"
                pending -> "Pending"
                failed -> "Failed"
                else -> "Empty"
            }, cards = cards,
            message = if (failed) "Some search catalogs could not respond." else null)
    }, complete = snapshot.complete,
)
