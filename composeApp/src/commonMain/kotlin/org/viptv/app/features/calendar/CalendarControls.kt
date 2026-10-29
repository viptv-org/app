package org.viptv.app.features.calendar

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

private val CalendarTypes = listOf(SelectOption("series", "TV"), SelectOption("anime", "Anime"), SelectOption("movie", "Movies"))
private val CalendarSources = listOf(SelectOption(YourShows, CalendarFilter.YourShows.label))

@Composable
internal fun CalendarControls(type: String, week: Int, television: Boolean, scale: Float,
    category: (String) -> Unit, shift: (Int) -> Unit, modifier: Modifier = Modifier, range: String? = null,
    filter: CalendarFilter? = null, filterChanged: (CalendarFilter) -> Unit = {},
    gutter: Dp = 0.dp, bleed: Boolean = true, entry: FocusRequester? = null) {
    val changed = type != CALENDAR_DEFAULT_TYPE || filter == CalendarFilter.YourShows
    FilterBar(modifier, gutter, bleed, clear = if (changed) ({ category(CALENDAR_DEFAULT_TYPE); filterChanged(CalendarFilter.All) }) else null) {
        item(key = "type") {
            SearchableSelect("Type", CalendarTypes, listOf(type), { category(it.firstOrNull() ?: type) },
                anyLabel = null, highlighted = type != CALENDAR_DEFAULT_TYPE, chipFocus = entry)
        }
        if (filter != null) item(key = "source") {
            SearchableSelect("Shows", CalendarSources,
                if (filter == CalendarFilter.YourShows) listOf(YourShows) else emptyList(),
                { ids -> filterChanged(if (YourShows in ids) CalendarFilter.YourShows else CalendarFilter.All) },
                anyLabel = "All", highlighted = filter == CalendarFilter.YourShows)
        }
        if (week > -CALENDAR_WEEK_LIMIT) item(key = "previous-week") { ActionChip("Previous week", Lucide.ChevronLeft, { shift(-1) }) }
        item(key = "this-week") { ActionChip("This week", null, { shift(0) }) }
        if (week < CALENDAR_WEEK_LIMIT) item(key = "next-week") { ActionChip("Next week", Lucide.ChevronRight, { shift(1) }) }
        range?.let { label -> item(key = "range") {
            Text(label, color = Palette.secondary, fontSize = if (television) (18 * scale).sp else 12.sp, maxLines = 1)
        } }
    }
}
