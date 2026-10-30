package org.viptv.app.features.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Info
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Play
import com.composables.icons.lucide.Plus
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource
import org.viptv.app.components.ArtFailure
import org.viptv.app.components.ArtWatchedBadge
import org.viptv.app.components.HoverFill
import org.viptv.app.components.MediaMetadata
import org.viptv.app.components.pointerHovered
import org.viptv.app.components.pressScale
import org.viptv.app.components.responseSpec
import org.viptv.app.components.retryAfterFailure
import org.viptv.app.components.WatchedBadge
import org.viptv.app.components.cardChip
import org.viptv.app.components.caughtUpCaption
import org.viptv.app.components.caughtUpNextCaption
import org.viptv.app.components.LocalNextEpisodes
import org.viptv.app.components.Motion
import org.viptv.app.components.hoverFill
import org.viptv.app.components.newEpisodesDescription
import org.viptv.app.components.isWatched
import org.viptv.app.components.tvBackdropBlur
import org.viptv.app.components.tvFocusOutline
import org.viptv.app.resources.Res
import org.viptv.app.resources.ic_play_zip
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.Card
import org.viptv.app.components.pointerClickable

internal class CardActions(
    val play: (Card) -> Unit,
    val toggleSave: (Card) -> Unit,
    val isSaved: (Card) -> Boolean,
)

internal val LocalCardActions = compositionLocalOf<CardActions?> { null }

