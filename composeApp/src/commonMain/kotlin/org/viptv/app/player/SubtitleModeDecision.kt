package org.viptv.app.player

internal data class SubtitleModeTrack<T>(val key: T, val language: String?, val forced: Boolean = false)

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

internal fun <T> subtitleModeChoice(mode: String, preferredAudio: String?, preferredSubtitle: String?, audioLanguage: String?,
    tracks: List<SubtitleModeTrack<T>>): SubtitleModeTrack<T>? {
    if (mode == "never") return null
    if (mode == "foreign" && !audioIsForeign(audioLanguage, preferredAudio)) return null
    val usable = tracks.filterNot { it.forced }
    val wanted = subtitleModeLanguage(preferredSubtitle) ?: return usable.singleOrNull()
    return usable.firstOrNull { subtitleModeLanguage(it.language) == wanted }
}
