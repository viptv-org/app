package org.viptv.app.features.live

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.viptv.app.theme.Palette

@Composable
internal fun LiveGuide(channels: List<LiveChannel>, start: Int, now: Int?, television: Boolean, scale: Float,
    select: (LiveChannel, LiveProgram?) -> Unit, watch: (LiveChannel) -> Unit) {
    val span = 150
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val channelWidth = if (television) 300.dp * scale else 192.dp
        val gap = 8.dp * scale
        val unit = (maxWidth - channelWidth - gap) / span
        val rowHeight = if (television) 72.dp * scale else 56.dp
        Column {
            Row(Modifier.fillMaxWidth().height(34.dp * scale)) {
                Spacer(Modifier.width(channelWidth + gap))
                Box(Modifier.weight(1f)) {
                    repeat(5) { index -> Text(liveTime(start + index * 30, period = index == 0), Modifier.offset(x = unit * index * 30),
                        fontSize = (if (television) 20 * scale else 12f).sp, color = Palette.secondary) }
                }
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp * scale)) {
                items(channels, key = { it.card.provider_id + ":" + it.card.identity.id }) { channel ->
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        Row(Modifier.width(channelWidth).height(rowHeight).clip(RoundedCornerShape(8.dp * scale))
                            .background(Palette.surface).clickable { watch(channel) }.padding(horizontal = 12.dp * scale),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp * scale)) {
                            channel.number?.let { Text(it, fontSize = (if (television) 20 * scale else 12f).sp, color = Palette.secondary) }
                            Text(channel.card.title, fontSize = (if (television) 26 * scale else 14f).sp, fontWeight = FontWeight.Bold)
                        }
                        Box(Modifier.weight(1f).height(rowHeight).clipToBounds()) {
                            channel.programs.forEach { program ->
                                val begin = maxOf(start, program.start)
                                val end = minOf(start + span, program.end)
                                if (end > begin) {
                                    var focused by remember { mutableStateOf(false) }
                                    Column(Modifier.offset(x = unit * (begin - start)).width(unit * (end - begin) - gap / 2).fillMaxHeight()
                                        .clip(RoundedCornerShape(8.dp * scale)).background(if (focused) Palette.text else Palette.raised)
                                        .onFocusChanged { focused = it.isFocused; if (it.isFocused) select(channel, program) }
                                        .clickable { select(channel, program) }.padding(12.dp * scale), verticalArrangement = Arrangement.Center) {
                                        Text(program.title, fontSize = (if (television) 22 * scale else 12f).sp, fontWeight = FontWeight.SemiBold,
                                            color = if (focused) Palette.background else Palette.text, maxLines = 1)
                                        if (!television) Text(liveTime(program.start), fontSize = 10.sp, color = Palette.secondary)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (now != null && now in start..start + span) Box(Modifier.padding(top = 34.dp * scale)
            .offset(x = channelWidth + gap + unit * (now - start)).width(2.dp * scale).fillMaxHeight().background(Palette.danger))
    }
}

