package org.viptv.app.features.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.*
import org.viptv.app.components.Button
import org.viptv.app.components.TvAction
import org.viptv.app.components.rememberArriveFade
import org.viptv.app.features.sources.factBadges
import org.viptv.app.features.sources.providerLabel
import org.viptv.app.features.sources.qualityLabel
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.SourceCandidate

@androidx.compose.runtime.Immutable
data class PlayerFallbackNotice(val reason: String, val nextLabel: String, val nextFacts: String) {
    val message: String get() = "$reason. Trying the next best source…"
}

internal fun SourceCandidate.fallbackFacts(): String = listOfNotNull(
    qualityLabel(), factBadges().joinToString(" · ").takeIf { it.isNotEmpty() }, providerLabel(),
).joinToString(" · ")

@Composable
internal fun BoxScope.TvPlayerFallback(notice: PlayerFallbackNotice, scale: Float, stop: (() -> Unit)?) {
    val initial = remember { FocusRequester() }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .85f)))
    Column(Modifier.align(Alignment.Center).widthIn(max = 1400.dp * scale).padding(horizontal = 48.dp * scale)
        .semantics { liveRegion = LiveRegionMode.Assertive }, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(notice.message, fontSize = (48 * scale).sp, lineHeight = (55.2f * scale).sp, letterSpacing = (-.96f * scale).sp,
            fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(18.dp * scale))
        Text(listOfNotNull(notice.nextLabel, notice.nextFacts).joinToString(" · "),
            fontSize = (28 * scale).sp, lineHeight = (40.6f * scale).sp, color = Palette.secondary, textAlign = TextAlign.Center)
        if (stop != null) {
            Spacer(Modifier.height(36.dp * scale))
            TvAction(Modifier.width(280.dp * scale).height(72.dp * scale), scale, primary = true, requester = initial, activate = stop) { ink ->
                Text("Stop", color = ink, fontSize = (26 * scale).sp, lineHeight = (37.7f * scale).sp, fontWeight = FontWeight.Bold)
            }
        }
    }
    LaunchedEffect(Unit) { if (stop != null) initial.requestFocus() }
}

@Composable
internal fun PhonePlayerFallback(notice: PlayerFallbackNotice, stop: (() -> Unit)?, insets: WindowInsets) {
    val initial = remember { FocusRequester() }
    val reveal = rememberArriveFade()
    Box(Modifier.fillMaxSize().alpha(reveal).background(Color.Black.copy(alpha = .8f)).windowInsetsPadding(insets)) {
        Column(Modifier.align(Alignment.Center).widthIn(max = 650.dp).padding(horizontal = 24.dp)
            .semantics { liveRegion = LiveRegionMode.Assertive },
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(Lucide.CircleAlert, null, Modifier.size(32.dp))
            Text(notice.message, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text(listOfNotNull(notice.nextLabel, notice.nextFacts).joinToString(" · "),
                fontSize = 13.sp, color = Palette.secondary, textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis)
            if (stop != null) Button(stop, Modifier.height(44.dp).focusRequester(initial), shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Palette.text, contentColor = Palette.background)) {
                Text("Stop", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
    LaunchedEffect(Unit) { if (stop != null) initial.requestFocus() }
}

@Composable
internal fun BoxScope.TvAudioNotice(notice: String, scale: Float, nextSource: (() -> Unit)?, requester: FocusRequester) {
    Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 132.dp * scale).widthIn(max = 1100.dp * scale)
        .background(Color(0xFF111113).copy(alpha = .82f), RoundedCornerShape(12.dp * scale))
        .padding(horizontal = 28.dp * scale, vertical = 18.dp * scale)
        .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp * scale)) {
        Text(notice, Modifier.weight(1f, fill = false), color = Palette.text, fontSize = (26 * scale).sp, lineHeight = (34 * scale).sp)
        if (nextSource != null) TvAction(Modifier.height(64.dp * scale), scale, requester = requester, activate = nextSource,
            contentPadding = PaddingValues(horizontal = 24.dp * scale)) { ink ->
            Text("Next source", color = ink, fontSize = (24 * scale).sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
internal fun BoxScope.PhoneAudioNotice(notice: String, insets: WindowInsets, nextSource: (() -> Unit)?) {
    Row(Modifier.align(Alignment.BottomCenter).windowInsetsPadding(insets).padding(bottom = 96.dp, start = 16.dp, end = 16.dp)
        .widthIn(max = 650.dp).background(Color(0xFF111113).copy(alpha = .86f), RoundedCornerShape(12.dp))
        .padding(horizontal = 14.dp, vertical = 10.dp).semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(notice, Modifier.weight(1f, fill = false), color = Palette.text, fontSize = 13.sp)
        if (nextSource != null) Button(nextSource, Modifier.height(36.dp), shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Palette.text, contentColor = Palette.background),
            contentPadding = PaddingValues(horizontal = 12.dp)) {
            Text("Next source", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}
