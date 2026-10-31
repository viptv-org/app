package org.viptv.app.components

import androidx.compose.animation.VectorConverter
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import kotlinx.coroutines.Job
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

val LocalPointerHover = staticCompositionLocalOf { false }

fun Modifier.handCursor(): Modifier = pointerHoverIcon(PointerIcon.Hand)

fun Modifier.pointerClickable(enabled: Boolean = true, onClickLabel: String? = null, role: Role? = null,
    interactionSource: MutableInteractionSource? = null, shape: Shape? = null, hover: Color? = HoverFill.wash,
    onClick: () -> Unit): Modifier =
    if (shape == null) cursorIf(enabled).clickable(enabled, onClickLabel, role, interactionSource, onClick)
    else cursorIf(enabled).clickable(interactionSource, ShapedHighlight(shape, hover, null, useLocalIndication = true), enabled, onClickLabel, role, onClick)

fun Modifier.pointerClickable(interactionSource: MutableInteractionSource?, indication: Indication?,
    enabled: Boolean = true, onClickLabel: String? = null, role: Role? = null,
    shape: Shape? = null, hover: Color? = HoverFill.wash, onClick: () -> Unit): Modifier =
    cursorIf(enabled).clickable(interactionSource, shaped(indication, shape, hover), enabled, onClickLabel, role, onClick)

fun Modifier.pointerSelectable(selected: Boolean, enabled: Boolean = true, role: Role? = null,
    shape: Shape? = null, hover: Color? = HoverFill.wash, onClick: () -> Unit): Modifier =
    if (shape == null) cursorIf(enabled).selectable(selected, enabled = enabled, role = role, onClick = onClick)
    else cursorIf(enabled).selectable(selected, null, ShapedHighlight(shape, hover, null, useLocalIndication = true), enabled, role, onClick)

fun Modifier.pointerSelectable(selected: Boolean, interactionSource: MutableInteractionSource?, indication: Indication?,
    enabled: Boolean = true, role: Role? = null, shape: Shape? = null, hover: Color? = HoverFill.wash,
    onClick: () -> Unit): Modifier =
    cursorIf(enabled).selectable(selected, interactionSource, shaped(indication, shape, hover), enabled, role, onClick)

fun Modifier.pointerToggleable(value: Boolean, enabled: Boolean = true, role: Role? = null,
    shape: Shape? = null, hover: Color? = HoverFill.wash, onValueChange: (Boolean) -> Unit): Modifier =
    if (shape == null) cursorIf(enabled).toggleable(value, enabled = enabled, role = role, onValueChange = onValueChange)
    else cursorIf(enabled).toggleable(value, null, ShapedHighlight(shape, hover, null, useLocalIndication = true), enabled, role, onValueChange)

fun Modifier.pointerToggleable(value: Boolean, interactionSource: MutableInteractionSource?, indication: Indication?,
    enabled: Boolean = true, role: Role? = null, shape: Shape? = null, hover: Color? = HoverFill.wash,
    onValueChange: (Boolean) -> Unit): Modifier =
    cursorIf(enabled).toggleable(value, interactionSource, shaped(indication, shape, hover), enabled, role, onValueChange)

private fun shaped(indication: Indication?, shape: Shape?, hover: Color?): Indication? =
    if (shape == null) indication else ShapedHighlight(shape, hover, indication, useLocalIndication = false)

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
    private var fill = Color.Transparent
    private var fillJob: Job? = null
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
                    animateFill()
                }
            }
        }
    }

    // A recycled list item attaches again; it must not stack a second touch indication.
    override fun onDetach() {
        touchNode?.let { undelegate(it) }
        touchNode = null
        fillJob?.cancel()
        fill = Color.Transparent
        hovered = false; pressed = false; focused = false
    }

    private fun animateFill() {
        val hover = hover ?: return
        val target = when {
            pressed -> HoverFill.washPressed
            hovered -> hover
            else -> Color.Transparent
        }
        fillJob?.cancel()
        if (currentValueOf(LocalReducedMotion)) { fill = target; invalidateDraw(); return }
        fillJob = coroutineScope.launch {
            animate(Color.VectorConverter(ColorSpaces.Srgb), fill, target, animationSpec = tween(Motion.response, easing = Motion.standard)) { value, _ ->
                fill = value
                invalidateDraw()
            }
        }
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        if (!pointer || hover == null) return
        val outline = shape.createOutline(size, layoutDirection, this)
        if (fill.alpha > 0f) drawOutline(outline, fill)
        if (focused && focusLayer) drawOutline(outline, HoverFill.focusLayer)
    }
}

// A disabled control keeps the default arrow: the hand promises a click that will not happen.
internal fun Modifier.cursorIf(enabled: Boolean): Modifier = if (enabled) handCursor() else this

@Composable
fun InteractionSource.pointerHovered(): Boolean {
    val hovered by collectIsHoveredAsState()
    return hovered && LocalPointerHover.current
}

object HoverFill {
    val surface4 = Color(0xFF34343A)
    val row = Color(0xFF212124)
    val wash = Color.White.copy(alpha = .06f)
    val washPressed = Color.White.copy(alpha = .04f)
    internal val focusLayer = Color.White.copy(alpha = .1f)
    val cardScrim = Color.Black.copy(alpha = .38f)

    fun primary(rest: Color = Palette.accent): Color = Color(
        (rest.red * 1.08f).coerceAtMost(1f), (rest.green * 1.08f).coerceAtMost(1f),
        (rest.blue * 1.08f).coerceAtMost(1f), rest.alpha)

    fun container(rest: Color): Color = when {
        rest.alpha == 0f -> wash
        rest == Palette.accent -> primary(rest)
        rest.alpha < 1f -> rest.copy(alpha = (rest.alpha + wash.alpha).coerceAtMost(1f))
        rest.luminance() > .5f -> Color.White
        else -> surface4
    }
}
