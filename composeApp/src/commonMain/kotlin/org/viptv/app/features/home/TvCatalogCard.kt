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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
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

/**
 * The card's focus state is passed down instead of read here, so a focus move
 * only restarts the small leaves that actually change (ring, play badge,
 * caption colour) and never the artwork or the text nodes.
 *
 * The subtree is exposed to accessibility as one node: a TV shelf has hundreds
 * of cards, and every extra semantics node is walked again on each focus or
 * scroll change while an accessibility service is bound.
 */
@Composable
internal fun TvCatalogCard(card: Card, scale: Float, portrait: Boolean, modifier: Modifier = Modifier,
    focused: (Card) -> Unit, open: (Card) -> Unit, showFocus: Boolean = true, focusedCaptions: Boolean = false) {
    val focus = remember { mutableStateOf(false) }
    val shape = remember(scale) { RoundedCornerShape(10.dp * scale) }
    val description = remember(card) { cardDescription(card) }
    Column(modifier.width((if (portrait) 232 else 384).dp * scale)
        // One accessibility node per card. A TV shelf has hundreds of cards and
        // the TV has an accessibility service bound, so every extra node is
        // walked again on each focus and scroll change. The focus state has to be
        // restated here because clearing semantics also drops the focusable
        // node's own properties.
        .onFocusChanged { if (focus.value != it.isFocused) { focus.value = it.isFocused; if (it.isFocused) focused(card) } }
        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { open(card) }
        .clearAndSetSemantics {
            role = Role.Button
            text = AnnotatedString(description)
            contentDescription = description
            set(SemanticsProperties.Focused, focus.value)
            onClick { open(card); true }
        }) {
        // The surface fill is the no-art fallback, not just a loading colour: an
        // AsyncImage with a null model draws no placeholder at all.
        Box(Modifier.fillMaxWidth().aspectRatio(if (portrait) 2f / 3 else 16f / 9).clip(shape).background(Palette.surface)) {
            AsyncImage(if (portrait) card.poster else card.landscape, card.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            card.badges.firstOrNull { !it.equals("Simkl", true) }?.let { Text(it, Modifier.align(Alignment.TopStart).padding(12.dp * scale).background(Palette.text, RoundedCornerShape(6.dp * scale))
                .padding(horizontal = 10.dp * scale, vertical = 4.dp * scale), color = Palette.background, fontSize = (18 * scale).sp, lineHeight = (22 * scale).sp, fontWeight = FontWeight.Bold) }
            card.progress?.let { TvWatchProgress(it, Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(start = 3.dp * scale, end = 3.dp * scale, bottom = 3.dp * scale).height(5.dp * scale), scale) }
            CardFocusRing(focus, showFocus, scale, Modifier.matchParentSize())
            if (!portrait) CardPlayBadge(focus, showFocus, scale, Modifier.align(Alignment.Center))
        }
        if (!portrait || focusedCaptions) Box(Modifier.fillMaxWidth().height(76.dp * scale)) {
            CardCaptions(card, focus, portrait, focusedCaptions, scale)
        }
    }
}

private fun cardDescription(card: Card): String = buildString {
    append(card.title)
    card.subtitle?.takeIf { it.isNotBlank() }?.let { append(", ").append(it) }
    card.badges.firstOrNull { !it.equals("Simkl", true) }?.let { append(", ").append(it) }
    card.progress?.takeIf { it.isFinite() }?.let { append(", ").append(((it.toFloat().coerceIn(0f, 1f)) * 100).toInt()).append("% watched") }
}

@Composable
private fun CardFocusRing(focus: State<Boolean>, showFocus: Boolean, scale: Float, modifier: Modifier) {
    org.viptv.app.components.FocusOutline(focus.value && showFocus, 10.dp * scale, 3.dp * scale, modifier)
}

@Composable
private fun CardPlayBadge(focus: State<Boolean>, showFocus: Boolean, scale: Float, modifier: Modifier) {
    // Always composed, hidden with alpha: adding or removing this overlay on
    // focus invalidated the layout of the whole shelf for every key press.
    val shown = focus.value && showFocus
    Box(modifier.size(72.dp * scale).graphicsLayer { alpha = if (shown) 1f else 0f }
        .background(Color.White.copy(alpha = 0.16f), CircleShape), contentAlignment = Alignment.Center) {
        Icon(painterResource(Res.drawable.ic_play_zip), null, Modifier.size(30.dp * scale))
    }
}

@Composable
private fun CardCaptions(card: Card, focus: State<Boolean>, portrait: Boolean, focusedCaptions: Boolean, scale: Float) {
    val focused = focus.value
    // Portrait shelves only show the caption on the focused card. Keep the text
    // composed and hide it with alpha so focus changes never re-lay-out text,
    // and keep the caption colour constant where it is never visible unfocused.
    val shown = !portrait || focused
    Column(Modifier.graphicsLayer { alpha = if (shown) 1f else 0f }
        .padding(top = 14.dp * scale), verticalArrangement = Arrangement.spacedBy(4.dp * scale)) {
        Text(card.title, Modifier.fillMaxWidth(),
            fontSize = (24 * scale).sp, lineHeight = (29 * scale).sp, color = if (portrait || focused) Palette.text else Palette.body,
            fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        card.subtitle?.let { Text(it, fontSize = (20 * scale).sp, lineHeight = (26 * scale).sp,
            color = Palette.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
}