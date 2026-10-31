package org.viptv.app.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

private const val LIST_STAGGER_CAP = 8

@Stable
internal class ListEntrance internal constructor(restored: Boolean) {
    var animate by mutableStateOf(false)
        private set
    private var opened = restored

    internal fun register(hasContent: Boolean) {
        if (opened || !hasContent) return
        opened = true
        animate = true
    }

    internal fun settle() {
        animate = false
    }
}

@Composable
internal fun rememberListEntrance(hasContent: Boolean): ListEntrance {
    val entrance = remember { ListEntrance(hasContent) }
    entrance.register(hasContent)
    if (entrance.animate) {
        LaunchedEffect(entrance) {
            delay((Motion.staggerStep * LIST_STAGGER_CAP + Motion.arrive).toLong())
            entrance.settle()
        }
    }
    return entrance
}

@Composable
internal fun Modifier.listItemEntrance(index: Int, animate: Boolean): Modifier {
    if (!animate || LocalTvFocusAnchoring.current || LocalReducedMotion.current) return this
    val rise = with(LocalDensity.current) { 8.dp.toPx() }
    val progress = remember { Animatable(0f) }
    val spec: FiniteAnimationSpec<Float> = motionSpec(tween(Motion.arrive, easing = Motion.standard))
    LaunchedEffect(index) {
        delay((index.coerceAtMost(LIST_STAGGER_CAP) * Motion.staggerStep).toLong())
        progress.animateTo(1f, spec)
    }
    val shown = progress.value
    return graphicsLayer {
        alpha = shown
        translationY = (1f - shown) * rise
    }
}
