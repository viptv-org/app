package org.viptv.app.player

import org.viptv.app.features.settings.languageChoices

internal data class SubtitleModeTrack<T>(val key: T, val language: String?, val forced: Boolean = false,
    val name: String? = null, val default: Boolean = false)

private val signsWords = Regex("""(?<![\p{L}\p{N}])(signs?|songs?|forced|s&s)(?![\p{L}\p{N}])""", RegexOption.IGNORE_CASE)
private val fullWords = Regex("""(?<![\p{L}\p{N}])(full|dialogue|dialog)(?![\p{L}\p{N}])""", RegexOption.IGNORE_CASE)

internal fun signsOnlySubtitle(name: String?, forced: Boolean): Boolean =
    forced || name != null && signsWords.containsMatchIn(name) && !fullWords.containsMatchIn(name)

internal fun subtitleLanguageName(language: String?): String? {
    val code = subtitleModeLanguage(language) ?: return null
    return languageChoices.firstOrNull { it.first == code }?.second ?: code
}

internal fun embeddedSubtitleTrack(id: Int, ordinal: Int, name: String?, language: String?, selected: Boolean,
    forced: Boolean, default: Boolean): PlaybackTrack {
    val languageName = subtitleLanguageName(language)
    val title = name?.trim()?.takeIf { it.isNotEmpty() && (languageName == null || subtitleModeLanguage(it) != subtitleModeLanguage(language)) }
    val label = listOfNotNull(title, languageName).joinToString(" · ").ifEmpty { "Subtitle $ordinal" }
    return PlaybackTrack(id, label, selected, language?.takeUnless { subtitleModeLanguage(it) == null }, title, forced, default)
}

private val unknownLanguages = setOf("", "und", "mul", "zxx", "mis")

private val languageAliases: Map<String, String> = listOf(
    "en" to listOf("eng", "english"), "es" to listOf("spa", "spanish", "español", "espanol", "castellano"),
    "pt" to listOf("por", "portuguese", "português"), "fr" to listOf("fra", "fre", "french", "français"),
    "de" to listOf("deu", "ger", "german", "deutsch"), "it" to listOf("ita", "italian"), "ja" to listOf("jpn", "japanese"),
    "ko" to listOf("kor", "korean"), "zh" to listOf("zho", "chi", "chinese", "mandarin"), "ru" to listOf("rus", "russian"),
    "nl" to listOf("nld", "dut", "dutch"), "pl" to listOf("pol", "polish"), "tr" to listOf("tur", "turkish"),
    "sv" to listOf("swe", "swedish"), "da" to listOf("dan", "danish"), "no" to listOf("nor", "nob", "nb", "norwegian"),
    "fi" to listOf("fin", "finnish"), "cs" to listOf("ces", "cze", "czech"), "uk" to listOf("ukr", "ukrainian"),
    "ar" to listOf("ara", "arabic"), "hi" to listOf("hin", "hindi"), "he" to listOf("heb", "hebrew"),
    "el" to listOf("ell", "gre", "greek"), "hu" to listOf("hun", "hungarian"), "ro" to listOf("ron", "rum", "romanian"),
    "th" to listOf("tha", "thai"), "vi" to listOf("vie", "vietnamese"), "id" to listOf("ind", "indonesian"),
    "ta" to listOf("tam", "tamil"), "te" to listOf("tel", "telugu"),
).flatMap { (code, names) -> (names + code).map { it to code } }.toMap()

internal fun subtitleModeLanguage(value: String?): String? {
    val cleaned = value?.trim()?.lowercase()?.replace('_', '-') ?: return null
    if (cleaned in unknownLanguages) return null
    languageAliases[cleaned]?.let { return it }
    val primary = cleaned.substringBefore('-')
    if (primary in unknownLanguages) return null
    return languageAliases[primary] ?: primary
}

// Audio is foreign only when its reported language is known and differs from the preference; empty means English.
internal fun audioIsForeign(audioLanguage: String?, preferredAudio: String?): Boolean {
    val audio = subtitleModeLanguage(audioLanguage) ?: return false
    return audio != (subtitleModeLanguage(preferredAudio) ?: "en")
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
    val wanted = subtitleModeLanguage(preferredSubtitle) ?: return usable.singleOrNull()
    val matching = usable.filter { subtitleModeLanguage(it.language) == wanted }
    return matching.firstOrNull { it.default } ?: matching.firstOrNull()
}

private fun <T> dubSigns(preferredAudio: String?, preferredSubtitle: String?, audioLanguage: String?,
    tracks: List<SubtitleModeTrack<T>>): SubtitleModeTrack<T>? {
    val audio = subtitleModeLanguage(audioLanguage) ?: return null
    if (audio != (subtitleModeLanguage(preferredAudio) ?: "en")) return null
    val wanted = subtitleModeLanguage(preferredSubtitle) ?: audio
    val signs = tracks.filter { signsOnlySubtitle(it.name, it.forced) && subtitleModeLanguage(it.language) == wanted }
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
