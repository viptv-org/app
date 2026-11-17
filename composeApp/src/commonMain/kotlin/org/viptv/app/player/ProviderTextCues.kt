package org.viptv.app.player

private val providerTextTiming = Regex("^(\\d{1,3}:\\d{2}:\\d{2}[.,]\\d{3}|\\d{2}:\\d{2}[.,]\\d{3})\\s+-->\\s+(\\d{1,3}:\\d{2}:\\d{2}[.,]\\d{3}|\\d{2}:\\d{2}[.,]\\d{3})(?:\\s+.*)?$")

internal fun parseProviderTextCues(bytes: ByteArray): SidecarCueTimeline<String> {
    require(bytes.isNotEmpty() && bytes.size <= 8 * 1024 * 1024)
    val text = bytes.decodeToString(throwOnInvalidSequence = true).trimStart('\uFEFF').replace("\r\n", "\n").replace('\r', '\n')
    require(!text.startsWith("[Script Info]")) { "Unsupported subtitle format" }
    val cues = ArrayList<SidecarCueGroup<String>>()
    for (block in text.split(Regex("\n[ \\t]*\n"))) {
        val lines = block.trim().lines()
        if (lines.firstOrNull()?.let { it.startsWith("NOTE") || it == "STYLE" || it == "REGION" } == true) continue
        val timingIndex = lines.indexOfFirst { providerTextTiming.matches(it.trim()) }
        if (timingIndex < 0) continue
        require(timingIndex <= 1)
        val match = requireNotNull(providerTextTiming.matchEntire(lines[timingIndex].trim()))
        val start = providerTextTimeUs(match.groupValues[1])
        val end = providerTextTimeUs(match.groupValues[2])
        require(end > start)
        val content = lines.drop(timingIndex + 1).joinToString("\n")
            .replace(Regex("<[^>]*>"), "")
            .replace("&lt;", "<").replace("&gt;", ">").replace("&nbsp;", " ").replace("&amp;", "&")
        require(content.length <= 16_384)
        if (content.isNotBlank()) cues += SidecarCueGroup(start, end, listOf(content))
        require(cues.size <= 20_000)
    }
    require(cues.isNotEmpty()) { "No readable subtitle cues" }
    return SidecarCueTimeline(cues)
}

private fun providerTextTimeUs(value: String): Long {
    val parts = value.replace(',', '.').split(':')
    val seconds = parts.last().split('.')
    val minutes = parts[parts.lastIndex - 1].toLong()
    val hours = if (parts.size == 3) parts[0].toLong() else 0
    require(minutes < 60 && seconds[0].toLong() < 60)
    return ((hours * 3600 + minutes * 60 + seconds[0].toLong()) * 1000 + seconds[1].toLong()) * 1000
}
