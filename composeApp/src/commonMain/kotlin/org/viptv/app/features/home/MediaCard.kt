package org.viptv.app.features.home

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.Card

@Composable
fun MediaCard(card: Card, width: Int = 240, portrait: Boolean = false, showLabels: Boolean = true, onFocus: (Card) -> Unit = {}, open: (Card) -> Unit, labelScale: Float = 1f, cornerRadius: androidx.compose.ui.unit.Dp = 8.dp, focusWidth: androidx.compose.ui.unit.Dp = 3.dp, showFocusOutline: Boolean = true, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    Column(modifier.width(width.dp).onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocus(card) }.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { open(card) }) {
        Box(Modifier.fillMaxWidth().aspectRatio(if (portrait) 2f / 3 else 16f / 9).clip(RoundedCornerShape(cornerRadius)).background(Palette.raised)) {
            AsyncImage(if (portrait) card.poster else card.landscape, card.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            if (card.badges.any { !it.equals("Simkl", true) }) Surface(Modifier.align(Alignment.TopStart).padding(8.dp), shape = RoundedCornerShape(4.dp), color = Palette.text) {
                Text(card.badges.first { !it.equals("Simkl", true) }, Modifier.padding(horizontal = 6.dp, vertical = 3.dp), color = Palette.background, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            }
            card.progress?.let { progress ->
                // Thin continue-watching bar at the artwork's bottom edge,
                // full width of the card — matching the supplied reference
                // design. No thumb: the card progress bar is an indicator,
                // not a draggable slider.
                val p = progress.toFloat().coerceIn(0f, 1f)
                Box(Modifier.fillMaxWidth().align(Alignment.BottomCenter).height(4.dp).drawBehind {
                    val bar = size.height
                    drawRoundRect(Palette.raised.copy(alpha = 0.9f), cornerRadius = CornerRadius(bar / 2), size = Size(size.width, bar))
                    if (p > 0f) drawRoundRect(Palette.accent, cornerRadius = CornerRadius(bar / 2), size = Size(size.width * p, bar))
                })
            }
            if (showFocusOutline) org.viptv.app.components.FocusOutline(focused, cornerRadius, focusWidth, Modifier.matchParentSize())
        }
        if (showLabels) {
            Spacer(Modifier.height(9.dp))
            Text(card.title, Modifier.fillMaxWidth().then(if (focused) Modifier.basicMarquee(iterations = Int.MAX_VALUE) else Modifier), fontSize = (14 * labelScale).sp, lineHeight = (18 * labelScale).sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = if (focused) TextOverflow.Clip else TextOverflow.Ellipsis)
            card.subtitle?.let { Text(it, fontSize = (12 * labelScale).sp, lineHeight = (16 * labelScale).sp, color = Palette.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}
