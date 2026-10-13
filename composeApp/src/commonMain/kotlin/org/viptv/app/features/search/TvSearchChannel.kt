package org.viptv.app.features.search

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import org.jetbrains.compose.resources.Font
import org.viptv.app.resources.Res
import org.viptv.app.resources.bricolage
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.Card

@Composable
internal fun TvSearchChannel(card: Card, scale: Float, open: (Card) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val frame by animateFloatAsState(if (focused) 1f else 0f, tween(120), label = "Channel focus")
    val shape = RoundedCornerShape(10.dp * scale)
    Column(Modifier.width(356.dp * scale).onFocusChanged { focused = it.isFocused }
        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { open(card) }) {
        Box(Modifier.fillMaxWidth().height(200.dp * scale).clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xFF1D2230), Color(0xFF121318)))), contentAlignment = Alignment.Center) {
            if (card.logo != null) AsyncImage(card.logo, card.title, Modifier.size(140.dp * scale), contentScale = ContentScale.Fit)
            else Text(card.title, fontSize = (56 * scale).sp, lineHeight = (67 * scale).sp, letterSpacing = (-1.12f * scale).sp,
                fontFamily = FontFamily(Font(Res.font.bricolage, FontWeight.ExtraBold)), fontWeight = FontWeight.ExtraBold)
            Box(Modifier.align(Alignment.TopStart).padding(12.dp * scale)) {
                Box(Modifier.height(28.dp * scale).background(Color(0xFFE5484D), RoundedCornerShape(6.dp * scale))
                    .padding(horizontal = 9.dp * scale), contentAlignment = Alignment.Center) {
                    Text("LIVE", fontSize = (17 * scale).sp, lineHeight = (21 * scale).sp, letterSpacing = (1.02f * scale).sp,
                        fontWeight = FontWeight.ExtraBold, color = Color.White)
                }
            }
            if (frame > 0f) Box(Modifier.fillMaxSize().border(3.dp * scale, Color.White.copy(alpha = frame), shape))
        }
        Text(card.title, Modifier.padding(top = 12.dp * scale), fontSize = (22 * scale).sp, lineHeight = (26 * scale).sp,
            fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        card.subtitle?.let { Text(it, Modifier.padding(top = 4.dp * scale), fontSize = (22 * scale).sp,
            lineHeight = (28 * scale).sp, color = Palette.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
}
