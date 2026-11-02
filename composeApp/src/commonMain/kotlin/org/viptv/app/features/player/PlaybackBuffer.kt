package org.viptv.app.features.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import org.viptv.app.player.BufferedRange

// A length the timeline cannot place draws nothing: no duration and no band.
internal fun bufferedFractions(ranges: List<BufferedRange>, duration: Long?): List<Pair<Float, Float>> {
    val length = duration?.takeIf { it > 0 } ?: return emptyList()
    return ranges.mapNotNull { range ->
        val start = (range.startMillis.toDouble() / length).toFloat().coerceIn(0f, 1f)
        val end = (range.endMillis.toDouble() / length).toFloat().coerceIn(0f, 1f)
        if (end > start) start to end else null
    }
}

// The one mapping every layer of the bar shares: the Material track slot already runs half a thumb in from the
// slider's ends, so its full width is the path the thumb's centre travels.
@Composable
internal fun BoxWithConstraintsScope.TimelineBarSpan(start: Float, end: Float, color: Color, shape: Shape = RectangleShape) {
    val from = start.coerceIn(0f, 1f)
    val to = end.coerceIn(from, 1f)
    Box(Modifier.offset(x = maxWidth * from).width(maxWidth * (to - from)).fillMaxHeight().background(color, shape))
}
