package org.viptv.app.player

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink

@UnstableApi
internal class Media3SubtitleRenderersFactory(context: Context, private val ready: (Media3PcmProbe) -> Unit) : DefaultRenderersFactory(context) {
    override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioOutputPlaybackParams: Boolean): AudioSink? =
        super.buildAudioSink(context, enableFloatOutput, enableAudioOutputPlaybackParams)?.let { sink ->
            Media3PcmProbe(sink).also { it.observe(false); ready(it) }
        }
}
