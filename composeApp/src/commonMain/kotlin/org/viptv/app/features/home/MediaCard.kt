package org.viptv.app.features.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.Card

@Composable
fun MediaCard(card: Card, width: Int = 240, portrait: Boolean = false, showLabels: Boolean = true, onFocus: (Card) -> Unit = {}, open: (Card) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Column(Modifier.width(width.dp).onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocus(card) }.clickable { open(card) }) {
        Box(Modifier.fillMaxWidth().aspectRatio(if (portrait) 2f / 3 else 16f / 9).clip(RoundedCornerShape(9.dp)).background(Palette.raised).then(if (focused) Modifier.border(3.dp, Palette.text, RoundedCornerShape(9.dp)) else Modifier)) {
            AsyncImage(if (portrait) card.poster else card.landscape, card.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            if (card.badges.isNotEmpty()) Surface(Modifier.align(Alignment.TopStart).padding(8.dp), shape = RoundedCornerShape(4.dp), color = Palette.text) {
                Text(card.badges.first(), Modifier.padding(horizontal = 6.dp, vertical = 3.dp), color = Palette.background, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            }
            card.progress?.let { progress ->
                LinearProgressIndicator(progress = { progress.toFloat() }, modifier = Modifier.fillMaxWidth().height(3.dp).align(Alignment.BottomCenter), color = Palette.accent, trackColor = Palette.raised)
            }
        }
        if (showLabels) {
            Spacer(Modifier.height(9.dp))
            Text(card.title, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            card.subtitle?.let { Text(it, fontSize = 11.sp, color = Palette.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}
