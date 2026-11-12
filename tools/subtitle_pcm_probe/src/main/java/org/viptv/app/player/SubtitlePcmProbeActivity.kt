@file:OptIn(androidx.media3.common.util.UnstableApi::class)
package org.viptv.app.player

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sin

class SubtitlePcmProbeActivity : Activity() {
    private var player: ExoPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var probe: Media3PcmProbe? = null
    private var spans = 0
    private var bytes = 0L
    private var firstUs = -1L
    private var lastUs = -1L
    private var ordered = true
    private var nonzero = false
    private var previousEpoch = -1L
    private var epochs = 0
    private var stage = 0
    private var finished = false
    private lateinit var status: TextView
    private val drain = object : Runnable {
        override fun run() {
            while (true) {
                val span = probe?.poll() ?: break
                if (firstUs < 0) firstUs = span.startUs
                if (span.epoch != previousEpoch) { previousEpoch = span.epoch; epochs++; lastUs = -1 }
                if (span.startUs < lastUs) ordered = false
                lastUs = span.startUs
                spans++; bytes += span.pcm16.size
                nonzero = nonzero || span.pcm16.any { it.toInt() != 0 }
            }
            val current = player ?: return
            if (stage == 0 && current.currentPosition >= 2500) {
                stage = 1; current.seekTo(5000)
            } else if (stage == 1 && current.currentPosition >= 6500) {
                stage = 2; current.seekTo(1000)
            } else if (stage == 2 && current.currentPosition >= 2500) {
                stage = 3; current.setPlaybackSpeed(1.25f)
            }
            if (current.playbackState == Player.STATE_ENDED && !finished) {
                finished = true
                val report = "spans=$spans bytes=$bytes firstUs=$firstUs lastUs=$lastUs ordered=$ordered nonzero=$nonzero gaps=${probe?.gaps?.get()} stage=$stage epochs=$epochs sdk=${android.os.Build.VERSION.SDK_INT} media3=1.11.1 state=ended"
                File(filesDir, "result.txt").writeText(report)
                status.text = report
                android.util.Log.i("SubtitlePcmProbe", report)
            }
            if (!finished) handler.postDelayed(this, 20)
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        File(filesDir, "result.txt").delete()
        status = TextView(this); status.text = "Running owned local PCM fixture"; setContentView(status)
        val fixture = File(filesDir, "owned-tone.wav")
        val rate = 48000; val frames = rate * 10
        val wav = ByteBuffer.allocate(44 + frames * 4).order(ByteOrder.LITTLE_ENDIAN)
        wav.put("RIFF".toByteArray()).putInt(36 + frames * 4).put("WAVEfmt ".toByteArray()).putInt(16)
            .putShort(1).putShort(2).putInt(rate).putInt(rate * 4).putShort(4).putShort(16)
            .put("data".toByteArray()).putInt(frames * 4)
        repeat(frames) { frame ->
            val sample = if (frame / rate % 2 == 0) (sin(2 * Math.PI * 440 * frame / rate) * 10000).toInt().toShort() else 0.toShort()
            wav.putShort(sample).putShort(sample)
        }
        fixture.writeBytes(wav.array())
        val factory = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(context: android.content.Context, enableFloatOutput: Boolean, enableAudioOutputPlaybackParams: Boolean): AudioSink? =
                super.buildAudioSink(context, enableFloatOutput, enableAudioOutputPlaybackParams)?.let { Media3PcmProbe(it).also { value -> probe = value } }
        }
        player = ExoPlayer.Builder(this, factory).build().also {
            it.addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    finished = true
                    File(filesDir, "result.txt").writeText("state=failed error=${error.errorCodeName}")
                }
            })
            it.setMediaItem(MediaItem.fromUri(android.net.Uri.fromFile(fixture)))
            it.prepare(); it.play()
        }
        handler.post(drain)
        handler.postDelayed({
            if (!finished) {
                finished = true
                File(filesDir, "result.txt").writeText("state=timeout spans=$spans bytes=$bytes")
                player?.pause()
            }
        }, 30_000)
    }
    override fun onDestroy() { handler.removeCallbacks(drain); player?.release(); player = null; super.onDestroy() }
}
