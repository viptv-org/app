package org.viptv.app.components

import androidx.compose.foundation.Indication
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.invalidateDraw
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.Role
import org.viptv.app.theme.Palette

/**
 * Pointer affordances for desktop and web (design platform "D").
 *
 * Every clickable control in the app goes through [pointerClickable] (or
 * [pointerSelectable] / [pointerToggleable]) so the mouse cursor becomes a hand
 * over it. The cursor is harmless where there is no pointer, so it is applied on
 * every platform. Hover *visuals* are different: phone, tablet and TV must stay
 * exactly as they are, so they read [pointerHovered], which is only true where
 * [LocalPointerHover] says the screen is driven by a mouse.
 *
 * `PointerAffordanceTest.everyClickableInTheAppGoesThroughThePointerAffordances` fails the build if a raw `clickable`,
 * `selectable` or `toggleable` is added anywhere else, so new UI gets the hand
 * cursor by default.
 */
val LocalPointerHover = staticCompositionLocalOf { false }

/** The hand cursor over a control that does something when clicked. */
fun Modifier.handCursor(): Modifier = pointerHoverIcon(PointerIcon.Hand)

/**
 * [clickable] with the hand cursor.
 *
 * Pass the control's [shape] (the same one its `background` / `border` uses) and
 * its hover and press highlight are drawn inside that shape on desktop and web,
 * so a rounded pill never gets a square highlight (app#56). [hover] is the hover
 * fill, by default the design's 0.06 white wash; pass `null` when the control
 * draws its own hover state. Without a shape the call is plain `clickable` plus
 * the cursor, for controls that are square on purpose.
 */
fun Modifier.pointerClickable(enabled: Boolean = true, onClickLabel: String? = null, role: Role? = null,
    interactionSource: MutableInteractionSource? = null, shape: Shape? = null, hover: Color? = HoverFill.wash,
    onClick: () -> Unit): Modifier =
    if (shape == null) cursorIf(enabled).clickable(enabled, onClickLabel, role, interactionSource, onClick)
    else cursorIf(enabled).clickable(interactionSource, ShapedHighlight(shape, hover, null, useLocalIndication = true), enabled, onClickLabel, role, onClick)

/** [clickable] with the hand cursor; see the other overload for [shape] and [hover]. */
fun Modifier.pointerClickable(interactionSource: MutableInteractionSource?, indication: Indication?,
    enabled: Boolean = true, onClickLabel: String? = null, role: Role? = null,
    shape: Shape? = null, hover: Color? = HoverFill.wash, onClick: () -> Unit): Modifier =
    cursorIf(enabled).clickable(interactionSource, shaped(indication, shape, hover), enabled, onClickLabel, role, onClick)

/** [selectable] with the hand cursor. */
fun Modifier.pointerSelectable(selected: Boolean, enabled: Boolean = true, role: Role? = null,
    shape: Shape? = null, hover: Color? = HoverFill.wash, onClick: () -> Unit): Modifier =
    if (shape == null) cursorIf(enabled).selectable(selected, enabled = enabled, role = role, onClick = onClick)
    else cursorIf(enabled).selectable(selected, null, ShapedHighlight(shape, hover, null, useLocalIndication = true), enabled, role, onClick)

/** [selectable] with the hand cursor. */
fun Modifier.pointerSelectable(selected: Boolean, interactionSource: MutableInteractionSource?, indication: Indication?,
    enabled: Boolean = true, role: Role? = null, shape: Shape? = null, hover: Color? = HoverFill.wash,
    onClick: () -> Unit): Modifier =
    cursorIf(enabled).selectable(selected, interactionSource, shaped(indication, shape, hover), enabled, role, onClick)

/** [toggleable] with the hand cursor. */
fun Modifier.pointerToggleable(value: Boolean, enabled: Boolean = true, role: Role? = null,
    shape: Shape? = null, hover: Color? = HoverFill.wash, onValueChange: (Boolean) -> Unit): Modifier =
    if (shape == null) cursorIf(enabled).toggleable(value, enabled = enabled, role = role, onValueChange = onValueChange)
    else cursorIf(enabled).toggleable(value, null, ShapedHighlight(shape, hover, null, useLocalIndication = true), enabled, role, onValueChange)

/** [toggleable] with the hand cursor. */
fun Modifier.pointerToggleable(value: Boolean, interactionSource: MutableInteractionSource?, indication: Indication?,
    enabled: Boolean = true, role: Role? = null, shape: Shape? = null, hover: Color? = HoverFill.wash,
    onValueChange: (Boolean) -> Unit): Modifier =
    cursorIf(enabled).toggleable(value, interactionSource, shaped(indication, shape, hover), enabled, role, onValueChange)

private fun shaped(indication: Indication?, shape: Shape?, hover: Color?): Indication? =
    if (shape == null) indication else ShapedHighlight(shape, hover, indication, useLocalIndication = false)

/**
 * The hover / press highlight of a shaped control.
 *
 * Desktop and web ([LocalPointerHover]): hover fills the shape with [hover]
 * (`fill-wash`) and a press with `fill-wash-pressed`, both drawn as the shape's
 * outline, never as the bounding rectangle. A control that asked for an
 * indication keeps keyboard-focus feedback, as a shaped layer like Material's.
 * Phone, tablet and TV keep exactly the indication the call site asked for, so
 * touch and D-pad feedback do not change.
 */
