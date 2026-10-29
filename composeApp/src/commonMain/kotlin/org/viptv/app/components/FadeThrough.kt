package org.viptv.app.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal fun <T> FadeThrough(target: T, modifier: Modifier = Modifier, contentKey: (T) -> Any? = { it }, content: @Composable (T) -> Unit) {
    androidx.compose.animation.core.updateTransition(target, label = "Billboard text")
        .Crossfade(modifier, animationSpec = tween(160), contentKey = contentKey, content = content)
}
