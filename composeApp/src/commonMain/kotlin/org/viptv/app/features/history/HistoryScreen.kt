package org.viptv.app.features.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.HistoryEntry
import org.viptv.app.connection.HistoryDisplay

@Composable
fun HistoryScreen(history: List<HistoryDisplay>, busy: Boolean, error: String?, open: (Card) -> Unit, clear: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("History", style = MaterialTheme.typography.headlineLarge)
                if (history.isNotEmpty()) TextButton(onClick = { confirm=true }) { Text("Clear") }
            }
        }
        if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth(), color = Palette.accent) }
        error?.let { item { Text(it, color = Palette.danger) } }
        if (!busy && history.isEmpty() && error==null) item { Text("Nothing watched yet.", color = Palette.secondary) }
        history.groupBy { it.dayLabel }.forEach { (label,entries) ->
            item(key="day:$label") { Text(label, style = MaterialTheme.typography.titleMedium) }
            items(entries, key = { it.entry.card.identity.kind + ":" + it.entry.card.identity.id }) { displayed ->
            val entry=displayed.entry
            Row(Modifier.fillMaxWidth().clickable { open(entry.card) }.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                val card=entry.card
                val art=card.landscape ?: card.poster
                Box(Modifier.size(width=112.dp,height=64.dp).clip(RoundedCornerShape(8.dp)).background(Palette.raised), contentAlignment = Alignment.Center) {
                    if (art != null) AsyncImage(art, null, Modifier.fillMaxSize(), contentScale = if (card.landscape!=null) ContentScale.Crop else ContentScale.Fit)
                    else Text(card.title.take(1).uppercase(), color = Palette.secondary)
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(card.title, style = MaterialTheme.typography.titleMedium)
                    card.subtitle?.let { Text(it, color = Palette.secondary) }
                }
            }
            }
        }
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm=false }, title = { Text("Clear history?") }, text = { Text("This removes viewing history for this profile on this device.") }, confirmButton = { TextButton(onClick = { clear();confirm=false }) { Text("Clear") } }, dismissButton = { TextButton(onClick = { confirm=false }) { Text("Cancel") } })
}
