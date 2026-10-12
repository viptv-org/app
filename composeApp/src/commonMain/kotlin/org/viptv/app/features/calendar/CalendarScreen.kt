package org.viptv.app.features.calendar

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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

@Composable
fun CalendarScreen(days: List<CalendarDay>, entries: List<CalendarEntry>, television: Boolean,
    open: (Card) -> Unit, manage: () -> Unit) {
    var selected by remember(days) { mutableStateOf(days.firstOrNull { it.label == "Today" }?.id ?: days.firstOrNull()?.id) }
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var focused by remember(entries) { mutableStateOf(entries.firstOrNull()) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val phone = maxWidth < 640.dp && !television
        val scale = if (television) maxWidth / 1920.dp else if (phone) 1f else 0.65f
        val visible = if (phone) entries else entries.filter { it.day == selected }
        Column(Modifier.fillMaxSize().padding(horizontal = if (phone) 16.dp else if (television) 96.dp * scale else 40.dp,
            vertical = if (phone) 8.dp else if (television) 8.dp * scale else 16.dp * scale), verticalArrangement = Arrangement.spacedBy(if (phone) 24.dp else 44.dp * scale)) {
            if (!phone && !television) Text("Calendar", fontSize = 28.sp, letterSpacing = (-0.56).sp, fontWeight = FontWeight.Bold)
            if (days.isNotEmpty()) CalendarDates(if (phone) days else days.filter { day -> entries.any { it.day == day.id } }, entries, selected, phone, scale) { day ->
                selected = day; focused = entries.firstOrNull { it.day == day }
                if (phone && entries.any { it.day == day }) scope.launch {
                    val index = days.takeWhile { it.id != day }.sumOf { preceding ->
                        entries.count { it.day == preceding.id }.let { count -> if (count > 0) count + 1 else 0 }
                    }
                    list.animateScrollToItem(index)
                }
            }
            if (entries.isEmpty()) {
                Text("Release schedules are not available for this profile.", color = Palette.secondary)
                TextButton(manage) { Text("Manage integrations") }
            } else Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(44.dp * scale)) {
                LazyColumn(Modifier.weight(1f), state = list, contentPadding = PaddingValues(bottom = if (phone) 96.dp else 24.dp),
                    verticalArrangement = Arrangement.spacedBy(if (phone) 4.dp else 18.dp * scale)) {
                    if (phone) days.forEach { day ->
                        val releases = visible.filter { it.day == day.id }.sortedBy { it.aired }
                        if (releases.isNotEmpty()) {
                            item(key = "day:${day.id}") {
                                Row(Modifier.padding(top = 12.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text(day.label, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                    Text(day.fullDate, fontSize = 12.sp, color = Palette.secondary)
                                }
                            }
                            items(releases, key = { it.id }) { CalendarRow(it, true, false, 1f, { focused = it }, { open(it.card) }) }
                        }
                    } else items(visible, key = { it.id }) {
                        CalendarRow(it, false, television, scale, { focused = it }, { open(it.card) }, selected = focused?.id == it.id)
                    }
                }
                if (television && focused != null) Column(Modifier.width(584.dp * scale), verticalArrangement = Arrangement.spacedBy(24.dp * scale)) {
                    val entry = requireNotNull(focused)
                    AsyncImage(entry.card.landscape ?: entry.card.poster, entry.card.title, Modifier.fillMaxWidth().aspectRatio(16f / 9).clip(RoundedCornerShape(16.dp * scale)), contentScale = ContentScale.Crop)
                    Text(entry.card.title, fontSize = (36 * scale).sp, fontWeight = FontWeight.Bold)
                    entry.description?.let { Text(it, fontSize = (24 * scale).sp, color = Palette.body) }
                    Button(onClick = { open(entry.card) }, shape = RoundedCornerShape(12.dp * scale), modifier = Modifier.height(72.dp * scale),
                        colors = ButtonDefaults.buttonColors(containerColor = Palette.raised, contentColor = Palette.text)) { Text("View details", fontSize = (26 * scale).sp) }
                }
            }
        }
    }
}
