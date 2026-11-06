package org.viptv.app.features.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.viptv.app.components.Button
import org.viptv.app.components.TextButton
import org.viptv.app.components.TvAction
import org.viptv.app.player.slateNoticeDetail
import org.viptv.app.theme.Palette

internal const val SLATE_NOTICE_TITLE = "This source might not be working."
internal const val slateNoticeVisibleMillis = 30_000L

// Phone, tablet, desktop and web: bottom centre, above the control layer.
@Composable
internal fun BoxScope.PlayerSlateNotice(provider: String?, phone: Boolean, insets: WindowInsets, bottomInset: Dp,
    configure: (() -> Unit)?, choose: (() -> Unit)?) {
    val padded = if (phone) Modifier.windowInsetsPadding(insets) else Modifier
    Column(padded.align(Alignment.BottomCenter).padding(bottom = bottomInset, start = 16.dp, end = 16.dp)
        .widthIn(max = if (phone) 650.dp else 560.dp)
        .background(Palette.raised.copy(alpha = .86f), RoundedCornerShape(12.dp))
        .padding(horizontal = if (phone) 14.dp else 18.dp, vertical = if (phone) 10.dp else 14.dp)
        .semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(if (phone) 2.dp else 4.dp)) {
        Text(SLATE_NOTICE_TITLE, color = Palette.text, fontSize = if (phone) 13.sp else 14.sp, fontWeight = FontWeight.SemiBold)
        Text(slateNoticeDetail(provider), color = Palette.secondary, fontSize = if (phone) 12.sp else 13.sp,
            maxLines = if (phone) 2 else 1, overflow = TextOverflow.Ellipsis)
        if (phone) Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (choose != null) Button(choose, Modifier.fillMaxWidth().height(44.dp), shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Palette.text, contentColor = Palette.background),
                contentPadding = PaddingValues(horizontal = 12.dp)) {
                Text("Choose another source", fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
            if (configure != null) TextButton(configure, Modifier.fillMaxWidth().height(44.dp)) {
                Text("Configure source", fontSize = 13.sp, color = Palette.secondary, maxLines = 1)
            }
        } else Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (configure != null) TextButton(configure, Modifier.height(44.dp),
                contentPadding = PaddingValues(horizontal = 8.dp)) { Text("Configure source", fontSize = 12.sp, color = Palette.secondary, maxLines = 1) }
            if (choose != null) Button(choose, Modifier.height(44.dp), shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Palette.text, contentColor = Palette.background),
                contentPadding = PaddingValues(horizontal = 12.dp)) {
                Text("Choose another source", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }
    }
}

// A television has no pointer: both actions are D-pad targets, and the notice never takes focus by itself.
@Composable
internal fun BoxScope.TvSlateNotice(provider: String?, scale: Float, configure: (() -> Unit)?, choose: (() -> Unit)?,
    configureRequester: FocusRequester, chooseRequester: FocusRequester) {
    Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 264.dp * scale).widthIn(max = 1100.dp * scale)
        .background(Color(0xFF111113).copy(alpha = .82f), RoundedCornerShape(12.dp * scale))
        .padding(horizontal = 28.dp * scale, vertical = 18.dp * scale)
        .semantics { liveRegion = LiveRegionMode.Polite }) {
        Text(SLATE_NOTICE_TITLE, color = Palette.text, fontSize = (26 * scale).sp, lineHeight = (34 * scale).sp)
        Text(slateNoticeDetail(provider), color = Palette.secondary, fontSize = (22 * scale).sp, lineHeight = (30 * scale).sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(Modifier.padding(top = 12.dp * scale), horizontalArrangement = Arrangement.spacedBy(16.dp * scale),
            verticalAlignment = Alignment.CenterVertically) {
            if (configure != null) TvAction(Modifier.heightIn(min = 64.dp * scale), scale, requester = configureRequester,
                activate = configure, contentPadding = PaddingValues(horizontal = 24.dp * scale)) { ink ->
                Text("Configure source", color = ink, fontSize = (24 * scale).sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
            if (choose != null) TvAction(Modifier.heightIn(min = 64.dp * scale), scale, primary = true, requester = chooseRequester,
                activate = choose, contentPadding = PaddingValues(horizontal = 24.dp * scale)) { ink ->
                Text("Choose another source", color = ink, fontSize = (24 * scale).sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }
    }
}