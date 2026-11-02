package org.viptv.app.features.player

import androidx.compose.ui.graphics.Color
import org.viptv.app.theme.Palette

internal data class TimelineSegment(val startMillis: Long, val endMillis: Long, val kind: TvSkipKind, val durationMillis: Long) {
    val startFraction = (startMillis.toFloat() / durationMillis).coerceIn(0f, 1f)
    val endFraction = (endMillis.toFloat() / durationMillis).coerceIn(0f, 1f)
    val color: Color get() = when (kind) {
        TvSkipKind.Intro -> Palette.segmentIntro
        TvSkipKind.Recap -> Palette.segmentRecap
        TvSkipKind.Credits -> Palette.segmentCredits
    }
    val description = "${kind.name} ${playbackTime(startMillis)}–${playbackTime(endMillis)}"
}

internal fun List<TvSkipRange>.timelineSegments(durationMillis: Long?): List<TimelineSegment> {
    val length = durationMillis?.takeIf { it > 0 } ?: return emptyList()
    return mapNotNull { range ->
        val start = range.startMillis.coerceIn(0, length)
        val end = range.endMillis.coerceIn(0, length)
        if (end <= start) null else TimelineSegment(start, end, range.kind, length)
    }
}