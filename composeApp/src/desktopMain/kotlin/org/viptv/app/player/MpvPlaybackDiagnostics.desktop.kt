package org.viptv.app.player


internal fun playbackFailureSummary(input: PlaybackInput, code: String, message: String): String {
    var detail = message
    val filename = input.uri.substringBefore('?').substringBefore('#').substringAfterLast('/')
    val privateValues = listOf(input.uri, filename) + input.headers.values + input.headers.values.map { it.substringAfterLast(' ') }
    privateValues.filter { it.length >= 4 }.distinct().sortedByDescending { it.length }.forEach {
        detail = detail.replace(it, "<private>")
    }
    detail = detail.replace(Regex("[a-zA-Z][a-zA-Z0-9+.-]*://[^\\s\"'<>]+"), "<private-uri>")
    return "$code: ${detail.take(600)}"
}

