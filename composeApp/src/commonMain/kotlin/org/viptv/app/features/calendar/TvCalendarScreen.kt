package org.viptv.app.features.calendar

import org.viptv.app.components.FocusLazyColumn as LazyColumn
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.viptv.app.components.TvAction
import org.viptv.app.components.tvRingReach
import org.viptv.app.components.tvRingRoom
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.Card

internal data class TvCalendarAction(val label: String, val target: Card, val providerLabel: String? = null)

@Composable
internal fun TvCalendarScreen(days: List<CalendarDay>, entries: List<CalendarEntry>, open: (Card) -> Unit,
    manage: () -> Unit, actions: Map<String, TvCalendarAction> = emptyMap(), watch: (Card) -> Unit = open, available: Boolean = true, busy: Boolean = false, error: String? = null,
    type: String = "series", week: Int = 0, controls: Boolean = false,
    categoryChanged: (String) -> Unit = {}, weekChanged: (Int) -> Unit = {}, watchRelease: ((CalendarEntry) -> Unit)? = null,
    filter: CalendarFilter? = null, filterChanged: (CalendarFilter) -> Unit = {}, openEntry: ((CalendarEntry) -> Unit)? = null) {
    val choose = calendarOpener(open, openEntry)
    var selected by remember { mutableStateOf(days.firstOrNull { it.label == "Today" }?.id ?: days.firstOrNull()?.id) }
    val activeDay = selected?.takeIf { id -> days.any { it.id == id } }
        ?: days.firstOrNull { it.label == "Today" }?.id ?: days.firstOrNull()?.id
    val byDay = remember(entries) { entries.groupBy { it.day } }
    val visible = byDay[activeDay].orEmpty()
    val byId = remember(visible) { visible.associateBy { it.id } }
    var focusedId by remember { mutableStateOf<String?>(null) }
    val focused = byId[focusedId] ?: visible.firstOrNull()
    val date = remember { FocusRequester() }; val preview = remember { FocusRequester() }; val categories=remember{FocusRequester()}
    val rowFocus = remember(activeDay) { mutableMapOf<String, FocusRequester>() }
    val selectedRow = focused?.let { rowFocus.getOrPut(it.id) { FocusRequester() } }
    val rows = rememberLazyListState()
    LaunchedEffect(activeDay) {
        selected = activeDay
        focusedId = null
        rows.scrollToItem(0)
    }
    BoxWithConstraints(Modifier.fillMaxSize().focusProperties { onEnter = { if (days.isNotEmpty()) date.requestFocus() } }.focusGroup()) {
        val scale = maxWidth / 1920.dp
        if(controls)CalendarControls(type,week,true,scale,categoryChanged,weekChanged,
            Modifier.offset(96.dp*scale,8.dp*scale),categories,date,days.takeIf{it.isNotEmpty()}?.let{"${it.first().fullDate} – ${it.last().fullDate}"},
            filter,filterChanged)
        val railY=if(controls)90.dp*scale else 8.dp*scale
        val contentY=if(controls)200.dp*scale else 118.dp*scale
        Row(Modifier.offset(96.dp * scale, railY).focusProperties { onEnter = { date.requestFocus() } }.focusGroup(),
            horizontalArrangement = Arrangement.spacedBy(10.dp * scale)) {
            days.forEach { day ->
                TvAction(Modifier.height(64.dp * scale).focusProperties { if (selectedRow != null) down = selectedRow; if(controls)up=categories }, scale,
                    primary = activeDay == day.id, requester = if (activeDay == day.id) date else null,
                    activate = { selected = day.id; focusedId = null }, cornerRadius = 32.dp * scale,
                    background = if(activeDay==day.id)Palette.raised else androidx.compose.ui.graphics.Color.Transparent, contentPadding = PaddingValues(horizontal = 26.dp * scale)) {
                    Text(day.label, color = if (it == Palette.background) it else Palette.secondary, fontSize = (26 * scale).sp,
                        lineHeight = (32 * scale).sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                }
            }
        }
        LazyColumn(Modifier.offset(96.dp * scale, contentY).width(1100.dp * scale)
            .height((maxHeight - contentY - 48.dp * scale).coerceAtLeast(0.dp)).tvRingRoom(tvRingReach(scale), horizontal = false), state = rows,
            contentPadding = PaddingValues(vertical = tvRingReach(scale)), verticalArrangement = Arrangement.spacedBy(18.dp * scale)) {
            itemsIndexed(visible, key = { _, entry -> entry.id }) { index, entry ->
                TvCalendarRow(entry, scale, focused?.id == entry.id, rowFocus.getOrPut(entry.id) { FocusRequester() },
                    if (index == 0) date else null, preview, { focusedId = entry.id }, { choose(entry) })
            }
            if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth(), color = Palette.accent) }
            error?.let { message -> item { Text(message, color = Palette.danger) } }
            if (visible.isEmpty() && !busy && error == null) item {
                Column(verticalArrangement = Arrangement.spacedBy(24.dp * scale)) {
                    Text(if (!available) CALENDAR_UNAVAILABLE else calendarEmptyDay(filter),
                        color = Palette.secondary, fontSize = (24 * scale).sp, lineHeight = (32 * scale).sp)
                    if (!available) TvAction(Modifier.height(64.dp * scale), scale, activate = manage,
                        contentPadding = PaddingValues(horizontal = 24.dp * scale)) { Text("Manage integrations", color = it, fontSize = (24 * scale).sp) }
                }
            }
        }
        focused?.let { entry ->
            val releaseAction=if(watchRelease!=null)entry.actionLabel()?.let{TvCalendarAction(it,entry.card,if(entry.library)null else "Data from Simkl")}else null
            TvCalendarPreview(entry, actions[entry.id] ?: releaseAction, scale, preview, selectedRow, date, { choose(entry) },
                if(releaseAction!=null&&watchRelease!=null){{ _: Card -> watchRelease(entry) }}else watch,contentY)
        }
    }
}
