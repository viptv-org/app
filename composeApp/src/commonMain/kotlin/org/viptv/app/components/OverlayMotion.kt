package org.viptv.app.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

internal class OverlayMotion(val container: Float, val scrim: Float, val presented: Boolean)

// The container travels over one spec and the scrim over another; both leave over the same spec,
// from wherever they are, so a dismissal that arrives mid-open never jumps to the open position.
@Composable
internal fun rememberOverlayMotion(
    visible: Boolean,
    containerEnter: FiniteAnimationSpec<Float>,
    scrimEnter: FiniteAnimationSpec<Float>,
    leave: FiniteAnimationSpec<Float>,
): OverlayMotion {
    val container = remember { Animatable(0f) }
    val scrim = remember { Animatable(0f) }
    var presented by remember { mutableStateOf(visible) }
    LaunchedEffect(visible) {
        if (visible) {
            presented = true
            coroutineScope {
                launch { container.snapTo(0f); container.animateTo(1f, containerEnter) }
                launch { scrim.snapTo(0f); scrim.animateTo(1f, scrimEnter) }
            }
        } else if (presented) {
            coroutineScope {
                launch { container.animateTo(0f, leave) }
                launch { scrim.animateTo(0f, leave) }
            }
            presented = false
        }
    }
    return OverlayMotion(container.value, scrim.value, presented)
}

// A surface that only exists while open fades in once; its removal is the exit.
@Composable
internal fun rememberArriveFade(): Float {
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(Unit) { reveal.animateTo(1f, motionSpec(tween(Motion.arrive, easing = Motion.standard))) }
    return reveal.value
}

internal class EnterLeaveMotion(val progress: Float, val presented: Boolean)

@Composable
internal fun rememberEnterLeaveMotion(
    visible: Boolean,
    enter: FiniteAnimationSpec<Float>,
    leave: FiniteAnimationSpec<Float>,
): EnterLeaveMotion {
    val progress = remember { Animatable(0f) }
    var presented by remember { mutableStateOf(visible) }
    LaunchedEffect(visible) {
        if (visible) {
            presented = true
            progress.snapTo(0f)
            progress.animateTo(1f, enter)
        } else if (presented) {
            progress.animateTo(0f, leave)
            presented = false
        }
    }
    return EnterLeaveMotion(progress.value, presented)
}