package org.viptv.app.features.browse

import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import org.viptv.app.connection.*
import org.viptv.engine.wire.*

/** Provider and per-catalog capabilities determine the controls; search never selects a provider.
 *
 * `catalogPill` is the phone browse header (M3Browse), which owns the catalog choice in its own
 * pill: the row then drops the duplicate `catalog` facet and keeps every other facet inline.
 */
@Composable
internal fun DiscoveryFilters(controls: DiscoveryControls, query: BrowseQuery, showType: Boolean,
    changed: (String, String) -> Unit, first: FocusRequester? = null, television: Boolean = false,
    scale: Float = 1f, multipleChanged: (String, List<String>) -> Unit = { _, _ -> },
    catalogPill: Boolean = false) {
    val catalog = controls.selectedCatalog(query)
    val fields = if (catalog == null) controls.controls else
        controls.controls.filter { it.id in listOf("type", "catalog") } + catalog.controls.filter { it.id !in listOf("type", "catalog") }
    val visible = fields.filter { (showType || it.id != "type") && (it.id != "search" || catalog != null) &&
        it.maximum_selections != 0L && !(catalogPill && it.id == "catalog") }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp * scale)) {
        if (visible.isEmpty()) Text("No browsing filters", Modifier.then(if (first != null) Modifier.focusRequester(first) else Modifier).focusable())
        visible.forEachIndexed { index, supplied ->
            val peers = controls.catalogs.filter { it.type == query.type }
            val control = if (supplied.id == "catalog" && peers.isNotEmpty()) supplied.copy(
                choices = peers.map { Choice(it.selectionKey(), it.displayName(peers)) }) else supplied
            val value = when (control.id) {
                "type" -> query.type; "catalog" -> catalog?.selectionKey() ?: query.catalog
                "genre" -> query.genre; "search" -> query.search; else -> query.extras[control.id]
            }
            val modifier = if (index == 0 && first != null) Modifier.focusRequester(first) else Modifier
            when (control.kind) {
                ControlKind.SINGLECHOICE -> DiscoveryChoiceFilter(control, value, modifier, television, scale) { changed(control.id, it) }
                ControlKind.TEXT, ControlKind.NUMBER, ControlKind.DATE -> DiscoveryTextFilter(control, value, modifier) { changed(control.id, it) }
                ControlKind.TOGGLE -> FilterChip(value == "true", { changed(control.id, (value != "true").toString()) }, label = { Text(control.label) }, modifier = modifier)
                ControlKind.MULTICHOICE -> DiscoveryMultiChoiceFilter(control, query.selections(control.id), modifier,
                    television, scale) { multipleChanged(control.id, it) }
            }
        }
    }
}
