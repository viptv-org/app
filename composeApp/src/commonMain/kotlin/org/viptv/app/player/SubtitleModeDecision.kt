package org.viptv.app.player

internal data class SubtitleModeTrack<T>(val key: T, val language: String?, val forced: Boolean = false,
    val name: String? = null, val default: Boolean = false)

private val signsWords = Regex("""(?<![\p{L}\p{N}])(signs?|songs?|forced|s&s)(?![\p{L}\p{N}])""", RegexOption.IGNORE_CASE)
private val fullWords = Regex("""(?<![\p{L}\p{N}])(full|dialogue|dialog)(?![\p{L}\p{N}])""", RegexOption.IGNORE_CASE)

internal fun signsOnlySubtitle(name: String?, forced: Boolean): Boolean =
    forced || name != null && signsWords.containsMatchIn(name) && !fullWords.containsMatchIn(name)

internal fun embeddedSubtitleTrack(id: Int, ordinal: Int, name: String?, language: String?, selected: Boolean,
    forced: Boolean, default: Boolean): PlaybackTrack {
    val languageName = languageName(language)
    val title = name?.trim()?.takeIf { it.isNotEmpty() && (languageName == null || !sameLanguage(it, language)) }
    val label = listOfNotNull(title, languageName).joinToString(" · ").ifEmpty { "Subtitle $ordinal" }
    return PlaybackTrack(id, label, selected, language?.takeUnless { languageCode(it) == null }, title, forced, default)
}

// Audio is foreign only when its reported language is known and differs from the preference; empty means English.
internal fun audioIsForeign(audioLanguage: String?, preferredAudio: String?): Boolean {
    val audio = languageCode(audioLanguage) ?: return false
    return audio != (languageCode(preferredAudio) ?: "en")
}

internal fun subtitleModeEnabled(mode: String, preferredAudio: String?, audioLanguage: String?): Boolean = when (mode) {
    "always" -> true
    "foreign" -> audioIsForeign(audioLanguage, preferredAudio)
    else -> false
}

internal fun <T> subtitleModeChoice(mode: String, preferredAudio: String?, preferredSubtitle: String?, audioLanguage: String?,
    tracks: List<SubtitleModeTrack<T>>): SubtitleModeTrack<T>? {
    if (mode == "never") return null
    if (mode == "foreign" && !audioIsForeign(audioLanguage, preferredAudio)) return dubSigns(preferredAudio, preferredSubtitle, audioLanguage, tracks)
    val usable = tracks.filterNot { signsOnlySubtitle(it.name, it.forced) }
    val wanted = languageCode(preferredSubtitle) ?: return usable.singleOrNull()
    val matching = usable.filter { languageCode(it.language) == wanted }
    return matching.firstOrNull { it.default } ?: matching.firstOrNull()
}

private fun <T> dubSigns(preferredAudio: String?, preferredSubtitle: String?, audioLanguage: String?,
    tracks: List<SubtitleModeTrack<T>>): SubtitleModeTrack<T>? {
    val audio = languageCode(audioLanguage) ?: return null
    if (audio != (languageCode(preferredAudio) ?: "en")) return null
    val wanted = languageCode(preferredSubtitle) ?: audio
    val signs = tracks.filter { signsOnlySubtitle(it.name, it.forced) && languageCode(it.language) == wanted }
    return signs.firstOrNull { it.default } ?: signs.firstOrNull()
}

internal fun dubSignsSubtitle(embedded: List<PlaybackTrack>, mode: String, preferredAudio: String?, preferredSubtitle: String?,
    audioLanguage: String?): Int? =
    if (mode != "foreign" || audioIsForeign(audioLanguage, preferredAudio)) null
    else subtitleModeChoice(mode, preferredAudio, preferredSubtitle, audioLanguage,
        embedded.map { SubtitleModeTrack(it.id, it.language, it.forced, it.name, it.default) })?.key

internal fun fullDialogueSubtitle(embedded: List<PlaybackTrack>, preferredSubtitle: String?): Int? =
    subtitleModeChoice("always", null, preferredSubtitle, null,
        embedded.map { SubtitleModeTrack(it.id, it.language, it.forced, it.name, it.default) })?.key
