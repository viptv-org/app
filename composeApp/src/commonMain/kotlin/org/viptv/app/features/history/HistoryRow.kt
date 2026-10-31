package org.viptv.app.features.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.composables.icons.lucide.Ellipsis
import com.composables.icons.lucide.Lucide
import org.viptv.app.components.DropdownMenuItem
import org.viptv.app.components.IconButton
import org.viptv.app.components.TextButton
import org.viptv.app.components.pointerClickable
import org.viptv.app.connection.HistoryCorrection
import org.viptv.app.connection.HistoryDisplay
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.Card

@Composable
internal fun HistoryRow(display: HistoryDisplay, phone: Boolean, open: (Card) -> Unit,
    correct: (HistoryCorrection) -> Unit, available: Boolean, modifier: Modifier = Modifier) {
    var menu by remember { mutableStateOf(false) }
    var resume by remember { mutableStateOf(false) }
    val card = display.entry.card
    val progress = display.progressFraction()
    Row(modifier.fillMaxWidth().pointerClickable(shape = RectangleShape) { open(card) }.padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(if (phone) 12.dp else 18.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(if (phone) 136.dp else 160.dp).aspectRatio(16f / 9)
            .clip(RoundedCornerShape(8.dp)).background(Palette.raised)) {
            AsyncImage(card.landscape ?: card.poster, card.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            if (progress > 0.0) LinearProgressIndicator(progress = { progress.toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp),
                color = Palette.accent, trackColor = Palette.raised)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(card.title, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold,
                maxLines = if (phone) 2 else 1, overflow = TextOverflow.Ellipsis)
            Text(display.watchStateLine(), fontSize = 12.sp, lineHeight = 16.sp, color = Palette.secondary,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            display.importedNote()?.let { note ->
                Text(note, fontSize = 12.sp, lineHeight = 16.sp, color = Palette.secondary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Box {
            IconButton(onClick = { menu = true }, modifier = Modifier.size(32.dp)) {
                Icon(Lucide.Ellipsis, "History actions", Modifier.size(18.dp))
            }
            DropdownMenu(menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("View details") }, onClick = { menu = false; open(card) })
                if (available) {
                    DropdownMenuItem(text = { Text(if (display.watched) "Mark unwatched" else "Mark watched") },
                        onClick = {
                            menu = false
                            correct(if (display.watched) HistoryCorrection.Unwatched else HistoryCorrection.Watched)
                        })
                    DropdownMenuItem(text = { Text("Resume at…") }, enabled = display.duration > 0.0,
                        onClick = { menu = false; resume = true })
                }
            }
        }
    }
    if (resume) ResumePositionDialog(display.position.toLong(), display.duration, onDismiss = { resume = false },
        onSave = { seconds -> resume = false; correct(HistoryCorrection.Position(seconds)) })
}

@Composable
internal fun ResumePositionDialog(initialSeconds: Long, duration: Double, onDismiss: () -> Unit, onSave: (Long) -> Unit) {
    var text by remember { mutableStateOf(initialSeconds.coerceAtLeast(0).toString()) }
    val maxSeconds = duration.toLong().coerceAtLeast(0)
    val value = text.toLongOrNull()
    val valid = value != null && value in 0..(if (maxSeconds > 0) maxSeconds else Long.MAX_VALUE)
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Resume at (seconds)") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(text, { next -> text = next.filter(Char::isDigit).take(9) }, Modifier.fillMaxWidth(),
                    singleLine = true, label = { Text("Resume at (seconds)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), isError = !valid)
                if (maxSeconds > 0) Text("Up to $maxSeconds seconds.", color = Palette.secondary, fontSize = 12.sp)
            }
        },
        confirmButton = { TextButton(onClick = { value?.let(onSave) }, enabled = valid) { Text("Save position") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}
