package org.viptv.app.format

private val BreakTag = Regex("<br\\s*/?>\\s*", RegexOption.IGNORE_CASE)
private val ParagraphTag = Regex("</?p(\\s[^>]*)?>", RegexOption.IGNORE_CASE)
private val AnyTag = Regex("<[a-z/!?][^>]*>", RegexOption.IGNORE_CASE)
private val Entity = Regex("&(#x?[0-9a-f]+|[a-z][a-z0-9]+);", RegexOption.IGNORE_CASE)
private val BlankLineRuns = Regex("\n{3,}")
private val TrailingLineSpace = Regex("[ \\t]+\\n")

private val NamedEntities = mapOf(
    "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'",
    "nbsp" to " ", "ndash" to "\u2013", "mdash" to "\u2014", "hellip" to "\u2026",
    "lsquo" to "\u2018", "rsquo" to "\u2019", "ldquo" to "\u201C", "rdquo" to "\u201D",
    "laquo" to "\u00AB", "raquo" to "\u00BB", "middot" to "\u00B7",
    "copy" to "\u00A9", "reg" to "\u00AE", "trade" to "\u2122",
)

// Decoding is one-way for escaped markup (`&amp;amp;` becomes `&`), so every value passes through once.
internal fun String?.providerText(): String? = this?.let(::providerPlainText)?.takeIf { it.isNotEmpty() }

internal fun providerPlainText(raw: String): String {
    val lined = ParagraphTag.replace(BreakTag.replace(raw.replace("\r\n", "\n"), "\n"), "\n\n")
    val text = AnyTag.replace(lined, "")
    return Entity.replace(text) { match -> decodeEntity(match.groupValues[1]) ?: match.value }
        .replace(BlankLineRuns, "\n\n").replace(TrailingLineSpace, "\n").trim()
}

private fun decodeEntity(body: String): String? {
    if (body.startsWith("#")) {
        val value = (if (body.startsWith("#x", ignoreCase = true)) body.substring(2).toIntOrNull(16)
            else body.substring(1).toIntOrNull()) ?: return null
        return codePointToString(value)
    }
    return NamedEntities[body.lowercase()]
}

private fun codePointToString(value: Int): String? = when {
    value < 0 || value > 0x10FFFF || value in 0xD800..0xDFFF -> null
    value <= 0xFFFF -> value.toChar().toString()
    else -> {
        val shifted = value - 0x10000
        charArrayOf((0xD800 + (shifted shr 10)).toChar(), (0xDC00 + (shifted and 0x3FF)).toChar()).concatToString()
    }
}
