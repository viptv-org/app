package org.viptv.app.features.history

import org.viptv.app.components.FocusLazyColumn as LazyColumn
import org.viptv.app.connection.HistoryCorrection
import org.viptv.app.connection.HistoryDisplay
import org.viptv.app.connection.cardLookupKey
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
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
import org.viptv.app.components.PageRestore
import org.viptv.app.components.TvAction
import org.viptv.app.components.pageAnchor
import org.viptv.app.components.rememberPageRestore
import org.viptv.app.components.tvFocusOutline
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.Card
import org.viptv.app.components.pointerClickable
import org.viptv.app.components.TextButton

@Composable
internal fun TvHistoryScreen(history: List<HistoryDisplay>, busy: Boolean, error: String?, open: (Card) -> Unit,
    clear: () -> Unit, back: () -> Unit = {}, correct: (HistoryDisplay, HistoryCorrection) -> Unit = { _, _ -> },
    localMode: Boolean = false) {
    var confirm by remember { mutableStateOf(false) }
    var lastFocused by remember { mutableStateOf<HistoryDisplay?>(null) }
    var resuming by remember { mutableStateOf<HistoryDisplay?>(null) }
    val restore = rememberPageRestore("history")
    val lines = remember(history) { history.lines() }
    val anchored = remember(lines, restore.lastKey) {
        lines.indexOfFirst { it is HistoryLine.Entry && cardLookupKey(it.display.entry.card) == restore.lastKey }
    }
    val landing = remember(anchored, lines) { if (anchored >= 0) anchored else lines.entryAt(0) }
    val first = remember { FocusRequester() }
    val clearFocus = remember { FocusRequester() }
    val actionsFocus = remember { FocusRequester() }
    var attached by remember { mutableStateOf(false) }
    LaunchedEffect(history.isEmpty(), busy) {
        if (!history.isEmpty()) { attached = false; return@LaunchedEffect }
        if (busy) return@LaunchedEffect
        repeat(30) {
            if (runCatching { first.requestFocus() }.getOrDefault(false)) { attached = true; return@LaunchedEffect }
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
            if (localMode) Text("Progress corrections need a server.", color = Palette.secondary, fontSize = (24 * scale).sp)
            if (!busy && history.isEmpty() && error == null) {
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
            val list = rememberLazyListState()
            PageRestore(list, restore, claimOnEntry = true, target = { landing })
            LazyColumn(Modifier.weight(1f), state = list, contentPadding = PaddingValues(bottom = 8.dp * scale),
                verticalArrangement = Arrangement.spacedBy(12.dp * scale)) {
                itemsIndexed(lines, key = { _, line -> line.key() }) { index, line ->
                    when (line) {
                        is HistoryLine.Day -> Text(line.label, Modifier.padding(top = 16.dp * scale, bottom = 4.dp * scale),
                            fontSize = (24 * scale).sp, fontWeight = FontWeight.SemiBold, color = Palette.secondary)
                        is HistoryLine.Entry -> {
                            val key = cardLookupKey(line.display.entry.card)
                            TvHistoryRow(line.display, scale, onFocus = { lastFocused = it },
                                modifier = Modifier.pageAnchor(restore, key, index, target = landing == index),
                                open = { restore.mark(key, index); open(it) })
                        }
                    }
                }
            }
            if (!localMode && history.isNotEmpty()) Row(Modifier.fillMaxWidth().padding(top = 12.dp * scale, bottom = 96.dp * scale),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp * scale)) {
                Text(lastFocused?.entry?.card?.title?.let { "“$it”" }.orEmpty(), Modifier.weight(1f), color = Palette.secondary,
                    fontSize = (22 * scale).sp, lineHeight = (28 * scale).sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                TvAction(Modifier.height(64.dp * scale).width(360.dp * scale), scale, requester = actionsFocus,
                    enabled = lastFocused != null, activate = {
                        lastFocused?.let { correct(it, if (it.watched) HistoryCorrection.Unwatched else HistoryCorrection.Watched) }
                    }, contentPadding = PaddingValues(horizontal = 24.dp * scale)) {
                    Text(if (lastFocused?.watched == true) "Mark unwatched" else "Mark watched", color = it,
                        fontSize = (24 * scale).sp, fontWeight = FontWeight.SemiBold)
                }
                TvAction(Modifier.height(64.dp * scale).width(280.dp * scale), scale,
                    enabled = lastFocused != null && (lastFocused?.duration ?: 0.0) > 0.0, activate = { resuming = lastFocused },
                    contentPadding = PaddingValues(horizontal = 24.dp * scale)) {
                    Text("Resume at…", color = it, fontSize = (24 * scale).sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
    if (confirm) androidx.compose.material3.AlertDialog(onDismissRequest = { confirm = false },
        title = { Text("Clear history?") },
        text = { Text("This removes viewing history for this profile.") },
        confirmButton = { TextButton(onClick = { clear(); confirm = false }) { Text("Clear") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } })
    resuming?.let { display ->
        ResumePositionDialog(display.position.toLong(), display.duration, onDismiss = { resuming = null },
            onSave = { seconds -> resuming = null; correct(display, HistoryCorrection.Position(seconds)) })
    }
}

@Composable
private fun TvHistoryRow(entry: HistoryDisplay, scale: Float, modifier: Modifier = Modifier,
    onFocus: (HistoryDisplay) -> Unit, open: (Card) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val card = entry.entry.card
    val shape = RoundedCornerShape(16.dp * scale)
    val progress = entry.progressFraction()
    Row(modifier.fillMaxWidth().height(148.dp * scale)
        .focusProperties { this.left = FocusRequester.Cancel; this.right = FocusRequester.Cancel }
        .onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocus(entry) }
        .graphicsLayer { this.shape = shape; shadowElevation = if (focused) 24.dp.toPx() * scale else 0f; clip = false }
        .background(if (focused) Palette.text else Color.White.copy(alpha = .06f), shape)
        .tvFocusOutline(focused, 16.dp * scale, scale)
        .pointerClickable(interactionSource = remember { MutableInteractionSource() }, indication = null, shape = shape) { open(card) }
        .padding(12.dp * scale), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dp * scale)) {
        Box(Modifier.width(232.dp * scale).height(124.dp * scale).clip(RoundedCornerShape(10.dp * scale)).background(Palette.surface)) {
            AsyncImage(card.landscape ?: card.poster, card.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp * scale)) {
            Text(card.title, fontSize = (28 * scale).sp, lineHeight = (34 * scale).sp, fontWeight = FontWeight.SemiBold,
                color = if (focused) Palette.background else Palette.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(entry.watchStateLine(), fontSize = (22 * scale).sp, lineHeight = (28 * scale).sp,
                color = if (focused) Color(0xFF4A4945) else Palette.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            entry.importedNote()?.let { note ->
                Text(note, fontSize = (18 * scale).sp, lineHeight = (24 * scale).sp,
                    color = if (focused) Color(0xFF4A4945) else Palette.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (progress > 0.0 && progress < 1.0) {
            Text("${(progress * 100).toInt()}% watched", fontSize = (22 * scale).sp,
                color = if (focused) Color(0xFF4A4945) else Palette.secondary)
        }
    }
}