package org.viptv.app.player

import kotlin.math.roundToLong

internal data class SubtitleSyncIdentity(val media: String, val audio: String, val subtitle: String)

internal data class SubtitleCorrection(val anchorUs: Long, val offsetUs: Long, val scale: Double = 1.0) {
    init {
        require(scale.isFinite() && scale in .995..1.005)
        require(offsetUs in -15_000_000..15_000_000)
        require(anchorUs >= 0)
    }

    fun subtitleTimeUs(titleTimeUs: Long, manualDelayUs: Long = 0): Long =
        (anchorUs + (titleTimeUs.toDouble() - anchorUs - offsetUs - manualDelayUs) / scale).roundToLong()
}

internal enum class SubtitleSyncAvailability { Unavailable, Observing, Accepted, Ambiguous }

internal class SubtitleSyncSession {
    var epoch = 0L
        private set
    var revision = -1L
        private set
    var observationRevision = -1L
        private set
    var availability = SubtitleSyncAvailability.Unavailable
        private set
    var manual = false
        private set
    private var identity: SubtitleSyncIdentity? = null
    private var correction: SubtitleCorrection? = null
    private var pending: SubtitleCorrection? = null
    private var manualDelayUs = 0L

    fun select(value: SubtitleSyncIdentity?) {
        if (identity == value) return
        identity = value
        epoch++
        revision = -1
        observationRevision = -1
        correction = null
        pending = null
        manualDelayUs = 0
        manual = false
        availability = SubtitleSyncAvailability.Unavailable
    }

    fun discontinuity() {
        epoch++
        revision = -1
        observationRevision = -1
        pending = null
        availability = if (correction == null) SubtitleSyncAvailability.Unavailable else SubtitleSyncAvailability.Accepted
    }

    fun audioChanged(audio: String) { identity?.let { select(it.copy(audio = audio)) } }

    fun offer(epoch: Long, revision: Long, state: SubtitleSyncAvailability, value: SubtitleCorrection? = null,
        observationRevision: Long = revision): Boolean {
        if (identity == null || this.epoch != epoch || observationRevision <= this.observationRevision || revision < this.revision || manual) return false
        if (state == SubtitleSyncAvailability.Accepted && value == null) return false
        if (state == SubtitleSyncAvailability.Accepted && revision == this.revision && value != (pending ?: correction)) return false
        this.revision = revision
        this.observationRevision = observationRevision
        availability = state
        if (state == SubtitleSyncAvailability.Accepted && value != correction) pending = value
        return true
    }

    fun manualDelay(delayUs: Long) {
        manualDelayUs = delayUs.coerceIn(-60_000_000, 60_000_000)
        manual = true
        pending = null
    }

    fun resumeAutomatic() {
        manual = false
        manualDelayUs = 0
        discontinuity()
    }

    fun subtitleTimeUs(titleTimeUs: Long, cueGap: Boolean = false): Long {
        if (cueGap && !manual) pending?.let { correction = it; pending = null }
        return (correction ?: SubtitleCorrection(0, 0)).subtitleTimeUs(titleTimeUs, manualDelayUs)
    }
}
