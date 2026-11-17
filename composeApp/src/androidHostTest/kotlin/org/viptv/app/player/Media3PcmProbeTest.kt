package org.viptv.app.player

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.Timeline
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioSink
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.lang.reflect.Proxy
import java.nio.ByteBuffer
import kotlin.test.*

@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class Media3PcmProbeTest {
    @Test fun analysisRequiresKnownStaticZeroOriginWithoutMillisecondTruncation() {
        val window = Timeline.Window()
        window.positionInFirstPeriodUs = 0
        assertTrue(nativeSubtitleWindowEligible(window))
        window.positionInFirstPeriodUs = 500
        assertEquals(0L, window.positionInFirstPeriodMs)
        assertFalse(nativeSubtitleWindowEligible(window))
        window.positionInFirstPeriodUs = C.TIME_UNSET
        assertFalse(nativeSubtitleWindowEligible(window))
        window.positionInFirstPeriodUs = 0
        window.isPlaceholder = true
        assertFalse(nativeSubtitleWindowEligible(window))
        window.isPlaceholder = false
        window.isDynamic = true
        assertFalse(nativeSubtitleWindowEligible(window))
        window.isDynamic = false
        window.liveConfiguration = MediaItem.LiveConfiguration.Builder().build()
        assertFalse(nativeSubtitleWindowEligible(window))
    }

    private var writes = 0
    private val sink = Proxy.newProxyInstance(AudioSink::class.java.classLoader, arrayOf(AudioSink::class.java)) { _, method, args ->
        when (method.name) {
            "handleBuffer" -> {
                val buffer = args!![0] as ByteBuffer
                writes++
                buffer.position(minOf(buffer.limit(), buffer.position() + 4))
                !buffer.hasRemaining()
            }
            else -> null
        }
    } as AudioSink

    private fun configured(): Media3PcmProbe = Media3PcmProbe(sink).apply {
        configure(AudioSink.AudioSinkConfig.Builder(Format.Builder().setSampleMimeType(MimeTypes.AUDIO_RAW)
            .setPcmEncoding(C.ENCODING_PCM_16BIT).setSampleRate(48_000).setChannelCount(2).build()).build())
    }

    @Test fun copiesOnceBeforePartialConsumptionWithoutMovingBufferAndMapsStreamOffset() {
        val probe = configured()
        probe.setOutputStreamOffsetUs(1_000_000)
        val bytes = ByteArray(16) { it.toByte() }
        val input = ByteBuffer.wrap(bytes)
        while (!probe.handleBuffer(input, 3_000_000, 1)) Unit
        assertEquals(4, writes)
        val span = assertNotNull(probe.poll())
        assertEquals(2_000_000L, span.startUs)
        assertContentEquals(bytes, span.pcm16)
        assertNull(probe.poll())
    }

    @Test fun overflowAndFlushInvalidateOldEvidenceAndPlaybackKeepsConsuming() {
        val probe = configured()
        repeat(9) { assertTrue(probe.handleBuffer(ByteBuffer.wrap(ByteArray(4)), it * 1000L, 1)) }
        assertEquals(9, writes)
        assertEquals(1L, probe.gaps.get())
        assertNull(probe.poll())
        probe.handleBuffer(ByteBuffer.wrap(ByteArray(4)), 12_000, 1)
        probe.flush()
        assertNull(probe.poll())
    }

    @Test fun encodedInputIsNotMisrepresentedAsPcm() {
        val probe = configured()
        probe.configure(AudioSink.AudioSinkConfig.Builder(Format.Builder().setSampleMimeType(MimeTypes.AUDIO_AC3)
            .setSampleRate(48_000).setChannelCount(2).build()).build())
        assertTrue(probe.handleBuffer(ByteBuffer.wrap(ByteArray(4)), 0, 1))
        assertNull(probe.poll())
    }
}
