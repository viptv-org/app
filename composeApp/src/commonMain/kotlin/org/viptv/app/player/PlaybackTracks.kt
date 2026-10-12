package org.viptv.app.player

enum class PlaybackTrackKind { Audio, Subtitle }
data class PlaybackTrack(val id: Int, val label: String, val selected: Boolean)
data class PlaybackTracks(val audio: List<PlaybackTrack> = emptyList(), val subtitles: List<PlaybackTrack> = emptyList())
