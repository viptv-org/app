package org.viptv.app.connection

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal fun DiscoveryCatalog.selectionKey(): String =
    Json.encodeToString(listOf(connection_id ?: addon_id?.toString().orEmpty(), type, id))

internal fun DiscoveryControls.selectedCatalog(query: BrowseQuery): DiscoveryCatalog? = catalogs.find {
    it.id == query.catalog && it.connection_id == query.connection_id && it.type == query.type
}

internal fun DiscoveryControls.catalogSelection(key: String): DiscoveryCatalog? =
    catalogs.find { it.selectionKey() == key } ?: catalogs.filter { it.id == key }.singleOrNull()

internal fun DiscoveryControls.missingRequired(query: BrowseQuery): List<String> = selectedCatalog(query)
    ?.controls.orEmpty().filter { it.required && query.selections(it.id).none(String::isNotBlank) }.map { it.label }

internal fun BrowseQuery.withDefaultCatalog(controls: DiscoveryControls): BrowseQuery {
    if (catalog != null) return this
    val candidates = controls.catalogs.filter { it.type == type }
    val first = candidates.firstOrNull { candidate -> candidate.controls.none { it.required } }
        ?: candidates.firstOrNull() ?: return this
    return copy(catalog = first.id, connection_id = first.connection_id)
}

// Mirrors the host's Home add-on row titles (server/src/engine_v3/home_titles.rs): say the type, name the add-on only on a clash.
internal fun DiscoveryCatalog.catalogTitle(peers: List<DiscoveryCatalog>): String {
    title?.let { return it }
    val typed = typedTitle()
    val shared = peers.count { it.typedTitle().lowercase() == typed.lowercase() } > 1
    return if (shared) "$typed · ${addonName()}" else typed
}

internal fun DiscoveryCatalog.catalogSource(peers: List<DiscoveryCatalog>): String {
    val title = catalogTitle(peers)
    val addon = connection_name.orEmpty().filterNot(Char::isISOControl).take(128).trim()
    return if (title != typedTitle() || addon.isEmpty()) title else "$title from $addon"
}

private fun DiscoveryCatalog.typedTitle(): String {
    val (label, typeWords) = typeCopy(type)
    val nameWords = wordsOf(name)
    val saysType = nameWords.any { it in typeWords } || wordsOf(label).all { it in nameWords }
    return if (saysType) name else "$name $label"
}

private fun typeCopy(kind: String): Pair<String, List<String>> = when (kind.lowercase()) {
    "movie" -> "Movies" to listOf("movie", "movies", "film", "films")
    "series" -> "Series" to listOf("series", "show", "shows")
    "anime" -> "Anime" to listOf("anime")
    "channel" -> "Channels" to listOf("channel", "channels")
    "tv" -> "TV" to listOf("tv")
    else -> titleCase(kind) to emptyList()
}

private fun DiscoveryCatalog.addonName(): String = connection_name.orEmpty()
    .filterNot(Char::isISOControl).take(128).trim().ifEmpty { "Stremio addon" }

private fun words(text: String): List<String> = buildList {
    var start = -1
    for (index in text.indices) {
        if (text[index].isLetterOrDigit()) {
            if (start < 0) start = index
        } else if (start >= 0) {
            add(text.substring(start, index))
            start = -1
        }
    }
    if (start >= 0) add(text.substring(start))
}

private fun wordsOf(text: String): List<String> = words(text).map { it.lowercase() }

private fun titleCase(kind: String): String = words(kind)
    .joinToString(" ") { it.take(1).uppercase() + it.drop(1) }
