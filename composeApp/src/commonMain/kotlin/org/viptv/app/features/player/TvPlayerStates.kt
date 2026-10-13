package org.viptv.app.features.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.*
import kotlinx.coroutines.delay
import org.viptv.app.components.TvAction
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.Card

@Composable
internal fun BoxScope.TvPlayerBuffer(title: Card?, extras: TvPlaybackExtras, scale: Float) {
    var showIndicator by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(500); showIndicator = true }
    if (!showIndicator) return
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))
    Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(Modifier.size(72.dp * scale), color = Palette.text, strokeWidth = 5.dp * scale)
        Spacer(Modifier.height(28.dp * scale))
        Text("Starting playback", fontSize = (30 * scale).sp, lineHeight = (36 * scale).sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp * scale))
        Text(extras.bufferLabel ?: listOfNotNull(title?.title, extras.sourceLabel).joinToString(" · "),
            fontSize = (24 * scale).sp, lineHeight = (30 * scale).sp, color = Palette.secondary)
    }
}

@Composable
internal fun BoxScope.TvSkipPrompt(skip: TvSkipRange, scale: Float, activate: () -> Unit) {
    val initial = remember { FocusRequester() }
    LaunchedEffect(skip) { initial.requestFocus() }
    TvAction(Modifier.align(Alignment.BottomEnd).padding(end = 96.dp * scale, bottom = 112.dp * scale).width(280.dp * scale).height(72.dp * scale),
        scale, primary = true, requester = initial, activate = activate) {
        Icon(Lucide.SkipForward, null, Modifier.size(28.dp * scale), it); Spacer(Modifier.width(12.dp * scale))
        Text(skip.label, color = it, fontSize = (26 * scale).sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun BoxScope.TvPlayerFailure(extras: TvPlaybackExtras, position: Long, error: String?, scale: Float,
    retry: (() -> Unit)?, choose: (() -> Unit)?, close: () -> Unit) {
    val initial = remember { FocusRequester() }
    LaunchedEffect(Unit) { initial.requestFocus() }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.75f)))
    Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(88.dp * scale).background(Palette.raised, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Lucide.CircleAlert, null, Modifier.size(44.dp * scale))
        }
        Spacer(Modifier.height(24.dp * scale))
        Text("This source stopped working", fontSize = (48 * scale).sp, lineHeight = (58 * scale).sp,
            letterSpacing = (-0.96f * scale).sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp * scale))
        Text(extras.failureDescription ?: error ?: "Choose another source to continue from ${playbackTime(position)}.",
            fontSize = (28 * scale).sp, lineHeight = (40 * scale).sp, color = Palette.secondary)
        Spacer(Modifier.height(34.dp * scale))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp * scale)) {
            if (retry != null) TvAction(Modifier.width(380.dp * scale).height(72.dp * scale), scale, primary = true, requester = initial, activate = retry) {
                Icon(Lucide.RefreshCw, null, Modifier.size(28.dp * scale), it); Spacer(Modifier.width(12.dp * scale))
                Text(extras.fallbackLabel?.let { name -> "Try $name" } ?: "Retry", color = it, fontSize = (26 * scale).sp, fontWeight = FontWeight.Bold)
            }
            if (choose != null) TvAction(Modifier.width(380.dp * scale).height(72.dp * scale), scale, requester = if (retry == null) initial else null, activate = choose) {
                Icon(Lucide.SlidersHorizontal, null, Modifier.size(28.dp * scale), it); Spacer(Modifier.width(12.dp * scale))
                Text("Choose another source", color = it, fontSize = (26 * scale).sp, fontWeight = FontWeight.Bold)
            }
            TvAction(Modifier.width(200.dp * scale).height(72.dp * scale), scale, requester = if (retry == null && choose == null) initial else null, activate = close) {
                Text("Back", color = it, fontSize = (26 * scale).sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
