package org.viptv.app.player

private val cueTimes = Regex("(\\d{1,3}:\\d{2}(?::\\d{2})?[.,]\\d{3})(\\s+-->\\s+)(\\d{1,3}:\\d{2}(?::\\d{2})?[.,]\\d{3})")
private fun subtitleMillis(value: String): Long? = runCatching {
    val parts = value.replace(',', '.').split(':')
    val seconds = parts.last().split('.')
    val hours = if (parts.size == 3) parts[0].toLong() else 0
    val minutes = parts[parts.size - 2].toLong()
    hours * 3_600_000 + minutes * 60_000 + seconds[0].toLong() * 1000 + seconds[1].padEnd(3, '0').take(3).toLong()
}.getOrNull()
private fun subtitleTime(value: Long, comma: Boolean = false, centiseconds: Boolean = false): String {
    val millis = value.coerceAtLeast(0)
    val hours = (millis / 3_600_000).toString().padStart(if (centiseconds) 1 else 2, '0')
    val minutes = (millis / 60_000 % 60).toString().padStart(2, '0')
    val seconds = (millis / 1000 % 60).toString().padStart(2, '0')
    val fraction = if (centiseconds) (millis % 1000 / 10).toString().padStart(2, '0') else (millis % 1000).toString().padStart(3, '0')
    return "$hours:$minutes:$seconds${if (comma) ',' else '.'}$fraction"
}

/** Shifts provider cues into a gateway cut without changing the original byte encoding or styles. */
internal fun shiftSubtitleBytes(content: ByteArray, offsetMillis: Long): ByteArray {
    if (offsetMillis <= 0) return content
    val text = content.map { (it.toInt() and 255).toChar() }.joinToString("")
    val shifted = if (text.contains("[Events]", ignoreCase = true)) shiftAss(text, offsetMillis) else {
        text.split(Regex("\\r?\\n\\r?\\n")).mapNotNull { block ->
            val match = cueTimes.find(block) ?: return@mapNotNull block
            val start = subtitleMillis(match.groupValues[1]) ?: return@mapNotNull block
            val end = subtitleMillis(match.groupValues[3]) ?: return@mapNotNull block
            if (end <= offsetMillis) return@mapNotNull null
            block.replaceRange(match.range, subtitleTime(start - offsetMillis, ',' in match.groupValues[1]) +
                match.groupValues[2] + subtitleTime(end - offsetMillis, ',' in match.groupValues[3]))
        }.joinToString("\n\n")
    }
    return shifted.map { it.code.toByte() }.toByteArray()
}
private fun shiftAss(text: String, offset: Long): String {
    var events = false
    var start = 1
    var end = 2
    var fields = 10
    return text.split('\n').mapNotNull { line ->
        val trimmed = line.trim()
        if (trimmed.startsWith('[')) events = trimmed.equals("[Events]", true)
        if (events && trimmed.startsWith("Format:", true)) {
            val columns = trimmed.substringAfter(':').split(',').map { it.trim().lowercase() }
            start = columns.indexOf("start"); end = columns.indexOf("end"); fields = columns.size
        }
        if (!events || !trimmed.startsWith("Dialogue:", true) || start < 0 || end < 0) return@mapNotNull line
        val prefix = line.substringBefore(':') + ':'
        val values = line.substringAfter(':').split(',', limit = fields).toMutableList()
        if (values.size <= maxOf(start, end)) return@mapNotNull line
        val from = subtitleMillis(values[start].trim()) ?: return@mapNotNull line
        val until = subtitleMillis(values[end].trim()) ?: return@mapNotNull line
        if (until <= offset) return@mapNotNull null
        values[start] = subtitleTime(from - offset, centiseconds = true)
        values[end] = subtitleTime(until - offset, centiseconds = true)
        prefix + values.joinToString(",")
    }.joinToString("\n")
}
