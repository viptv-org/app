package org.viptv.app.player

enum class PlaybackTrackKind { Audio, Subtitle }
// `language` is the durable identity of a choice; `id` is session-local per renderer.
data class PlaybackTrack(val id: Int, val label: String, val selected: Boolean, val language: String? = null)

// Renderers and providers can report the same track more than once with different session ids.
data class PlaybackTracks private constructor(val audio: List<PlaybackTrack>, val subtitles: List<PlaybackTrack>) {
    companion object {
        operator fun invoke(audio: List<PlaybackTrack> = emptyList(), subtitles: List<PlaybackTrack> = emptyList()) =
            PlaybackTracks(audio.distinctTracks(), subtitles.distinctTracks())
    }
}

private fun List<PlaybackTrack>.distinctTracks(): List<PlaybackTrack> {
    val tracks = LinkedHashMap<Pair<String, String?>, PlaybackTrack>()
    forEach { track -> if (tracks[track.label to track.language]?.selected != true) tracks[track.label to track.language] = track }
    return tracks.values.toList()
}