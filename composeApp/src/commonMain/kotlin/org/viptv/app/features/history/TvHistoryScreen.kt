package org.viptv.app.features.history

import org.viptv.app.connection.HistoryDisplay
import org.viptv.app.connection.cardLookupKey
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import org.viptv.app.components.TvAction
import org.viptv.app.components.tvFocusOutline
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.Card

/**
 * TV History. The board export has no History screen, so this follows the TV
 * design vocabulary rather than the phone list: the page heading owns the top
 * inset, entries are 16:9 rows with the light focus fill and the 4 px ring, and
 * Clear history is a TV action with a confirmation dialog.
 */
@Composable
internal fun TvHistoryScreen(history: List<HistoryDisplay>, busy: Boolean, error: String?, open: (Card) -> Unit,
    clear: () -> Unit, back: () -> Unit = {}) {
    var confirm by remember { mutableStateOf(false) }
    val first = remember { FocusRequester() }
    val clearFocus = remember { FocusRequester() }
    var attached by remember { mutableStateOf(false) }
    LaunchedEffect(history.size, busy) {
        if (busy) return@LaunchedEffect
        if (history.isNotEmpty()) attached = false
        repeat(30) {
            if (runCatching { first.requestFocus() }.getOrDefault(false)) {
                attached = true; return@LaunchedEffect
            }
            withFrameNanos { }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val scale = maxWidth / 1920.dp
        Column(Modifier.fillMaxSize().padding(start = 96.dp * scale, end = 96.dp * scale, top = 96.dp * scale)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("History", fontSize = (40 * scale).sp, lineHeight = (48 * scale).sp,
                        letterSpacing = (-.8f * scale).sp, fontWeight = FontWeight.Bold)
                    Text("Everything watched on this profile, newest first.", color = Palette.secondary,
                        fontSize = (24 * scale).sp, lineHeight = (32 * scale).sp)
                }
                TvAction(Modifier.height(64.dp * scale).width(260.dp * scale), scale,
                    requester = clearFocus, enabled = !busy && history.isNotEmpty(), activate = { confirm = true }) { tint ->
                    Text("Clear history", color = tint, fontSize = (24 * scale).sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(32.dp * scale))
            if (busy) androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth(), color = Palette.accent)
            error?.let { Text(it, color = Palette.danger, fontSize = (24 * scale).sp) }
            if (!busy && history.isEmpty() && error == null) {
                // The Clear button is disabled here, so it cannot hold focus.
                // Give the empty state a real action and focus it.
                Column(Modifier.padding(top = 96.dp * scale), verticalArrangement = Arrangement.spacedBy(18.dp * scale)) {
                    Text("Nothing watched yet", fontSize = (32 * scale).sp, fontWeight = FontWeight.Bold)
                    Text("Titles you play show up here.", color = Palette.secondary, fontSize = (24 * scale).sp)
                    TvAction(Modifier.height(64.dp * scale).width(280.dp * scale), scale, primary = true,
                        requester = if (!attached) first else null, activate = back,
                        contentPadding = PaddingValues(horizontal = 24.dp * scale)) {
                        Text("Browse Home", color = it, fontSize = (24 * scale).sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            val ordered = remember(history) { history.distinctBy { cardLookupKey(it.entry.card) } }
            val groups = remember(ordered) { ordered.groupBy { it.dayLabel } }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp * scale),
                verticalArrangement = Arrangement.spacedBy(12.dp * scale)) {
                groups.forEach { (day, entries) ->
                    item(key = "day:$day") {
                        Text(day, Modifier.padding(top = 16.dp * scale, bottom = 4.dp * scale),
                            fontSize = (24 * scale).sp, fontWeight = FontWeight.SemiBold, color = Palette.secondary)
                    }
                    items(entries, key = { cardLookupKey(it.entry.card) }) { entry ->
                        val requester = remember(entry.entry.card) { FocusRequester() }
                        TvHistoryRow(entry, scale, requester = if (entry === ordered.first()) first else requester,
                            left = clearFocus, open = open)
                    }
                }
            }
        }
    }
    if (confirm) androidx.compose.material3.AlertDialog(onDismissRequest = { confirm = false },
        title = { Text("Clear history?") },
        text = { Text("This removes viewing history for this profile.") },
        confirmButton = { androidx.compose.material3.TextButton(onClick = { clear(); confirm = false }) { Text("Clear") } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = { confirm = false }) { Text("Cancel") } })
}

@Composable
private fun TvHistoryRow(entry: HistoryDisplay, scale: Float, requester: FocusRequester, left: FocusRequester,
    open: (Card) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val card = entry.entry.card
    val shape = RoundedCornerShape(16.dp * scale)
    Row(Modifier.fillMaxWidth().height(148.dp * scale).focusRequester(requester)
        // No control sits left of a full-width row; LEFT must dead-end instead
        // of jumping diagonally to Clear history.
        .focusProperties { this.left = FocusRequester.Cancel; this.right = FocusRequester.Cancel }
        .onFocusChanged { focused = it.isFocused }
        .graphicsLayer { this.shape = shape; shadowElevation = if (focused) 24.dp.toPx() * scale else 0f; clip = false }
        .background(if (focused) Palette.text else Color.White.copy(alpha = .06f), shape)
        .tvFocusOutline(focused, 16.dp * scale, scale)
        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { open(card) }
        .padding(12.dp * scale), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dp * scale)) {
        Box(Modifier.width(232.dp * scale).height(124.dp * scale).clip(RoundedCornerShape(10.dp * scale)).background(Palette.surface)) {
            AsyncImage(card.landscape ?: card.poster, card.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp * scale)) {
            Text(card.title, fontSize = (28 * scale).sp, lineHeight = (34 * scale).sp, fontWeight = FontWeight.SemiBold,
                color = if (focused) Palette.background else Palette.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (entry.entry.completed_at_millis != null) "Watched" else card.subtitle ?: "In progress",
                fontSize = (22 * scale).sp, lineHeight = (28 * scale).sp,
                color = if (focused) Color(0xFF4A4945) else Palette.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        card.progress?.takeIf { it > 0 }?.let {
            Text("${((it.toFloat().coerceIn(0f, 1f)) * 100).toInt()}% watched", fontSize = (22 * scale).sp,
                color = if (focused) Color(0xFF4A4945) else Palette.secondary)
        }
    }
}
