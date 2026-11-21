package org.viptv.app.player

internal fun audioTrack(id: Int, ordinal: Int, name: String?, language: String?, channels: Int?, selected: Boolean): PlaybackTrack {
    val languageName = languageName(language)
    val title = name?.trim()?.takeIf { it.isNotEmpty() && (languageName == null || !sameLanguage(it, language)) }
    val primary = title ?: languageName ?: "Audio track $ordinal"
    val detail = listOfNotNull(languageName.takeIf { title != null }, channelLayout(channels)).joinToString(" · ").ifEmpty { null }
    return PlaybackTrack(id, listOfNotNull(primary, detail).joinToString(" · "), selected,
        language?.takeUnless { languageCode(it) == null }, name = primary, detail = detail)
}

internal fun channelLayout(channels: Int?): String? = when {
    channels == null || channels <= 0 -> null
    channels == 1 -> "Mono"
    channels == 2 -> "Stereo"
    channels == 6 -> "5.1"
    channels == 8 -> "7.1"
    else -> "$channels channels"
}
