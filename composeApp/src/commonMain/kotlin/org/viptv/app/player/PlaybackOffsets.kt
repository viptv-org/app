package org.viptv.app.player

import kotlin.math.roundToLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class TimingOffsets(val audioUs: Long = 0, val subtitleUs: Long = 0)

data class OffsetSupport(val audio: Boolean = false, val subtitles: Boolean = false)

internal const val OFFSET_STEP_US = 100_000L
internal const val OFFSET_LIMIT_US = 10_000_000L

internal fun clampOffset(us: Long): Long =
    ((us.toDouble() / OFFSET_STEP_US).roundToLong() * OFFSET_STEP_US).coerceIn(-OFFSET_LIMIT_US, OFFSET_LIMIT_US)

internal fun offsetLabel(us: Long): String {
    val tenths = clampOffset(us) / OFFSET_STEP_US
    val sign = when { tenths > 0 -> "+"; tenths < 0 -> "−"; else -> "" }
    val magnitude = kotlin.math.abs(tenths)
    return "$sign${magnitude / 10}.${magnitude % 10} s"
}

class PlaybackOffsets internal constructor(initial: TimingOffsets, private val subtitleSync: SubtitleSyncSession) {
    private val mutable = MutableStateFlow(TimingOffsets(clampOffset(initial.audioUs), clampOffset(initial.subtitleUs)))
    val state = mutable.asStateFlow()
    val remembered: TimingOffsets = mutable.value
    internal var changed: ((TimingOffsets) -> Unit)? = null
    private val support = MutableStateFlow(OffsetSupport())
    val supported = support.asStateFlow()

    init { if (remembered.subtitleUs != 0L) subtitleSync.manualDelay(remembered.subtitleUs) }

    val audioUs: Long get() = mutable.value.audioUs
    val subtitleUs: Long get() = mutable.value.subtitleUs

    fun supports(audio: Boolean, subtitles: Boolean) { support.value = OffsetSupport(audio, subtitles) }

    private fun update(next: TimingOffsets) {
        if (next == mutable.value) return
        mutable.value = next
        changed?.invoke(next)
    }

    fun shiftAudio(deltaUs: Long) = update(mutable.value.copy(audioUs = clampOffset(mutable.value.audioUs + deltaUs)))

    fun resetAudio() = update(mutable.value.copy(audioUs = 0))

    fun shiftSubtitle(deltaUs: Long) {
        val next = clampOffset(mutable.value.subtitleUs + deltaUs)
        update(mutable.value.copy(subtitleUs = next))
        subtitleSync.manualDelay(next)
    }

    fun resetSubtitle() {
        update(mutable.value.copy(subtitleUs = 0))
        subtitleSync.resetTiming()
    }

    internal fun automaticSubtitleTiming(enabled: Boolean) {
        if (enabled) update(mutable.value.copy(subtitleUs = 0))
        subtitleSync.setAutomatic(enabled)
    }
}
