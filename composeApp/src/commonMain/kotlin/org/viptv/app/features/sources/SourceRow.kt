package org.viptv.app.features.sources

import androidx.compose.foundation.background
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Lucide
import org.viptv.app.components.OwnFocusTreatment
import org.viptv.app.components.pointerClickable
import org.viptv.app.components.tvHold
import org.viptv.app.components.PendingActionSpinner
import org.viptv.app.components.isPlayPending
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.SourceCandidate

private val RowQualityFont = 18.sp
private val RowQualitySubline = 11.sp
private val RowNameFont = 15.sp
private val RowDetailFont = 12.sp
private val RowQualityColumn = 57.dp

@Composable
internal fun SourceRow(source: SourceCandidate, television: Boolean, choose: (SourceCandidate) -> Unit, enabled: Boolean = true, selected: Boolean = false,
    header: String? = null, bestMatch: Boolean = false, didntStart: Boolean = false, focusChanged: (Boolean) -> Unit = {}, headerMeasured: (Int) -> Unit = {},
    details: ((SourceCandidate) -> Unit)? = null, requester: FocusRequester? = null, addon: String? = null) {
    var focused by remember { mutableStateOf(false) }
    val inverted = focused || selected
    val foreground = if (inverted) Palette.background else Palette.text
    val interaction = remember { MutableInteractionSource() }
    // The fill is the row's focus treatment; Material's focus layer would double it.
    OwnFocusTreatment { Column(Modifier.fillMaxWidth().then(if (requester != null) Modifier.focusRequester(requester) else Modifier)
        .onFocusChanged { if (focused != it.isFocused) { focused = it.isFocused; focusChanged(it.isFocused) } }
        .tvHold(enabled = details != null) { details?.invoke(source) }
        .pointerClickable(interaction, indication = null, enabled = enabled, longClickLabel = details?.let { SOURCE_DETAILS_LABEL },
            onLongClick = details?.let { { it(source) } }, onClick = { choose(source) })) {
        header?.let { SourceGroupHeader(it, if (television) 16.sp else 11.sp, Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 6.dp), measured = headerMeasured) }
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(if (inverted) Palette.text else if (television) Palette.raised else Palette.surface)
            .indication(interaction, LocalIndication.current)
            .padding(horizontal = if (television) 28.dp else 16.dp, vertical = if (television) 14.dp else 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (television) 20.dp else 13.dp)) {
            Column(Modifier.width(RowQualityColumn)) {
                Text(source.qualityLabel() ?: "—", fontSize = RowQualityFont, fontWeight = FontWeight.ExtraBold, color = foreground,
                    maxLines = 1, softWrap = false)
                source.releaseTypeLabel()?.let { Text(it, fontSize = RowQualitySubline, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false, color = foreground.copy(alpha = .55f)) }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(source.releaseGroupLabel(), fontSize = RowNameFont, fontWeight = FontWeight.SemiBold,
                        color = foreground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    source.cacheStatus()?.let { SourceCacheChip(it, 11.sp, 12.dp, 7.dp, 3.dp, 5.dp, 4.dp) }
                    if (bestMatch) Text(BEST_MATCH_MARKER, Modifier.background(if (inverted) Palette.background else foreground.copy(alpha = .16f), RoundedCornerShape(5.dp)).padding(horizontal = 7.dp, vertical = 3.dp),
                        color = if (inverted) Palette.text else Palette.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                    if (didntStart) Text(DIDNT_START_LABEL, color = if (inverted) Palette.background else Palette.danger, fontSize = 11.sp,
                        fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                }
                SourceAudioSubsLine(source, foreground, RowDetailFont, 11.sp, 12.dp, 6.dp, 20.dp, 6.dp, 1.dp, 4.dp)
                SourceFactsRow(source, addon, foreground, RowDetailFont, 12.dp)
            }
            if (source.isPlayPending()) PendingActionSpinner(Modifier.size(22.dp), size = 22.dp, strokeWidth = 2.dp, color = foreground)
            else if (selected) Box(Modifier.size(22.dp).clip(CircleShape).background(Palette.background), contentAlignment = Alignment.Center) {
                Icon(Lucide.Check, "Selected source", Modifier.size(14.dp), tint = Palette.text)
            }
        }
    } }
}