package org.viptv.app.features.live

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.Font
import org.viptv.app.components.TvAction
import org.viptv.app.resources.Res
import org.viptv.app.resources.bricolage
import org.viptv.app.theme.Palette

@Composable
internal fun TvLiveChannelLabel(channel: LiveChannel, scale: Float, first: FocusRequester?, up: FocusRequester?, modifier: Modifier = Modifier,
    focus: () -> Unit, watch: () -> Unit) {
    TvAction(modifier.width(300.dp * scale).height(72.dp * scale).onFocusChanged { if (it.isFocused) focus() }
        .focusProperties { if (up != null) this.up = up }, scale, requester = first, activate = watch,
        background = Color.White.copy(alpha = .06f), cornerRadius = 10.dp * scale,
        contentPadding = PaddingValues(horizontal = 18.dp * scale), arrangement = Arrangement.Start) {
        channel.number?.let { number -> Text(number, Modifier.width(34.dp * scale), fontSize = (22 * scale).sp,
            lineHeight = (26 * scale).sp, color = if (it == Palette.background) it else Palette.secondary, fontWeight = FontWeight.SemiBold) }
        Spacer(Modifier.width(16.dp * scale))
        Text(channel.card.title, fontSize = (30 * scale).sp, lineHeight = (36 * scale).sp, letterSpacing = (-.6f * scale).sp, color = it,
            fontFamily = FontFamily(Font(Res.font.bricolage, FontWeight.ExtraBold)), fontWeight = FontWeight.ExtraBold, maxLines = 1)
    }
}
