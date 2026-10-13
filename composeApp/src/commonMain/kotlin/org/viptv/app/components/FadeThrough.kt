package org.viptv.app.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/** Swap text after it fades out so two titles never draw over each other. */
@Composable
internal fun <T> FadeThrough(target: T, modifier: Modifier = Modifier, content: @Composable (T) -> Unit) {
    var shown by remember { mutableStateOf(target) }
    val opacity = remember { Animatable(1f) }
    LaunchedEffect(target) {
        if (target != shown) {
            opacity.animateTo(0f, tween(150))
            shown = target
        }
        opacity.animateTo(1f, tween(150))
    }
    Box(modifier.graphicsLayer { alpha = opacity.value }) { content(shown) }
}
