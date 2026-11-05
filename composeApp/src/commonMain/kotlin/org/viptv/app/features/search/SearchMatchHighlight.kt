package org.viptv.app.features.search

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

private val searchAccentFolds: Map<Char, String> = buildMap {
    listOf(
        "aàáâãäåāăą", "cçćĉċč", "dďđ", "eèéêëēĕėęě", "gĝğġģ", "hĥħ",
        "iìíîïĩīĭįı", "jĵ", "kķ", "lĺļľŀł", "nñńņňŉ", "oòóôõöøōŏő",
        "rŕŗř", "sśŝşšș", "tţťŧț", "uùúûüũūŭůűų", "wŵ", "yýÿŷ", "zźżž",
    ).forEach { group -> group.drop(1).forEach { put(it, group.take(1)) } }
    put('ß', "ss"); put('æ', "ae"); put('œ', "oe"); put('þ', "th")
}

private const val combiningMarksStart = '̀'
private const val combiningMarksEnd = 'ͯ'

private class SearchFold(val text: String, val sourceIndex: IntArray)

private fun foldForMatch(text: String): SearchFold {
    val folded = StringBuilder(text.length)
    val source = ArrayList<Int>(text.length)
    text.lowercase().forEachIndexed { index, char ->
        if (char in combiningMarksStart..combiningMarksEnd) return@forEachIndexed
        searchAccentFolds[char]?.forEach { folded.append(it); source.add(index) }
            ?: run { folded.append(char); source.add(index) }
    }
    return SearchFold(folded.toString(), source.toIntArray())
}

internal fun searchMatchHighlight(title: String, query: String): AnnotatedString {
    val needle = foldForMatch(query)
    if (needle.text.isEmpty()) return AnnotatedString(title)
    val haystack = foldForMatch(title)
    val start = haystack.text.indexOf(needle.text)
    if (start < 0) return AnnotatedString(title)
    val end = start + needle.text.length
    val matchedFirst = haystack.sourceIndex[start]
    val matchedAfter = haystack.sourceIndex[end - 1] + 1
    return buildAnnotatedString {
        append(title.substring(0, matchedFirst))
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
            append(title.substring(matchedFirst, matchedAfter.coerceAtMost(title.length)))
        }
        if (matchedAfter < title.length) append(title.substring(matchedAfter))
    }
}