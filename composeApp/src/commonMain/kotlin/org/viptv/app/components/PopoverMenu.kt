package org.viptv.app.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.viptv.app.theme.Palette

internal const val PopoverMenuItemIconTag = "PopoverMenuItem.icon"

/** The popover card's width; content that cannot report an intrinsic width (a lazy list) sizes to it. */
internal val PopoverMenuWidth = 260.dp

/** Design token `line.hairline`: white at 0.07. */
private val Hairline = Color.White.copy(alpha = .07f)

/**
 * The design system's anchored popover (components.md section 8): a surface-1 card with a
 * hairline border and radius 16, opened below the control it belongs to, with no scrim. Its
 * shadow elevation approximates the `shadow.popover` token. Dismissal (outside click, Back,
 * Escape) and keyboard focus come from [DropdownMenu]. [wash] tints the top of the card,
 * fading out over the first 88 dp.
 */
@Composable
internal fun PopoverMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    wash: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    DropdownMenu(
        expanded, onDismissRequest,
        modifier = modifier.width(PopoverMenuWidth).then(if (wash == null) Modifier else Modifier.drawBehind {
            drawRect(Brush.verticalGradient(listOf(wash.copy(alpha = .24f), Color.Transparent), endY = 88.dp.toPx()))
        }),
        offset = DpOffset(0.dp, 6.dp),
        shape = RoundedCornerShape(16.dp),
        containerColor = Palette.surface,
        tonalElevation = 0.dp,
        shadowElevation = 20.dp,
        border = BorderStroke(1.dp, Hairline),
        content = content,
    )
}

/**
 * A popover row: an optional leading Lucide icon and a label, 44 tall. Hover, focus and press fill it with
 * surface-2; a press also applies the phone row scale (`pressed-scale-row`, 0.99).
 */
@Composable
internal fun PopoverMenuItem(label: String, icon: ImageVector? = null, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val focused by interaction.collectIsFocusedAsState()
    val pressed by interaction.collectIsPressedAsState()
    val active = hovered || focused || pressed
    val fill by animateColorAsState(if (active) Palette.raised else Color.Transparent, tween(150), label = "Popover row fill")
    val scale by animateFloatAsState(if (pressed) .99f else 1f, tween(150), label = "Popover row press")
    Box(Modifier.padding(horizontal = 6.dp).fillMaxWidth().heightIn(min = 44.dp).scale(scale).clip(RoundedCornerShape(10.dp)).background(fill)
        .pointerClickable(interaction, indication = null, role = Role.Button, onClick = onClick, shape = RoundedCornerShape(10.dp), hover = null)) {
        Row(Modifier.align(Alignment.CenterStart).padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (icon != null) Icon(icon, null, Modifier.size(18.dp).testTag(PopoverMenuItemIconTag), tint = if (active) Palette.text else Palette.secondary)
            Text(label, color = Palette.text, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        FocusOutline(focused, 10.dp, 2.dp, Modifier.matchParentSize())
    }
}

/** A hairline between groups of popover rows. */
@Composable
internal fun PopoverMenuDivider() {
    Box(Modifier.padding(horizontal = 12.dp, vertical = 6.dp).fillMaxWidth().height(1.dp).background(Hairline))
}
