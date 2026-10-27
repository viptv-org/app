package org.viptv.app.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonElevation
import androidx.compose.material3.CheckboxColors
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.MenuItemColors
import androidx.compose.material3.SelectableChipColors
import androidx.compose.material3.SelectableChipElevation
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.contentColorFor
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.RippleConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * Material 3's clickable components with the hand cursor (app#56).
 *
 * Each wrapper keeps Material's exact name, parameters and defaults and only adds
 * [handCursor] to the modifier when the control is enabled. A file opts in by
 * importing the wrapper by name (`import org.viptv.app.components.TextButton`):
 * an explicit import outranks the `androidx.compose.material3.*` star import, so
 * the call sites stay unchanged. On desktop and web the buttons also take the
 * design's hover fill (components.md §1). `PointerAffordanceSourceTest` fails when a file
 * calls one of these components without that import.
 */

@Composable
fun Button(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    shape: Shape = ButtonDefaults.shape, colors: ButtonColors = ButtonDefaults.buttonColors(),
    elevation: ButtonElevation? = ButtonDefaults.buttonElevation(), border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding, interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val hovered = enabled && source.pointerHovered()
    DesignHoverOnly { androidx.compose.material3.Button(onClick, modifier.cursor(enabled), enabled, shape,
        if (hovered) colors.copy(containerColor = HoverFill.container(colors.containerColor)) else colors,
        elevation, border, contentPadding, source, content) }
}

@Composable
fun OutlinedButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    shape: Shape = ButtonDefaults.outlinedShape, colors: ButtonColors = ButtonDefaults.outlinedButtonColors(),
    elevation: ButtonElevation? = null, border: BorderStroke? = ButtonDefaults.outlinedButtonBorder(enabled),
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding, interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val hovered = enabled && source.pointerHovered()
    DesignHoverOnly { androidx.compose.material3.OutlinedButton(onClick, modifier.cursor(enabled), enabled, shape,
        if (hovered) colors.copy(containerColor = HoverFill.container(colors.containerColor)) else colors,
        elevation, border, contentPadding, source, content) }
}

@Composable
fun TextButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    shape: Shape = ButtonDefaults.textShape, colors: ButtonColors = ButtonDefaults.textButtonColors(),
    elevation: ButtonElevation? = null, border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.TextButtonContentPadding, interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val hovered = enabled && source.pointerHovered()
    DesignHoverOnly { androidx.compose.material3.TextButton(onClick, modifier.cursor(enabled), enabled, shape,
        if (hovered) colors.copy(containerColor = HoverFill.container(colors.containerColor)) else colors,
        elevation, border, contentPadding, source, content) }
}

@Composable
fun IconButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    colors: IconButtonColors = IconButtonDefaults.iconButtonColors(), interactionSource: MutableInteractionSource? = null,
    shape: Shape = IconButtonDefaults.standardShape, content: @Composable () -> Unit) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val hovered = enabled && source.pointerHovered()
    DesignHoverOnly { androidx.compose.material3.IconButton(onClick, modifier.cursor(enabled), enabled,
        if (hovered) colors.copy(containerColor = HoverFill.container(colors.containerColor)) else colors, source, shape, content) }
}

@Composable
fun Checkbox(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier,
    enabled: Boolean = true, colors: CheckboxColors = CheckboxDefaults.colors(), interactionSource: MutableInteractionSource? = null) =
    androidx.compose.material3.Checkbox(checked, onCheckedChange, modifier.cursor(enabled && onCheckedChange != null), enabled, colors, interactionSource)

@Composable
fun Switch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier,
    thumbContent: (@Composable () -> Unit)? = null, enabled: Boolean = true, colors: SwitchColors = SwitchDefaults.colors(),
    interactionSource: MutableInteractionSource? = null) =
    androidx.compose.material3.Switch(checked, onCheckedChange, modifier.cursor(enabled && onCheckedChange != null), thumbContent, enabled, colors, interactionSource)

@Composable
fun FilterChip(selected: Boolean, onClick: () -> Unit, label: @Composable () -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, leadingIcon: @Composable (() -> Unit)? = null, trailingIcon: @Composable (() -> Unit)? = null,
    shape: Shape = FilterChipDefaults.shape, colors: SelectableChipColors = FilterChipDefaults.filterChipColors(),
    elevation: SelectableChipElevation? = FilterChipDefaults.filterChipElevation(),
    border: BorderStroke? = FilterChipDefaults.filterChipBorder(enabled, selected), interactionSource: MutableInteractionSource? = null) =
    androidx.compose.material3.FilterChip(selected, onClick, label, modifier.cursor(enabled), enabled, leadingIcon, trailingIcon,
        shape, colors, elevation, border, interactionSource)

@Composable
fun DropdownMenuItem(text: @Composable () -> Unit, onClick: () -> Unit, modifier: Modifier = Modifier,
    leadingIcon: @Composable (() -> Unit)? = null, trailingIcon: @Composable (() -> Unit)? = null, enabled: Boolean = true,
    colors: MenuItemColors = MenuDefaults.itemColors(), contentPadding: PaddingValues = MenuDefaults.DropdownMenuItemContentPadding,
    interactionSource: MutableInteractionSource? = null) =
    androidx.compose.material3.DropdownMenuItem(text, onClick, modifier.cursor(enabled), leadingIcon, trailingIcon, enabled,
        colors, contentPadding, interactionSource)

/** The clickable `Surface(onClick = …)` overload. The plain Surface stays Material's. */
@Composable
fun Surface(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, shape: Shape = RectangleShape,
    color: Color = MaterialTheme.colorScheme.surface, contentColor: Color = contentColorFor(color),
    tonalElevation: Dp = 0.dp, shadowElevation: Dp = 0.dp, border: BorderStroke? = null,
    interactionSource: MutableInteractionSource? = null, content: @Composable () -> Unit) =
    androidx.compose.material3.Surface(onClick, modifier.cursor(enabled), enabled, shape, color, contentColor,
        tonalElevation, shadowElevation, border, interactionSource, content)

@Composable
fun Tab(selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    text: @Composable (() -> Unit)? = null, icon: @Composable (() -> Unit)? = null,
    selectedContentColor: Color = LocalContentColor.current, unselectedContentColor: Color = selectedContentColor,
    interactionSource: MutableInteractionSource? = null) =
    androidx.compose.material3.Tab(selected, onClick, modifier.cursor(enabled), enabled, text, icon,
        selectedContentColor, unselectedContentColor, interactionSource)

private fun Modifier.cursor(enabled: Boolean): Modifier = if (enabled) handCursor() else this

/**
 * On desktop and web the buttons above draw the design's hover fill themselves
 * ([HoverFill.container]), so Material's own hover state layer is switched off
 * there; press, focus and drag layers keep the surrounding configuration.
 */
@Composable
private fun DesignHoverOnly(content: @Composable () -> Unit) {
    if (!LocalPointerHover.current) return content()
    val current = LocalRippleConfiguration.current ?: return content()
    val alpha = current.rippleAlpha
    CompositionLocalProvider(LocalRippleConfiguration provides RippleConfiguration(current.color, RippleAlpha(
        draggedAlpha = alpha?.draggedAlpha ?: .16f, focusedAlpha = alpha?.focusedAlpha ?: .1f,
        hoveredAlpha = 0f, pressedAlpha = alpha?.pressedAlpha ?: .1f)), content = content)
}
