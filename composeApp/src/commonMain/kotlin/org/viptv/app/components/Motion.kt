package org.viptv.app.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer

internal object Motion {
    const val response = 120
    const val arrive = 200
    const val panel = 300
    const val leave = 200
    const val staggerStep = 30
    const val card = .98f
    const val button = .97f
    const val row = .99f
    const val control = .94f
    val standard = CubicBezierEasing(.2f, 0f, 0f, 1f)
    val exit = CubicBezierEasing(.3f, 0f, 1f, 1f)
}

@Composable
internal fun <T> responseSpec(): FiniteAnimationSpec<T> = motionSpec(tween(Motion.response, easing = Motion.standard))

@Composable
internal fun hoverFill(source: InteractionSource, rest: Color, hovered: Color = HoverFill.container(rest),
    enabled: Boolean = true): Color =
    animateColorAsState(if (enabled && source.pointerHovered()) hovered else rest, responseSpec(), label = "Hover fill").value

// A press that turns into a drag gives the scale back at once: a scroll must never read as a held control.
@Composable
internal fun Modifier.pressScale(source: InteractionSource, pressed: Float): Modifier {
    if (pressed >= 1f || LocalTvFocusAnchoring.current || LocalReducedMotion.current) return this
    var down by remember(source) { mutableStateOf(false) }
    var cancelled by remember(source) { mutableStateOf(false) }
    LaunchedEffect(source) {
        source.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> { down = true; cancelled = false }
                is PressInteraction.Release -> { down = false; cancelled = false }
                is PressInteraction.Cancel -> { down = false; cancelled = true }
            }
        }
    }
    val scale = remember(source) { Animatable(1f) }
    LaunchedEffect(down, cancelled) {
        val spec: AnimationSpec<Float> = when {
            cancelled -> snap()
            down -> tween(Motion.response, easing = Motion.standard)
            else -> tween(Motion.arrive, easing = Motion.standard)
        }
        scale.animateTo(if (down) pressed else 1f, spec)
    }
    return graphicsLayer { scaleX = scale.value; scaleY = scale.value }
}
