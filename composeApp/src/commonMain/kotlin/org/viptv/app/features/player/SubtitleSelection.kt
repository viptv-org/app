package org.viptv.app.features.player

import org.viptv.app.player.PlaybackTracks

internal sealed interface SubtitleSelection {
    data object Off : SubtitleSelection
    data class Track(val id: Int) : SubtitleSelection

    companion object {
        fun of(id: Int?) = if (id == null) Off else Track(id)
    }
}

internal val SubtitleSelection.idOrNull: Int? get() = when (this) {
    SubtitleSelection.Off -> null
    is SubtitleSelection.Track -> id
}

internal fun SubtitleSelection.resolved(tracks: PlaybackTracks): Boolean = when (this) {
    SubtitleSelection.Off -> tracks.subtitles.none { it.selected }
    is SubtitleSelection.Track -> tracks.subtitles.firstOrNull { it.selected }?.id == id
}
