package org.viptv.app.features.details

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import org.jetbrains.compose.resources.painterResource
import org.viptv.app.resources.Res
import org.viptv.app.resources.ic_play_zip
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.Card

@Composable
internal fun TvEpisodeCard(card: Card, scale: Float, play: (Card) -> Unit, onFocus: () -> Unit, requester: FocusRequester?, up: FocusRequester) {
    var focused by remember { mutableStateOf(false) }
    Column(Modifier.width(448.dp * scale).then(if (requester != null) Modifier.focusRequester(requester) else Modifier).focusProperties { this.up = up }
        .onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocus() }
        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { play(card) }) {
        val shape = RoundedCornerShape(12.dp * scale)
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9).clip(shape).background(Palette.surface)) {
            AsyncImage(card.landscape ?: card.poster, card.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            card.badges.firstOrNull { !it.equals("Simkl", true) }?.let { badge -> Text(badge, Modifier.align(Alignment.TopStart).padding(12.dp * scale)
                .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.75f), RoundedCornerShape(4.dp * scale))
                .padding(horizontal = 10.dp * scale, vertical = 4.dp * scale), color = Palette.text,
                fontSize = (18 * scale).sp, lineHeight = (22 * scale).sp, fontWeight = FontWeight.SemiBold) }
            org.viptv.app.components.FocusOutline(focused, 12.dp * scale, 3.dp * scale, Modifier.matchParentSize())
            if (focused) {
                Box(Modifier.align(Alignment.Center).size(72.dp * scale).background(Palette.text.copy(alpha = 0.2f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(painterResource(Res.drawable.ic_play_zip), null, Modifier.size(30.dp * scale), tint = Palette.text)
                }
            }
            if (card.progress != null) LinearProgressIndicator(progress = { card.progress.toFloat() },
                modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(horizontal = 3.dp * scale, vertical = 3.dp * scale).height(5.dp * scale), color = Palette.text, trackColor = Palette.text.copy(alpha = 0.3f))
        }
        Spacer(Modifier.height(14.dp * scale))
        Text(card.identity.episode?.let { "${it.episode}. ${card.title}" } ?: card.title, color = if (focused) Palette.text else Palette.body,
            fontSize = (24 * scale).sp, lineHeight = (29 * scale).sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        card.subtitle?.let { Text(it, Modifier.padding(top = 4.dp * scale), fontSize = (20 * scale).sp, lineHeight = (26 * scale).sp, color = Palette.secondary) }
        card.description?.let { Text(it, Modifier.padding(top = 8.dp * scale), fontSize = (20 * scale).sp, lineHeight = (28 * scale).sp,
            color = Palette.secondary, maxLines = 2, overflow = TextOverflow.Ellipsis) }
    }
}
