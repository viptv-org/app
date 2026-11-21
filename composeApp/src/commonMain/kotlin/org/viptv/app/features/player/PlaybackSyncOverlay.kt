package org.viptv.app.features.player

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Minus
import com.composables.icons.lucide.Plus
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.viptv.app.components.frameDelay
import org.viptv.app.components.pointerClickable
import org.viptv.app.components.tvFocusOutline
import org.viptv.app.player.OFFSET_STEP_US
import org.viptv.app.player.PlaybackOffsets
import org.viptv.app.player.offsetLabel
import org.viptv.app.theme.Palette

internal enum class SyncKind { Audio, Subtitle }

internal const val syncOverlayIdleMillis = 5_000L
internal const val syncRepeatDelayMillis = 400L
internal const val syncRepeatMillis = 100L
internal const val SUBTITLES_OFF = "Subtitles off"
internal const val SYNC_SAVED = "Saved for this release"

internal fun syncTitle(kind: SyncKind) = if (kind == SyncKind.Audio) "Audio sync" else "Subtitle sync"

internal fun PlaybackOffsets.valueOf(kind: SyncKind) = if (kind == SyncKind.Audio) audioUs else subtitleUs

private fun PlaybackOffsets.shift(kind: SyncKind, steps: Int) =
    if (kind == SyncKind.Audio) shiftAudio(steps * OFFSET_STEP_US) else shiftSubtitle(steps * OFFSET_STEP_US)

private fun PlaybackOffsets.reset(kind: SyncKind) = if (kind == SyncKind.Audio) resetAudio() else resetSubtitle()

@Composable
internal fun PlaybackSyncOverlay(kind: SyncKind, offsets: PlaybackOffsets, close: () -> Unit, modifier: Modifier = Modifier,
    scale: Float = 1f, tv: Boolean = false, bottomInset: Dp = 24.dp) {
    val state by offsets.state.collectAsState()
    val value = if (kind == SyncKind.Audio) state.audioUs else state.subtitleUs
    val remembered = if (kind == SyncKind.Audio) offsets.remembered.audioUs else offsets.remembered.subtitleUs
    var inputs by remember(kind) { mutableIntStateOf(0) }
    val latestClose by rememberUpdatedState(close)
    LaunchedEffect(kind, inputs) { frameDelay(syncOverlayIdleMillis); latestClose() }
    val adjuster = remember { FocusRequester() }
    val reset = remember { FocusRequester() }
    var adjusterFocused by remember { mutableStateOf(false) }
    var resetFocused by remember { mutableStateOf(false) }
    val press: (Int) -> Unit = { steps -> offsets.shift(kind, steps); inputs++ }
    val clear: () -> Unit = { offsets.reset(kind); inputs++ }
    LaunchedEffect(kind) { withFrameNanos { }; adjuster.requestFocus() }
    val text = (if (tv) 26 else 15) * scale
    val small = (if (tv) 20 else 12) * scale
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Row(Modifier.padding(bottom = bottomInset).padding(horizontal = 16.dp * scale).widthIn(max = (if (tv) 960 else 520).dp * scale).fillMaxWidth()
            .clip(RoundedCornerShape(16.dp * scale)).background(Palette.surface.copy(alpha = .94f))
            .pointerInput(Unit) { detectTapGestures { } }
            .semantics { paneTitle = syncTitle(kind) }
            .padding(start = 16.dp * scale, end = 8.dp * scale, top = 8.dp * scale, bottom = 8.dp * scale),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp * scale)) {
            Column(Modifier.weight(1f)) {
                Text(syncTitle(kind), fontSize = text.sp, lineHeight = (text * 1.25f).sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, color = Palette.text)
                if (value != 0L && value == remembered) Text(SYNC_SAVED, fontSize = small.sp, lineHeight = (small * 1.3f).sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, color = Palette.secondary)
            }
            Row(Modifier.focusRequester(adjuster).onFocusChanged { adjusterFocused = it.isFocused }
                .tvFocusOutline(adjusterFocused && tv, 28.dp * scale, scale, width = 3.dp)
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.DirectionLeft, Key.Minus -> { press(-1); true }
                        Key.DirectionRight, Key.Plus, Key.Equals -> { press(1); true }
                        Key.DirectionDown -> { reset.requestFocus(); inputs++; true }
                        else -> false
                    }
                }.focusable().semantics(mergeDescendants = false) { contentDescription = "${syncTitle(kind)} ${offsetLabel(value)}" },
                verticalAlignment = Alignment.CenterVertically) {
                SyncStepButton(Lucide.Minus, "Earlier", scale, tv) { press(-1) }
                Text(offsetLabel(value), Modifier.widthIn(min = (if (tv) 120 else 64).dp * scale), textAlign = TextAlign.Center,
                    fontSize = text.sp, lineHeight = (text * 1.25f).sp, fontWeight = FontWeight.SemiBold, color = Palette.text, maxLines = 1)
                SyncStepButton(Lucide.Plus, "Later", scale, tv) { press(1) }
            }
            Text("Reset", Modifier.focusRequester(reset).onFocusChanged { resetFocused = it.isFocused }
                .tvFocusOutline(resetFocused && tv, 20.dp * scale, scale, width = 3.dp)
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> { clear(); true }
                        Key.DirectionUp, Key.DirectionLeft -> { adjuster.requestFocus(); inputs++; true }
                        else -> false
                    }
                }
                .clip(RoundedCornerShape(20.dp * scale))
                .pointerClickable(role = Role.Button, shape = RoundedCornerShape(20.dp * scale), onClick = clear)
                .padding(horizontal = 14.dp * scale, vertical = 8.dp * scale),
                fontSize = text.sp, lineHeight = (text * 1.25f).sp, fontWeight = FontWeight.Medium,
                color = if (value == 0L) Palette.tertiary else Palette.accent)
        }
    }
}

@Composable
private fun SyncStepButton(icon: ImageVector, label: String, scale: Float, tv: Boolean, step: () -> Unit) {
    val scope = rememberCoroutineScope()
    val latestStep by rememberUpdatedState(step)
    val size = (if (tv) 64 else 44).dp * scale
    Box(Modifier.size(size).clip(CircleShape).background(Color.White.copy(alpha = .1f))
        .semantics { role = Role.Button; contentDescription = label; onClick { latestStep(); true } }
        .pointerInput(Unit) {
            detectTapGestures(onPress = {
                latestStep()
                val repeat: Job = scope.launch {
                    frameDelay(syncRepeatDelayMillis)
                    while (isActive) { latestStep(); frameDelay(syncRepeatMillis) }
                }
                tryAwaitRelease()
                repeat.cancel()
            })
        }, contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size(size * .45f), tint = Palette.text)
    }
}
