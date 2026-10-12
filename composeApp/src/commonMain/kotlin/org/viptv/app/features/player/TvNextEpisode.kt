package org.viptv.app.features.player

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.painterResource
import org.viptv.app.components.TvAction
import org.viptv.app.components.MediaMetadata
import org.viptv.app.resources.Res
import org.viptv.app.resources.ic_play_zip
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.Card

@Composable
internal fun TvNextEpisode(current: Card?, next: Card, scale: Float, play: () -> Unit, keepWatching: () -> Unit) {
    val initial = remember { FocusRequester() }
    LaunchedEffect(Unit) { initial.requestFocus() }
    Box(Modifier.fillMaxSize()) {
        Text(listOfNotNull(current?.title, "Credits").joinToString(" · "), Modifier.offset(96.dp * scale, 612.dp * scale),
            fontSize = (22 * scale).sp, lineHeight = (32 * scale).sp, color = Palette.secondary)
        Column(Modifier.offset(1040.dp * scale, 120.dp * scale).width(784.dp * scale), verticalArrangement = Arrangement.spacedBy(18.dp * scale)) {
            Text("Next episode", color = Palette.secondary, fontSize = (24 * scale).sp, lineHeight = (35 * scale).sp, fontWeight = FontWeight.SemiBold)
            Text(next.identity.episode?.let { "${it.episode}. ${next.title}" } ?: next.title,
                fontSize = (52 * scale).sp, lineHeight = (60 * scale).sp, letterSpacing = (-1.04f * scale).sp, fontWeight = FontWeight.Bold)
            MediaMetadata(next.copy(subtitle = listOfNotNull(next.identity.episode?.let { "S${it.season ?: 1} E${it.episode}" }, next.subtitle).joinToString(" · ")),
                fontSize = 24f, lineHeight = 35f, badgeSize = 18f, scale = scale)
            next.description?.let { Text(it, fontSize = (26 * scale).sp, lineHeight = (38 * scale).sp, color = Palette.body, maxLines = 3, overflow = TextOverflow.Ellipsis) }
            Spacer(Modifier.height(2.dp * scale))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp * scale)) {
                TvAction(Modifier.width(317.dp * scale).height(72.dp * scale), scale, primary = true, requester = initial, activate = play) {
                    Icon(painterResource(Res.drawable.ic_play_zip), null, Modifier.size(24.dp * scale), it); Spacer(Modifier.width(12.dp * scale))
                    Text("Play next episode", color = it, fontSize = (26 * scale).sp, fontWeight = FontWeight.Bold)
                }
                TvAction(Modifier.width(340.dp * scale).height(72.dp * scale), scale, activate = keepWatching) {
                    Text("Keep watching credits", color = it, fontSize = (26 * scale).sp, fontWeight = FontWeight.Bold)
                }
            }
            Text("Nothing plays on its own — the credits keep running until you choose.", color = Palette.secondary,
                fontSize = (22 * scale).sp, lineHeight = (32 * scale).sp)
        }
    }
}
