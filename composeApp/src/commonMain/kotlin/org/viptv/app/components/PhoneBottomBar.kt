package org.viptv.app.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import org.viptv.app.state.Screen

internal val PhoneBarReveal = 48.dp

@Composable
internal fun PhoneBottomBar(selected: Screen, navigate: (Screen) -> Unit, hidden: Boolean) {
    val density = LocalDensity.current
    val slide by animateDpAsState(if (hidden) BottomBarHeight else 0.dp,
        animationSpec = motionSpec(tween(Motion.leave, easing = Motion.exit)), label = "bottom bar slide")
    val fade by animateFloatAsState(if (hidden) 0f else 1f,
        animationSpec = motionSpec(tween(Motion.leave, easing = Motion.exit)), label = "bottom bar fade")
    Box(Modifier.fillMaxWidth().graphicsLayer {
        translationY = with(density) { slide.toPx() }
        alpha = fade
    }) {
        BottomNavigation(selected, navigate)
    }
}