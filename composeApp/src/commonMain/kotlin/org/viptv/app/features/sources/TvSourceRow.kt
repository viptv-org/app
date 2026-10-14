package org.viptv.app.features.sources

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.SourceCandidate

@Composable
internal fun TvSourceRow(source: SourceCandidate, info: TvSourceInfo?, scale: Float, requester: FocusRequester?,
    enabled: Boolean, choose: (SourceCandidate) -> Unit, up: FocusRequester? = null, focusedSource: (String) -> Unit = {}) {
    var focused by remember { mutableStateOf(false) }
    val color = if (focused) Palette.background else Palette.text
    Row(Modifier.fillMaxWidth().height(104.dp * scale).then(if (requester != null) Modifier.focusRequester(requester) else Modifier)
        .focusProperties { if (up != null) this.up = up }
        .onFocusChanged { if (focused != it.isFocused) focused = it.isFocused; if (it.isFocused) focusedSource(source.id) }.clip(RoundedCornerShape(14.dp * scale))
        .background(if (focused) Palette.text else androidx.compose.ui.graphics.Color(0xFF222222))
        .clickable(enabled = enabled, interactionSource = remember { MutableInteractionSource() }, indication = null) { choose(source) }
        .padding(horizontal = 28.dp * scale), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp * scale)) {
        if (info?.quality != null) Column(Modifier.width(116.dp * scale)) {
            Text(info.quality, color = color, fontSize = (30 * scale).sp, lineHeight = (36 * scale).sp, fontWeight = FontWeight.ExtraBold)
            info.format?.let { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis, color = color.copy(alpha = 0.75f), fontSize = (18 * scale).sp, lineHeight = (22 * scale).sp, fontWeight = FontWeight.SemiBold) }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp * scale)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp * scale)) {
                Text(source.label, Modifier.weight(1f), overflow = TextOverflow.Ellipsis, color = color, fontSize = (28 * scale).sp, lineHeight = (34 * scale).sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                if (info?.bestMatch == true) Text("Best match", Modifier.background(color, RoundedCornerShape(5.dp * scale)).padding(horizontal = 10.dp * scale, vertical = 4.dp * scale),
                    color = if (focused) Palette.text else Palette.background, fontSize = (18 * scale).sp, lineHeight = (20 * scale).sp, fontWeight = FontWeight.Bold)
            }
            source.technicalDetail()?.let { Text(it, color = color.copy(alpha = 0.75f), fontSize = (22 * scale).sp, lineHeight = (28 * scale).sp, maxLines = 1) }
        }
        info?.addon?.let { Text(it, Modifier.widthIn(max = 170.dp * scale), maxLines = 1, overflow = TextOverflow.Ellipsis, color = color.copy(alpha = 0.75f), fontSize = (20 * scale).sp) }
    }
}
