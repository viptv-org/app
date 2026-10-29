package org.viptv.app.features.live

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.viptv.app.components.TvAction
import org.viptv.app.theme.Palette

@Composable
internal fun TvLiveChannelRow(channel: LiveChannel, now: Int?, scale: Float, requester: FocusRequester?,
    up: FocusRequester?, focus: () -> Unit, watch: () -> Unit) {
    val program = channel.current(now)
    TvAction(Modifier.fillMaxWidth().height(72.dp * scale)
        .onFocusChanged { if (it.isFocused) focus() }
        .focusProperties { if (up != null) this.up = up }, scale, requester = requester, activate = watch,
        background = Color.White.copy(alpha = .06f), cornerRadius = 10.dp * scale,
        contentPadding = PaddingValues(horizontal = 18.dp * scale), arrangement = Arrangement.Start) { color ->
        val secondary = if (color == Palette.background) Color(0xFF4A4945) else Palette.secondary
        channel.number?.let {
            Text(it, Modifier.width(50.dp * scale), color = secondary, fontSize = (22 * scale).sp,
                lineHeight = (26 * scale).sp, maxLines = 1)
        }
        Text(channel.card.title, Modifier.width(300.dp * scale), color = color, fontSize = (26 * scale).sp,
            lineHeight = (32 * scale).sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(program?.title ?: channel.card.subtitle ?: "Program information unavailable", Modifier.weight(1f),
            color = secondary, fontSize = (24 * scale).sp, lineHeight = (30 * scale).sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        program?.let {
            Text("${liveTime(it.start)} – ${liveTime(it.end)}", Modifier.padding(start = 24.dp * scale), color = secondary,
                fontSize = (22 * scale).sp, lineHeight = (28 * scale).sp, maxLines = 1)
        }
    }
}