@Composable
fun MediaCard(card: Card, width: Int = 240, portrait: Boolean = false, showLabels: Boolean = true, onFocus: (Card) -> Unit = {}, open: (Card) -> Unit, labelScale: Float = 1f, cornerRadius: androidx.compose.ui.unit.Dp = 8.dp, focusWidth: androidx.compose.ui.unit.Dp = 3.dp, showFocusOutline: Boolean = true, modifier: Modifier = Modifier,
    watchedBadgeSize: Dp? = null,
    select: ((Card) -> Unit)? = null,
    onHover: (Boolean) -> Unit = {}) {
    var focused by remember { mutableStateOf(false) }
    val actions = LocalCardActions.current
    val nextEpisodes = LocalNextEpisodes.current
    val caughtUp = remember(card) { card.caughtUpCaption() }
    val nextCaption = remember(card, caughtUp, nextEpisodes) {
        if (caughtUp != null) nextEpisodes[card]?.caughtUpNextCaption() else null
    }
    val desktop = actions != null
    val hover = remember { MutableInteractionSource() }
    val press = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    val pointerHovered = hover.pointerHovered()
    val hoverFade by animateFloatAsState(if (pointerHovered) 1f else 0f, responseSpec(), label = "Card hover")
    val ringFade by animateFloatAsState(if (pointerHovered && !(focused && showFocusOutline)) 1f else 0f, responseSpec(), label = "Card hover ring")
    LaunchedEffect(hovered) { onHover(hovered) }
    val gap = with(LocalDensity.current) { 12.dp.roundToPx() }
    var previewOpen by remember { mutableStateOf(false) }
    var previewHovered by remember { mutableStateOf(false) }
    var menuAt by remember { mutableStateOf<IntOffset?>(null) }
    val saved = actions?.isSaved?.invoke(card) == true
    val activate = select ?: actions?.play ?: open
    // The preview survives the 12 dp gap, so the pointer can reach its own buttons.
    LaunchedEffect(desktop, hovered) { if (desktop && hovered) { delay(450); previewOpen = true } }
    LaunchedEffect(desktop, hovered, previewHovered, menuAt) {
        if (desktop && previewOpen && menuAt == null && !hovered && !previewHovered) {
            delay(180)
            if (!hovered && !previewHovered) previewOpen = false
        }
    }
    Column(modifier.width(width.dp).hoverable(hover)
        .cardContextClick(desktop) { previewOpen = false; menuAt = it }
        .onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocus(card) }
        .pointerClickable(interactionSource = press, indication = null) { (select ?: open)(card) }) {
        Box(Modifier.fillMaxWidth().aspectRatio(if (portrait) 2f / 3 else 16f / 9).pressScale(press, Motion.card)
            .clip(RoundedCornerShape(cornerRadius)).background(Palette.raised)) {
            if (width > 0) {
                val density = LocalDensity.current
                CardArtwork(card, portrait, with(density) { width.dp.toPx().toInt() },
                    with(density) { (width * (if (portrait) 1.5f else 0.5625f)).dp.toPx().toInt() }, labelScale.coerceAtLeast(1f))
            } else BoxWithConstraints(Modifier.matchParentSize()) {
                CardArtwork(card, portrait, constraints.maxWidth, constraints.maxHeight, labelScale.coerceAtLeast(1f))
            }
            card.cardChip()?.let { chip -> Surface(Modifier.align(Alignment.TopStart).padding(8.dp), shape = RoundedCornerShape(4.dp), color = Palette.text) {
                val spoken = newEpisodesDescription(chip)
                Text(chip, Modifier.padding(horizontal = 6.dp, vertical = 3.dp).semantics { contentDescription = spoken }, color = Palette.background, fontSize = (10 * labelScale.coerceAtLeast(1f)).sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            } }
            if (card.isWatched()) {
                if (watchedBadgeSize != null) WatchedBadge(watchedBadgeSize, Modifier.align(Alignment.TopEnd))
                else ArtWatchedBadge(Modifier.matchParentSize())
            }
            if (hoverFade > 0f) CardHoverScrim(Modifier.matchParentSize().graphicsLayer { alpha = hoverFade })
            card.progress?.let { progress ->
                val p = progress.toFloat().coerceIn(0f, 1f)
                Box(Modifier.fillMaxWidth().align(Alignment.BottomCenter).height(4.dp).drawBehind {
                    val bar = size.height
                    drawRoundRect(Palette.raised.copy(alpha = 0.9f), cornerRadius = CornerRadius(bar / 2), size = Size(size.width, bar))
                    if (p > 0f) drawRoundRect(Palette.accent, cornerRadius = CornerRadius(bar / 2), size = Size(size.width * p, bar))
                })
            }
            // A focused card draws only its focus outline, never the hover ring as well.
            if (ringFade > 0f) CardHoverRing(cornerRadius, Modifier.matchParentSize().graphicsLayer { alpha = ringFade })
            if (showFocusOutline) org.viptv.app.components.FocusOutline(focused, cornerRadius, focusWidth, Modifier.matchParentSize())
        }
        if (showLabels) {
            Spacer(Modifier.height(9.dp))
            Text(card.title, Modifier.fillMaxWidth().then(if (focused) Modifier.basicMarquee(iterations = Int.MAX_VALUE) else Modifier), fontSize = (14 * labelScale).sp, lineHeight = (18 * labelScale).sp, fontWeight = FontWeight.Medium, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
            (card.subtitle ?: caughtUp)?.let { Text(it, fontSize = (12 * labelScale).sp, lineHeight = (16 * labelScale).sp, color = Palette.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            nextCaption?.let { Text(it, fontSize = (12 * labelScale).sp, lineHeight = (16 * labelScale).sp, color = Palette.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        if (actions != null) {
            menuAt?.let { at ->
                Popup(popupPositionProvider = CardMenuPosition(at),
                    onDismissRequest = { menuAt = null }, properties = PopupProperties(focusable = true)) {
                    CardMenu(card, saved,
                        onDismiss = { menuAt = null },
                        onPlay = { menuAt = null; activate(card) },
                        onInfo = { menuAt = null; open(card) },
                        onToggleSave = { menuAt = null; actions.toggleSave(card) })
                }
            }
            if (previewOpen && menuAt == null) {
                Popup(popupPositionProvider = CardPreviewPosition(gap),
                    onDismissRequest = { previewOpen = false },
                    properties = PopupProperties(focusable = false, dismissOnClickOutside = false, dismissOnBackPress = false)) {
                    CardPreview(card, saved,
                        onPlay = { previewOpen = false; activate(card) },
                        onSave = { previewOpen = false; actions.toggleSave(card) },
                        onInfo = { previewOpen = false; open(card) },
                        onHoverChanged = { previewHovered = it })
                }
            }
        }
    }
}

@Composable
private fun CardArtwork(card: Card, portrait: Boolean, slotW: Int, slotH: Int, labelScale: Float) {
    val landscape = if (portrait) null else card.landscapeArt()
    val url = if (portrait) card.poster?.takeIf { it.isNotBlank() } else (landscape as? LandscapeArt.Still)?.url
    // Keyed on what is drawn, so a slot reused for other art or the other orientation never keeps a stale failure.
    val art = remember(card.poster, card.landscape, portrait) { ArtFailure() }
    val attempt = art.retryAfterFailure()
    Box(Modifier.fillMaxSize()) {
        when {
            url != null -> key(attempt) { AsyncImage(org.viptv.app.images.sizedArtwork(url, slotW, slotH),
                card.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
                onSuccess = { art.failed = false }, onError = { art.failed = true }) }
            landscape is LandscapeArt.Poster -> key(attempt) { PosterInLandscape(
                org.viptv.app.images.sizedArtwork(landscape.url, slotH * 2 / 3, slotH) ?: landscape.url, card.title,
                onSuccess = { art.failed = false }, onError = { art.failed = true }) }
        }
        if (art.failed || url == null && landscape !is LandscapeArt.Poster) MissingArtTile(card.title, labelScale)
    }
}

internal const val MEDIA_CARD_HOVER_TAG = "media-card-hover"
internal const val MEDIA_CARD_HOVER_RING_TAG = "media-card-hover-ring"

@Composable
private fun CardHoverScrim(modifier: Modifier) {
    Box(modifier.testTag(MEDIA_CARD_HOVER_TAG).background(HoverFill.cardScrim), contentAlignment = Alignment.Center) {
        Box(Modifier.size(48.dp).background(Palette.accent, CircleShape), contentAlignment = Alignment.Center) {
            Icon(painterResource(Res.drawable.ic_play_zip), null, Modifier.size(20.dp), tint = OnAccent)
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

private val OnAccent = Color(0xFF15130F)

@Composable
private fun Modifier.cardContextClick(enabled: Boolean, onClick: (IntOffset) -> Unit): Modifier {
    val current = rememberUpdatedState(onClick)
    if (!enabled) return this
    return pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                if (event.type != PointerEventType.Press || !event.buttons.isSecondaryPressed) continue
                val change = event.changes.firstOrNull() ?: continue
                current.value(IntOffset(change.position.x.roundToInt(), change.position.y.roundToInt()))
                change.consume()
            }
        }
    }
}

private class CardPreviewPosition(private val gapPx: Int) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize,
        layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val right = anchorBounds.right + gapPx
        val fitsRight = right + popupContentSize.width <= windowSize.width
        val x = if (fitsRight || anchorBounds.left - gapPx - popupContentSize.width < 0) right
            else anchorBounds.left - gapPx - popupContentSize.width
        val y = anchorBounds.top.coerceIn(0, (windowSize.height - popupContentSize.height).coerceAtLeast(0))
        return IntOffset(x.coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0)), y)
    }
}

private class CardMenuPosition(private val at: IntOffset) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize,
        layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset = IntOffset(
        (anchorBounds.left + at.x).coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0)),
        (anchorBounds.top + at.y).coerceIn(0, (windowSize.height - popupContentSize.height).coerceAtLeast(0)),
    )
}

