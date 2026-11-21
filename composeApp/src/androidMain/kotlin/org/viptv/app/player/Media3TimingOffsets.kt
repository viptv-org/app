package org.viptv.app.player

import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ForwardingRenderer
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink

// The audio sink is the playback clock: reporting it ahead by the delay shows video earlier, so the sound lands later.
@UnstableApi
internal class Media3AudioDelaySink(sink: AudioSink, private val delayUs: () -> Long) : ForwardingAudioSink(sink) {
    override fun getCurrentPositionUs(sourceEnded: Boolean): Long {
        val position = super.getCurrentPositionUs(sourceEnded)
        return if (position == AudioSink.CURRENT_POSITION_NOT_SET) position else position + delayUs()
    }
}

@UnstableApi
internal class Media3SubtitleDelayRenderer(renderer: Renderer, private val delayUs: () -> Long) : ForwardingRenderer(renderer) {
    override fun render(positionUs: Long, elapsedRealtimeUs: Long) = super.render(positionUs - delayUs(), elapsedRealtimeUs)
}
