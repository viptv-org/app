package org.viptv.app.features.library

import org.viptv.engine.wire.LibraryQuery

internal fun LibraryQuery.values() = mapOf(
    "source" to source, "sort" to sort, "show" to show, "type" to type,
    "status" to status.orEmpty(), "genre" to genre.orEmpty(), "rated" to rated.orEmpty(),
    "added" to added.orEmpty(), "episodes_left_max" to (episodes_left_max?.toString() ?: ""))
internal fun LibraryQuery.withValues(values: Map<String, String>) = copy(offset = 0,
    source = values["source"] ?: source, sort = values["sort"] ?: sort,
    show = values["show"] ?: show, type = values["type"] ?: type,
    status = values["status"]?.takeIf(String::isNotBlank), genre = values["genre"]?.takeIf(String::isNotBlank),
    rated = values["rated"]?.takeIf(String::isNotBlank), added = values["added"]?.takeIf(String::isNotBlank),
    episodes_left_max = values["episodes_left_max"]?.toLongOrNull())