@Composable
private fun CardPreview(card: Card, saved: Boolean,
    onPlay: () -> Unit, onSave: () -> Unit, onInfo: () -> Unit, onHoverChanged: (Boolean) -> Unit) {
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    LaunchedEffect(hovered) { onHoverChanged(hovered) }
    Column(Modifier.width(360.dp).hoverable(hover)
        .graphicsLayer { shape = RoundedCornerShape(12.dp); shadowElevation = 24.dp.toPx() }
        .clip(RoundedCornerShape(12.dp)).background(Color(0xFF1C1C1F))
        .border(1.dp, Color.White.copy(alpha = .08f), RoundedCornerShape(12.dp))) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9).background(Palette.raised)) {
            when (val art = card.landscapeArt()) {
                is LandscapeArt.Still -> AsyncImage(art.url, card.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                is LandscapeArt.Poster -> PosterInLandscape(art.url, card.title)
                LandscapeArt.None -> MissingArtTile(card.title)
            }
            card.progress?.let { progress ->
                Box(Modifier.fillMaxWidth().align(Alignment.BottomCenter).height(3.dp).background(Color.White.copy(alpha = .2f))) {
                    Box(Modifier.fillMaxWidth(progress.toFloat().coerceIn(0f, 1f)).fillMaxHeight().background(Palette.accent))
                }
            }
        }
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val previewPlay = remember { MutableInteractionSource() }
                var previewPlayFocused by remember { mutableStateOf(false) }
                Row(Modifier.height(36.dp).pressScale(previewPlay, Motion.button)
                    .onFocusChanged { previewPlayFocused = it.isFocused }
                    .tvFocusOutline(previewPlayFocused, 18.dp, darkGap = 2.dp, width = 2.dp)
                    .clip(CircleShape).background(hoverFill(previewPlay, Palette.text, Color.White))
                    .pointerClickable(interactionSource = previewPlay, indication = null,
                        onClickLabel = if (card.progress != null) "Resume" else "Play", onClick = onPlay, shape = CircleShape, hover = null)
                    .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(painterResource(Res.drawable.ic_play_zip), null, Modifier.size(12.dp), tint = Palette.background)
                    Text(if (card.progress != null) "Resume" else "Play", color = Palette.background,
                        fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold)
                }
                PreviewCircle(if (saved) "Remove from My List" else "Add to My List", onSave) {
                    Icon(if (saved) Lucide.Check else Lucide.Plus, null, Modifier.size(18.dp), tint = Palette.text)
                }
                Spacer(Modifier.weight(1f))
                PreviewCircle("More info", onInfo) { Icon(Lucide.Info, null, Modifier.size(18.dp), tint = Palette.text) }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(card.title, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                MediaMetadata(card, fontSize = 12f, lineHeight = 16f, badgeSize = 10f)
            }
        }
    }
}

