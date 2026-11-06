package org.viptv.app.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
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
import org.viptv.app.theme.Palette

internal const val PopoverMenuItemIconTag = "PopoverMenuItem.icon"

// Content that cannot report an intrinsic width (a lazy list) sizes to this.
internal val PopoverMenuWidth = 260.dp

internal val Hairline = Color.White.copy(alpha = .07f)

@Composable
internal fun PopoverMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    wash: Color? = null,
    paneTitle: String? = null,
    scale: Float = 1f,
    width: Dp = PopoverMenuWidth,
    content: @Composable ColumnScope.() -> Unit,
) {
    val motion = rememberEnterLeaveMotion(expanded,
        enter = motionSpec(tween(Motion.arrive, easing = Motion.standard)),
        leave = motionSpec(tween(Motion.leave, easing = Motion.exit)))
    if (!motion.presented) return
    val density = LocalDensity.current
    val menuShape = RoundedCornerShape(16.dp * scale)
    val washBrush = wash?.let { with(density) { Brush.verticalGradient(listOf(it.copy(alpha = .24f), Color.Transparent), endY = (88 * scale).dp.toPx()) } }
    Popup(onDismissRequest = onDismissRequest,
        popupPositionProvider = remember(density) { PopoverMenuPosition(density) },
        properties = PopupProperties(focusable = true)) {
        Column(modifier.width(width * scale)
            .graphicsLayer {
                alpha = motion.progress
                val s = 0.98f + 0.02f * motion.progress
                scaleX = s; scaleY = s
                shape = menuShape
                shadowElevation = 20.dp.toPx() * scale
            }
            .clip(menuShape)
            .background(Palette.surface)
            .then(if (washBrush == null) Modifier else Modifier.background(washBrush, menuShape))
            .border(1.dp, Hairline, menuShape)
            .then(if (paneTitle == null) Modifier else Modifier.semantics { this.paneTitle = paneTitle })) {
            content()
        }
    }
}

private class PopoverMenuPosition(private val density: Density) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection,
        popupContentSize: IntSize): IntOffset {
        val offset = with(density) { 6.dp.roundToPx() }
        val margin = with(density) { 4.dp.roundToPx() }
        val toBottom = anchorBounds.bottom + offset
        val toTop = anchorBounds.top - offset - popupContentSize.height
        val y = sequenceOf(toBottom, toTop).firstOrNull { it >= margin && it + popupContentSize.height <= windowSize.height - margin }
            ?: toBottom
        val toLeading = anchorBounds.left
        val toTrailing = anchorBounds.right - popupContentSize.width
        val toDisplayLeading = windowSize.width - offset - popupContentSize.width
        val toDisplayTrailing = anchorBounds.left
        val horizontal = if (layoutDirection == LayoutDirection.Ltr)
            sequenceOf(toLeading, toTrailing, toDisplayLeading, toDisplayTrailing)
        else sequenceOf(toTrailing, toLeading, toDisplayTrailing, toDisplayLeading)
        val x = horizontal.firstOrNull { it >= margin && it + popupContentSize.width <= windowSize.width - margin } ?: toLeading
        return IntOffset(x, y)
    }
}

@Composable
internal fun PopoverMenuItem(label: String, icon: ImageVector? = null, scale: Float = 1f,
    requester: FocusRequester? = null, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val pressed by interaction.collectIsPressedAsState()
    val active = interaction.pointerHovered() || focused || pressed
    val fill by animateColorAsState(if (active) Palette.raised else Color.Transparent, responseSpec(), label = "Popover row fill")
    val shape = RoundedCornerShape(10.dp * scale)
    Box(Modifier.padding(horizontal = 6.dp * scale).fillMaxWidth().heightIn(min = 44.dp * scale).pressScale(interaction, Motion.row).clip(shape).background(fill)
        .then(if (requester == null) Modifier else requester.requestFocusWhenPlaced())
        .pointerClickable(interaction, indication = null, role = Role.Button, onClick = onClick, shape = shape, hover = null)) {
        Row(Modifier.align(Alignment.CenterStart).padding(horizontal = 10.dp * scale, vertical = 10.dp * scale),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp * scale)) {
            if (icon != null) Icon(icon, null, Modifier.size(18.dp * scale).testTag(PopoverMenuItemIconTag), tint = if (active) Palette.text else Palette.secondary)
            Text(label, color = Palette.text, fontSize = (15 * scale).sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        FocusOutline(focused, 10.dp * scale, 2.dp, Modifier.matchParentSize())
    }
}

@Composable
internal fun PopoverMenuDivider(scale: Float = 1f) {
    Box(Modifier.padding(horizontal = 12.dp * scale, vertical = 6.dp * scale).fillMaxWidth().height(1.dp).background(Hairline))
}
