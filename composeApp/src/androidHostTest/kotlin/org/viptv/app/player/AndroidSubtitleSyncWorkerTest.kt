package org.viptv.app.player

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioSink
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.lang.reflect.Proxy
import java.nio.ByteBuffer
import kotlin.test.*

@UnstableApi
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AndroidSubtitleSyncWorkerTest {
    private var forwarded = 0
    private fun probe(): Media3PcmProbe {
        val sink = Proxy.newProxyInstance(AudioSink::class.java.classLoader, arrayOf(AudioSink::class.java)) { _, method, args ->
            if (method.name == "handleBuffer") { forwarded++; (args!![0] as ByteBuffer).let { it.position(it.limit()) }; true } else null
        } as AudioSink
        return Media3PcmProbe(sink).apply {
            observe(false)
            configure(AudioSink.AudioSinkConfig.Builder(Format.Builder().setSampleMimeType(MimeTypes.AUDIO_RAW)
                .setPcmEncoding(C.ENCODING_PCM_16BIT).setSampleRate(48_000).setChannelCount(2).build()).build())
        }
    }
    private fun session() = SubtitleSyncSession().apply {
        select(SubtitleSyncIdentity("owned", "audio", "subtitle"), listOf(SubtitleCueInterval(0, 20_000_000)))
    }
    private fun result(epoch: Long) = """{"epoch":$epoch,"status":"ok","processedFrames":1,"emittedFrames":1,"estimate":{"epoch":$epoch,"algorithmVersion":"activity-offset-affine-v1","confidenceKind":"uncalibratedHeuristic","revision":1,"observationRevision":1,"state":"accepted","correction":{"anchorUs":0,"offsetUs":1000000,"scale":1.0}}}"""

    @Test fun dormantByDefaultOffThreadQueueAndManualFence() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val capture = probe()
        val session = session()
        var pushed = 0
        var created = 0
        var closed = 0
        val worker = AndroidSubtitleSyncWorker(this, session, { capture }, MutableStateFlow(true),
            workerDispatcher = dispatcher, mainDispatcher = dispatcher, nativeAvailable = { true }, engineFactory = {
                created++
                object : SubtitlePcmEngine {
                    override fun push(epoch: Long, span: SubtitlePcmSpan): String { pushed++; return result(epoch) }
                    override fun close() { closed++ }
                }
            })
        try {
            runCurrent()
            capture.handleBuffer(ByteBuffer.wrap(ByteArray(3840)), 0, 1)
            advanceTimeBy(100); runCurrent()
            assertEquals(0, created)
            assertEquals(0, pushed)
            session.setAutomatic(true)
            runCurrent()
            val epoch = session.epoch
            capture.handleBuffer(ByteBuffer.wrap(ByteArray(3840)), 20_000, 1)
            assertEquals(0, pushed)
            advanceTimeBy(20); runCurrent()
            assertEquals(1, pushed)
            assertEquals(9_000_000L, session.subtitleTimeUs(10_000_000, true))
            session.adjustManual(500_000)
            runCurrent()
            capture.handleBuffer(ByteBuffer.wrap(ByteArray(3840)), 40_000, 1)
            advanceTimeBy(100); runCurrent()
            assertEquals(1, pushed)
            assertEquals(1, closed)
            assertFalse(session.acceptNativeEstimate(parseSubtitlePcmResult(result(epoch)).estimate!!))
            assertEquals(8_500_000L, session.subtitleTimeUs(10_000_000))
            session.setAutomatic(false)
            assertEquals(9_500_000L, session.subtitleTimeUs(10_000_000))
            assertEquals(3, forwarded)
        } finally { worker.close(); runCurrent() }
    }

    @Test fun acquisitionDiscontinuityFencesOldNativeResultWithoutClearingAcceptedView() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val capture = probe()
        val session = session()
        val epochs = mutableListOf<Long>()
        val delivery = MutableStateFlow(true)
        val worker = AndroidSubtitleSyncWorker(this, session, { capture }, delivery,
            workerDispatcher = dispatcher, mainDispatcher = dispatcher, nativeAvailable = { true }, engineFactory = { target ->
                epochs += target.epoch
                object : SubtitlePcmEngine {
                    override fun push(epoch: Long, span: SubtitlePcmSpan) = result(epoch)
                    override fun close() = Unit
                }
            })
        try {
            runCurrent(); session.setAutomatic(true); runCurrent()
            val before = session.epoch
            capture.handleBuffer(ByteBuffer.wrap(ByteArray(3840)), 0, 1)
            advanceTimeBy(20); runCurrent()
            assertEquals(9_000_000L, session.subtitleTimeUs(10_000_000, true))
            capture.flush()
            advanceTimeBy(20); runCurrent()
            assertTrue(session.epoch > before)
            assertEquals(2, epochs.size)
            assertFalse(session.acceptNativeEstimate(parseSubtitlePcmResult(result(before)).estimate!!))
            assertEquals(9_000_000L, session.subtitleTimeUs(10_000_000))
            delivery.value = false
            advanceTimeBy(100); runCurrent()
            assertFalse(session.ui.value.localAudioAvailable)
            assertNull(session.target.value)
        } finally { worker.close(); runCurrent() }
    }

    @Test fun nativeFailureAbstainsWithoutTouchingPlaybackSink() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val capture = probe()
        val session = session()
        val worker = AndroidSubtitleSyncWorker(this, session, { capture }, MutableStateFlow(true),
            workerDispatcher = dispatcher, mainDispatcher = dispatcher, nativeAvailable = { true },
            engineFactory = { throw UnsatisfiedLinkError("owned failure") })
        try {
            runCurrent(); session.setAutomatic(true); runCurrent()
            assertEquals(SubtitleSyncAvailability.Unavailable, session.availability)
            assertEquals("failed", worker.stats.value.nativeStatus)
            assertTrue(capture.handleBuffer(ByteBuffer.wrap(ByteArray(3840)), 0, 1))
            assertEquals(1, forwarded)
            assertEquals(10_000_000L, session.subtitleTimeUs(10_000_000))
        } finally { worker.close(); runCurrent() }
    }
    @Test fun overloadStopsAnalysisUntilExplicitResetAndNeverBackpressuresSink() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val capture = probe()
        val session = session()
        var created = 0
        val worker = AndroidSubtitleSyncWorker(this, session, { capture }, MutableStateFlow(true),
            workerDispatcher = dispatcher, mainDispatcher = dispatcher, nativeAvailable = { true }, engineFactory = {
                created++
                object : SubtitlePcmEngine {
                    override fun push(epoch: Long, span: SubtitlePcmSpan) = result(epoch)
                    override fun close() = Unit
                }
            })
        try {
            runCurrent(); session.setAutomatic(true); runCurrent()
            repeat(9) { assertTrue(capture.handleBuffer(ByteBuffer.wrap(ByteArray(3840)), it * 20_000L, 1)) }
            advanceTimeBy(200); runCurrent()
            assertEquals(9, forwarded)
            assertEquals(1, created)
            assertEquals(SubtitleSyncAvailability.Unavailable, session.availability)
            assertEquals(0L, worker.stats.value.spans)
            advanceTimeBy(200); runCurrent()
            assertEquals(1, created)
            session.resetTiming(); runCurrent()
            assertEquals(2, created)
        } finally { worker.close(); runCurrent() }
    }

}