@Composable
private fun PreviewCircle(label: String, action: () -> Unit, glyph: @Composable () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    var focused by remember { mutableStateOf(false) }
    Box(Modifier.size(36.dp).pressScale(interaction, Motion.button)
        // The ring is the shared focus outline, drawn before the clip so it shows.
        .onFocusChanged { focused = it.isFocused }
        .tvFocusOutline(focused, 18.dp, darkGap = 2.dp, width = 2.dp)
        .background(hoverFill(interaction, Color.Transparent, Color.White.copy(alpha = .18f)), CircleShape)
        .clip(CircleShape).border(1.dp, Color.White.copy(alpha = .14f), CircleShape)
        .pointerClickable(interactionSource = interaction, indication = null,
            onClickLabel = label, onClick = action, shape = CircleShape, hover = null),
        contentAlignment = Alignment.Center) { glyph() }
}

@Composable
private fun CardMenu(card: Card, saved: Boolean, onDismiss: () -> Unit,
    onPlay: () -> Unit, onInfo: () -> Unit, onToggleSave: () -> Unit) {
    Column(Modifier.width(300.dp)
        .graphicsLayer { shape = RoundedCornerShape(10.dp); shadowElevation = 16.dp.toPx() }
        .tvBackdropBlur(30.dp)
        .clip(RoundedCornerShape(10.dp))
        .background(Color(0xFF242428).copy(alpha = .97f))
        .border(1.dp, Color.White.copy(alpha = .14f), RoundedCornerShape(10.dp))
        .semantics { contentDescription = "${card.title} actions" }
        .padding(5.dp)) {
        MenuItem(if (card.progress != null) "Resume" else "Play", Lucide.Play, onDismiss, onPlay)
        MenuItem("More info", Lucide.Info, onDismiss, onInfo)
        Box(Modifier.padding(horizontal = 6.dp, vertical = 4.dp).fillMaxWidth().height(1.dp)
            .background(Color.White.copy(alpha = .08f)))
        MenuItem(if (saved) "In My List" else "Add to My List",
            if (saved) Lucide.Check else Lucide.Plus, onDismiss, onToggleSave)
    }
}

@Composable
private fun MenuItem(label: String, icon: ImageVector, dismiss: () -> Unit, action: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    var focused by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().height(34.dp).pressScale(interaction, Motion.row)
        // Draw the ring before the clip on the menu's rows, otherwise the clip cuts it.
        .onFocusChanged { focused = it.isFocused }
        .tvFocusOutline(focused, 6.dp, darkGap = 2.dp, width = 2.dp)
        .clip(RoundedCornerShape(6.dp))
        .background(hoverFill(interaction, Color.Transparent, Color.White.copy(alpha = .12f)))
        .hoverable(interaction)
        .pointerClickable(interactionSource = interaction, indication = null, onClickLabel = label, shape = RoundedCornerShape(6.dp), hover = null) { dismiss(); action() }
        .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(icon, null, Modifier.size(16.dp), tint = Palette.text)
        Text(label, fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, color = Palette.text)
    }
}
