package org.viptv.app.player

import org.viptv.app.state.audioLanguageName

internal fun browserAudioTrack(id: Int, name: String?, language: String?, selected: Boolean): PlaybackTrack {
    val lang = language?.takeUnless { it == "und" }
    val tag = lang?.let { subtitleModeLanguage(it) }
    val label = name?.takeIf { it.isNotBlank() }
        ?: tag?.let(::audioLanguageName)?.takeIf { it.isNotBlank() }
        ?: "Audio track ${id + 1}"
    return PlaybackTrack(id, label, selected, lang)
}
