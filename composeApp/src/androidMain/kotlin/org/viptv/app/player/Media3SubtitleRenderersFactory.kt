package org.viptv.app.player

import android.content.Context
import android.os.Looper
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.text.TextOutput

@UnstableApi
internal class Media3SubtitleRenderersFactory(context: Context, private val offsets: PlaybackOffsets,
    private val ready: (Media3PcmProbe) -> Unit) : DefaultRenderersFactory(context) {
    override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioOutputPlaybackParams: Boolean): AudioSink? =
        super.buildAudioSink(context, enableFloatOutput, enableAudioOutputPlaybackParams)?.let { sink ->
            Media3PcmProbe(Media3AudioDelaySink(sink) { offsets.audioUs }).also { it.observe(false); ready(it) }
        }

    override fun buildTextRenderers(context: Context, output: TextOutput, outputLooper: Looper, extensionRendererMode: Int,
        out: ArrayList<Renderer>) {
        val built = ArrayList<Renderer>()
        super.buildTextRenderers(context, output, outputLooper, extensionRendererMode, built)
        built.mapTo(out) { Media3SubtitleDelayRenderer(it) { offsets.subtitleUs } }
    }
}
