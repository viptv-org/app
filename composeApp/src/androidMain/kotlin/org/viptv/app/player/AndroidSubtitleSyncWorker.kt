package org.viptv.app.player

import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import uniffi.viptv_core.SubtitlePcmSyncBridge
import uniffi.viptv_core.subtitlePcmCapabilities

private val subtitleAnalysisDispatcher = Dispatchers.Default.limitedParallelism(1)

internal interface SubtitlePcmEngine : AutoCloseable {
    fun push(epoch: Long, span: SubtitlePcmSpan): String
}

private class NativeSubtitlePcmEngine(target: SubtitleAnalysisTarget) : SubtitlePcmEngine {
    private val bridge = SubtitlePcmSyncBridge(target.epoch.toULong(), subtitleCueJson(target.cues))
    override fun push(epoch: Long, span: SubtitlePcmSpan): String = bridge.pushPcm16(epoch.toULong(), span.startUs,
        span.sampleRate.toUInt(), span.channels.toUByte(), "pcmS16Le", span.pcm16)
    override fun close() = bridge.close()
}

internal data class SubtitleWorkerStats(val spans: Long = 0, val pcmBytes: Long = 0, val processedFrames: Long = 0,
    val gaps: Long = 0, val acceptedOffsetUs: Long? = null, val nativeStatus: String = "off", val workerThread: String = "")

@UnstableApi
internal class AndroidSubtitleSyncWorker(scope: CoroutineScope, private val session: SubtitleSyncSession,
    private val capture: () -> Media3PcmProbe?, private val deliveryEligible: StateFlow<Boolean>,
    private val diagnostics: ((String) -> Unit)? = null,
    private val workerDispatcher: CoroutineDispatcher = subtitleAnalysisDispatcher,
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val engineFactory: (SubtitleAnalysisTarget) -> SubtitlePcmEngine = ::NativeSubtitlePcmEngine,
    private val nativeAvailable: () -> Boolean = {
        val value = Json.parseToJsonElement(subtitlePcmCapabilities()).jsonObject
        value["available"]?.jsonPrimitive?.booleanOrNull == true && value["encoding"]?.jsonPrimitive?.content == "pcmS16Le" &&
            value["frameMs"]?.jsonPrimitive?.intOrNull == 20 && value["maxInputBytes"]?.jsonPrimitive?.intOrNull == 32_768
    }) {
    private val mutableStats = MutableStateFlow(SubtitleWorkerStats())
    val stats = mutableStats.asStateFlow()
    private val owner = SupervisorJob(scope.coroutineContext[Job])
    private val workerScope = CoroutineScope(scope.coroutineContext + owner + workerDispatcher)
    private var backendReady = false

    init {
        workerScope.launch {
            combine(deliveryEligible, session.ui) { eligible, ui -> eligible && ui.providerSelected }.first { it }
            while (isActive && (!deliveryEligible.value || !session.ui.value.providerSelected || !qualifiedFormat())) delay(100)
            val supported = try { nativeAvailable() } catch (error: Throwable) {
                if (error is CancellationException) throw error
                diagnostics?.invoke("subtitle_sync=unavailable native=missing")
                false
            }
            backendReady = supported
            if (!supported) {
                withContext(mainDispatcher) { session.setLocalAudioAvailable(false) }
                return@launch
            }
            while (isActive) {
                val available = deliveryEligible.value && qualifiedFormat()
                withContext(mainDispatcher) { session.setLocalAudioAvailable(available) }
                delay(100)
            }
        }
        workerScope.launch {
            session.target.collectLatest { target ->
                if (target == null || !backendReady) { capture()?.observe(false); return@collectLatest }
                val probe = capture() ?: return@collectLatest
                var engine: SubtitlePcmEngine? = null
                try {
                    probe.observe(false)
                    while (probe.poll() != null) Unit
                    engine = engineFactory(target)
                    probe.observe(true)
                    val sourceEpoch = probe.epoch
                    val sourceGaps = probe.gaps.get()
                    var firstUs: Long? = null
                    var spans = 0L
                    var bytes = 0L
                    while (currentCoroutineContext().isActive) {
                        if (!deliveryEligible.value) {
                            withContext(mainDispatcher) { session.setLocalAudioAvailable(false) }
                            break
                        }
                        if (probe.epoch != sourceEpoch) {
                            mutableStats.update { it.copy(gaps = it.gaps + 1, nativeStatus = "gap") }
                            withContext(mainDispatcher) {
                                if (session.epoch == target.epoch) {
                                    if (probe.gaps.get() != sourceGaps) session.analysisFailed(target.epoch)
                                    else session.discontinuity()
                                }
                            }
                            break
                        }
                        val span = probe.poll()
                        if (span == null) { delay(20); continue }
                        if (span.epoch != sourceEpoch) continue
                        if (span.sampleRate != 48_000 || span.channels != 2) error("Unqualified PCM layout")
                        if (firstUs == null) firstUs = span.startUs
                        if (span.startUs - requireNotNull(firstUs) >= 120_000_000) break
                        val result = parseSubtitlePcmResult(engine.push(target.epoch, span))
                        currentCoroutineContext().ensureActive()
                        if (result.epoch != target.epoch || probe.epoch != sourceEpoch) continue
                        if (result.status == "needsNewEpoch") {
                            withContext(mainDispatcher) { if (session.epoch == target.epoch) session.discontinuity() }
                            break
                        }
                        check(result.status == "ok")
                        spans++; bytes += span.pcm16.size
                        result.estimate?.let { estimate ->
                            withContext(mainDispatcher) {
                                if (probe.epoch == sourceEpoch && deliveryEligible.value) session.acceptNativeEstimate(estimate)
                            }
                        }
                        mutableStats.value = mutableStats.value.copy(spans = spans, pcmBytes = bytes,
                            processedFrames = result.processedFrames, nativeStatus = result.status,
                            acceptedOffsetUs = result.estimate?.correction?.offsetUs ?: mutableStats.value.acceptedOffsetUs,
                            workerThread = Thread.currentThread().name)
                    }
                } catch (error: Throwable) {
                    if (error is CancellationException) throw error
                    diagnostics?.invoke("subtitle_sync=unavailable worker=failed")
                    mutableStats.update { it.copy(nativeStatus = "failed") }
                    withContext(mainDispatcher) { session.analysisFailed(target.epoch) }
                } finally {
                    probe.observe(false)
                    runCatching { engine?.close() }
                }
            }
        }
    }

    private fun qualifiedFormat(): Boolean = capture()?.format?.let { it.sampleRate == 48_000 && it.channels == 2 } == true

    fun close() {
        capture()?.observe(false)
        owner.cancel()
        session.select(null)
        session.setLocalAudioAvailable(false)
    }
}
