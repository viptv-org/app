package org.viptv.app.features.live

import org.viptv.engine.wire.Card

data class LiveProgram(val id: String, val title: String, val start: Int, val end: Int,
    val description: String? = null, val artwork: String? = null)
data class LiveChannel(val card: Card, val number: String? = null, val category: String? = null,
    val programs: List<LiveProgram> = emptyList(), val id: String = card.identity.id, val connectionId: String = card.provider_id, val groupId: String? = null, val favorite: Boolean = false, val favoritePending: Boolean = false) {
    fun current(now: Int?) = now?.let { minute -> programs.firstOrNull { minute in it.start until it.end } }
    fun next(now: Int?) = now?.let { minute -> programs.firstOrNull { it.start > minute } }
}

internal fun LiveChannel.key(): String = card.provider_id + ":" + card.identity.id

internal fun LiveChannel.initialProgram(start: Int, span: Int, now: Int?): LiveProgram? {
    val windowed = programs.filter { it.end > start && it.start < start + span }
    return windowed.find { now != null && now in it.start until it.end } ?: windowed.firstOrNull()
}

@androidx.compose.runtime.Composable
internal fun liveTime(minutes: Int, period: Boolean = false): String {
    LocalLiveClock.current?.let { return liveTimeAt(it.originMillis + minutes.toLong() * 60_000, period) }
    val dayMinute = (minutes % 1440 + 1440) % 1440
    val hour = dayMinute / 60
    return "${(hour + 11) % 12 + 1}:${(dayMinute % 60).toString().padStart(2, '0')}" +
        if (period) { if (hour < 12) " AM" else " PM" } else ""
}
internal fun liveProgress(program: LiveProgram?, now: Int?) =
    if (program != null && now != null && program.end > program.start) ((now - program.start).toFloat() / (program.end - program.start)).coerceIn(0f, 1f) else 0f
