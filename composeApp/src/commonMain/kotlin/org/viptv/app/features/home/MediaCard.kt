package org.viptv.app.features.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import coil3.compose.AsyncImage
import com.composables.icons.lucide.Ellipsis
import com.composables.icons.lucide.Lucide
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.painterResource
import org.viptv.app.components.ArtFailure
import org.viptv.app.components.ArtWatchedBadge
import org.viptv.app.components.ClampedText
import org.viptv.app.components.HoverFill
import org.viptv.app.components.pointerHovered
import org.viptv.app.components.pressScale
import org.viptv.app.components.retryAfterFailure
import org.viptv.app.components.responseSpec
import org.viptv.app.components.WatchedBadge
import org.viptv.app.components.cardChip
import org.viptv.app.components.caughtUpCaption
import org.viptv.app.components.caughtUpNextCaption
import org.viptv.app.components.LocalNextEpisodes
import org.viptv.app.components.Motion
import org.viptv.app.components.MutationFailure
import org.viptv.app.state.saveTarget
import org.viptv.app.components.LocalListRemoval
import org.viptv.app.components.PendingActionSpinner
import org.viptv.app.components.isPlayPending
import org.viptv.app.components.LocalCardOverflow
import org.viptv.app.components.LocalPointerHover
import org.viptv.app.components.newEpisodesDescription
import org.viptv.app.components.isWatched
import org.viptv.app.components.tvHold
import org.viptv.app.components.holdMenu
import org.viptv.app.components.secondaryClickMenu
import org.viptv.app.features.search.searchMatchHighlight
import org.viptv.app.resources.Res
import org.viptv.app.resources.ic_play_zip
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.Card
import org.viptv.app.components.pointerClickable

internal class CardActions(
    val play: (Card) -> Unit,
    val toggleSave: (Card) -> Unit,
    val isSaved: (Card) -> Boolean,
    val chooseSource: ((Card) -> Unit)? = null,
    val playFromStart: ((Card) -> Unit)? = null,
    val markWatched: ((Card) -> Unit)? = null,
)

internal val LocalCardActions = compositionLocalOf<CardActions?> { null }

