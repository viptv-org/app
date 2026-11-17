package org.viptv.app.features.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.*
import org.viptv.app.components.Button
import org.viptv.app.components.TvAction
import org.viptv.app.features.sources.sourceTags
import org.viptv.app.features.sources.providerLabel
import org.viptv.app.features.sources.qualityLabel
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.SourceCandidate

@androidx.compose.runtime.Immutable
data class PlayerFallbackNotice(val reason: String, val nextLabel: String, val nextFacts: String) {
    val message: String get() = "$reason. Trying the next best source…"
}

internal fun SourceCandidate.fallbackFacts(): String = listOfNotNull(
    qualityLabel(), sourceTags().joinToString(" · ").takeIf { it.isNotEmpty() }, providerLabel(),
).joinToString(" · ")

@Composable
internal fun PlayerFallbackPage(notice: PlayerFallbackNotice, stop: (() -> Unit)?, style: PlayerFailureStyle,
    insets: WindowInsets = WindowInsets(0, 0, 0, 0)) {
    PlayerFailurePage(style, notice.message, description = listOfNotNull(notice.nextLabel, notice.nextFacts).joinToString(" \u00b7 "),
        primary = stop?.let { PlayerFailureAction("Stop", null, it) }, insets = insets)
}

@Composable
internal fun BoxScope.TvAudioNotice(notice: String, scale: Float, nextSource: (() -> Unit)?, requester: FocusRequester) {
    Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 132.dp * scale).widthIn(max = 1100.dp * scale)
        .background(Color(0xFF111113).copy(alpha = .82f), RoundedCornerShape(12.dp * scale))
        .padding(horizontal = 28.dp * scale, vertical = 18.dp * scale)
        .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp * scale)) {
        Text(notice, Modifier.weight(1f, fill = false), color = Palette.text, fontSize = (26 * scale).sp, lineHeight = (34 * scale).sp)
        if (nextSource != null) TvAction(Modifier.heightIn(min = 64.dp * scale), scale, requester = requester, activate = nextSource,
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
