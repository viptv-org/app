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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
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

private val PopoverShape = RoundedCornerShape(16.dp)

@Composable
internal fun PopoverMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    wash: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val motion = rememberEnterLeaveMotion(expanded,
        enter = motionSpec(tween(Motion.arrive, easing = Motion.standard)),
        leave = motionSpec(tween(Motion.leave, easing = Motion.exit)))
    if (!motion.presented) return
    val density = LocalDensity.current
    val washBrush = wash?.let { with(density) { Brush.verticalGradient(listOf(it.copy(alpha = .24f), Color.Transparent), endY = 88.dp.toPx()) } }
    Popup(onDismissRequest = onDismissRequest,
        popupPositionProvider = remember(density) { PopoverMenuPosition(density) },
        properties = PopupProperties(focusable = true)) {
        Column(modifier.width(PopoverMenuWidth)
            .graphicsLayer {
                alpha = motion.progress
                val s = 0.98f + 0.02f * motion.progress
                scaleX = s; scaleY = s
                shape = PopoverShape
                shadowElevation = 20.dp.toPx()
            }
            .clip(PopoverShape)
            .background(Palette.surface)
            .then(if (washBrush == null) Modifier else Modifier.background(washBrush, PopoverShape))
            .border(1.dp, Hairline, PopoverShape)) {
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
internal fun PopoverMenuItem(label: String, icon: ImageVector? = null, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val pressed by interaction.collectIsPressedAsState()
    val active = interaction.pointerHovered() || focused || pressed
    val fill by animateColorAsState(if (active) Palette.raised else Color.Transparent, responseSpec(), label = "Popover row fill")
    Box(Modifier.padding(horizontal = 6.dp).fillMaxWidth().heightIn(min = 44.dp).pressScale(interaction, Motion.row).clip(RoundedCornerShape(10.dp)).background(fill)
        .pointerClickable(interaction, indication = null, role = Role.Button, onClick = onClick, shape = RoundedCornerShape(10.dp), hover = null)) {
        Row(Modifier.align(Alignment.CenterStart).padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (icon != null) Icon(icon, null, Modifier.size(18.dp).testTag(PopoverMenuItemIconTag), tint = if (active) Palette.text else Palette.secondary)
            Text(label, color = Palette.text, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        FocusOutline(focused, 10.dp, 2.dp, Modifier.matchParentSize())
    }
}

@Composable
internal fun PopoverMenuDivider() {
    Box(Modifier.padding(horizontal = 12.dp, vertical = 6.dp).fillMaxWidth().height(1.dp).background(Hairline))
}
