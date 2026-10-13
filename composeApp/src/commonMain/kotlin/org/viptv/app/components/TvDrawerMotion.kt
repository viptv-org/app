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
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(300, easing = CubicBezierEasing(0f, 0f, 0f, 1f))) }
    return TvDrawerMotion(progress) {
        if (!closing) {
            closing = true
            scope.launch {
                progress.animateTo(0f, tween(200, easing = CubicBezierEasing(.3f, 0f, 1f, 1f)))
                currentClose()
            }
        }
    }
}
