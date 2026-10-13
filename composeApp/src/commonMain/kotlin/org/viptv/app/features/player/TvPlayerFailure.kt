package org.viptv.app.features.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.*
import org.viptv.app.components.TvAction
import org.viptv.app.theme.Palette

@Composable
internal fun BoxScope.TvPlayerFailure(extras: TvPlaybackExtras, position: Long, error: String?, scale: Float,
    retry: (() -> Unit)?, choose: (() -> Unit)?, close: () -> Unit) {
    val initial = remember { FocusRequester() }
    LaunchedEffect(Unit) { initial.requestFocus() }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .85f)))
    Column(Modifier.align(Alignment.Center).semantics { liveRegion = LiveRegionMode.Assertive }, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(88.dp * scale).background(Color.White.copy(alpha = .12f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(Lucide.CircleAlert, null, Modifier.size(44.dp * scale), tint = Palette.text)
        }
        Spacer(Modifier.height(18.dp * scale))
        Text("This source stopped working", Modifier.height(55.2.dp * scale).wrapContentHeight(Alignment.CenterVertically, unbounded = true),
            fontSize = (48 * scale).sp, lineHeight = (55.2f * scale).sp,
            letterSpacing = (-.96f * scale).sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(18.dp * scale))
        Text(extras.failureDescription ?: error ?: "Choose another source to continue from ${playbackTime(position)}.",
            fontSize = (28 * scale).sp, lineHeight = (40.6f * scale).sp, color = Palette.secondary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(36.dp * scale))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp * scale)) {
            if (retry != null) TvAction(Modifier.width(380.dp * scale).height(72.dp * scale)
                .dropShadow(RoundedCornerShape(12.dp * scale), Shadow(radius = 50.dp * scale,
                    color = Color.Black.copy(alpha = .45f), offset = DpOffset(0.dp, 18.dp * scale))),
                scale, primary = true, requester = initial, activate = retry) {
                Icon(Lucide.RefreshCw, null, Modifier.size(28.dp * scale), it); Spacer(Modifier.width(12.dp * scale))
                Text(extras.fallbackLabel?.let { name -> "Try $name" } ?: "Retry", color = it,
                    fontSize = (26 * scale).sp, lineHeight = (37.7f * scale).sp, fontWeight = FontWeight.Bold)
            }
            if (choose != null) TvAction(Modifier.width(380.dp * scale).height(72.dp * scale), scale,
                requester = if (retry == null) initial else null, activate = choose, background = Color.White.copy(alpha = .16f)) {
                Icon(Lucide.SlidersHorizontal, null, Modifier.size(28.dp * scale), it); Spacer(Modifier.width(12.dp * scale))
                Text("Choose another source", color = it, fontSize = (26 * scale).sp, lineHeight = (37.7f * scale).sp, fontWeight = FontWeight.Bold)
            }
            TvAction(Modifier.width(200.dp * scale).height(72.dp * scale), scale,
                requester = if (retry == null && choose == null) initial else null, activate = close, background = Color.White.copy(alpha = .16f)) {
                Text("Back", color = it, fontSize = (26 * scale).sp, lineHeight = (37.7f * scale).sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
