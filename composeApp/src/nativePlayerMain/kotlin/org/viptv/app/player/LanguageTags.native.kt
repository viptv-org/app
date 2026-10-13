package org.viptv.app.player

private val bibliographicAliases = mapOf("fre" to "fra", "ger" to "deu", "chi" to "zho", "dut" to "nld",
    "cze" to "ces", "slo" to "slk", "rum" to "ron", "gre" to "ell", "alb" to "sqi", "arm" to "hye",
    "baq" to "eus", "bur" to "mya", "geo" to "kat", "ice" to "isl", "mac" to "mkd", "mao" to "mri",
    "may" to "msa", "per" to "fas", "tib" to "bod", "wel" to "cym")

internal actual fun canonicalLanguageTag(value: String): String {
    val locale = java.util.Locale.forLanguageTag(value)
    val language = try { locale.isO3Language } catch (_: java.util.MissingResourceException) { locale.language }
    return (listOf(bibliographicAliases[language] ?: language) + listOf(locale.script, locale.country).filter(String::isNotEmpty))
        .joinToString("-").lowercase()
}
