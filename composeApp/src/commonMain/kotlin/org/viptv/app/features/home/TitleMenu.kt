package org.viptv.app.features.home

import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupPositionProvider
import com.composables.icons.lucide.Circle
import com.composables.icons.lucide.CircleCheck
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.History
import com.composables.icons.lucide.Info
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Play
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Rewind
import com.composables.icons.lucide.Server
import com.composables.icons.lucide.Trash2
import com.composables.icons.lucide.X
import org.viptv.app.components.ListRemoval
import org.viptv.app.components.MutationFailure
import org.viptv.app.components.hoverFill
import org.viptv.app.components.isWatched
import org.viptv.app.components.motionSpec
import org.viptv.app.components.pointerClickable
import org.viptv.app.components.pressScale
import org.viptv.app.components.rememberEnterLeaveMotion
import org.viptv.app.components.tvBackdropBlur
import org.viptv.app.components.tvFocusOutline
import org.viptv.app.components.Motion
import org.viptv.app.components.LocalPendingMutation
import org.viptv.app.components.MenuMetrics
import org.viptv.app.components.MutationSpinner
import org.viptv.app.state.saveTarget
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.Card

internal const val RESUME_PREVIOUS_LABEL = "Resume previous episode"
internal const val CHOOSE_SOURCE_LABEL = "Choose source"
internal const val MARK_WATCHED_LABEL = "Mark watched"
internal const val MARK_UNWATCHED_LABEL = "Mark unwatched"
internal const val WATCH_FROM_START_LABEL = "Watch from the beginning"
internal const val CANCEL_LABEL = "Cancel"

// A movie or a specific episode has one source and one watch state; a live channel or a bare series does not.
internal fun Card.offersTitleActions(): Boolean = identity.kind == "movie" || identity.episode != null

internal fun Card.resumesPreviousEpisode(removal: ListRemoval?): Boolean =
    removal?.continueWatching == true && identity.episode != null && progress != null

@Composable
internal fun CardMenu(card: Card, saved: Boolean, removal: ListRemoval?, actions: CardActions?,
    onDismiss: () -> Unit, onPlay: () -> Unit, onInfo: () -> Unit, onToggleSave: (() -> Unit)?,
    visible: Boolean = true, onClosed: () -> Unit = {}, scale: Float = 1f, television: Boolean = false) {
    val metrics = MenuMetrics(television, scale)
    val menuShape = RoundedCornerShape(if (television) metrics.menuRadius else 10.dp * scale)
    val toggleSave = onToggleSave?.takeIf { removal?.replacesSave != true }
    val previous = card.resumesPreviousEpisode(removal)
    val watched = card.isWatched()
    val motion = rememberEnterLeaveMotion(visible,
        enter = motionSpec(tween(Motion.arrive, easing = Motion.standard)),
        leave = motionSpec(tween(Motion.leave, easing = Motion.exit)))
    LaunchedEffect(motion.presented) { if (!motion.presented) onClosed() }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { first.requestFocus() }
    Column(Modifier.width(if (television) metrics.width else 300.dp * scale)
        .onPreviewKeyEvent { event ->
            if (event.type == KeyEventType.KeyDown && (event.key == Key.Back || event.key == Key.Escape)) { onDismiss(); true }
            else false
        }
        .graphicsLayer {
            shape = menuShape; shadowElevation = (if (television) metrics.shadow else 16.dp * scale).toPx()
            alpha = motion.progress
            val s = 0.98f + 0.02f * motion.progress
            scaleX = s; scaleY = s
        }
        .tvBackdropBlur(30.dp * scale)
        .clip(menuShape)
        .background(Color(0xFF242428).copy(alpha = .97f))
        .border(1.dp, Color.White.copy(alpha = .14f), menuShape)
        .semantics { contentDescription = "${card.title} actions" }
        .padding(if (television) metrics.outerPadding else 5.dp * scale)) {
        MenuItem(if (previous) RESUME_PREVIOUS_LABEL else if (card.progress != null) "Resume" else "Play",
            if (previous) Lucide.History else Lucide.Play, onDismiss, onPlay, scale = scale, requester = first, metrics = metrics)
        val titled = card.offersTitleActions()
        actions?.chooseSource?.takeIf { titled }?.let { choose ->
            MenuItem(CHOOSE_SOURCE_LABEL, Lucide.Server, onDismiss, action = { choose(card) }, scale = scale, metrics = metrics)
        }
        actions?.markWatched?.takeIf { titled }?.let { mark ->
            MenuItem(if (watched) MARK_UNWATCHED_LABEL else MARK_WATCHED_LABEL,
                if (watched) Lucide.Circle else Lucide.CircleCheck, onDismiss, action = { mark(card) }, scale = scale, metrics = metrics)
        }
        actions?.playFromStart?.takeIf { titled }?.let { restart ->
            MenuItem(WATCH_FROM_START_LABEL, Lucide.Rewind, onDismiss, action = { restart(card) }, scale = scale, metrics = metrics)
        }
        if (toggleSave != null || removal != null) {
            MenuDivider(metrics)
            if (toggleSave != null) {
                val pending = LocalPendingMutation.current.isPending(saveTarget(card))
                MenuItem(if (pending) "Saving…" else if (saved) "In My List" else "Add to My List",
                    if (saved) Lucide.Check else Lucide.Plus, onDismiss, toggleSave, scale = scale, pending = pending, metrics = metrics)
                MutationFailure(saveTarget(card), Modifier.padding(start = if (television) metrics.rowPaddingH else 10.dp * scale,
                    end = if (television) metrics.rowPaddingH else 10.dp * scale, top = 2.dp * scale, bottom = 6.dp * scale),
                    scale = if (television) scale * 1.7f else scale)
            }
            if (removal != null) MenuItem(removal.label, Lucide.Trash2, onDismiss, action = { removal.request(card) }, danger = true, scale = scale, metrics = metrics)
        }
        MenuDivider(metrics)
        MenuItem("More info", Lucide.Info, onDismiss, onInfo, scale = scale, metrics = metrics)
        MenuItem(CANCEL_LABEL, Lucide.X, onDismiss, action = {}, scale = scale, metrics = metrics)
    }
}

