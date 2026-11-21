package org.viptv.app.player

private class Language(val code: String, val name: String, vararg val aliases: String)

private val languages = listOf(
    Language("en", "English", "eng"), Language("es", "Spanish", "spa", "español", "espanol", "castellano"),
    Language("pt", "Portuguese", "por", "português", "portugues"), Language("fr", "French", "fra", "fre", "français", "francais"),
    Language("de", "German", "deu", "ger", "deutsch"), Language("it", "Italian", "ita", "italiano"),
    Language("ja", "Japanese", "jpn", "日本語"), Language("ko", "Korean", "kor", "한국어"),
    Language("zh", "Chinese", "zho", "chi", "mandarin", "cantonese", "中文"), Language("ru", "Russian", "rus", "русский"),
    Language("nl", "Dutch", "nld", "dut", "nederlands"), Language("pl", "Polish", "pol", "polski"),
    Language("tr", "Turkish", "tur", "türkçe"), Language("sv", "Swedish", "swe", "svenska"),
    Language("da", "Danish", "dan", "dansk"), Language("no", "Norwegian", "nor", "nob", "nno", "nb", "nn", "norsk"),
    Language("fi", "Finnish", "fin", "suomi"), Language("cs", "Czech", "ces", "cze", "čeština"),
    Language("uk", "Ukrainian", "ukr"), Language("ar", "Arabic", "ara"), Language("hi", "Hindi", "hin"),
    Language("he", "Hebrew", "heb", "iw"), Language("el", "Greek", "ell", "gre"), Language("hu", "Hungarian", "hun", "magyar"),
    Language("ro", "Romanian", "ron", "rum"), Language("th", "Thai", "tha"), Language("vi", "Vietnamese", "vie"),
    Language("id", "Indonesian", "ind", "in"), Language("ta", "Tamil", "tam"), Language("te", "Telugu", "tel"),
    Language("ms", "Malay", "msa", "may"), Language("tl", "Filipino", "tgl", "fil", "tagalog"),
    Language("bg", "Bulgarian", "bul"), Language("hr", "Croatian", "hrv"), Language("sr", "Serbian", "srp"),
    Language("sk", "Slovak", "slk", "slo"), Language("sl", "Slovenian", "slv"), Language("ca", "Catalan", "cat"),
    Language("et", "Estonian", "est"), Language("lv", "Latvian", "lav"), Language("lt", "Lithuanian", "lit"),
    Language("is", "Icelandic", "isl", "ice"), Language("fa", "Persian", "fas", "per", "farsi"),
    Language("ur", "Urdu", "urd"), Language("bn", "Bengali", "ben"), Language("ml", "Malayalam", "mal"),
    Language("kn", "Kannada", "kan"), Language("mr", "Marathi", "mar"), Language("pa", "Punjabi", "pan"),
    Language("gu", "Gujarati", "guj"), Language("sq", "Albanian", "sqi", "alb"), Language("hy", "Armenian", "hye", "arm"),
    Language("eu", "Basque", "eus", "baq"), Language("my", "Burmese", "mya", "bur"), Language("ka", "Georgian", "kat", "geo"),
    Language("mk", "Macedonian", "mkd", "mac"), Language("mi", "Maori", "mri", "mao"), Language("bo", "Tibetan", "bod", "tib"),
    Language("cy", "Welsh", "cym", "wel"), Language("gl", "Galician", "glg"), Language("yi", "Yiddish", "yid", "ji"),
    Language("sw", "Swahili", "swa"), Language("af", "Afrikaans", "afr"), Language("km", "Khmer", "khm"),
    Language("lo", "Lao", "lao"), Language("mn", "Mongolian", "mon"), Language("kk", "Kazakh", "kaz"),
)

private val codes: Map<String, String> = languages.flatMap { language ->
    (listOf(language.code, language.name.lowercase()) + language.aliases).map { it to language.code }
}.toMap()

private val names: Map<String, String> = languages.associate { it.code to it.name }

private val unknown = setOf("und", "mul", "zxx", "mis")

private fun cleaned(value: String?): String? = value?.trim()?.lowercase()?.replace('_', '-')?.takeIf(String::isNotEmpty)

private fun primary(value: String): String? {
    codes[value]?.let { return it }
    val subtag = value.substringBefore('-')
    if (subtag in unknown || subtag.length !in 2..3 || !subtag.all { it in 'a'..'z' }) return null
    return codes[subtag] ?: subtag
}

internal fun languageCode(value: String?): String? = cleaned(value)?.let(::primary)

internal fun languageTag(value: String?): String? {
    val tag = cleaned(value) ?: return null
    val code = primary(tag) ?: return null
    if (tag in codes) return code
    return (listOf(code) + tag.split('-').drop(1).filter(String::isNotEmpty)).joinToString("-")
}

internal fun sameLanguage(a: String?, b: String?): Boolean = languageCode(a)?.let { it == languageCode(b) } ?: false

internal fun languageName(value: String?): String? = languageCode(value)?.let { names[it] ?: it }
