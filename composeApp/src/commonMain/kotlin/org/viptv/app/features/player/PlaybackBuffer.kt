package org.viptv.app.features.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

@Composable
internal fun BoxWithConstraintsScope.BufferedSegments(fractions: List<Pair<Float, Float>>, color: Color) {
    fractions.forEach { (start, end) ->
        Box(Modifier.offset(x = maxWidth * start).fillMaxWidth(end - start).fillMaxHeight().background(color))
    }
}
