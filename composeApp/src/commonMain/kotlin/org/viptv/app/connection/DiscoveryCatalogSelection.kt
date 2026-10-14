package org.viptv.app.connection

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** A UI selection key; raw provider IDs and owners remain separate on the wire. */
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

internal fun DiscoveryCatalog.displayName(peers: List<DiscoveryCatalog>): String {
    if (peers.count { it.name == name } < 2) return name
    val connection = connection_name?.takeIf(String::isNotBlank)
    val label = connection?.let { "$name · $it" } ?: name
    val duplicates = peers.filter { it.name == name && it.connection_name?.takeIf(String::isNotBlank) == connection }
    return if (connection != null && duplicates.size == 1) label else "$label (${duplicates.indexOf(this) + 1})"
}