@Composable
fun MediaCard(card: Card, width: Int = 240, portrait: Boolean = false, showLabels: Boolean = true, onFocus: (Card) -> Unit = {}, open: (Card) -> Unit, labelScale: Float = 1f, cornerRadius: androidx.compose.ui.unit.Dp = 8.dp, focusWidth: androidx.compose.ui.unit.Dp = 3.dp, showFocusOutline: Boolean = true, modifier: Modifier = Modifier,
    watchedBadgeSize: Dp? = null,
    select: ((Card) -> Unit)? = null,
    onHover: (Boolean) -> Unit = {},
    scale: Float = 1f,
    searchQuery: String? = null) {
    var focused by remember { mutableStateOf(false) }
    val accent = Palette.accent
    val actions = LocalCardActions.current
    val removal = LocalListRemoval.current
    val menu = actions != null || removal != null
    val nextEpisodes = LocalNextEpisodes.current
    val caughtUp = remember(card) { card.caughtUpCaption() }
    val nextCaption = remember(card, caughtUp, nextEpisodes) {
        if (caughtUp != null) nextEpisodes[card]?.caughtUpNextCaption() else null
    }
    val desktop = LocalPointerHover.current
    val television = !desktop && !LocalCardOverflow.current
    val hover = remember { MutableInteractionSource() }
    val press = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    val pointerHovered = hover.pointerHovered()
    val hoverFade by animateFloatAsState(if (pointerHovered) 1f else 0f, responseSpec(), label = "Card hover")
    val ringFade by animateFloatAsState(if (pointerHovered && !(focused && showFocusOutline)) 1f else 0f, responseSpec(), label = "Card hover ring")
    LaunchedEffect(hovered) { onHover(hovered) }
    var menuAt by remember { mutableStateOf<IntOffset?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    val saved = actions?.isSaved?.invoke(card) == true
    val activate = select ?: actions?.play ?: open
    Column(modifier.width(width.dp).hoverable(hover)
        .secondaryClickMenu(desktop || removal != null) { menuAt = it; menuOpen = true }
        .tvHold(enabled = television && menu, onHold = { menuOpen = true })
        .onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocus(card) }
        .pointerClickable(interactionSource = press, indication = null) { (select ?: open)(card) }
        .holdMenu(LocalCardOverflow.current && menu) { menuAt = it; menuOpen = true }) {
        Box(Modifier.fillMaxWidth().aspectRatio(if (portrait) 2f / 3 else 16f / 9).pressScale(press, Motion.card)
            .clip(RoundedCornerShape(cornerRadius)).background(Palette.raised)) {
            if (width > 0) {
                val density = LocalDensity.current
                CardArtwork(card, portrait, with(density) { width.dp.toPx().toInt() },
                    with(density) { (width * (if (portrait) 1.5f else 0.5625f)).dp.toPx().toInt() }, labelScale.coerceAtLeast(1f))
            } else BoxWithConstraints(Modifier.matchParentSize()) {
                CardArtwork(card, portrait, constraints.maxWidth, constraints.maxHeight, labelScale.coerceAtLeast(1f))
            }
            card.cardChip()?.let { chip -> Surface(Modifier.align(Alignment.TopStart).padding(8.dp).widthIn(max = (width - 16).coerceAtLeast(0).dp), shape = RoundedCornerShape(4.dp), color = Palette.text) {
                val spoken = newEpisodesDescription(chip)
                ClampedText(chip, Modifier.padding(horizontal = 6.dp, vertical = 3.dp).semantics { contentDescription = spoken }, color = Palette.background, fontSize = (10 * labelScale.coerceAtLeast(1f)).sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            } }
            if (card.isWatched()) {
                if (watchedBadgeSize != null) WatchedBadge(watchedBadgeSize, Modifier.align(Alignment.TopEnd))
                else ArtWatchedBadge(Modifier.matchParentSize())
            }
            if (hoverFade > 0f) CardHoverPlay(Modifier.matchParentSize().graphicsLayer { alpha = hoverFade })
            // Touch has no context menu, so the menu gets a target of its own.
            if (LocalCardOverflow.current && menu) {
                var at by remember { mutableStateOf(IntOffset.Zero) }
                val more = remember { MutableInteractionSource() }
                Box(Modifier.align(Alignment.BottomEnd).padding(6.dp)
                    .onGloballyPositioned { at = IntOffset(it.positionInParent().x.roundToInt(), it.positionInParent().y.roundToInt()) }
                    .size(44.dp).pressScale(more, Motion.button).clip(CircleShape).background(HoverFill.cardScrim)
                    .pointerClickable(interactionSource = more, indication = null, role = Role.Button,
                        onClickLabel = "More actions", shape = CircleShape) { menuAt = at; menuOpen = true },
                    contentAlignment = Alignment.Center) {
                    Icon(Lucide.Ellipsis, "More actions", Modifier.size(20.dp), tint = Palette.text)
                }
            }
            card.progress?.let { progress ->
                val p = progress.toFloat().coerceIn(0f, 1f)
                Box(Modifier.fillMaxWidth().align(Alignment.BottomCenter).height(4.dp).drawBehind {
                    val bar = size.height
                    drawRoundRect(Palette.raised.copy(alpha = 0.9f), cornerRadius = CornerRadius(bar / 2), size = Size(size.width, bar))
                    if (p > 0f) drawRoundRect(accent, cornerRadius = CornerRadius(bar / 2), size = Size(size.width * p, bar))
                })
            }
            if (card.isPlayPending()) {
                Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                    PendingActionSpinner(size = 40.dp, strokeWidth = 3.dp)
                }
            }
            // A focused card draws only its focus outline, never the hover ring as well.
            if (ringFade > 0f) CardHoverRing(cornerRadius, Modifier.matchParentSize().graphicsLayer { alpha = ringFade })
            if (showFocusOutline) org.viptv.app.components.FocusOutline(focused, cornerRadius, focusWidth, Modifier.matchParentSize())
        }
        if (showLabels) {
            Spacer(Modifier.height(9.dp))
            val title = remember(card.title, searchQuery) {
                searchQuery?.takeIf { it.isNotBlank() }?.let { searchMatchHighlight(card.title, it) } ?: AnnotatedString(card.title)
            }
            Text(title, Modifier.fillMaxWidth().then(if (focused) Modifier.basicMarquee(iterations = Int.MAX_VALUE) else Modifier), fontSize = (14 * labelScale).sp, lineHeight = (18 * labelScale).sp, fontWeight = FontWeight.Medium, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
            (card.subtitle ?: caughtUp)?.let { Text(it, fontSize = (12 * labelScale).sp, lineHeight = (16 * labelScale).sp, color = Palette.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            nextCaption?.let { Text(it, fontSize = (12 * labelScale).sp, lineHeight = (16 * labelScale).sp, color = Palette.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
            MutationFailure(saveTarget(card), Modifier.fillMaxWidth().padding(top = 6.dp), scale = labelScale.coerceAtLeast(1f), retry = !television)
        if (menu) {
            val present: @Composable () -> Unit = {
                CardMenu(card, saved, removal, actions,
                    onDismiss = { menuOpen = false },
                    onPlay = { menuOpen = false; activate(card) },
                    onInfo = { menuOpen = false; open(card) },
                    onToggleSave = actions?.let { { menuOpen = false; it.toggleSave(card) } },
                    visible = menuOpen, onClosed = { menuAt = null }, scale = scale, television = television)
            }
            menuAt?.let { at ->
                Popup(popupPositionProvider = CardMenuPosition(at),
                    onDismissRequest = { menuOpen = false }, properties = PopupProperties(focusable = true)) {
                    present()
                }
            }
            if (menuAt == null && menuOpen) {
                Popup(popupPositionProvider = TvCardMenuPosition(),
                    onDismissRequest = { menuOpen = false }, properties = PopupProperties(focusable = true)) {
                    present()
                }
            }
        }

    }
}

@Composable
private fun CardArtwork(card: Card, portrait: Boolean, slotW: Int, slotH: Int, labelScale: Float) {
    val landscape = if (portrait) null else card.landscapeArt()
    val url = if (portrait) card.poster?.takeIf { it.isNotBlank() } else (landscape as? LandscapeArt.Still)?.url
    val posterUrl = (landscape as? LandscapeArt.Poster)?.url
    // Keyed on what is drawn, so a slot reused for other art or the other orientation never keeps a stale failure.
    val art = remember(card.poster, card.landscape, portrait) { ArtFailure(url ?: posterUrl) }
    val attempt = art.retryAfterFailure()
    Box(Modifier.fillMaxSize()) {
        when {
            url != null && !art.gone -> key(attempt) { AsyncImage(org.viptv.app.images.sizedArtwork(url, slotW, slotH),
                card.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
                onSuccess = { art.ready() }, onError = { art.fail(it.result.throwable, url) }) }
            posterUrl != null && !art.gone -> key(attempt) { PosterInLandscape(
                org.viptv.app.images.sizedArtwork(posterUrl, slotH * 2 / 3, slotH) ?: posterUrl, card.title,
                onSuccess = { art.ready() }, onError = { art.fail(it.result.throwable, posterUrl) }) }
        }
        if (art.failed || url == null && landscape !is LandscapeArt.Poster) MissingArtTile(card.title, labelScale)
    }
}

internal const val MEDIA_CARD_HOVER_TAG = "media-card-hover"
internal const val MEDIA_CARD_HOVER_RING_TAG = "media-card-hover-ring"

@Composable
private fun CardHoverPlay(modifier: Modifier) {
    Box(modifier.testTag(MEDIA_CARD_HOVER_TAG), contentAlignment = Alignment.Center) {
        Box(Modifier.size(48.dp).background(Color.White.copy(alpha = .16f)), contentAlignment = Alignment.Center) {
            Icon(painterResource(Res.drawable.ic_play_zip), null, Modifier.size(20.dp), tint = Palette.text)
        }
    }
}

@Composable
private fun CardHoverRing(radius: Dp, modifier: Modifier) {
    Canvas(modifier.testTag(MEDIA_CARD_HOVER_RING_TAG)) {
        val stroke = 2.dp.toPx()
        drawRoundRect(Palette.text, Offset(stroke / 2, stroke / 2),
            Size((size.width - stroke).coerceAtLeast(0f), (size.height - stroke).coerceAtLeast(0f)),
            CornerRadius((radius.toPx() - stroke / 2).coerceAtLeast(0f)), style = Stroke(stroke))
    }
}

private class CardMenuPosition(private val at: IntOffset) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize,
        layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset = IntOffset(
        (anchorBounds.left + at.x).coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0)),
        (anchorBounds.top + at.y).coerceIn(0, (windowSize.height - popupContentSize.height).coerceAtLeast(0)),
    )
}
