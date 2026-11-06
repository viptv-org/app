package org.viptv.app.features.live

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.composables.icons.lucide.*
import org.viptv.app.theme.Palette
import org.viptv.app.components.pointerClickable
import org.viptv.app.components.ClampedText
import org.viptv.app.components.AccessibleIconButton
import org.viptv.app.components.favouriteStarPainter
import org.viptv.app.components.MutationFailure
import org.viptv.app.components.LocalPendingMutation
import org.viptv.app.state.liveFavoriteTarget
import androidx.compose.ui.graphics.RectangleShape

@Composable
internal fun LiveChannelRow(channel: LiveChannel, now: Int?, watch: () -> Unit, favourite: (() -> Unit)?, modifier: Modifier = Modifier) {
    val program = channel.current(now)
    val next = channel.next(now)
    val mutations = LocalPendingMutation.current
    val target = liveFavoriteTarget(channel.id)
    val pending = mutations.isPending(target)
    Column {
        Row(modifier.fillMaxWidth().pointerClickable(onClick = watch, shape = RectangleShape).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(Modifier.width(64.dp).height(48.dp), shape = RoundedCornerShape(10.dp), color = Palette.surface) {
                Column(Modifier.fillMaxSize().padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    val art = channel.card.landscape ?: channel.card.poster
                    if (art != null) AsyncImage(art, null, Modifier.weight(1f).fillMaxWidth(), contentScale = ContentScale.Fit)
                    else liveChannelMonogram(channel.card.title)?.let {
                        Text(it, fontSize = 15.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold, color = Palette.secondary, maxLines = 1)
                    }
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
                next?.let { ClampedText("Next: ${it.title} ${liveTime(it.start)}", fontSize = 11.sp, lineHeight = 15.sp, color = Palette.secondary, maxLines = 1) }
            }
            favourite?.let { save -> AccessibleIconButton(favouriteStarPainter(channel.favorite),
                if (channel.favorite) "Remove favourite channel" else "Favourite channel", save,
                enabled = !pending, iconSize = 18.dp, tint = if (channel.favorite) Palette.accent else Palette.secondary, wash = null, pending = pending) }
        }
        MutationFailure(target, Modifier.padding(start = 76.dp, bottom = 8.dp))
        HorizontalDivider(color = Palette.raised)
    }
}
