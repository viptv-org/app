package org.viptv.app.features.live

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.viptv.app.components.FilterBar
import org.viptv.app.components.SearchableSelect
import org.viptv.app.components.SelectOption
import org.viptv.app.components.ToggleSelect

internal const val AllGroups = "All"

// The controller keeps the group and the favourites filter mutually exclusive, so the toggle's off state is the group reset.
@Composable
internal fun LiveFilters(groups: Map<String, String>, group: String?, chooseGroup: (String?) -> Unit,
    favoritesOnly: Boolean, favorites: (() -> Unit)?, reset: () -> Unit,
    sources: Map<String, String> = emptyMap(), selectedSource: String? = null, sourceChanged: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier, gutter: Dp = 0.dp, bleed: Boolean = true, entry: FocusRequester? = null) {
    val groupOptions = remember(groups) { groups.map { (id, label) -> SelectOption(id, label) } }
    val sourceOptions = remember(sources) { sources.map { (id, name) -> SelectOption(id, name) } }
    val hasSource = sourceChanged != null && sourceOptions.size > 1
    val chooseSource: (String) -> Unit = { id -> sourceChanged?.invoke(id) }
    FilterBar(modifier, gutter, bleed, clear = if (group != null || favoritesOnly) ({ reset() }) else null) {
        if (groupOptions.isNotEmpty()) item(key = "group") {
            SearchableSelect("Group", groupOptions, listOfNotNull(group), { chooseGroup(it.firstOrNull()) },
                anyLabel = AllGroups, anyTrailing = groups.size.toString(), searchPlaceholder = "Search groups", chipFocus = entry)
        }
        if (hasSource) item(key = "source") {
            SearchableSelect("Source", sourceOptions, listOfNotNull(selectedSource),
                { ids -> ids.firstOrNull()?.let(chooseSource) }, anyLabel = null,
                placeholder = "Choose", searchPlaceholder = "Search sources",
                chipFocus = entry.takeIf { groupOptions.isEmpty() })
        }
        if (favorites != null) item(key = "favorites") {
            ToggleSelect("Favourites", favoritesOnly, { on -> if (on) favorites?.invoke() else reset() },
                chipFocus = entry.takeIf { groupOptions.isEmpty() && !hasSource })
        }
    }
}
