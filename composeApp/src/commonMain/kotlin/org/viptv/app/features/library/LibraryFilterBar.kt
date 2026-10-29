package org.viptv.app.features.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.viptv.app.components.FilterBar
import org.viptv.app.components.FilterDivider
import org.viptv.app.components.QueryControlFilter
import org.viptv.app.components.SearchableSelect
import org.viptv.app.components.SelectOption
import org.viptv.app.connection.defaultLibraryQuery
import org.viptv.engine.wire.Availability
import org.viptv.engine.wire.LibraryQuery
import org.viptv.engine.wire.LibrarySnapshot

internal fun LibraryQuery.reset(): LibraryQuery = defaultLibraryQuery().copy(source = source, limit = limit)

@Composable
internal fun LibraryFilterBar(snapshot: LibrarySnapshot, query: LibraryQuery, change: (LibraryQuery) -> Unit,
    modifier: Modifier = Modifier, gutter: Dp = 0.dp) {
    val values = query.values()
    val defaults = remember(query.source, query.limit) { query.reset().values() }
    val controls = remember(snapshot.controls) {
        snapshot.controls.filter { it.id != "source" }.sortedBy { if (it.id == "type") 0 else 1 }
    }
    val lists = remember(snapshot.sources) {
        snapshot.sources.map { SelectOption(it.id, it.label, enabled = it.availability == Availability.AVAILABLE) }
    }
    FilterBar(modifier, gutter, clear = if (values != defaults) ({ change(query.reset()) }) else null) {
        if (lists.size >= 2) {
            item(key = "list") {
                SearchableSelect("List", lists, listOf(query.source), { id -> id.firstOrNull()?.let { change(query.forList(it)) } },
                    anyLabel = null, searchPlaceholder = "Search lists")
            }
            item(key = "list:divider") { FilterDivider() }
        }
        controls.forEach { control ->
            item(key = "control:${control.id}") {
                val value = values[control.id].orEmpty()
                QueryControlFilter(control, listOf(value), { change(query.withValues(values + (control.id to it))) }, {},
                    impliedAny = false, highlighted = value != defaults[control.id].orEmpty())
            }
        }
    }
}
