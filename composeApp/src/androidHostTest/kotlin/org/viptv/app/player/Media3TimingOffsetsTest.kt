package org.viptv.app.player

import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.audio.AudioSink
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.lang.reflect.Proxy
import kotlin.test.*

@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class Media3TimingOffsetsTest {
    private var sinkPosition = 5_000_000L
    private val sink = Proxy.newProxyInstance(AudioSink::class.java.classLoader, arrayOf(AudioSink::class.java)) { _, method, _ ->
        when (method.name) {
            "getCurrentPositionUs" -> sinkPosition
            else -> null
        }
    } as AudioSink
    private val rendered = mutableListOf<Long>()
    private val text = Proxy.newProxyInstance(Renderer::class.java.classLoader, arrayOf(Renderer::class.java)) { _, method, args ->
        if (method.name == "render") rendered += args!![0] as Long
        null
    } as Renderer

    @Test fun theAudioClockMovesBothWaysLiveWithoutReopening() {
        val offsets = PlaybackOffsets(TimingOffsets(), SubtitleSyncSession())
        val delayed = Media3AudioDelaySink(sink) { offsets.audioUs }
        assertEquals(5_000_000L, delayed.getCurrentPositionUs(false))
        offsets.shiftAudio(300_000)
        assertEquals(5_300_000L, delayed.getCurrentPositionUs(false), "a later audio track reports the clock ahead so the picture leads")
        repeat(8) { offsets.shiftAudio(-100_000) }
        assertEquals(4_500_000L, delayed.getCurrentPositionUs(false))
        sinkPosition = AudioSink.CURRENT_POSITION_NOT_SET
        assertEquals(AudioSink.CURRENT_POSITION_NOT_SET, delayed.getCurrentPositionUs(false))
    }

    @Test fun embeddedSubtitlesRenderAtTheShiftedTime() {
        val offsets = PlaybackOffsets(TimingOffsets(subtitleUs = 1_200_000), SubtitleSyncSession())
        val delayed = Media3SubtitleDelayRenderer(text) { offsets.subtitleUs }
        delayed.render(10_000_000, 0)
        offsets.shiftSubtitle(-2_000_000)
        delayed.render(10_000_000, 0)
        assertEquals(listOf(8_800_000L, 10_800_000L), rendered)
    }
}
