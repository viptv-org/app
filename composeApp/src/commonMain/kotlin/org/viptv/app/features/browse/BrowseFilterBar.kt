package org.viptv.app.features.browse

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.viptv.app.components.FilterBar
import org.viptv.app.components.FilterDivider
import org.viptv.app.components.QueryControlFilter
import org.viptv.app.components.SearchableSelect
import org.viptv.app.components.SelectOption
import org.viptv.app.connection.BrowseQuery
import org.viptv.app.connection.DiscoveryCatalog
import org.viptv.app.connection.DiscoveryControls
import org.viptv.app.connection.displayName
import org.viptv.app.connection.forCatalog
import org.viptv.app.connection.selectedCatalog
import org.viptv.app.connection.selectionKey
import org.viptv.app.connection.selections
import org.viptv.engine.wire.QueryControl

internal fun browseFacets(controls: DiscoveryControls?, query: BrowseQuery, showType: Boolean): List<QueryControl> {
    if (controls == null) return emptyList()
    val catalog = controls.selectedCatalog(query)
    val peers = controls.catalogs.any { it.type == query.type }
    val fields = if (catalog == null) controls.controls
    else controls.controls.filter { it.id in listOf("type", "catalog") } + catalog.controls.filter { it.id !in listOf("type", "catalog") }
    return fields.filter { (showType || it.id != "type") && (it.id != "search" || catalog != null) &&
        it.maximum_selections != 0L && !(peers && it.id == "catalog") }
}

internal fun BrowseQuery.facetValues(control: QueryControl): List<String> =
    if (control.id == "type") listOf(type) else selections(control.id).filter(String::isNotBlank)

internal fun appliedFacets(facets: List<QueryControl>, query: BrowseQuery): List<QueryControl> =
    facets.filter { it.id != "type" && query.facetValues(it).isNotEmpty() }

internal fun clearedFilters(query: BrowseQuery): BrowseQuery =
    query.copy(skip = 0, genre = null, search = null, extras = emptyMap(), values = emptyList())

@Composable
internal fun BrowseFilterBar(controls: DiscoveryControls?, query: BrowseQuery, showType: Boolean,
    changed: (String, String) -> Unit, multipleChanged: (String, List<String>) -> Unit, applyQuery: (BrowseQuery) -> Unit,
    catalogOpen: MutableState<Boolean>, modifier: Modifier = Modifier, gutter: Dp = 0.dp, extra: LazyListScope.() -> Unit = {}) {
    val facets = remember(controls, query, showType) { browseFacets(controls, query, showType) }
    val applied = remember(facets, query) { appliedFacets(facets, query) }
    val peers = remember(controls, query.type) { controls?.catalogs?.filter { it.type == query.type }.orEmpty() }
    val catalog = controls?.selectedCatalog(query)
    FilterBar(modifier, gutter, clear = if (applied.isNotEmpty()) ({ applyQuery(clearedFilters(query)) }) else null) {
        if (peers.isNotEmpty()) {
            item(key = "catalog") { CatalogSelect(peers, catalog, catalogOpen) { applyQuery(query.forCatalog(it)) } }
            if (facets.isNotEmpty()) item(key = "catalog:divider") { FilterDivider() }
        }
        facets.forEach { control ->
            item(key = "facet:${control.id}") {
                QueryControlFilter(control, query.facetValues(control), { changed(control.id, it) }, { multipleChanged(control.id, it) },
                    impliedAny = control.id != "type")
            }
        }
        extra()
    }
}

@Composable
private fun CatalogSelect(peers: List<DiscoveryCatalog>, catalog: DiscoveryCatalog?, open: MutableState<Boolean>, select: (DiscoveryCatalog) -> Unit) {
    val options = remember(peers) {
        peers.map { peer ->
            val name = peer.displayName(peers)
            SelectOption(peer.selectionKey(), name, peer.connection_name?.takeIf { it.isNotBlank() && !name.contains(it) })
        }
    }
    SearchableSelect("Catalog", options, listOfNotNull(catalog?.selectionKey()),
        { key -> peers.firstOrNull { it.selectionKey() == key.firstOrNull() }?.let(select) },
        anyLabel = null, placeholder = "Choose", searchPlaceholder = "Search catalogs", open = open)
}
