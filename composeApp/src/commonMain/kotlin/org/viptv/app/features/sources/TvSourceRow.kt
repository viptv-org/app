package org.viptv.app.features.sources

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.SourceCandidate
import org.viptv.app.components.pointerClickable

@Composable
internal fun TvSourceRow(source: SourceCandidate, info: TvSourceInfo?, scale: Float, requester: FocusRequester?,
    enabled: Boolean, choose: (SourceCandidate) -> Unit, up: FocusRequester? = null, focusedSource: (String) -> Unit = {},
    header: String? = null, bestMatch: Boolean = false, headerMeasured: (Int) -> Unit = {}) {
    var focused by remember { mutableStateOf(false) }
    val color = if (focused) Palette.background else Palette.text
    Column(Modifier.fillMaxWidth().then(if (requester != null) Modifier.focusRequester(requester) else Modifier)
        .focusProperties { if (up != null) this.up = up }
        .onFocusChanged { if (focused != it.isFocused) focused = it.isFocused; if (it.isFocused) focusedSource(source.id) }
        .pointerClickable(enabled = enabled, interactionSource = remember { MutableInteractionSource() }, indication = null) { choose(source) }) {
    header?.let { SourceGroupHeader(it, (22 * scale).sp, Modifier.padding(start = 4.dp * scale, top = 14.dp * scale, bottom = 12.dp * scale), measured = headerMeasured) }
    Row(Modifier.fillMaxWidth().heightIn(min = 104.dp * scale).clip(RoundedCornerShape(14.dp * scale))
        .background(if (focused) Palette.text else androidx.compose.ui.graphics.Color(0xFF222222))
        .padding(horizontal = 28.dp * scale, vertical = 20.dp * scale), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp * scale)) {
        Column(Modifier.width(116.dp * scale)) {
            Text(info?.quality ?: "—", color = color, fontSize = (30 * scale).sp, lineHeight = (36 * scale).sp, fontWeight = FontWeight.ExtraBold)
            info?.format?.let { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis, color = color.copy(alpha = 0.75f), fontSize = (18 * scale).sp, lineHeight = (22 * scale).sp, fontWeight = FontWeight.SemiBold) }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp * scale)) {
            SourceTitleLine(source.label, TextStyle(fontSize = (28 * scale).sp, lineHeight = (34 * scale).sp, fontWeight = FontWeight.SemiBold), color,
                source.factBadges(), SourceBadgeStyle((18 * scale).sp, (20 * scale).sp, radius = 8.dp * scale, horizontal = 10.dp * scale,
                    vertical = 4.dp * scale, gap = 12.dp * scale), Modifier.fillMaxWidth(),
                trailing = if (bestMatch) ({ Text(BEST_MATCH_MARKER, Modifier.background(color, RoundedCornerShape(5.dp * scale)).padding(horizontal = 10.dp * scale, vertical = 4.dp * scale),
                    color = if (focused) Palette.text else Palette.background, fontSize = (18 * scale).sp, lineHeight = (20 * scale).sp, fontWeight = FontWeight.Bold) }) else null)
            source.releaseDetail()?.let { Text(it, color = color, fontSize = (22 * scale).sp, lineHeight = (28 * scale).sp) }
            source.technicalDetail()?.let { Text(it, color = color.copy(alpha = 0.75f), fontSize = (22 * scale).sp, lineHeight = (28 * scale).sp) }
        }
        info?.addon?.let { Text(it, Modifier.widthIn(max = 170.dp * scale), maxLines = 1, overflow = TextOverflow.Ellipsis, color = color.copy(alpha = 0.75f), fontSize = (20 * scale).sp) }
    }
    }
}
