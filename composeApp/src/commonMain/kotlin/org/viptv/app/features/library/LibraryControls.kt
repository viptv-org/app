package org.viptv.app.features.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import org.viptv.app.components.FilterBar
import org.viptv.app.components.SearchableSelect
import org.viptv.app.components.SelectOption

private val LocalTypes = listOf("All", "Movies", "Series").map { SelectOption(it, it) }
private val LocalSorts = listOf("Date added", "A–Z", "Z–A").map { SelectOption(it, it) }

@Composable
internal fun LibraryControls(filter: String, setFilter: (String) -> Unit, sort: String, setSort: (String) -> Unit,
    watchedAvailable: Boolean = false) {
    val sorts = remember(watchedAvailable) { LocalSorts + if (watchedAvailable) listOf(SelectOption("Last watched", "Last watched")) else emptyList() }
    FilterBar(clear = if (filter != "All" || sort != "Date added") ({ setFilter("All"); setSort("Date added") }) else null) {
        item(key = "type") {
            SearchableSelect("Type", LocalTypes, listOf(filter), { it.firstOrNull()?.let(setFilter) }, anyLabel = null,
                highlighted = filter != "All")
        }
        item(key = "sort") {
            SearchableSelect("Sort", sorts, listOf(sort), { it.firstOrNull()?.let(setSort) }, anyLabel = null,
                highlighted = sort != "Date added")
        }
    }
}