private class ShapedHighlight(
    private val shape: Shape,
    private val hover: Color?,
    private val fallback: Indication?,
    private val useLocalIndication: Boolean,
) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode =
        ShapedHighlightNode(interactionSource, shape, hover, fallback, useLocalIndication)
    override fun equals(other: Any?) = other is ShapedHighlight && shape == other.shape && hover == other.hover &&
        fallback == other.fallback && useLocalIndication == other.useLocalIndication
    override fun hashCode() = ((shape.hashCode() * 31 + hover.hashCode()) * 31 + fallback.hashCode()) * 31 + useLocalIndication.hashCode()
}

private class ShapedHighlightNode(
    private val source: InteractionSource,
    private val shape: Shape,
    private val hover: Color?,
    private val fallback: Indication?,
    private val useLocalIndication: Boolean,
) : DelegatingNode(), DrawModifierNode, CompositionLocalConsumerModifierNode {
    private var pointer = false
    private var focusLayer = false
    private var hovered = false
    private var pressed = false
    private var focused = false
    private var touchNode: DelegatableNode? = null

    override fun onAttach() {
        pointer = currentValueOf(LocalPointerHover)
        val asked = if (useLocalIndication) currentValueOf(LocalIndication) else fallback
        if (!pointer) {
            if (asked is IndicationNodeFactory) touchNode = delegate(asked.create(source))
            return
        }
        focusLayer = asked != null && hover != null
        coroutineScope.launch {
            val hovers = mutableListOf<HoverInteraction.Enter>()
            val presses = mutableListOf<PressInteraction.Press>()
            val focuses = mutableListOf<FocusInteraction.Focus>()
            source.interactions.collect { interaction ->
                when (interaction) {
                    is HoverInteraction.Enter -> hovers += interaction
                    is HoverInteraction.Exit -> hovers -= interaction.enter
                    is PressInteraction.Press -> presses += interaction
                    is PressInteraction.Release -> presses -= interaction.press
                    is PressInteraction.Cancel -> presses -= interaction.press
                    is FocusInteraction.Focus -> focuses += interaction
                    is FocusInteraction.Unfocus -> focuses -= interaction.focus
                }
                val nowHovered = hovers.isNotEmpty(); val nowPressed = presses.isNotEmpty(); val nowFocused = focuses.isNotEmpty()
                if (nowHovered != hovered || nowPressed != pressed || nowFocused != focused) {
                    hovered = nowHovered; pressed = nowPressed; focused = nowFocused
                    invalidateDraw()
                }
            }
        }
    }

    /** A recycled list item attaches again; it must not stack a second touch indication. */
    override fun onDetach() {
        touchNode?.let { undelegate(it) }
        touchNode = null
        hovered = false; pressed = false; focused = false
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        if (!pointer || hover == null) return
        val fill = when {
            pressed -> HoverFill.washPressed
            hovered -> hover
            else -> null
        }
        val outline = shape.createOutline(size, layoutDirection, this)
        if (fill != null) drawOutline(outline, fill)
        if (focused && focusLayer) drawOutline(outline, HoverFill.focusLayer)
    }
}

/** A disabled control keeps the default arrow: the hand promises a click that will not happen. */
internal fun Modifier.cursorIf(enabled: Boolean): Modifier = if (enabled) handCursor() else this

/**
 * True while a mouse rests on the control, and only on desktop and web. Pass the
 * same [InteractionSource] the control's `pointerClickable` uses (or a
 * `hoverable` one).
 */
@Composable
fun InteractionSource.pointerHovered(): Boolean {
    val hovered by collectIsHoveredAsState()
    return hovered && LocalPointerHover.current
}

/** The design's desktop hover tokens (components.md §1 and §6, README colour table, tokens.css). */
object HoverFill {
    /** `surface-4`: secondary / destructive button hover (desktop). */
    val surface4 = Color(0xFF34343A)
    /** `surface-2`: a hovered row, such as an unselected side-navigation item. */
    val row = Color(0xFF212124)
    /** `fill-wash`: the outline / quiet hover wash. */
    val wash = Color.White.copy(alpha = .06f)
    /** `fill-wash-pressed`: the outline / quiet pressed wash. */
    val washPressed = Color.White.copy(alpha = .04f)
    /** Keyboard focus on a shaped control that asked for an indication: Material's 0.1 focus layer, kept in shape. */
    internal val focusLayer = Color.White.copy(alpha = .1f)
    /** `scrim-card-hover`: the dim laid over a hovered card's art. */
    val cardScrim = Color.Black.copy(alpha = .38f)

    /** Primary (accent fill): brightens 8 %. */
    fun primary(rest: Color = Palette.accent): Color = Color(
        (rest.red * 1.08f).coerceAtMost(1f), (rest.green * 1.08f).coerceAtMost(1f),
        (rest.blue * 1.08f).coerceAtMost(1f), rest.alpha)

    /**
     * The hover fill for a control resting on [rest], by the button kind that
     * fill means: transparent (outline / quiet) takes the wash, the accent
     * brightens, an off-white (light) fill goes white, a translucent fill gains
     * the wash and an opaque dark fill (secondary / destructive) goes to surface-4.
     */
    fun container(rest: Color): Color = when {
        rest.alpha == 0f -> wash
        rest == Palette.accent -> primary(rest)
        rest.alpha < 1f -> rest.copy(alpha = (rest.alpha + wash.alpha).coerceAtMost(1f))
        rest.luminance() > .5f -> Color.White
        else -> surface4
    }
}
