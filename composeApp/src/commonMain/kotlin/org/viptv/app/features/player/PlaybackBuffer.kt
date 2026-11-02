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
import androidx.compose.ui.unit.Dp
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

// `inset` is half a thumb on the pointer slider, so a band starts and ends where the thumb travels.
@Composable
internal fun BoxWithConstraintsScope.BufferedSegments(fractions: List<Pair<Float, Float>>, inset: Dp, color: Color) {
    val span = maxWidth - inset * 2
    fractions.forEach { (start, end) ->
        Box(Modifier.offset(x = inset + span * start).width(span * (end - start)).fillMaxHeight().background(color))
    }
}