@Composable
private fun MenuDivider(metrics: MenuMetrics) {
    val scale = metrics.scale
    Box(Modifier.padding(horizontal = if (metrics.television) metrics.dividerHorizontal else 6.dp * scale,
        vertical = if (metrics.television) metrics.dividerVertical else 4.dp * scale).fillMaxWidth().height(1.dp)
        .background(Color.White.copy(alpha = .08f)))
}

@Composable
private fun MenuItem(label: String, icon: ImageVector, dismiss: () -> Unit, action: () -> Unit, danger: Boolean = false, scale: Float = 1f,
    requester: FocusRequester? = null, pending: Boolean = false, metrics: MenuMetrics? = null) {
    val interaction = remember { MutableInteractionSource() }
    var focused by remember { mutableStateOf(false) }
    val tv = metrics?.takeIf { it.television }
    val shape = RoundedCornerShape(tv?.rowRadius ?: (6.dp * scale))
    Row(Modifier.fillMaxWidth().then(if (tv == null) Modifier.height(34.dp * scale) else Modifier.heightIn(min = tv.rowMinHeight))
        .pressScale(interaction, Motion.row)
        // Draw the ring before the clip on the menu's rows, otherwise the clip cuts it.
        .onFocusChanged { focused = it.isFocused }
        .tvFocusOutline(focused, tv?.rowRadius ?: (6.dp * scale), if (tv == null) scale else 1f,
            darkGap = tv?.focusGap ?: 2.dp, width = tv?.focusRing ?: 2.dp)
        .clip(shape)
        .background(hoverFill(interaction, Color.Transparent, Color.White.copy(alpha = .12f)))
        .hoverable(interaction)
        .then(if (requester != null) Modifier.focusRequester(requester) else Modifier)
        .pointerClickable(interactionSource = interaction, indication = null, onClickLabel = label, shape = shape, hover = null) { dismiss(); if (!pending) action() }
        .padding(horizontal = tv?.rowPaddingH ?: (10.dp * scale), vertical = tv?.rowPaddingV ?: 0.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(tv?.iconGap ?: (10.dp * scale))) {
        if (pending) MutationSpinner(tv?.iconSize ?: (16.dp * scale), scale = scale)
        else Icon(icon, null, Modifier.size(tv?.iconSize ?: (16.dp * scale)), tint = if (danger) Palette.danger else Palette.text)
        Text(label, fontSize = tv?.textSize ?: (13.sp * scale), lineHeight = tv?.lineHeight ?: (16.sp * scale),
            fontWeight = FontWeight.Medium, color = if (danger) Palette.danger else Palette.text)
    }
}

// Anchors the menu below a card when there is no pointer position to open against (TV hold).
internal class TvCardMenuPosition : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val margin = 24
        val x = (anchorBounds.left + (anchorBounds.width - popupContentSize.width) / 2)
            .coerceIn(margin, (windowSize.width - popupContentSize.width - margin).coerceAtLeast(margin))
        val below = anchorBounds.bottom + margin
        val above = anchorBounds.top - margin - popupContentSize.height
        val y = when {
            below + popupContentSize.height <= windowSize.height - margin -> below
            above >= margin -> above
            else -> margin
        }
        return IntOffset(x, y)
    }
}