package org.viptv.app.player

enum class PlaybackTrackKind { Audio, Subtitle }
// `language` is the durable identity of a choice; `id` is session-local per renderer.
data class PlaybackTrack(val id: Int, val label: String, val selected: Boolean, val language: String? = null)

// One id is one source: a provider subtitle id or an embedded stream index.
data class PlaybackTracks private constructor(val audio: List<PlaybackTrack>, val subtitles: List<PlaybackTrack>) {
    companion object {
        operator fun invoke(audio: List<PlaybackTrack> = emptyList(), subtitles: List<PlaybackTrack> = emptyList()) =
            PlaybackTracks(audio.distinctSources(), subtitles.distinctSources())
    }
}

private fun List<PlaybackTrack>.distinctSources(): List<PlaybackTrack> {
    val sources = LinkedHashMap<Int, PlaybackTrack>()
    forEach { track -> if (sources[track.id]?.selected != true) sources[track.id] = track }
    val numbered = mutableMapOf<String, Int>()
    return sources.values.map { track ->
        val count = (numbered[track.label] ?: 0) + 1
        numbered[track.label] = count
        if (count == 1) track else track.copy(label = "${track.label} · $count")
    }
}