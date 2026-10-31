package org.viptv.app.features.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.ChevronLeft
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Lucide
import org.viptv.app.components.ActionChip
import org.viptv.app.components.FilterBar
import org.viptv.app.components.SearchableSelect
import org.viptv.app.components.SelectOption
import org.viptv.app.theme.Palette

private const val YourShows = "your_shows"

private val CalendarTypes = listOf(SelectOption(CALENDAR_TYPE_ALL, "All"), SelectOption("series", "TV"),
    SelectOption("anime", "Anime"), SelectOption("movie", "Movies"))
private val CalendarSources = listOf(SelectOption(YourShows, CalendarFilter.YourShows.label))

@Composable
internal fun CalendarControls(type: String, week: Int, television: Boolean, scale: Float,
    category: (String) -> Unit, shift: (Int) -> Unit, modifier: Modifier = Modifier, range: String? = null,
    filter: CalendarFilter? = null, filterChanged: (CalendarFilter) -> Unit = {},
    gutter: Dp = 0.dp, bleed: Boolean = true, entry: FocusRequester? = null, phone: Boolean = false) {
    val changed = type != CALENDAR_DEFAULT_TYPE || (filter != null && filter != CALENDAR_DEFAULT_FILTER)
    val clear = if (changed) ({ category(CALENDAR_DEFAULT_TYPE); filterChanged(CALENDAR_DEFAULT_FILTER) }) else null
    val filters: LazyListScope.() -> Unit = { calendarFilters(type, filter, category, filterChanged, entry) }
    val weeks: LazyListScope.() -> Unit = { calendarWeeks(week, shift, television, scale, range) }
    if (!phone) { FilterBar(modifier, gutter, bleed, clear = clear) { filters(); weeks() }; return }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterBar(gutter = gutter, bleed = bleed, content = weeks)
        FilterBar(gutter = gutter, bleed = bleed, clear = clear, content = filters)
    }
}

private fun LazyListScope.calendarFilters(type: String, filter: CalendarFilter?, category: (String) -> Unit,
    filterChanged: (CalendarFilter) -> Unit, entry: FocusRequester?) {
    item(key = "type") {
        SearchableSelect("Type", CalendarTypes, listOf(type), { category(it.firstOrNull() ?: type) },
            anyLabel = null, highlighted = type != CALENDAR_DEFAULT_TYPE, chipFocus = entry)
    }
    if (filter != null) item(key = "source") {
        SearchableSelect("Shows", CalendarSources, if (filter == CalendarFilter.YourShows) listOf(YourShows) else emptyList(),
            { ids -> filterChanged(if (YourShows in ids) CalendarFilter.YourShows else CalendarFilter.All) },
            anyLabel = "All", highlighted = filter != CALENDAR_DEFAULT_FILTER)
    }
}

private fun LazyListScope.calendarWeeks(week: Int, shift: (Int) -> Unit, television: Boolean, scale: Float, range: String?) {
    if (week > -CALENDAR_WEEK_LIMIT) item(key = "previous-week") { ActionChip("Previous week", Lucide.ChevronLeft, { shift(-1) }) }
    item(key = "this-week") { ActionChip("This week", null, { shift(0) }) }
    if (week < CALENDAR_WEEK_LIMIT) item(key = "next-week") { ActionChip("Next week", Lucide.ChevronRight, { shift(1) }) }
    range?.let { label -> item(key = "range") {
        Text(label, color = Palette.secondary, fontSize = if (television) (18 * scale).sp else 12.sp, maxLines = 1)
    } }
}
