package org.viptv.app.features.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.composables.icons.lucide.Ellipsis
import com.composables.icons.lucide.Lucide
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.HistoryEntry
import org.viptv.app.components.pointerClickable
import org.viptv.app.components.IconButton
import org.viptv.app.components.DropdownMenuItem
import androidx.compose.ui.graphics.RectangleShape

@Composable
internal fun HistoryRow(entry: HistoryEntry, phone: Boolean, open: (Card) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val card = entry.card
    Row(Modifier.fillMaxWidth().pointerClickable(shape = RectangleShape) { open(card) }.padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(if (phone) 12.dp else 18.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(if (phone) 136.dp else 160.dp).aspectRatio(16f / 9)
            .clip(RoundedCornerShape(8.dp)).background(Palette.raised)) {
            AsyncImage(card.landscape ?: card.poster, card.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            card.progress?.let { progress ->
                LinearProgressIndicator(progress = { progress.toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp),
                    color = Palette.accent, trackColor = Palette.raised)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(card.title, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold,
                maxLines = if (phone) 2 else 1, overflow = TextOverflow.Ellipsis)
            Text(if (entry.completed_at_millis != null) "Watched" else card.subtitle ?: card.identity.kind,
                fontSize = 12.sp, lineHeight = 16.sp, color = Palette.secondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Box {
            IconButton(onClick = { menu = true }, modifier = Modifier.size(32.dp)) {
                Icon(Lucide.Ellipsis, "History actions", Modifier.size(18.dp))
            }
            DropdownMenu(menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("View details") }, onClick = { menu = false; open(card) })
            }
        }
    }
}
