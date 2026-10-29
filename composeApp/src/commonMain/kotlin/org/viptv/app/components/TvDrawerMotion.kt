package org.viptv.app.components

import androidx.compose.animation.core.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch

internal class TvDrawerMotion(val progress: Animatable<Float, AnimationVector1D>, val dismiss: () -> Unit)

@Composable
internal fun rememberTvDrawerMotion(close: () -> Unit): TvDrawerMotion {
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val currentClose by rememberUpdatedState(close)
    var closing by remember { mutableStateOf(false) }
    val enter = motionSpec<Float>(tween(300, easing = CubicBezierEasing(0f, 0f, 0f, 1f)))
    val exit = motionSpec<Float>(tween(200, easing = CubicBezierEasing(.3f, 0f, 1f, 1f)))
    LaunchedEffect(Unit) { progress.animateTo(1f, enter) }
    return TvDrawerMotion(progress) {
        if (!closing) {
            closing = true
            scope.launch {
                progress.animateTo(0f, exit)
                currentClose()
            }
        }
    }
}
