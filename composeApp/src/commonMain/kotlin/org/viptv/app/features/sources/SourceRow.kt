package org.viptv.app.features.sources

import androidx.compose.foundation.background
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Check
import org.viptv.app.components.OwnFocusTreatment
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Lucide
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.SourceCandidate
import org.viptv.app.components.pointerClickable

@Composable
internal fun SourceRow(source: SourceCandidate, television: Boolean, choose: (SourceCandidate) -> Unit, enabled: Boolean = true, selected: Boolean = false,
    header: String? = null, bestMatch: Boolean = false, focusChanged: (Boolean) -> Unit = {}, headerMeasured: (Int) -> Unit = {}) {
    var focused by remember { mutableStateOf(false) }
    val foreground = if (focused) Palette.background else Palette.text
    val interaction = remember { MutableInteractionSource() }
    // The fill is the row's focus treatment; Material's focus layer would double it.
    OwnFocusTreatment { Column(Modifier.fillMaxWidth()
        .onFocusChanged { if (focused != it.isFocused) { focused = it.isFocused; focusChanged(it.isFocused) } }
        .pointerClickable(interaction, indication = null, enabled = enabled) { choose(source) }) {
        header?.let { SourceGroupHeader(it, if (television) 16.sp else 11.sp, Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 6.dp), measured = headerMeasured) }
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(if (television) 14.dp else 0.dp))
        .background(if (focused) Palette.text else if (television || selected) Palette.raised else Palette.surface)
        .indication(interaction, LocalIndication.current)
        .padding(horizontal = if (television) 28.dp else 20.dp, vertical = if (television) 24.dp else 18.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.widthIn(min = 56.dp)) {
            Text(source.qualityLabel() ?: "—", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = foreground,
                maxLines = 1, softWrap = false, overflow = TextOverflow.Clip)
            source.formatLabel()?.let { Text(it, fontSize = 10.sp, maxLines = 1, softWrap = false, color = foreground.copy(alpha = .7f)) }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SourceTitleLine(source.label, TextStyle(fontSize = if (television) 24.sp else 14.sp, fontWeight = FontWeight.SemiBold), foreground,
                source.factBadges(), if (television) SourceBadgeStyle(16.sp, 20.sp, 8.dp, 8.dp, 2.dp, 8.dp) else SourceBadgeStyle(10.sp, 13.sp, 4.dp, 5.dp, 2.dp, 6.dp),
                Modifier.fillMaxWidth(), trailing = if (bestMatch) ({
                    Text(BEST_MATCH_MARKER, color = if (focused) Palette.background else Palette.accent, fontSize = if (television) 16.sp else 11.sp,
                        lineHeight = if (television) 20.sp else 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                }) else null)
            source.releaseDetail()?.let { Text(it, fontSize = if (television) 18.sp else 12.sp, color = foreground) }
            source.technicalDetail()?.let { Text(it, fontSize = if (television) 18.sp else 12.sp,
                color = foreground.copy(alpha = 0.7f)) }
        }
        Icon(if (selected) Lucide.Check else Lucide.ChevronRight, if (selected) "Selected source" else null, Modifier.size(if (television) 24.dp else 18.dp), tint = foreground)
    } } }
}
