package org.viptv.app.features.sources

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Lucide
import org.viptv.app.components.pointerClickable
import org.viptv.app.components.tvHold
import org.viptv.app.components.PendingActionSpinner
import org.viptv.app.components.isPlayPending
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.SourceCandidate

private val TvQualitySize = 30.sp
private val TvQualityLine = 36.sp
private val TvQualityColumn = 110.dp
private val TvQualityWidths = listOf("1080p", "4K", "—")
private val TvNameSize = 26.sp
private val TvNameLine = 34.sp
private val TvDetailSize = 18.sp
private val TvDetailLine = 24.sp
private val TvTagHeight = 30.dp
private val TvSourceRowHeight = 112.dp

@Composable
internal fun tvSourceRowHeight(scale: Float): Dp = TvSourceRowHeight * scale

@Composable
private fun tvQualityColumnWidth(scale: Float): Dp {
    val measurer = rememberTextMeasurer()
    val style = LocalTextStyle.current.merge(TextStyle(fontSize = (TvQualitySize.value * scale).sp, lineHeight = (TvQualityLine.value * scale).sp,
        fontWeight = FontWeight.ExtraBold))
    val widest = TvQualityWidths.maxOf { measurer.measure(it, style, maxLines = 1, softWrap = false).size.width }
    return maxOf(TvQualityColumn * scale, with(LocalDensity.current) { widest.toDp() })
}

@Composable
internal fun TvSourceRow(source: SourceCandidate, info: TvSourceInfo?, scale: Float, requester: FocusRequester?,
    enabled: Boolean, choose: (SourceCandidate) -> Unit, up: FocusRequester? = null, focusedSource: (String) -> Unit = {},
    header: String? = null, bestMatch: Boolean = false, didntStart: Boolean = false, headerMeasured: (Int) -> Unit = {},
    details: ((SourceCandidate) -> Unit)? = null, menu: SourceRowMenuActions? = null) {
    var focused by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    val inverted = focused
    val color = if (inverted) Palette.background else Palette.text
    val interaction = remember { MutableInteractionSource() }
    Box(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().then(if (requester != null) Modifier.focusRequester(requester) else Modifier)
            .focusProperties { if (up != null) this.up = up }
            .onFocusChanged { if (focused != it.isFocused) focused = it.isFocused; if (it.isFocused) focusedSource(source.id) }
            .tvHold(enabled = menu != null && enabled) { menuOpen = true }
            .pointerClickable(interaction, indication = null, enabled = enabled, onClick = { choose(source) })) {
            header?.let { SourceGroupHeader(it, (22 * scale).sp, Modifier.padding(start = 4.dp * scale, top = 14.dp * scale, bottom = 12.dp * scale), measured = headerMeasured) }
            Row(Modifier.fillMaxWidth().heightIn(min = tvSourceRowHeight(scale)).clip(RoundedCornerShape(12.dp * scale))
                .background(if (inverted) Palette.text else androidx.compose.ui.graphics.Color(0xFF222222))
                .padding(horizontal = 28.dp * scale, vertical = 14.dp * scale), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(26.dp * scale)) {
                Column(Modifier.width(tvQualityColumnWidth(scale))) {
                    Text(info?.quality ?: "—", color = color, fontSize = (TvQualitySize.value * scale).sp, lineHeight = (TvQualityLine.value * scale).sp,
                        fontWeight = FontWeight.ExtraBold, maxLines = 1, softWrap = false)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp * scale)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp * scale), verticalArrangement = Arrangement.spacedBy(6.dp * scale)) {
                        Text(source.providerLabel(), fontSize = (TvNameSize.value * scale).sp, lineHeight = (TvNameLine.value * scale).sp,
                            fontWeight = FontWeight.SemiBold, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        source.cacheStatus()?.let { SourceCacheChip(it, (16 * scale).sp, (14 * scale).dp, (9 * scale).dp, (4 * scale).dp, (6 * scale).dp, (5 * scale).dp) }
                        if (bestMatch) Text(BEST_MATCH_MARKER, Modifier.background(if (inverted) Palette.background else color.copy(alpha = 0.16f), RoundedCornerShape(6.dp * scale)).padding(horizontal = 10.dp * scale, vertical = 4.dp * scale),
                            color = if (inverted) Palette.text else Palette.accent, fontSize = (18 * scale).sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                        if (didntStart) Text(DIDNT_START_LABEL, color = if (inverted) Palette.background else Palette.danger,
                            fontSize = (18 * scale).sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                    }
                    SourceTextWindow(source.addonText(), active = focused,
                        style = LocalTextStyle.current.merge(TextStyle(fontSize = (TvDetailSize.value * scale).sp, lineHeight = (TvDetailLine.value * scale).sp,
                            color = color.copy(alpha = .75f))))
                    SourceTagLine(source, color, (15 * scale).sp, TvTagHeight * scale, (10 * scale).dp, (8 * scale).dp, (1.5 * scale).dp, (6 * scale).dp)
                }
                if (source.isPlayPending()) PendingActionSpinner(Modifier.size(28.dp * scale), size = 28.dp * scale, strokeWidth = 3.dp * scale, color = color)
                else if (inverted) Box(Modifier.size(28.dp * scale).clip(CircleShape).background(Palette.background), contentAlignment = Alignment.Center) {
                    Icon(Lucide.Check, "Selected source", Modifier.size(18.dp * scale), tint = Palette.text)
                }
            }
        }
        if (menu != null) SourceRowMenu(menu, menuOpen, scale = scale, television = true, onDismiss = { menuOpen = false },
            onPlay = { choose(source) }, onDetails = details?.let { { it(source) } })
    }
}
