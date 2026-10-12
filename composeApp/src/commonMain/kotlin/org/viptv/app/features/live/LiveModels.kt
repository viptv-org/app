package org.viptv.app.features.live

import org.viptv.engine.wire.Card

data class LiveProgram(val id: String, val title: String, val start: Int, val end: Int,
    val description: String? = null, val artwork: String? = null)
data class LiveChannel(val card: Card, val number: String? = null, val category: String? = null,
    val programs: List<LiveProgram> = emptyList()) {
    fun current(now: Int?) = now?.let { minute -> programs.firstOrNull { minute in it.start until it.end } }
    fun next(now: Int?) = now?.let { minute -> programs.firstOrNull { it.start > minute } }
}

internal fun liveTime(minutes: Int, period: Boolean = false): String {
    val hour = minutes / 60 % 24
    return "${(hour + 11) % 12 + 1}:${(minutes % 60).toString().padStart(2, '0')}" +
        if (period) { if (hour < 12) " AM" else " PM" } else ""
}
internal fun liveProgress(program: LiveProgram?, now: Int?) =
    if (program != null && now != null && program.end > program.start) ((now - program.start).toFloat() / (program.end - program.start)).coerceIn(0f, 1f) else 0f
