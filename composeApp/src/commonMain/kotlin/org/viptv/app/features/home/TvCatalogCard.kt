package org.viptv.app.features.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.*
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
    focused: (Card) -> Unit, open: (Card) -> Unit, showFocus: Boolean = true) {
    var focus by remember { mutableStateOf(false) }
    val frame by animateFloatAsState(if (focus && showFocus) 1f else 0f, tween(120), label = "Remote focus frame")
    Column(modifier.width((if (portrait) 232 else 384).dp * scale).onFocusChanged { focus = it.isFocused; if (focus) focused(card) }
        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { open(card) }) {
        val shape = RoundedCornerShape(12.dp * scale)
        Box(Modifier.fillMaxWidth().aspectRatio(if (portrait) 2f / 3 else 16f / 9).clip(shape).background(Palette.surface)) {
            AsyncImage(if (portrait) card.poster else card.landscape, card.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            card.badges.firstOrNull()?.let { Text(it, Modifier.align(Alignment.TopStart).padding(12.dp * scale).background(Palette.text, RoundedCornerShape(5.dp * scale))
                .padding(horizontal = 10.dp * scale, vertical = 4.dp * scale), color = Palette.background, fontSize = (18 * scale).sp, lineHeight = (22 * scale).sp, fontWeight = FontWeight.Bold) }
            card.progress?.let { LinearProgressIndicator(progress = { it.toFloat() }, modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(4.dp * scale), color = Palette.accent, trackColor = Palette.text.copy(alpha = 0.3f)) }
            if (frame > 0) {
                Box(Modifier.fillMaxSize().border(3.dp * scale, Palette.text.copy(alpha = frame), shape))
                if (!portrait) Box(Modifier.align(Alignment.Center).size(72.dp * scale).background(Palette.text.copy(alpha = 0.2f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(painterResource(Res.drawable.ic_play_zip), null, Modifier.size(30.dp * scale))
                }
            }
        }
        if (!portrait) {
            Text(card.title, Modifier.padding(top = 14.dp * scale), fontSize = (24 * scale).sp, lineHeight = (29 * scale).sp,
                fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            card.subtitle?.let { Text(it, Modifier.padding(top = 4.dp * scale), fontSize = (20 * scale).sp, lineHeight = (26 * scale).sp,
                color = Palette.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}
