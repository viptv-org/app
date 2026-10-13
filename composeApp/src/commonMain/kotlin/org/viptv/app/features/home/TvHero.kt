package org.viptv.app.features.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.composables.icons.lucide.*
import org.viptv.app.components.TvMediaMetadata
import org.viptv.app.components.TvAction
import org.jetbrains.compose.resources.painterResource
import org.viptv.app.resources.Res
import org.viptv.app.resources.ic_play_zip
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.Card

@Composable
internal fun TvHero(card: Card, open: (Card) -> Unit, play: (Card) -> Unit, first: FocusRequester? = null,
    down: FocusRequester? = null, indicators: Int = 1, selected: Int = 0, save: (Card) -> Unit = {}, saved: Boolean = false) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val scale = maxWidth / 1920.dp
        Box(Modifier.fillMaxWidth().height(800.dp * scale)) {
        if (card.logo != null) AsyncImage(card.logo, card.title, Modifier.offset(96.dp * scale, 306.dp * scale).size(224.dp * scale, 160.dp * scale)
            .graphicsLayer { blendMode = BlendMode.Lighten; compositingStrategy = CompositingStrategy.Offscreen }, contentScale = ContentScale.Fit, alignment = Alignment.CenterStart)
        else Text(card.title, Modifier.offset(96.dp * scale, 306.dp * scale).width(800.dp * scale), fontSize = (48 * scale).sp, lineHeight = (56 * scale).sp, fontWeight = FontWeight.Bold)
        Box(Modifier.offset(96.dp * scale, 494.dp * scale)) {
            TvMediaMetadata(card, scale, subtitleParts = card.subtitle?.split('·')?.map(String::trim)?.filter(String::isNotEmpty))
        }
        card.description?.let { Text(it, Modifier.offset(96.dp * scale, 544.dp * scale).width(780.dp * scale), fontSize = (26 * scale).sp,
            lineHeight = (38 * scale).sp, color = Palette.body, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        Row(Modifier.offset(96.dp * scale, 648.dp * scale), horizontalArrangement = Arrangement.spacedBy(20.dp * scale)) {
            TvAction(Modifier.width(220.dp * scale).height(72.dp * scale).focusProperties { if (down != null) this.down = down }, scale,
                primary = true, requester = first, activate = { play(card) }, contentPadding = PaddingValues(horizontal = 28.dp * scale), arrangement = Arrangement.Start) {
                Icon(painterResource(Res.drawable.ic_play_zip), null, Modifier.size(24.dp * scale), it); Spacer(Modifier.width(12.dp * scale))
                Text("Play", color = it, fontSize = (26 * scale).sp, fontWeight = FontWeight.Bold)
            }
            TvAction(Modifier.size(72.dp * scale).focusProperties { if (down != null) this.down = down }, scale, round = true, activate = { save(card) }) { Icon(if (saved) Lucide.Check else Lucide.Plus, if (saved) "Remove from My List" else "Add to My List", Modifier.size(30.dp * scale), it) }
            TvAction(Modifier.size(72.dp * scale).focusProperties { if (down != null) this.down = down }, scale, round = true, activate = { open(card) }) { Icon(Lucide.Info, "More info", Modifier.size(30.dp * scale), it) }
        }
        if (indicators > 1) Row(Modifier.offset(96.dp * scale, 760.dp * scale), horizontalArrangement = Arrangement.spacedBy(8.dp * scale)) {
            repeat(indicators) { Box(Modifier.width((if (it == selected) 48 else 16).dp * scale).height(4.dp * scale)
                .background(if (it == selected) Palette.text else Palette.text.copy(alpha = 0.35f), androidx.compose.foundation.shape.RoundedCornerShape(2.dp * scale))) }
        }
        }
    }
}
