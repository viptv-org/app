package org.viptv.app.features.sources

import androidx.compose.foundation.background
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Ellipsis
import com.composables.icons.lucide.Lucide
import org.viptv.app.components.HoverFill
import org.viptv.app.components.LocalPointerHover
import org.viptv.app.components.Motion
import org.viptv.app.components.OwnFocusTreatment
import org.viptv.app.components.holdMenu
import org.viptv.app.components.hoverFill
import org.viptv.app.components.pointerClickable
import org.viptv.app.components.pressScale
import org.viptv.app.components.secondaryClickMenu
import org.viptv.app.components.PendingActionSpinner
import org.viptv.app.components.isPlayPending
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.SourceCandidate

private val RowQualityFont = 18.sp
private val RowQualitySubline = 11.sp
private val RowNameFont = 16.sp
private val RowDetailFont = 12.sp
private val RowQualityColumn = 70.dp

@Composable
internal fun SourceRow(source: SourceCandidate, television: Boolean, choose: (SourceCandidate) -> Unit, enabled: Boolean = true, selected: Boolean = false,
    header: String? = null, bestMatch: Boolean = false, didntStart: Boolean = false, focusChanged: (Boolean) -> Unit = {}, headerMeasured: (Int) -> Unit = {},
    details: ((SourceCandidate) -> Unit)? = null, requester: FocusRequester? = null, addon: String? = null,
    menu: SourceRowMenuActions? = null) {
    var focused by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    val pointer = LocalPointerHover.current
    val inverted = focused || selected
    val foreground = if (inverted) Palette.background else Palette.text
    val interaction = remember { MutableInteractionSource() }
    // The fill is the row's focus treatment; Material's focus layer would double it.
    OwnFocusTreatment { Box(Modifier.fillMaxWidth()) { Column(Modifier.fillMaxWidth().then(if (requester != null) Modifier.focusRequester(requester) else Modifier)
        .secondaryClickMenu(menu != null && pointer && enabled) { menuOpen = true }
        .onFocusChanged { if (focused != it.isFocused) { focused = it.isFocused; focusChanged(it.isFocused) } }
        .pointerClickable(interaction, indication = null, enabled = enabled, onClick = { choose(source) })
        .holdMenu(menu != null && enabled && !pointer && !television) { menuOpen = true }) {
        header?.let { SourceGroupHeader(it, if (television) 16.sp else 11.sp, Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 6.dp), measured = headerMeasured) }
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(0.dp)).heightIn(min = 72.dp)
            .background(if (inverted) Palette.text else if (television) Palette.raised else Color(0xFF222222))
            .indication(interaction, LocalIndication.current)
            .padding(horizontal = if (television) 28.dp else 16.dp, vertical = if (television) 14.dp else 10.dp),
            verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(if (television) 20.dp else 13.dp)) {
            Column(Modifier.width(RowQualityColumn)) {
                Text(source.qualityLabel() ?: "—", fontSize = RowQualityFont, lineHeight = 22.sp, fontWeight = FontWeight.ExtraBold, color = foreground,
                    maxLines = 1, softWrap = false)
                source.releaseTypeLabel()?.let { Text(it, fontSize = RowQualitySubline, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false, color = foreground.copy(alpha = .55f)) }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(source.releaseGroupLabel(), Modifier.weight(1f, fill = false), fontSize = RowNameFont, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold,
                        color = foreground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (bestMatch) Text(BEST_MATCH_MARKER, Modifier.background(if (inverted) Palette.background else foreground.copy(alpha = .16f), RoundedCornerShape(0.dp)).padding(horizontal = 7.dp, vertical = 2.dp),
                        color = if (inverted) Palette.text else Palette.accent, fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                    if (didntStart) Text(DIDNT_START_LABEL, color = if (inverted) Palette.background else Palette.danger, fontSize = 11.sp, lineHeight = 14.sp,
                        fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                }
                Text(source.rowAudioSubs(), fontSize = RowDetailFont, lineHeight = 14.sp, color = foreground.copy(alpha = .65f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(source.factItems(addon).filterNot { it == SEASON_PACK_LABEL }.joinToString(" · "),
                    fontSize = RowDetailFont, lineHeight = 14.sp, color = foreground.copy(alpha = .65f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (source.isPlayPending()) PendingActionSpinner(Modifier.size(22.dp), size = 22.dp, strokeWidth = 2.dp, color = foreground)
            else if (selected) Box(Modifier.size(22.dp).clip(CircleShape).background(Palette.background), contentAlignment = Alignment.Center) {
                Icon(Lucide.Check, "Selected source", Modifier.size(14.dp), tint = Palette.text)
            }
            if (menu != null && pointer && enabled) {
                val more = remember { MutableInteractionSource() }
                Box(Modifier.size(44.dp).pressScale(more, Motion.button).clip(CircleShape)
                    .background(hoverFill(more, Color.Transparent, HoverFill.wash))
                    .pointerClickable(interactionSource = more, indication = null, role = Role.Button,
                        onClickLabel = SOURCE_MENU_TARGET_LABEL, shape = CircleShape) { menuOpen = true }
                    .semantics { contentDescription = SOURCE_MENU_TARGET_LABEL }, contentAlignment = Alignment.Center) {
                    Icon(Lucide.Ellipsis, null, Modifier.size(20.dp), tint = foreground)
                }
            }
        }
    }
    if (menu != null) SourceRowMenu(menu, menuOpen, onDismiss = { menuOpen = false },
        onPlay = { choose(source) }, onDetails = details?.let { { it(source) } })
    } }
}
