package org.viptv.app.features.live

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.Font
import org.viptv.app.components.TvAction
import org.viptv.app.components.ClampedText
import org.viptv.app.resources.Res
import org.viptv.app.resources.bricolage
import org.viptv.app.theme.Palette

private val NumberColumnFloor = 34.dp

// The number column is as wide as the widest number in the guide, so names line up and no number wraps whatever its digits.
@Composable
internal fun liveGuideNumberWidth(channels: List<LiveChannel>, television: Boolean, scale: Float): Dp {
    if (!television) return 0.dp
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val style = remember(scale) { TextStyle(fontSize = (22 * scale).sp, lineHeight = (26 * scale).sp, fontWeight = FontWeight.SemiBold) }
    val numbers = channels.mapNotNull { it.number }
    return remember(measurer, density, style, numbers) {
        val widest = numbers.maxOfOrNull { measurer.measure(it, style, softWrap = false).size.width } ?: 0
        maxOf(NumberColumnFloor * scale, with(density) { widest.toDp() } + 1.dp)
    }
}

@Composable
internal fun TvLiveChannelLabel(channel: LiveChannel, scale: Float, numberWidth: Dp, first: FocusRequester?, up: FocusRequester?, modifier: Modifier = Modifier,
    focus: () -> Unit, watch: () -> Unit) {
    TvAction(modifier.width(300.dp * scale).heightIn(min = 72.dp * scale).onFocusChanged { if (it.isFocused) focus() }
        .focusProperties { if (up != null) this.up = up }, scale, requester = first, activate = watch,
        background = Color.White.copy(alpha = .06f), cornerRadius = 10.dp * scale,
        contentPadding = PaddingValues(horizontal = 18.dp * scale), arrangement = Arrangement.Start) {
        channel.number?.let { number -> Text(number, Modifier.width(numberWidth), fontSize = (22 * scale).sp,
            lineHeight = (26 * scale).sp, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip,
            color = if (it == Palette.background) it else Palette.secondary, fontWeight = FontWeight.SemiBold) }
        Spacer(Modifier.width(16.dp * scale))
        ClampedText(channel.card.title, Modifier.weight(1f), fontSize = (30 * scale).sp, lineHeight = (36 * scale).sp, letterSpacing = (-.6f * scale).sp, color = it,
            fontFamily = FontFamily(Font(Res.font.bricolage, FontWeight.ExtraBold)), fontWeight = FontWeight.ExtraBold, maxLines = 1)
    }
}
