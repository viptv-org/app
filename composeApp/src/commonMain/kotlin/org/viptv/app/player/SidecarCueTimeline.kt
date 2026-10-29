package org.viptv.app.player

// Parsed provider cues stay on the original title clock across rolling windows and seeks.
internal data class SidecarCueGroup<T>(val startUs: Long, val endUs: Long?, val cues: List<T>)

internal class SidecarCueTimeline<T>(groups: List<SidecarCueGroup<T>>, private val replace: Boolean = false) {
    private val groups = groups.sortedBy { it.startUs }
    private val events = groups.flatMap { listOfNotNull(it.startUs, it.endUs) }.distinct().sorted()
    private val maximumEnd = LongArray(this.groups.size)
    private var previousEvent = Int.MIN_VALUE
    private var previousCues: List<T> = emptyList()
    init {
        require(groups.size <= 20_000 && groups.sumOf { it.cues.size.toLong() } <= 50_000)
        require(groups.all { it.startUs >= 0 && (it.endUs == null && replace || it.endUs != null && it.endUs >= it.startUs) })
        this.groups.forEachIndexed { index, group -> maximumEnd[index] = maxOf(group.endUs ?: Long.MAX_VALUE,
            maximumEnd.getOrNull(index - 1) ?: 0) }
    }
    fun cuesAt(positionUs: Long): List<T> {
        val event = floor(events.size, { events[it] }, positionUs)
        if (event == previousEvent) return previousCues
        previousEvent = event
        val last = floor(groups.size, { groups[it].startUs }, positionUs)
        previousCues = when {
            last < 0 -> emptyList()
            replace -> groups[last].let { if (positionUs < (it.endUs ?: Long.MAX_VALUE)) it.cues else emptyList() }
            else -> {
                val active = mutableListOf<SidecarCueGroup<T>>()
                var index = last
                while (index >= 0 && maximumEnd[index] > positionUs) {
                    groups[index].takeIf { positionUs < requireNotNull(it.endUs) }?.let(active::add)
                    index--
                }
                // Malformed providers cannot create an unbounded on-screen cue stack.
                active.asReversed().flatMap { it.cues }.take(256)
            }
        }
        return previousCues
    }
    private inline fun floor(size: Int, at: (Int) -> Long, value: Long): Int {
        var low = 0; var high = size
        while (low < high) { val middle = (low + high) ushr 1; if (at(middle) <= value) low = middle + 1 else high = middle }
        return low - 1
    }
}
