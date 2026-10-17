package org.viptv.app.player

// Mirrors nativePlayerMain's Java-Locale-based canonicalization: 2/3-letter
// codes with bibliographic aliases mapped to their ISO-639-2/T forms, then
// lowercase joined tags. NSLocale's class-level canonicalization is not
// exposed to Kotlin in this toolchain, so the mapping is implemented here.
private val bibliographicAliases = mapOf("fre" to "fra", "ger" to "deu", "chi" to "zho", "dut" to "nld",
    "cze" to "ces", "slo" to "slk", "rum" to "ron", "gre" to "ell", "alb" to "sqi", "arm" to "hye",
    "baq" to "eus", "bur" to "mya", "geo" to "kat", "ice" to "isl", "mac" to "mkd", "mao" to "mri",
    "may" to "msa", "per" to "fas", "tib" to "bod", "wel" to "cym")

internal actual fun canonicalLanguageTag(value: String): String {
    val cleaned = value.trim().replace('_', '-')
    if (cleaned.isEmpty()) return cleaned
    return cleaned.split('-').joinToString("-") { part ->
        when (part.length) {
            2 -> part.lowercase()
            3 -> bibliographicAliases[part.lowercase()] ?: part.lowercase()
            else -> part
        }
    }
}
