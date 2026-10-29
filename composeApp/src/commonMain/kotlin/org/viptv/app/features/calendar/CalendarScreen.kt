package org.viptv.app.features.calendar

import org.viptv.app.components.FocusLazyColumn as LazyColumn
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import org.viptv.app.components.TextButton
import org.viptv.app.components.LocalReducedMotion

internal const val CALENDAR_UNAVAILABLE = "The calendar needs series in My List or a Simkl connection."

internal fun calendarEmptyDay(filter: CalendarFilter?) =
    if (filter == CalendarFilter.YourShows) "None of your shows air this day." else "No releases for this day."

@Composable
fun CalendarScreen(days: List<CalendarDay>, entries: List<CalendarEntry>, television: Boolean,
    open: (Card) -> Unit, manage: () -> Unit, available: Boolean = true, busy: Boolean = false, error: String? = null,
    type: String = "series", week: Int = 0, controls: Boolean = false,
    categoryChanged: (String) -> Unit = {}, weekChanged: (Int) -> Unit = {}, watch: ((CalendarEntry) -> Unit)? = null,
    sources: Set<CalendarSource>? = null, openEntry: ((CalendarEntry) -> Unit)? = null) {
    var chosen by remember { mutableStateOf(CalendarFilter.All) }
    val offered = calendarFilterOffered(entries, sources)
    LaunchedEffect(offered) { if (!offered) chosen = CalendarFilter.All }
    val filter = chosen.takeIf { offered }
    val shown = entries.shown(chosen, offered)
    if (television) { TvCalendarScreen(days, shown, open, manage, available = available, busy = busy, error = error, type = type, week = week, controls = controls, categoryChanged = categoryChanged, weekChanged = weekChanged, watchRelease = watch,
        filter = filter, filterChanged = { chosen = it }, openEntry = openEntry); return }
    val choose = calendarOpener(open, openEntry)
    var selected by remember(days) { mutableStateOf(days.firstOrNull { it.label == "Today" }?.id ?: days.firstOrNull()?.id) }
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val reduceMotion = LocalReducedMotion.current
    var focused by remember(shown) { mutableStateOf(shown.firstOrNull()) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val phone = maxWidth < 640.dp && !television
        val scale = if (television) maxWidth / 1920.dp else if (phone) 1f else 0.65f
        val visible = if (phone) shown else shown.filter { it.day == selected }
        val gutter = if (phone) 16.dp else if (television) 96.dp * scale else 40.dp
        Column(Modifier.fillMaxSize().padding(horizontal = gutter,
            vertical = if (phone) 8.dp else if (television) 8.dp * scale else 16.dp * scale), verticalArrangement = Arrangement.spacedBy(if (phone) 24.dp else 44.dp * scale)) {
            if (!phone && !television) Text("Calendar", fontSize = 28.sp, letterSpacing = (-0.56).sp, fontWeight = FontWeight.Bold)
            if (controls) CalendarControls(type,week,false,scale,categoryChanged,weekChanged, range = days.takeIf{it.isNotEmpty()}?.let{"${it.first().fullDate} – ${it.last().fullDate}"},
                filter = filter, filterChanged = { chosen = it })
            if (days.isNotEmpty()) CalendarDates(days, shown, selected, phone, scale, gutter) { day ->
                selected = day; focused = shown.firstOrNull { it.day == day }
                if (phone) scope.launch {
                    val index = days.takeWhile { it.id != day }.sumOf { preceding ->
                        shown.count { it.day == preceding.id }.let { count -> if (count > 0) count + 1 else 2 }
                    }
                    if (reduceMotion) list.scrollToItem(index) else list.animateScrollToItem(index)
                }
            }
            error?.let { Text(it, color = Palette.danger) }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Palette.accent)
            if (entries.isEmpty() && !busy && error == null) {
                Text(if (available) "Nothing is scheduled in these dates." else CALENDAR_UNAVAILABLE, color = Palette.secondary)
                TextButton(manage) { Text("Manage integrations") }
            } else Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(44.dp * scale)) {
                LazyColumn(Modifier.weight(1f), state = list, contentPadding = PaddingValues(bottom = if (phone) 96.dp else 24.dp),
                    verticalArrangement = Arrangement.spacedBy(if (phone) 4.dp else 18.dp * scale)) {
                    if (phone) days.forEach { day ->
                        val releases = visible.filter { it.day == day.id }.sortedBy { it.aired }
                        run {
                            item(key = "day:${day.id}") {
                                Row(Modifier.padding(top = 12.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text(day.label, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                    Text(day.fullDate, fontSize = 12.sp, color = Palette.secondary)
                                }
                            }
                            if(releases.isEmpty())item(key="empty:${day.id}"){Text(calendarEmptyDay(filter),color=Palette.secondary,fontSize=13.sp)}
                            items(releases, key = { it.id }) { CalendarReleaseRow(it,true,scale,watch,{focused=it},{choose(it)}) }
                        }
                    } else items(visible, key = { it.id }) {
                        CalendarReleaseRow(it,false,scale,watch,{focused=it},{choose(it)})
                    }
                    if(!phone&&visible.isEmpty()&&!busy&&error==null)item{Text(calendarEmptyDay(filter),color=Palette.secondary)}
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
private fun CalendarReleaseRow(entry: CalendarEntry, phone: Boolean, scale: Float, watch: ((CalendarEntry)->Unit)?, focus:()->Unit, open:()->Unit) {
    Column {
        CalendarRow(entry,phone,false,if(phone)1f else scale,focus,open)
        if(watch!=null)entry.actionLabel()?.let{label->TextButton({watch(entry)}){Text(label)}}
    }
}
