package org.viptv.app.features.live

import org.viptv.app.components.FocusLazyColumn as LazyColumn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.viptv.app.components.AutomaticPage
import org.viptv.app.theme.Palette
import org.viptv.app.components.pointerClickable

@Composable
internal fun LiveGuide(channels: List<LiveChannel>, start: Int, now: Int?, television: Boolean, scale: Float,
    select: (LiveChannel, LiveProgram?) -> Unit, watch: (LiveChannel) -> Unit,
    first: FocusRequester? = null, up: FocusRequester? = null, more: (() -> Unit)? = null, busy: Boolean = false) {
    val rows = rememberLazyListState()
    AutomaticPage(channels.size, busy, more) { rows.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
    val span = if (television) 120 else 150
    BoxWithConstraints(Modifier.fillMaxWidth().clipToBounds()) {
        val channelWidth = if (television) 300.dp * scale else 192.dp
        val gap = (if (television) 28 else 8).dp * scale
        val unit = (maxWidth - channelWidth - gap + if (television) 4.dp * scale else 0.dp) / span
        val initialProgram = channels.firstOrNull()?.let { channel ->
            val visible = channel.programs.filter { it.end > start && it.start < start + span }
            visible.find { now != null && now in it.start until it.end } ?: visible.firstOrNull()
        }
        val rowHeight = if (television) 72.dp * scale else 56.dp
        val headerHeight = (if (television) 44 else 34).dp * scale
        val spacing = (if (television) 8 else 6).dp * scale
        val programGap = if (television) 8.dp * scale else gap / 2
        Column {
            Row(Modifier.fillMaxWidth().height(headerHeight)) {
                Spacer(Modifier.width(channelWidth + gap))
                Box(Modifier.weight(1f)) {
                    repeat(if (television) 4 else 5) { index -> Text(liveTime(start + index * 30, period = index == 0), Modifier.offset(x = unit * index * 30 - if (television) 4.dp * scale else 0.dp),
                        fontSize = (if (television) 20 * scale else 12f).sp,
                        lineHeight = (if (television) 24 * scale else 16f).sp, color = Palette.secondary,
                        fontWeight = if (television) FontWeight.SemiBold else FontWeight.Normal) }
                }
            }
            LazyColumn(state = rows, verticalArrangement = Arrangement.spacedBy(spacing)) {
                items(channels, key = { it.card.provider_id + ":" + it.card.identity.id }) { channel ->
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        if (television) TvLiveChannelLabel(channel, scale,
                            if (channel == channels.firstOrNull() && initialProgram == null) first else null,
                            if (channel == channels.firstOrNull()) up else null, { select(channel, channel.current(now)) }, { watch(channel) })
                        else
                        Row(Modifier.width(channelWidth).height(rowHeight).clip(RoundedCornerShape(8.dp * scale))
                            .background(Palette.surface).pointerClickable (shape = RoundedCornerShape(8.dp * scale)) { watch(channel) }.padding(horizontal = (if (television) 18 else 12).dp * scale),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp * scale)) {
                            channel.number?.let { Text(it, fontSize = (if (television) 20 * scale else 12f).sp, color = Palette.secondary) }
                            Text(channel.card.title, fontSize = (if (television) 26 * scale else 14f).sp, fontWeight = FontWeight.Bold)
                        }
                        Box(Modifier.weight(1f).height(rowHeight).clipToBounds()) {
                            channel.programs.forEach { program ->
                                val begin = maxOf(start, program.start)
                                val end = minOf(start + span, program.end)
                                if (end > begin) {
                                    key(program.id) {
                                        LiveProgramCell(program, now, start, television, scale,
                                            Modifier.offset(x = unit * (begin - start)).width((unit * (end - begin) - programGap).coerceAtLeast(1.dp)).fillMaxHeight()
                                                .then(if (first != null && channel == channels.firstOrNull() && program == initialProgram) Modifier.focusRequester(first) else Modifier)
                                                .focusProperties { if (up != null && channel == channels.firstOrNull()) this.up = up },
                                            { select(channel, program) }, { if (television) watch(channel) else select(channel, program) })
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (now != null && now in start until start + span) {
            val x = channelWidth + gap + unit * (now - start) - if (television) 5.dp * scale else 0.dp
            val bodyHeight = ((rowHeight + spacing) * channels.size - spacing).coerceAtLeast(0.dp)
            val markerTop = if (television) headerHeight - 10.dp * scale else headerHeight
            val markerHeight = minOf(bodyHeight + if (television) 12.dp * scale else 0.dp,
                (maxHeight - markerTop).coerceAtLeast(0.dp))
            val color = if (television) Color(0xFFE5484D) else Palette.danger
            Box(Modifier.offset(x = x, y = markerTop).width(2.dp * scale).height(markerHeight).background(color))
            if (television) Box(Modifier.offset(x = x - 5.dp * scale, y = headerHeight - 16.dp * scale)
                .size(12.dp * scale).background(color, CircleShape))
        }
    }
}
