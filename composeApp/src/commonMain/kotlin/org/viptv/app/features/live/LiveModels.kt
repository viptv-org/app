package org.viptv.app.features.live

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.viptv.app.format.formatTimeOfDay
import org.viptv.app.format.is24HourTime
import org.viptv.engine.wire.Card
import kotlin.math.ceil

data class LiveProgram(val id: String, val title: String, val start: Int, val end: Int,
    val description: String? = null, val artwork: String? = null)
data class LiveChannel(val card: Card, val number: String? = null, val category: String? = null,
    val programs: List<LiveProgram> = emptyList(), val id: String = card.identity.id, val connectionId: String = card.provider_id, val groupId: String? = null, val favorite: Boolean = false) {
    fun current(now: Int?) = now?.let { minute -> programs.firstOrNull { minute in it.start until it.end } }
    fun next(now: Int?) = now?.let { minute -> programs.firstOrNull { it.start > minute } }
}

internal fun LiveChannel.key(): String = card.provider_id + ":" + card.identity.id

private val liveMonogramSkip = setOf("uhd", "fhd", "hdr", "4k", "8k")

internal fun liveChannelMonogram(title: String): String? {
    val words = buildList {
        val word = StringBuilder()
        for (character in title.substringAfter('|', title)) {
            if (character.isLetterOrDigit()) word.append(character)
            else if (word.isNotEmpty()) { add(word.toString()); word.clear() }
        }
        if (word.isNotEmpty()) add(word.toString())
    }
    val chosen = words.filter { it.length > 2 && it.lowercase() !in liveMonogramSkip }.ifEmpty { words }
    return when {
        chosen.isEmpty() -> null
        chosen.size == 1 -> chosen.first().take(4).uppercase()
        else -> chosen.take(3).map { it.first().uppercaseChar() }.joinToString("")
    }
}

internal fun liveProgramPadding(television: Boolean, scale: Float) = (if (television) 18 else 12).dp * scale

private val liveProgramLabelWidth = 32.dp

internal fun liveGuideMinimumMinutes(television: Boolean, scale: Float, minuteWidth: Dp, gap: Dp): Int =
    ceil((liveProgramPadding(television, scale) * 2 + liveProgramLabelWidth * scale + gap) / minuteWidth).toInt().coerceAtLeast(1)

internal data class LiveGuideSlot(val program: LiveProgram, val begin: Int, val end: Int)

internal fun liveGuideSlots(programs: List<LiveProgram>, start: Int, span: Int, minimumMinutes: Int): List<LiveGuideSlot> {
    val slots = programs.mapNotNull { program ->
        val begin = maxOf(start, program.start)
        val end = minOf(start + span, program.end)
        if (end > begin) LiveGuideSlot(program, begin, end) else null
    }.sortedBy { it.begin }.toMutableList()
    while (true) {
        val index = slots.indexOfFirst { it.end - it.begin < minimumMinutes }
        if (index < 0) return slots
        val slot = slots[index]
        val previous = slots.getOrNull(index - 1)
        val next = slots.getOrNull(index + 1)
        when {
            previous != null && previous.end == slot.begin -> { slots[index - 1] = previous.copy(end = slot.end); slots.removeAt(index) }
            next != null && next.begin == slot.end -> { slots[index + 1] = next.copy(begin = slot.begin); slots.removeAt(index) }
            else -> slots.removeAt(index)
        }
    }
}

internal fun List<LiveGuideSlot>.initial(now: Int?): LiveGuideSlot? =
    find { now != null && now in it.program.start until it.program.end } ?: firstOrNull()

@androidx.compose.runtime.Composable
internal fun liveTime(minutes: Int, period: Boolean = false): String {
    LocalLiveClock.current?.let { return liveTimeAt(it.originMillis + minutes.toLong() * 60_000, period) }
    val dayMinute = (minutes % 1440 + 1440) % 1440
    return formatTimeOfDay(dayMinute / 60, dayMinute % 60, is24HourTime(), period)
}
internal fun liveProgress(program: LiveProgram?, now: Int?) =
    if (program != null && now != null && program.end > program.start) ((now - program.start).toFloat() / (program.end - program.start)).coerceIn(0f, 1f) else 0f

internal enum class LiveFraming { OnAir, Upcoming, Ended }

internal fun liveFraming(program: LiveProgram, now: Int?): LiveFraming = when {
    now == null || now in program.start until program.end -> LiveFraming.OnAir
    now < program.start -> LiveFraming.Upcoming
    else -> LiveFraming.Ended
}

internal fun liveLength(program: LiveProgram) = "${program.end - program.start} min"

private fun liveRemaining(program: LiveProgram, now: Int) = "${(program.end - now).coerceAtLeast(0)} min left"

// The phone preview's own line: the live countdown while on air, the start and the programme's length before it, "Ended" after.
@androidx.compose.runtime.Composable
internal fun liveStatus(program: LiveProgram, now: Int?): String? = when (liveFraming(program, now)) {
    LiveFraming.OnAir -> now?.let { liveRemaining(program, it) }
    LiveFraming.Upcoming -> "Starts at ${liveTime(program.start)} · ${liveLength(program)}"
    LiveFraming.Ended -> "Ended"
}

// TV and the desktop preview keep their start – end line and append the same clock state to it.
@androidx.compose.runtime.Composable
internal fun liveRangeStatus(program: LiveProgram, now: Int?): String = when (liveFraming(program, now)) {
    LiveFraming.OnAir -> now?.let { " · ${liveRemaining(program, it)}" } ?: ""
    LiveFraming.Upcoming -> " · ${liveLength(program)}"
    LiveFraming.Ended -> " · Ended"
}
