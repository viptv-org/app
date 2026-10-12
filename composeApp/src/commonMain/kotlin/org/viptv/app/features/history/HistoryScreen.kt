package org.viptv.app.features.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.ChevronLeft
import com.composables.icons.lucide.Lucide
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.Card
import org.viptv.app.connection.HistoryDisplay

@Composable
fun HistoryScreen(history: List<HistoryDisplay>, busy: Boolean, error: String?, open: (Card) -> Unit,
    clear: () -> Unit, back: () -> Unit = {}) {
    var confirm by remember { mutableStateOf(false) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val phone = maxWidth < 640.dp
        LazyColumn(Modifier.widthIn(max = 1080.dp).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = if (phone) 16.dp else 40.dp, vertical = if (phone) 8.dp else 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
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
            if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth(), color = Palette.accent) }
            error?.let { item { Text(it, color = Palette.danger) } }
            if (!busy && history.isEmpty() && error == null) item { Text("Nothing watched yet.", color = Palette.secondary) }
            history.groupBy { it.dayLabel }.forEach { (label, entries) ->
                item(key = "day:$label") {
                    Text(label, Modifier.padding(top = 12.dp, bottom = 4.dp), fontSize = 12.sp,
                        fontWeight = FontWeight.Medium, color = Palette.secondary)
                }
                items(entries, key = { it.entry.card.identity.kind + ":" + it.entry.card.identity.id }) {
                    HistoryRow(it.entry, phone, open)
                }
            }
            item { Spacer(Modifier.height(if (phone) 96.dp else 24.dp)) }
        }
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("Clear history?") },
        text = { Text("This removes viewing history for this profile on this device.") },
        confirmButton = { TextButton(onClick = { clear(); confirm = false }) { Text("Clear") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } })
}
