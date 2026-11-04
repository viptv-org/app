package org.viptv.app.player

// A web audio rendition in renderer coordinates; "und" carries no language for identity or the foreign-audio rule.
internal fun browserAudioTrack(id: Int, name: String?, language: String?, selected: Boolean): PlaybackTrack {
    val lang = language?.takeUnless { it == "und" }
    val label = listOfNotNull(name?.takeIf { it.isNotBlank() }, lang).distinct().joinToString(" · ")
        .ifBlank { "Audio track ${id + 1}" }
    return PlaybackTrack(id, label, selected, lang)
}