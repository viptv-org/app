package org.viptv.app.features.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import org.viptv.app.components.motionSpec

@Composable
internal fun TvControlsVisibility(visible: Boolean, content: @Composable BoxScope.() -> Unit) {
    val transition = remember { MutableTransitionState(false) }
    transition.targetState = visible
    AnimatedVisibility(visibleState = transition, modifier = Modifier.fillMaxSize(),
        enter = fadeIn(motionSpec(tween(200))), exit = fadeOut(motionSpec(tween(300)))) {
        Box(Modifier.fillMaxSize(), content = content)
    }
}
