package org.viptv.app.features.calendar

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.viptv.app.components.TvAction
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.Card

internal data class TvCalendarAction(val label: String, val target: Card, val providerLabel: String? = null)

@Composable
internal fun TvCalendarScreen(days: List<CalendarDay>, entries: List<CalendarEntry>, open: (Card) -> Unit,
    manage: () -> Unit, actions: Map<String, TvCalendarAction> = emptyMap(), watch: (Card) -> Unit = open) {
    var selected by remember { mutableStateOf(days.firstOrNull { it.label == "Today" }?.id ?: days.firstOrNull()?.id) }
    val activeDay = selected?.takeIf { id -> days.any { it.id == id } }
        ?: days.firstOrNull { it.label == "Today" }?.id ?: days.firstOrNull()?.id
    val visible = entries.filter { it.day == activeDay }
    var focusedId by remember { mutableStateOf<String?>(null) }
    val focused = visible.find { it.id == focusedId } ?: visible.firstOrNull()
    val date = remember { FocusRequester() }; val first = remember { FocusRequester() }
    BoxWithConstraints(Modifier.fillMaxSize().focusProperties { onEnter = { if (days.isNotEmpty()) date.requestFocus() } }.focusGroup()) {
        val scale = maxWidth / 1920.dp
        Row(Modifier.offset(96.dp * scale, 8.dp * scale).focusProperties { onEnter = { date.requestFocus() } }.focusGroup(),
            horizontalArrangement = Arrangement.spacedBy(10.dp * scale)) {
            days.forEach { day ->
                TvAction(Modifier.height(64.dp * scale).focusProperties { if (visible.isNotEmpty()) down = first }, scale,
                    primary = activeDay == day.id, requester = if (activeDay == day.id) date else null,
                    activate = { selected = day.id; focusedId = null }, cornerRadius = 32.dp * scale,
                    background = androidx.compose.ui.graphics.Color.Transparent, contentPadding = PaddingValues(horizontal = 26.dp * scale)) {
                    Text(day.label, color = if (it == Palette.background) it else Palette.secondary, fontSize = (26 * scale).sp,
                        lineHeight = (32 * scale).sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                }
            }
        }
        LazyColumn(Modifier.offset(96.dp * scale, 118.dp * scale).width(1100.dp * scale)
            .height((maxHeight - 166.dp * scale).coerceAtLeast(0.dp)), verticalArrangement = Arrangement.spacedBy(18.dp * scale)) {
            itemsIndexed(visible, key = { _, entry -> entry.id }) { index, entry ->
                TvCalendarRow(entry, scale, focused?.id == entry.id, if (index == 0) first else null,
                    if (index == 0) date else null, { focusedId = entry.id }, { open(entry.card) })
            }
            if (visible.isEmpty()) item {
                Column(verticalArrangement = Arrangement.spacedBy(24.dp * scale)) {
                    Text(if (days.isEmpty()) "Release schedules are not available for this profile." else "No releases for this day.",
                        color = Palette.secondary, fontSize = (24 * scale).sp, lineHeight = (32 * scale).sp)
                    if (days.isEmpty()) TvAction(Modifier.height(64.dp * scale), scale, activate = manage,
                        contentPadding = PaddingValues(horizontal = 24.dp * scale)) { Text("Manage integrations", color = it, fontSize = (24 * scale).sp) }
                }
            }
        }
        focused?.let { TvCalendarPreview(it, actions[it.id], scale, open, watch) }
    }
}
