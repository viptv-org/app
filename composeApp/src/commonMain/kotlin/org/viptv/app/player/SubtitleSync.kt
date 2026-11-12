package org.viptv.app.player

import kotlin.math.roundToLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class SubtitleSyncIdentity(val media: String, val audio: String, val subtitle: String)

internal data class SubtitleCueInterval(val startUs: Long, val endUs: Long)

internal data class SubtitleAnalysisTarget(val epoch: Long, val cues: List<SubtitleCueInterval>)

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

internal data class SubtitleSyncUiState(val providerSelected: Boolean = false, val localAudioAvailable: Boolean = false,
    val automatic: Boolean = false, val manual: Boolean = false, val manualDelayUs: Long = 0,
    val availability: SubtitleSyncAvailability = SubtitleSyncAvailability.Unavailable, val corrected: Boolean = false)

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
    private var cues: List<SubtitleCueInterval> = emptyList()
    private var correction: SubtitleCorrection? = null
    private var pending: SubtitleCorrection? = null
    private var pendingAtUs = 0L
    private var lastTitleUs = 0L
    private var manualDelayUs = 0L
    private var automatic = false
    private var localAudioAvailable = false
    private val mutableUi = MutableStateFlow(SubtitleSyncUiState())
    val ui = mutableUi.asStateFlow()
    private val mutableTarget = MutableStateFlow<SubtitleAnalysisTarget?>(null)
    val target = mutableTarget.asStateFlow()

    fun select(value: SubtitleSyncIdentity?, intervals: List<SubtitleCueInterval> = cues) {
        if (identity == value && intervals === cues) return
        require(intervals.size <= 20_000 && intervals.all { it.startUs >= 0 && it.endUs > it.startUs })
        identity = value
        cues = if (value == null) emptyList() else intervals
        restart(clearCorrection = true)
    }

    fun audioChanged(audio: String) { identity?.let { select(it.copy(audio = audio)) } }

    fun setLocalAudioAvailable(value: Boolean) {
        if (localAudioAvailable == value) return
        localAudioAvailable = value
        restart(clearCorrection = false)
    }

    fun setAutomatic(value: Boolean) {
        automatic = value
        if (value) { manualDelayUs = 0; manual = false }
        restart(clearCorrection = true)
    }

    fun resetTiming() {
        manualDelayUs = 0
        manual = false
        restart(clearCorrection = true)
    }

    fun discontinuity() = restart(clearCorrection = false)

    fun analysisFailed(operation: Long) {
        if (operation != epoch || manual) return
        availability = SubtitleSyncAvailability.Unavailable
        publish()
    }

    private fun restart(clearCorrection: Boolean) {
        epoch++
        revision = -1
        observationRevision = -1
        pending = null
        if (clearCorrection) correction = null
        availability = if (automatic && !manual && localAudioAvailable && identity != null && cues.size in 1..16_384)
            SubtitleSyncAvailability.Observing else SubtitleSyncAvailability.Unavailable
        publish()
    }

    fun offer(epoch: Long, revision: Long, state: SubtitleSyncAvailability, value: SubtitleCorrection? = null,
        observationRevision: Long = revision): Boolean {
        if (!automatic || !localAudioAvailable || cues.isEmpty() || identity == null || this.epoch != epoch || observationRevision <= this.observationRevision || revision < this.revision || manual) return false
        if (state == SubtitleSyncAvailability.Accepted && value == null) return false
        if (state == SubtitleSyncAvailability.Accepted && revision == this.revision && value != (pending ?: correction)) return false
        this.revision = revision
        this.observationRevision = observationRevision
        availability = state
        if (state == SubtitleSyncAvailability.Accepted && value != correction) { pending = value; pendingAtUs = lastTitleUs }
        publish()
        return true
    }

    fun manualDelay(delayUs: Long) {
        manualDelayUs = delayUs.coerceIn(-60_000_000, 60_000_000)
        manual = true
        pending = null
        epoch++
        publish()
    }

    fun adjustManual(deltaUs: Long) = manualDelay(manualDelayUs + deltaUs.coerceIn(-500_000, 500_000))

    fun resumeAutomatic() = setAutomatic(true)

    fun subtitleTimeUs(titleTimeUs: Long, cueGap: Boolean = false): Long {
        lastTitleUs = titleTimeUs
        if (!manual && automatic && (cueGap || titleTimeUs - pendingAtUs >= 5_000_000)) pending?.let {
            correction = it; pending = null; publish()
        }
        return (correction ?: SubtitleCorrection(0, 0)).subtitleTimeUs(titleTimeUs, manualDelayUs)
    }

    private fun publish() {
        mutableUi.value = SubtitleSyncUiState(identity != null, localAudioAvailable, automatic, manual, manualDelayUs, availability, correction != null)
        mutableTarget.value = if (automatic && !manual && localAudioAvailable && identity != null && cues.size in 1..16_384)
            SubtitleAnalysisTarget(epoch, cues) else null
    }
}
