package org.viptv.app.features.home

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
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.Color
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
internal fun TvCatalogCard(card: Card, scale: Float, portrait: Boolean, modifier: Modifier = Modifier,
    focused: (Card) -> Unit, open: (Card) -> Unit, showFocus: Boolean = true, focusedCaptions: Boolean = false) {
    var focus by remember { mutableStateOf(false) }
    Column(modifier.width((if (portrait) 232 else 384).dp * scale).onFocusChanged { if (focus != it.isFocused) { focus = it.isFocused; if (focus) focused(card) } }
        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { open(card) }) {
        val shape = RoundedCornerShape(10.dp * scale)
        Box(Modifier.fillMaxWidth().aspectRatio(if (portrait) 2f / 3 else 16f / 9).clip(shape).background(Palette.surface)) {
            AsyncImage(if (portrait) card.poster else card.landscape, card.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            card.badges.firstOrNull { !it.equals("Simkl", true) }?.let { Text(it, Modifier.align(Alignment.TopStart).padding(12.dp * scale).background(Palette.text, RoundedCornerShape(6.dp * scale))
                .padding(horizontal = 10.dp * scale, vertical = 4.dp * scale), color = Palette.background, fontSize = (18 * scale).sp, lineHeight = (22 * scale).sp, fontWeight = FontWeight.Bold) }
            card.progress?.let { TvWatchProgress(it, Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(start = 3.dp * scale, end = 3.dp * scale, bottom = 3.dp * scale).height(5.dp * scale), scale) }
            org.viptv.app.components.FocusOutline(focus && showFocus, 10.dp * scale, 3.dp * scale, Modifier.matchParentSize())
            if (focus && showFocus && !portrait) Box(Modifier.align(Alignment.Center).size(72.dp * scale)
                .background(Color.White.copy(alpha = 0.16f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(painterResource(Res.drawable.ic_play_zip), null, Modifier.size(30.dp * scale))
            }
        }
        if (!portrait || focusedCaptions) Box(Modifier.fillMaxWidth().height(76.dp * scale)) {
            if (!portrait || focus) Column(Modifier.padding(top = 14.dp * scale), verticalArrangement = Arrangement.spacedBy(4.dp * scale)) {
                Text(card.title, Modifier.fillMaxWidth(),
                    fontSize = (24 * scale).sp, lineHeight = (29 * scale).sp, color = if (focus) Palette.text else Palette.body,
                    fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                card.subtitle?.let { Text(it, fontSize = (20 * scale).sp, lineHeight = (26 * scale).sp,
                    color = Palette.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
    }
}
