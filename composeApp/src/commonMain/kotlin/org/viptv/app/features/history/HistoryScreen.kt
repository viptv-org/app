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
import org.viptv.app.components.ListPageState
import org.viptv.app.components.PageRestore
import org.viptv.app.components.listPageState
import org.viptv.app.components.pageAnchor
import org.viptv.app.components.rememberPageRestore
import org.viptv.app.components.listItemEntrance
import org.viptv.app.components.rememberListEntrance
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.Card
import org.viptv.app.connection.HistoryDisplay
import org.viptv.app.connection.seasonNumber
import org.viptv.app.connection.HistoryCorrection
import org.viptv.app.components.TextButton
import org.viptv.app.components.Button
import org.viptv.app.components.BackControl
import org.viptv.app.components.IconButton
import org.viptv.app.components.ListEndFooter
import org.viptv.app.components.LIST_TITLES
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.style.TextAlign

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

internal fun List<HistoryLine>.entryAt(index: Int): Int? {
    if (isEmpty()) return null
    val start = index.coerceIn(0, lastIndex)
    if (this[start] is HistoryLine.Entry) return start
    return (start..lastIndex).firstOrNull { this[it] is HistoryLine.Entry }
        ?: (start downTo 0).firstOrNull { this[it] is HistoryLine.Entry }
}

internal fun HistoryDisplay.watchState(): String = when {
    watched && resumeActive -> "Watched · Rewatch in progress"
    watched -> "Watched"
    resumeActive -> "In progress"
    else -> "Unwatched"
}

internal fun HistoryDisplay.descriptor(): String = entry.card.identity.episode?.let { episode ->
    "S${episode.seasonNumber()} · E${episode.episode}"
} ?: entry.card.identity.kind.replaceFirstChar { it.uppercase() }

internal fun HistoryDisplay.watchStateLine(): String = "${descriptor()} · ${watchState()}"

internal fun HistoryDisplay.importedNote(): String? =
    if (completionOnly && !watchDateKnown) "Imported — date unknown" else null

internal fun HistoryDisplay.progressFraction(): Double = when {
    watched -> 1.0
    duration > 0.0 -> (position / duration).coerceIn(0.0, 1.0)
    else -> entry.card.progress ?: 0.0
}

@Composable
fun HistoryScreen(history: List<HistoryDisplay>, busy: Boolean, error: String?, open: (Card) -> Unit,
    clear: () -> Unit, back: () -> Unit = {}, correct: (HistoryDisplay, HistoryCorrection) -> Unit = { _, _ -> },
    localMode: Boolean = false, retry: () -> Unit = {}) {
    var confirm by remember { mutableStateOf(false) }
    val restore = rememberPageRestore("history")
    val lines = remember(history) { history.lines() }
    val entrance = rememberListEntrance(history.isNotEmpty())
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val phone = maxWidth < 640.dp
        val list = rememberLazyListState()
        val pageState = listPageState(history.size, busy, error)
        val heading = 1 + (if (busy) 1 else 0) + (if (pageState == ListPageState.Failed) 1 else 0) + (if (localMode) 1 else 0) + (if (pageState == ListPageState.Empty) 1 else 0)
        val anchored = remember(lines, restore.lastKey) {
            lines.indexOfFirst { it is HistoryLine.Entry && cardLookupKey(it.display.entry.card) == restore.lastKey }
        }
        PageRestore(list, restore, target = {
            val line = if (anchored >= 0) anchored else restore.lastKey?.let { lines.entryAt(restore.lastIndex) }
            line?.plus(heading)
        })
        LazyColumn(Modifier.widthIn(max = 1080.dp).fillMaxSize(), state = list,
            contentPadding = PaddingValues(start = if (phone) 16.dp else 40.dp, end = if (phone) 16.dp else 40.dp,
                bottom = if (phone) 8.dp else 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item(key = "heading") {
                if (phone) Box(Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    BackControl(back, Modifier.align(Alignment.CenterStart))
                    Text("History", Modifier.align(Alignment.Center), fontSize = 16.sp,
                        fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    TextButton(onClick = { confirm = true }, modifier = Modifier.align(Alignment.CenterEnd),
                        enabled = !busy && history.isNotEmpty()) {
                        Text("Clear", fontSize = 13.sp)
                    }
                } else Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("History", Modifier.weight(1f), fontSize = 28.sp,
                        fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Start)
                    TextButton(onClick = { confirm = true }, enabled = !busy && history.isNotEmpty()) {
                        Text("Clear history", fontSize = 13.sp)
                    }
                }
            }
            if (busy) item(key = "busy") { LinearProgressIndicator(Modifier.fillMaxWidth(), color = Palette.accent) }
            if (pageState == ListPageState.Failed) item(key = "error") { HistoryFailure(error.orEmpty(), retry) }
            if (localMode) item(key = "server") { Text("Progress corrections need a server.", color = Palette.secondary) }
            if (pageState == ListPageState.Empty) item(key = "empty") { Text("Nothing watched yet.", color = Palette.secondary) }
            itemsIndexed(lines, key = { _, line -> line.key() }) { index, line ->
                when (line) {
                    is HistoryLine.Day -> Text(line.label, Modifier.padding(top = 12.dp, bottom = 4.dp), fontSize = 12.sp,
                        fontWeight = FontWeight.Medium, color = Palette.secondary)
                    is HistoryLine.Entry -> {
                        val key = cardLookupKey(line.display.entry.card)
                        HistoryRow(line.display, phone, open = { restore.mark(key, index); open(it) },
                            correct = { correct(line.display, it) }, available = !localMode,
                            modifier = Modifier.pageAnchor(restore, key, index, target = anchored == index).listItemEntrance(index, entrance.animate))
                    }
                }
            }
            if (history.isNotEmpty() && !busy && error != null) item(key = "footer") {
                ListEndFooter(failed = true, retry = retry, noun = LIST_TITLES, modifier = Modifier.fillMaxWidth(), television = false, scale = 1f)
            } else if (history.isNotEmpty() && busy) item(key = "footer") {
                ListEndFooter(loading = true, noun = LIST_TITLES, modifier = Modifier.fillMaxWidth(), television = false, scale = 1f)
            } else if (history.isNotEmpty() && !busy && error == null) item(key = "footer") {
                ListEndFooter(ended = true, noun = LIST_TITLES, modifier = Modifier.fillMaxWidth(), television = false, scale = 1f)
            }
        }
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("Clear history?") },
        text = { Text("This removes viewing history for this profile.") },
        confirmButton = { TextButton(onClick = { clear(); confirm = false }) { Text("Clear") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } })
}

@Composable
private fun HistoryFailure(reason: String, retry: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 64.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Couldn’t load history", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(reason, color = Palette.secondary, fontSize = 14.sp, lineHeight = 20.sp, textAlign = TextAlign.Center)
        Button(onClick = retry, shape = RoundedCornerShape(12.dp), modifier = Modifier.heightIn(min = 46.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Palette.text, contentColor = Palette.background)) { Text("Try again") }
    }
}
