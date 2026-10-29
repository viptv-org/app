package org.viptv.app.connection

import org.viptv.engine.wire.QueryValue

internal fun BrowseQuery.forCatalog(next: DiscoveryCatalog?): BrowseQuery =
    if (catalog == next?.id && connection_id == next?.connection_id) this
    else BrowseQuery(type, catalog = next?.id, connection_id = next?.connection_id)

internal fun BrowseQuery.withSelections(id: String, selected: List<String>): BrowseQuery {
    val active = selected.distinct()
    return copy(skip = 0, genre = genre.takeUnless { id == "genre" },
        search = search.takeUnless { id == "search" }, extras = extras - id,
        values = values.filterNot { it.id == id } +
            if (active.isEmpty()) emptyList() else listOf(QueryValue(id, active)))
}

internal fun BrowseQuery.selections(id: String): List<String> = values.find { it.id == id }?.values
    ?: (when (id) { "genre" -> genre; "search" -> search; else -> extras[id] })?.let(::listOf).orEmpty()
