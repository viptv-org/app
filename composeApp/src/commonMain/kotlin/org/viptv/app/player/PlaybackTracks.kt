package org.viptv.app.player

enum class PlaybackTrackKind { Audio, Subtitle }
/** `language` is the durable identity of a choice; `id` is session-local per renderer. */
data class PlaybackTrack(val id: Int, val label: String, val selected: Boolean, val language: String? = null)
data class PlaybackTracks(val audio: List<PlaybackTrack> = emptyList(), val subtitles: List<PlaybackTrack> = emptyList())
