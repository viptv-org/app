package org.viptv.app.features.sources

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.delay
import org.viptv.app.components.LocalReducedMotion
import org.viptv.app.components.Motion

private const val TEXT_WINDOW_LINES = 2
private const val DWELL_MILLIS = 1_500L
private const val LINE_TRAVERSE_MILLIS = 600L

@Composable
internal fun SourceTextWindow(text: String?, active: Boolean, style: TextStyle, modifier: Modifier = Modifier) {
    var width by remember { mutableIntStateOf(0) }
    val measurer = rememberTextMeasurer()
    val lineCount = if (text.isNullOrEmpty() || width <= 0) 0
        else measurer.measure(text, style, constraints = Constraints(maxWidth = width)).lineCount
    val lineHeight = with(LocalDensity.current) { style.lineHeight.toDp() }
    val scroll by rememberSourceTextScroll(active, lineCount, lineHeight)
    Box(modifier.height(lineHeight * TEXT_WINDOW_LINES).clipToBounds().onSizeChanged { width = it.width }) {
        if (!text.isNullOrEmpty()) Text(text, Modifier.offset { IntOffset(0, -scroll.toInt()) }, style = style, maxLines = Int.MAX_VALUE)
    }
}

@Composable
private fun rememberSourceTextScroll(active: Boolean, lineCount: Int, lineHeight: Dp): State<Float> {
    val linePx = with(LocalDensity.current) { lineHeight.toPx() }
    val maxScroll = ((lineCount - TEXT_WINDOW_LINES).coerceAtLeast(0)) * linePx
    val reduced = LocalReducedMotion.current
    val offset = remember { Animatable(0f) }
    LaunchedEffect(active, maxScroll, reduced) {
        offset.snapTo(0f)
        if (!active || maxScroll <= 0f || reduced) return@LaunchedEffect
        while (true) {
            delay(DWELL_MILLIS)
            while (offset.value < maxScroll) {
                offset.animateTo(minOf(offset.value + linePx, maxScroll), tween(LINE_TRAVERSE_MILLIS.toInt(), easing = Motion.standard))
                delay(DWELL_MILLIS - LINE_TRAVERSE_MILLIS)
            }
            delay(DWELL_MILLIS)
            offset.snapTo(0f)
        }
    }
    return offset.asState()
}
