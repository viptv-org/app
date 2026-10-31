package org.viptv.app.features.live

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.*
import org.viptv.app.theme.Palette
import org.viptv.app.components.pointerClickable
import org.viptv.app.components.IconButton
import androidx.compose.ui.graphics.RectangleShape

@Composable
internal fun LiveChannelRow(channel: LiveChannel, now: Int?, watch: () -> Unit, favourite: (() -> Unit)?, modifier: Modifier = Modifier) {
    val program = channel.current(now)
    val next = channel.next(now)
    Column {
        Row(modifier.fillMaxWidth().pointerClickable(onClick = watch, shape = RectangleShape).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(Modifier.width(64.dp).height(48.dp), shape = RoundedCornerShape(10.dp), color = Palette.surface) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text(channel.card.title, fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    channel.number?.let { Text(it, fontSize = 10.sp, lineHeight = 14.sp, color = Palette.secondary) }
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(program?.title ?: channel.card.subtitle ?: channel.card.title, fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold)
                if (program != null) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LinearProgressIndicator(progress = { liveProgress(program, now) }, modifier = Modifier.width(54.dp).height(3.dp),
                        color = Palette.text, trackColor = Palette.raised)
                    Text("${liveTime(program.start)}–${liveTime(program.end)}", fontSize = 11.sp, lineHeight = 14.sp, color = Palette.secondary)
                }
                next?.let { Text("Next: ${it.title} ${liveTime(it.start)}", fontSize = 11.sp, lineHeight = 15.sp, color = Palette.secondary, maxLines = 1) }
            }
            favourite?.let { save -> IconButton(save, Modifier.size(28.dp), enabled = !channel.favoritePending) { Icon(Lucide.Star, if (channel.favorite) "Remove favourite channel" else "Favourite channel", Modifier.size(18.dp), tint = if (channel.favorite) Palette.accent else Palette.secondary) } }
        }
        HorizontalDivider(color = Palette.raised)
    }
}
