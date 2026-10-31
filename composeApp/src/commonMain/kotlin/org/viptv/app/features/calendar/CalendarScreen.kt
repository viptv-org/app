package org.viptv.app.features.calendar

import org.viptv.app.components.FocusLazyColumn as LazyColumn
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.launch
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.Card
import org.viptv.app.components.Button
import org.viptv.app.components.PageRestore
import org.viptv.app.components.TextButton
import org.viptv.app.components.pageAnchor
import org.viptv.app.components.rememberPageRestore
import org.viptv.app.components.LocalReducedMotion

internal const val CALENDAR_UNAVAILABLE = "The calendar needs series in My List or a Simkl connection."

internal fun calendarEmptyDay(filter: CalendarFilter?) =
    if (filter == CalendarFilter.YourShows) "None of your shows air this day." else "No releases for this day."

@Composable
fun CalendarScreen(days: List<CalendarDay>, entries: List<CalendarEntry>, television: Boolean,
    open: (Card) -> Unit, manage: () -> Unit, available: Boolean = true, busy: Boolean = false, error: String? = null,
    type: String = CALENDAR_DEFAULT_TYPE, week: Int = 0, controls: Boolean = false,
    categoryChanged: (String) -> Unit = {}, weekChanged: (Int) -> Unit = {}, watch: ((CalendarEntry) -> Unit)? = null,
    sources: Set<CalendarSource>? = null, openEntry: ((CalendarEntry) -> Unit)? = null) {
    var chosen by remember { mutableStateOf(CALENDAR_DEFAULT_FILTER) }
    val offered = calendarFilterOffered(entries, sources)
    val filter = chosen.takeIf { offered }
    val fallback = entries.fallsBackToAll(filter)
    val shown = if (fallback) entries else entries.shown(chosen, offered)
    val notice = if (fallback) CALENDAR_FALLBACK else null
    val emptyFilter = filter.takeUnless { fallback }
    val restore = rememberPageRestore("calendar")
    if (television) { TvCalendarScreen(days, shown, open, manage, available = available, busy = busy, error = error, type = type, week = week, controls = controls, categoryChanged = categoryChanged, weekChanged = weekChanged, watchRelease = watch,
        filter = filter, filterChanged = { chosen = it }, openEntry = openEntry, notice = notice, restore = restore); return }
    val choose = calendarOpener(open, openEntry)
    var selected by rememberSaveable(days) { mutableStateOf(days.firstOrNull { it.label == "Today" }?.id ?: days.firstOrNull()?.id) }
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val reduceMotion = LocalReducedMotion.current
    var focused by remember(shown) { mutableStateOf(shown.firstOrNull()) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val phone = maxWidth < 640.dp && !television
        val scale = if (television) maxWidth / 1920.dp else if (phone) 1f else 0.65f
        val visible = if (phone) shown else shown.filter { it.day == selected }
        val lines = remember(days, visible) { visible.dayLines(days) }
        val markedLine = remember(lines, restore.lastKey) { lines.indexOfFirst { it.key() == restore.lastKey } }
        val markedEntry = remember(visible, restore.lastKey) { visible.indexOfFirst { it.id == restore.lastKey } }
        val anchored = restore.anchor(visible.size) { visible[it].id }
        val gutter = if (phone) 16.dp else if (television) 96.dp * scale else 40.dp
        Column(Modifier.fillMaxSize().padding(horizontal = gutter,
            vertical = if (phone) 8.dp else if (television) 8.dp * scale else 16.dp * scale), verticalArrangement = Arrangement.spacedBy(if (phone) 24.dp else 44.dp * scale)) {
            if (!phone && !television) Text("Calendar", fontSize = 28.sp, letterSpacing = (-0.56).sp, fontWeight = FontWeight.Bold)
            if (controls) CalendarControls(type,week,false,scale,categoryChanged,weekChanged, range = days.takeIf{it.isNotEmpty()}?.let{"${it.first().fullDate} – ${it.last().fullDate}"},
                filter = filter, filterChanged = { chosen = it }, gutter = gutter, phone = phone)
            if (days.isNotEmpty()) CalendarDates(days, shown, selected, phone, scale, gutter) { day ->
                selected = day; focused = shown.firstOrNull { it.day == day }
                if (phone) scope.launch {
                    val index = lines.indexOfFirst { it is CalendarLine.Day && it.day.id == day }
                    if (index >= 0) { if (reduceMotion) list.scrollToItem(index) else list.animateScrollToItem(index) }
                }
            }
            error?.let { Text(it, color = Palette.danger) }
            notice?.let { Text(it, color = Palette.secondary, fontSize = if (phone) 13.sp else 14.sp) }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Palette.accent)
            if (entries.isEmpty() && !busy && error == null) {
                Text(if (available) "Nothing is scheduled in these dates." else CALENDAR_UNAVAILABLE, color = Palette.secondary)
                TextButton(manage) { Text("Manage integrations") }
            } else Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(44.dp * scale)) {
                PageRestore(list, restore, target = { (if (phone) markedLine else markedEntry).takeIf { it >= 0 } })
                LazyColumn(Modifier.weight(1f), state = list, contentPadding = PaddingValues(bottom = if (phone) 96.dp else 24.dp),
                    verticalArrangement = Arrangement.spacedBy(if (phone) 4.dp else 18.dp * scale)) {
                    if (phone) itemsIndexed(lines, key = { _, line -> line.key() }) { index, line ->
                        when (line) {
                            is CalendarLine.Day -> Row(Modifier.padding(top = 12.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(line.day.label, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                Text(line.day.fullDate, fontSize = 12.sp, color = Palette.secondary)
                            }
                            is CalendarLine.Empty -> Text(calendarEmptyDay(emptyFilter), color = Palette.secondary, fontSize = 13.sp)
                            is CalendarLine.Entry -> CalendarReleaseRow(line.entry, true, scale, watch, { focused = line.entry },
                                { restore.mark(line.entry.id, index); choose(line.entry) })
                        }
                    } else itemsIndexed(visible, key = { _, entry -> entry.id }) { index, entry ->
                        CalendarReleaseRow(entry, false, scale, watch, { focused = entry }, { restore.mark(entry.id, index); choose(entry) },
                            Modifier.pageAnchor(restore, entry.id, index, target = anchored == index))
                    }
                    if(!phone&&visible.isEmpty()&&!busy&&error==null)item{Text(calendarEmptyDay(emptyFilter),color=Palette.secondary)}
                }
                if (television && focused != null) Column(Modifier.width(584.dp * scale), verticalArrangement = Arrangement.spacedBy(24.dp * scale)) {
                    val entry = requireNotNull(focused)
                    AsyncImage(entry.card.landscape ?: entry.card.poster, entry.card.title, Modifier.fillMaxWidth().aspectRatio(16f / 9).clip(RoundedCornerShape(16.dp * scale)), contentScale = ContentScale.Crop)
                    Text(entry.card.title, fontSize = (36 * scale).sp, fontWeight = FontWeight.Bold)
                    entry.description?.let { Text(it, fontSize = (24 * scale).sp, color = Palette.body) }
                    Button(onClick = { choose(entry) }, shape = RoundedCornerShape(12.dp * scale), modifier = Modifier.height(72.dp * scale),
                        colors = ButtonDefaults.buttonColors(containerColor = Palette.raised, contentColor = Palette.text)) { Text("View details", fontSize = (26 * scale).sp) }
                }
            }
        }
    }
}

@Composable
private fun CalendarReleaseRow(entry: CalendarEntry, phone: Boolean, scale: Float, watch: ((CalendarEntry)->Unit)?, focus:()->Unit, open:()->Unit, modifier: Modifier = Modifier) {
    Column {
        CalendarRow(entry,phone,false,if(phone)1f else scale,focus,open,modifier)
        if(watch!=null)entry.actionLabel()?.let{label->TextButton({watch(entry)}){Text(label)}}
    }
}
