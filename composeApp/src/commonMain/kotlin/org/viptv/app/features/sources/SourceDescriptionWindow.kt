package org.viptv.app.features.sources

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import kotlin.math.roundToInt
import org.viptv.app.components.LocalReducedMotion

internal const val SOURCE_DESCRIPTION_LINES = 2
internal const val SOURCE_DESCRIPTION_DWELL_MILLIS = 1_500L
internal const val SOURCE_DESCRIPTION_LINE_MILLIS = 1_500L

@Composable
internal fun sourceDescriptionWindowHeight(lineHeight: TextUnit): Dp =
    with(LocalDensity.current) { (lineHeight.toPx() * SOURCE_DESCRIPTION_LINES).toDp() }

internal fun sourceDescriptionOffset(elapsedMillis: Long, overflowLines: Int, linePx: Float): Float {
    if (overflowLines <= 0 || linePx <= 0f) return 0f
    val travel = overflowLines * linePx
    val traverse = overflowLines * SOURCE_DESCRIPTION_LINE_MILLIS
    val at = elapsedMillis % (SOURCE_DESCRIPTION_DWELL_MILLIS * 2 + traverse)
    return when {
        at < SOURCE_DESCRIPTION_DWELL_MILLIS -> 0f
        at >= SOURCE_DESCRIPTION_DWELL_MILLIS + traverse -> travel
        else -> ((at - SOURCE_DESCRIPTION_DWELL_MILLIS) / SOURCE_DESCRIPTION_LINE_MILLIS.toFloat() * linePx)
            .coerceAtMost(travel)
    }
}

@Composable
internal fun SourceDescriptionWindow(description: String?, active: Boolean, fontSize: TextUnit, lineHeight: TextUnit,
    color: Color, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val linePx = with(density) { lineHeight.toPx() }
    val reduced = LocalReducedMotion.current
    var overflowLines by remember { mutableIntStateOf(0) }
    var offset by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(description, active, reduced, overflowLines, linePx) {
        offset = 0f
        if (!active || reduced || overflowLines <= 0 || linePx <= 0f) return@LaunchedEffect
        val start = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            offset = sourceDescriptionOffset((now - start) / 1_000_000, overflowLines, linePx)
        }
    }
    Box(modifier.fillMaxWidth().height(sourceDescriptionWindowHeight(lineHeight))
        // Clipping at draw time keeps every wrapped line in layout and semantics.
        .drawWithContent { clipRect { this@drawWithContent.drawContent() } }) {
        if (description.isNullOrEmpty()) return@Box
        Text(description, Modifier.fillMaxWidth().wrapContentHeight(Alignment.Top, unbounded = true)
            .offset { IntOffset(0, -offset.roundToInt()) },
            fontSize = fontSize, lineHeight = lineHeight, color = color,
            onTextLayout = { layout -> overflowLines = (layout.lineCount - SOURCE_DESCRIPTION_LINES).coerceAtLeast(0) })
    }
}
