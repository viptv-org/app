package org.viptv.app.components

import androidx.compose.foundation.Indication
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
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
import androidx.compose.ui.graphics.lerp
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
 * `PointerAffordanceSourceTest` fails the build if a raw `clickable`,
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
    shape: Shape? = null, hover: Color? = HoverFill.wash, onClick: () -> Unit): Modifier =
    if (shape == null) cursorIf(enabled).clickable(enabled = enabled, onClickLabel = onClickLabel, role = role, onClick = onClick)
    else cursorIf(enabled).clickable(null, ShapedHighlight(shape, hover, null, useLocalIndication = true), enabled, onClickLabel, role, onClick)

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
 * outline, never as the bounding rectangle. Phone, tablet and TV keep exactly the
 * indication the call site asked for, so touch and D-pad feedback do not change.
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
    private var hovered = false
    private var pressed = false

    override fun onAttach() {
        pointer = currentValueOf(LocalPointerHover)
        if (!pointer) {
            val touch = if (useLocalIndication) currentValueOf(LocalIndication) else fallback
            if (touch is IndicationNodeFactory) delegate(touch.create(source))
            return
        }
        coroutineScope.launch {
            val hovers = mutableListOf<HoverInteraction.Enter>()
            val presses = mutableListOf<PressInteraction.Press>()
            source.interactions.collect { interaction ->
                when (interaction) {
                    is HoverInteraction.Enter -> hovers += interaction
                    is HoverInteraction.Exit -> hovers -= interaction.enter
                    is PressInteraction.Press -> presses += interaction
                    is PressInteraction.Release -> presses -= interaction.press
                    is PressInteraction.Cancel -> presses -= interaction.press
                }
                val nowHovered = hovers.isNotEmpty(); val nowPressed = presses.isNotEmpty()
                if (nowHovered != hovered || nowPressed != pressed) {
                    hovered = nowHovered; pressed = nowPressed
                    invalidateDraw()
                }
            }
        }
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        if (!pointer || hover == null) return
        val fill = when {
            pressed -> HoverFill.washPressed
            hovered -> hover
            else -> return
        }
        drawOutline(shape.createOutline(size, layoutDirection, this), fill)
    }
}

/** A disabled control keeps the default arrow: the hand promises a click that will not happen. */
private fun Modifier.cursorIf(enabled: Boolean): Modifier = if (enabled) handCursor() else this

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

/**
 * The design's desktop hover tokens (components.md §1, README colour table).
 * Each function takes the control's resting fill and returns its hover fill.
 */
object HoverFill {
    /** `surface-3`: secondary and destructive buttons, the selected chip. */
    val surface3 = Color(0xFF2A2A2E)
    /** `surface-4`: secondary / destructive button hover (desktop). */
    val surface4 = Color(0xFF34343A)
    /** `fill-wash`: the outline / quiet hover wash. */
    val wash = Color.White.copy(alpha = .06f)
    /** `fill-wash-pressed`: the outline / quiet pressed wash. */
    val washPressed = Color.White.copy(alpha = .04f)
    /** `player-control-hover`: transparent player buttons. */
    val playerControl = Color.White.copy(alpha = .12f)
    /** `scrim-card-hover`: the dim laid over a hovered card's art. */
    val cardScrim = Color.Black.copy(alpha = .38f)

    /** Primary (accent fill): brightens 8 %. */
    fun primary(rest: Color = Palette.accent): Color = Color(
        (rest.red * 1.08f).coerceAtMost(1f), (rest.green * 1.08f).coerceAtMost(1f),
        (rest.blue * 1.08f).coerceAtMost(1f), rest.alpha)

    /** Light (off-white fill): goes to pure white. */
    val light = Color.White

    /** Secondary / destructive (surface-3 fill): surface-4. */
    val secondary = surface4

    /** Rows and nav items: `surface-2` (README: hover / pressed rows). */
    val row = Palette.raised

    /** Outline / quiet: the resting fill with the 0.06 white wash over it. */
    fun quiet(rest: Color = Color.Transparent): Color = if (rest.alpha == 0f) wash else lerp(rest, Color.White, .06f)
}
