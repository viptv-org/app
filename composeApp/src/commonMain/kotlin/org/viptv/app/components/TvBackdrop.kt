package org.viptv.app.components

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import dev.chrisbanes.haze.*
import dev.chrisbanes.haze.blur.*

private val LocalTvBackdrop = compositionLocalOf<HazeState?> { null }

@Composable
internal fun TvBackdrop(modifier: Modifier, content: @Composable BoxWithConstraintsScope.() -> Unit) {
    val state = rememberHazeState()
    CompositionLocalProvider(LocalTvBackdrop provides state) { BoxWithConstraints(modifier, content = content) }
}

@Composable
internal fun Modifier.tvBackdropSource(enabled: Boolean): Modifier {
    val state = LocalTvBackdrop.current
    return if (enabled && state != null) hazeSource(state) else this
}

@Composable
internal fun Modifier.tvBackdropBlur(radius: Dp): Modifier {
    val state = LocalTvBackdrop.current ?: return this
    val style = remember(radius) { HazeBlurStyle { blurRadius(radius); noiseFactor(0f) } }
    return hazeBlur(input = HazeInput.Sources(state), style = style)
}
