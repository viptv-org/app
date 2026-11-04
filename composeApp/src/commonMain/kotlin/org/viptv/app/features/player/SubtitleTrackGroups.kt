package org.viptv.app.features.player

import org.viptv.app.features.settings.languageChoices
import org.viptv.app.player.PlaybackTrack
import org.viptv.app.player.subtitleModeLanguage

internal data class SubtitleGroup(val primary: String, val source: String?, val selected: Boolean, val ids: List<Int>)

// Provider captions arrive as one track per source ("eng · OpenSubtitles v3") with a negative id;
// embedded captions keep their own row. Several sources of one language become one entry.
internal fun groupSubtitleTracks(tracks: List<PlaybackTrack>): List<SubtitleGroup> = buildList {
    val provider = linkedMapOf<String, MutableList<PlaybackTrack>>()
    val plain = mutableListOf<PlaybackTrack>()
    for (track in tracks) {
        if (track.id < 0) provider.getOrPut(providerKey(track)) { mutableListOf() } += track else plain += track
    }
    for ((key, entries) in provider) {
        val sources = entries.map { it.label.substringAfter(" · ") }.distinct()
        add(SubtitleGroup(displaySubtitleLanguage(entries.first().language, key),
            sources.joinToString(" · "), entries.any { it.selected }, entries.map { it.id }))
    }
    for (track in plain) add(SubtitleGroup(track.label, null, track.selected, listOf(track.id)))
}

private fun providerKey(track: PlaybackTrack): String =
    subtitleModeLanguage(track.language) ?: track.label.substringBefore(" · ")

private fun displaySubtitleLanguage(language: String?, key: String): String {
    val code = subtitleModeLanguage(language)
    return languageChoices.firstOrNull { it.first == code }?.second ?: code ?: key
}