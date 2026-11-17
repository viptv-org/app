package org.viptv.app.features.sources

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.ceil
import org.viptv.app.components.LocalReducedMotion

private const val TEXT_WINDOW_LINES = 2
internal const val TEXT_DWELL_MILLIS = 1_500L
internal const val TEXT_LINE_STEP_MILLIS = 1_500L

@Composable
internal fun SourceTextWindow(text: String?, active: Boolean, style: TextStyle, modifier: Modifier = Modifier) {
    var width by remember { mutableIntStateOf(0) }
    val measurer = rememberTextMeasurer()
    val lineCount = if (text.isNullOrEmpty() || width <= 0) 0
        else measurer.measure(text, style, constraints = Constraints(maxWidth = width)).lineCount
    val lineHeight = with(LocalDensity.current) { style.lineHeight.toDp() }
    val linePx = with(LocalDensity.current) { lineHeight.toPx() }
    val maxScroll = ((lineCount - TEXT_WINDOW_LINES).coerceAtLeast(0)) * linePx
    val reduced = LocalReducedMotion.current
    val elapsed = produceState(0L, active, maxScroll, reduced) {
        value = 0L
        if (!active || maxScroll <= 0f || reduced) return@produceState
        val start = withFrameNanos { it }
        while (true) value = withFrameNanos { (it - start) / 1_000_000 }
    }
    Box(modifier.height(lineHeight * TEXT_WINDOW_LINES).clipToBounds().onSizeChanged { width = it.width }) {
        if (!text.isNullOrEmpty()) Text(text, Modifier.offset { IntOffset(0, -sourceTextScroll(elapsed.value, maxScroll, linePx).toInt()) },
            style = style, maxLines = Int.MAX_VALUE)
    }
}

internal fun sourceTextScroll(elapsedMillis: Long, maxScroll: Float, linePx: Float): Float {
    if (maxScroll <= 0f || linePx <= 0f) return 0f
    val steps = ceil(maxScroll / linePx).toInt()
    val cycle = TEXT_DWELL_MILLIS * 2 + steps * TEXT_LINE_STEP_MILLIS
    val at = elapsedMillis.mod(cycle)
    if (at < TEXT_DWELL_MILLIS) return 0f
    val moved = (at - TEXT_DWELL_MILLIS).toFloat() / TEXT_LINE_STEP_MILLIS * linePx
    return moved.coerceAtMost(maxScroll)
}
