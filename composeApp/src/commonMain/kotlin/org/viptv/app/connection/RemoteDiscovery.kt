package org.viptv.app.connection

import io.ktor.http.encodeURLParameter
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import org.viptv.engine.wire.*

@Serializable
data class FeatureAvailability(val availability: String, val message: String? = null)
@Serializable
data class DiscoveryCatalog(val id: String, val name: String, val type: String,
    val connection_id: String? = null, val addon_id: Long? = null, val controls: List<QueryControl> = emptyList(),
    val connection_name: String? = null)
@Serializable
data class DiscoveryControls(val provider_id: String, val media_types: List<String>,
    val controls: List<QueryControl>, val catalogs: List<DiscoveryCatalog> = emptyList(),
    val discovery: FeatureAvailability, val search: FeatureAvailability,
    val filters: FeatureAvailability, val calendar: FeatureAvailability)
@Serializable
data class BrowseQuery(val type: String, val catalog: String? = null, val connection_id: String? = null,
    val skip: Int = 0, val search: String? = null, val genre: String? = null, val extras: Map<String, String> = emptyMap(),
    val values: List<QueryValue> = emptyList())

internal suspend fun RemoteConnection.discoveryControls(type: String, genre: String? = null): DiscoveryControls =
    Json { ignoreUnknownKeys = true }.decodeFromJsonElement(call("/api/discovery-controls?type=${type.encodeURLParameter()}" + (genre?.let { "&genre=${it.encodeURLParameter()}" } ?: "")))

internal suspend fun RemoteConnection.browse(query: BrowseQuery, controls: DiscoveryControls?): CatalogPage {
    val params = linkedMapOf("type" to query.type, "skip" to query.skip.toString())
    query.catalog?.let { params["catalog"] = it }
    query.search?.let { params["search"] = it }
    query.genre?.let { params["genre"] = it }
    controls?.catalogs?.find { it.id == query.catalog && it.connection_id == query.connection_id }?.addon_id?.let { params["addon_id"] = it.toString() }
    if (query.extras.isNotEmpty()) params["extras"] = Json.encodeToString(query.extras)
    if (query.values.isNotEmpty()) params["values"] = Json.encodeToString(query.values)
    return Json { ignoreUnknownKeys = true }.decodeFromJsonElement(call("/api/discover?" + params.entries.joinToString("&") { "${it.key}=${it.value.encodeURLParameter()}" }))
}
