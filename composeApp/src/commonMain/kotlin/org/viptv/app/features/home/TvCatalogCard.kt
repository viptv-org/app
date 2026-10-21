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
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import org.jetbrains.compose.resources.painterResource
import org.viptv.app.components.TvMediaBadge
import org.viptv.app.components.WATCHED_BADGE
import org.viptv.app.components.WatchedBadge
import org.viptv.app.components.isWatched
import org.viptv.app.components.isCapabilityBadge
import org.viptv.app.components.metadataParts
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
 *
 * Card *states* follow the canvas dictionary §A1 and Tv3CardStates: a label on
 * the art (New episode is light, Ended is dark, LIVE is red), Watched as a
 * corner check, Returning moves to the meta line, and the certification chip
 * sits next to the year/runtime/rating line. Missing art draws a dark tile with
 * the title's initials instead of an empty surface.
 */
@Composable
internal fun TvCatalogCard(card: Card, scale: Float, portrait: Boolean, modifier: Modifier = Modifier,
    focused: (Card) -> Unit, open: (Card) -> Unit, showFocus: Boolean = true, focusedCaptions: Boolean = false,
    onUp: (() -> Unit)? = null) {
    val focus = remember { mutableStateOf(false) }
    val shape = remember(scale) { RoundedCornerShape(10.dp * scale) }
    val states = remember(card) { TvCardStates.of(card) }
    val description = remember(card, states) { cardDescription(card, states) }
    Column(modifier.width((if (portrait) 232 else 384).dp * scale)
        // One accessibility node per card. A TV shelf has hundreds of cards and
        // the TV has an accessibility service bound, so every extra node is
        // walked again on each focus and scroll change. The focus state has to be
        // restated here because clearing semantics also drops the focusable
        // node's own properties.
        .onPreviewKeyEvent { event ->
            // Every card in the first shelf row can return to the billboard, not
            // just the left-most one (the old handler lived on index 0 only).
            if (onUp != null && event.type == androidx.compose.ui.input.key.KeyEventType.KeyDown &&
                event.key == androidx.compose.ui.input.key.Key.DirectionUp) { onUp(); true } else false
        }
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
            // Measured on the ONN: routing TV card art through the resizing proxy
            // moved gpu p50 from 13.5 ms to 15.0-15.5 ms and missed vsyncs from
            // 8.4 % to 11.2-12.0 %, so TV keeps the proxied WebP without custom
            // sizing (see docs/TV_FRAME_BUDGET.md).
            val art = if (portrait) card.poster else card.landscape
            if (art.isNullOrBlank()) CardNoArtFallback(card, scale, portrait, shape)
            else AsyncImage(art, card.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            states.artLabel?.let { label ->
                Text(label, Modifier.align(Alignment.TopStart).padding(10.dp * scale)
                    .background(states.artLabelFill, RoundedCornerShape(6.dp * scale))
                    .padding(horizontal = 10.dp * scale, vertical = 4.dp * scale),
                    color = states.artLabelInk,
                    fontSize = (18 * scale).sp, lineHeight = (22 * scale).sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            // The card's one accessibility node says "Watched" (cardDescription).
            if (states.watched) WatchedBadge(34.dp * scale, Modifier.align(Alignment.TopEnd), description = null)
            card.progress?.let {
                TvWatchProgress(it, Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .padding(start = 3.dp * scale, end = 3.dp * scale, bottom = 3.dp * scale).height(5.dp * scale), scale)
            }
            // Board "Logo" state: a wordmark replaces the text title and sits
            // on the art instead of under it.
            if (!portrait && card.logo != null) AsyncImage(org.viptv.app.images.rememberLogoRequest(card.logo), card.title,
                Modifier.align(Alignment.BottomStart).padding(18.dp * scale).fillMaxWidth(.62f).height(56.dp * scale),
                contentScale = ContentScale.Fit, alignment = Alignment.BottomStart)
            CardFocusRing(focus, showFocus, scale, Modifier.matchParentSize())
            if (!portrait) CardPlayBadge(focus, showFocus, scale, Modifier.align(Alignment.Center))
        }
        if (!portrait || focusedCaptions) Box(Modifier.fillMaxWidth().height(76.dp * scale)) {
            CardCaptions(card, states, focus, portrait, focusedCaptions, scale)
        }
    }
}

/** Parsed, display-only card states; deliberately independent of focus. */
private data class TvCardStates(
    val artLabel: String?,
    val artLabelFill: Color,
    val artLabelInk: Color,
    val watched: Boolean,
    val metaLabel: String?,
    val certification: String?,
    val metaText: String?,
) {
    companion object {
        fun of(card: Card): TvCardStates {
            val badges = card.badges.filter { !it.equals("Simkl", true) }
            val lower = badges.map { it.lowercase() }
            val watched = card.isWatched()
            val live = card.identity.kind.equals("channel", true) || card.identity.kind.equals("tv", true) ||
                "live" in lower
            val newEpisode = badges.firstOrNull { it.equals("New episode", true) }
            val ended = badges.firstOrNull { it.equals("Ended", true) }
            val returning = badges.firstOrNull { it.equals("Returning", true) }
            // Only *state* labels belong on the artwork (board Tv3CardStates).
            // Presentation badges (4K/HDR/5.1/CC/Dolby) are metadata, not art
            // labels; they stay off the card entirely. Any other badge is a
            // state label (New, Remastered, …) and takes the dark label slot.
            val other = badges.firstOrNull { badge ->
                !isCapabilityBadge(badge) && !badge.equals("New episode", true) && !badge.equals("Ended", true) &&
                    !badge.equals("Watched", true) && !badge.equals("Returning", true)
            }
            val label = when {
                live && badges.any { it.equals("LIVE", true) } -> "LIVE"
                newEpisode != null -> "New episode"
                ended != null -> "Ended"
                else -> other
            }
            val loud = label == "New episode"
            val fill = when {
                loud -> Palette.text
                label == "LIVE" -> Color(0xFFE5484D)
                else -> Color(0xFF0B0B0C).copy(alpha = .8f)
            }
            val ink = when {
                loud -> Palette.background
                else -> Palette.text
            }
            return TvCardStates(
                artLabel = label,
                artLabelFill = fill,
                artLabelInk = ink,
                watched = watched,
                metaLabel = (returning ?: other)?.takeIf { it != label },
                certification = card.metadata?.certification?.takeIf { it.isNotBlank() },
                metaText = card.subtitle?.takeIf { it.isNotBlank() }
                    ?: card.identity.episode?.let { episode ->
                        listOfNotNull(episode.season?.let { "S$it" }, "E${episode.episode}").joinToString(" ")
                    }
                    ?: card.metadataParts().take(3).takeIf { it.isNotEmpty() }?.joinToString(" · "),
            )
        }
    }
}

private fun cardDescription(card: Card, states: TvCardStates): String = buildString {
    append(card.title)
    states.metaText?.let { append(", ").append(it) }
    states.artLabel?.let { append(", ").append(it) }
    if (states.watched) append(", ").append(WATCHED_BADGE)
    states.metaLabel?.let { append(", ").append(it) }
    states.certification?.let { append(", rated ").append(it) }
    card.progress?.takeIf { it.isFinite() }?.let { append(", ").append(((it.toFloat().coerceIn(0f, 1f)) * 100).toInt()).append("% watched") }
}

@Composable
private fun CardNoArtFallback(card: Card, scale: Float, portrait: Boolean, shape: RoundedCornerShape) {
    // Board Tv3CardStates: "No art — title and year on a dark tile, same size."
    // The title stacks at the bottom-left with the year directly under it.
    Box(Modifier.fillMaxSize().background(Color(0xFF1A1A1C), shape)) {
        Column(Modifier.align(Alignment.BottomStart).padding(14.dp * scale),
            verticalArrangement = Arrangement.spacedBy(4.dp * scale)) {
            Text(
                card.title,
                color = Palette.text.copy(alpha = .9f),
                fontSize = ((if (portrait) 26 else 22) * scale).sp,
                lineHeight = ((if (portrait) 32 else 28) * scale).sp,
                fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            card.metadata?.year?.let {
                Text("$it", color = Palette.secondary, fontSize = (18 * scale).sp, fontWeight = FontWeight.Medium)
            }
        }
    }
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
private fun CardCaptions(card: Card, states: TvCardStates, focus: State<Boolean>, portrait: Boolean, focusedCaptions: Boolean, scale: Float) {
    val focused = focus.value
    // Portrait shelves only show the caption on the focused card. Keep the text
    // composed and hide it with alpha so focus changes never re-lay-out text,
    // and keep the caption colour constant where it is never visible unfocused.
    val shown = !portrait || focused
    Column(Modifier.graphicsLayer { alpha = if (shown) 1f else 0f }
        .padding(top = 14.dp * scale), verticalArrangement = Arrangement.spacedBy(4.dp * scale)) {
        // Logo cards render the wordmark on the art, so the caption is meta
        // only (board "Logo — no text title").
        if (portrait || card.logo == null) {
            Text(card.title, Modifier.fillMaxWidth(),
                fontSize = (24 * scale).sp, lineHeight = (29 * scale).sp, color = if (portrait || focused) Palette.text else Palette.body,
                fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        // Board order: title, then meta value + cert chip on the second line.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp * scale)) {
            states.metaText?.let {
                Text(it, fontSize = (20 * scale).sp, lineHeight = (26 * scale).sp,
                    color = Palette.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            states.metaLabel?.let {
                Text(it, fontSize = (20 * scale).sp, lineHeight = (26 * scale).sp,
                    color = Palette.secondary, maxLines = 1)
            }
            states.certification?.let { TvMediaBadge(it, scale) }
        }
    }
}
