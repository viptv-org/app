package org.viptv.app.features.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.viptv.app.player.PlaybackInput

internal const val skippedNoticeMillis = 5_000L
internal const val creditsTailMillis = 10_000L

internal fun TvSkipRange.skipsItself(intros: Boolean, credits: Boolean, durationMillis: Long?, nextEpisode: Boolean): Boolean = !mixed && when (kind) {
    TvSkipKind.Intro, TvSkipKind.Recap -> intros
    TvSkipKind.Credits -> credits && (scenesFollow(durationMillis) || !nextEpisode)
}

internal fun List<TvSkipRange>.endCreditsReached(position: Long, credits: Boolean, durationMillis: Long?): Boolean =
    firstOrNull { it.kind == TvSkipKind.Credits && !(credits && !it.mixed && it.scenesFollow(durationMillis)) }
        ?.let { position >= it.startMillis } == true

private fun TvSkipRange.scenesFollow(durationMillis: Long?) = durationMillis != null && durationMillis - endMillis > creditsTailMillis

internal class SegmentAutoSkip {
    var notice by mutableStateOf<TvSkipRange?>(null)
    private var opened: PlaybackInput? = null
    private var runFrom = 0L
    private var runChosen = false
    private val undone = mutableSetOf<TvSkipRange>()

    fun due(input: PlaybackInput?, position: Long, segments: List<TvSkipRange>, enabled: (TvSkipRange) -> Boolean): TvSkipRange? {
        if (input != null && input !== opened) {
            opened = input
            runFrom = input.positionMillis + input.timelineOffsetMillis
            runChosen = input.startChosen || input.positionMillis > 0
        }
        if (position < runFrom) { runFrom = position; runChosen = false }
        return segments.firstOrNull { range ->
            position in range.startMillis until range.endMillis && range !in undone && enabled(range) &&
                (runFrom < range.startMillis || runFrom == range.startMillis && !runChosen)
        }
    }

    fun skipped(range: TvSkipRange) { runFrom = range.endMillis; runChosen = false; notice = range }

    fun seeked(targetMillis: Long) { runFrom = targetMillis; runChosen = true }

    fun undo(range: TvSkipRange) { undone += range; notice = null }
}

internal val TvSkipRange.skippedLabel: String get() = when (kind) {
    TvSkipKind.Intro -> "Skipped intro"
    TvSkipKind.Recap -> "Skipped recap"
    TvSkipKind.Credits -> "Skipped credits"
}

internal const val undoSkipLabel = "Undo"
