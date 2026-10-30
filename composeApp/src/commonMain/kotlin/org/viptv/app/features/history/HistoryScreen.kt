package org.viptv.app.features.history

import org.viptv.app.components.FocusLazyColumn as LazyColumn
import org.viptv.app.connection.cardLookupKey
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.ChevronLeft
import com.composables.icons.lucide.Lucide
import org.viptv.app.components.PageRestore
import org.viptv.app.components.pageAnchor
import org.viptv.app.components.rememberPageRestore
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.Card
import org.viptv.app.connection.HistoryDisplay
import org.viptv.app.components.TextButton
import org.viptv.app.components.IconButton

internal sealed interface HistoryLine {
    data class Day(val label: String) : HistoryLine
    data class Entry(val display: HistoryDisplay) : HistoryLine
}

internal fun List<HistoryDisplay>.lines(): List<HistoryLine> = distinctBy { cardLookupKey(it.entry.card) }
    .groupBy { it.dayLabel }.flatMap { (label, entries) ->
        listOf(HistoryLine.Day(label)) + entries.map(HistoryLine::Entry)
    }

internal fun HistoryLine.key(): String = when (this) {
    is HistoryLine.Day -> "day:$label"
    is HistoryLine.Entry -> cardLookupKey(display.entry.card)
}

/** The entry nearest where the page was left: a day heading moves to the row under it. */
internal fun List<HistoryLine>.entryAt(index: Int): Int? {
    if (isEmpty()) return null
    val start = index.coerceIn(0, lastIndex)
    if (this[start] is HistoryLine.Entry) return start
    return (start..lastIndex).firstOrNull { this[it] is HistoryLine.Entry }
        ?: (start downTo 0).firstOrNull { this[it] is HistoryLine.Entry }
}

@Composable
fun HistoryScreen(history: List<HistoryDisplay>, busy: Boolean, error: String?, open: (Card) -> Unit,
    clear: () -> Unit, back: () -> Unit = {}) {
    var confirm by remember { mutableStateOf(false) }
    val restore = rememberPageRestore("history")
    val lines = remember(history) { history.lines() }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val phone = maxWidth < 640.dp
        val list = rememberLazyListState()
        val heading = 1 + (if (busy) 1 else 0) + (if (error != null) 1 else 0) + (if (!busy && history.isEmpty() && error == null) 1 else 0)
        val anchored = remember(lines, restore.lastKey) {
            lines.indexOfFirst { it is HistoryLine.Entry && cardLookupKey(it.display.entry.card) == restore.lastKey }
        }
        PageRestore(list, restore, target = {
            val line = if (anchored >= 0) anchored else restore.lastKey?.let { lines.entryAt(restore.lastIndex) }
            line?.plus(heading)
        })
        LazyColumn(Modifier.widthIn(max = 1080.dp).fillMaxSize(), state = list,
            contentPadding = PaddingValues(horizontal = if (phone) 16.dp else 40.dp, vertical = if (phone) 8.dp else 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item(key = "heading") {
                Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (phone) IconButton(onClick = back, modifier = Modifier.size(32.dp)) {
                        Icon(Lucide.ChevronLeft, "Back", Modifier.size(22.dp))
                    }
                    Text("History", Modifier.weight(1f), fontSize = if (phone) 16.sp else 28.sp,
                        fontWeight = FontWeight.Bold, textAlign = if (phone) androidx.compose.ui.text.style.TextAlign.Center else androidx.compose.ui.text.style.TextAlign.Start)
                    TextButton(onClick = { confirm = true }, enabled = !busy && history.isNotEmpty()) {
                        Text(if (phone) "Clear" else "Clear history", fontSize = 13.sp)
                    }
                }
            }
            if (busy) item(key = "busy") { LinearProgressIndicator(Modifier.fillMaxWidth(), color = Palette.accent) }
            error?.let { item(key = "error") { Text(it, color = Palette.danger) } }
            if (!busy && history.isEmpty() && error == null) item(key = "empty") { Text("Nothing watched yet.", color = Palette.secondary) }
            itemsIndexed(lines, key = { _, line -> line.key() }) { index, line ->
                when (line) {
                    is HistoryLine.Day -> Text(line.label, Modifier.padding(top = 12.dp, bottom = 4.dp), fontSize = 12.sp,
                        fontWeight = FontWeight.Medium, color = Palette.secondary)
                    is HistoryLine.Entry -> {
                        val key = cardLookupKey(line.display.entry.card)
                        HistoryRow(line.display.entry, phone, open = { restore.mark(key, index); open(it) },
                            modifier = Modifier.pageAnchor(restore, key, index, target = anchored == index))
                    }
                }
            }
            item(key = "bottom") { Spacer(Modifier.height(if (phone) 96.dp else 24.dp)) }
        }
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("Clear history?") },
        text = { Text("This removes viewing history for this profile.") },
        confirmButton = { TextButton(onClick = { clear(); confirm = false }) { Text("Clear") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } })
}
