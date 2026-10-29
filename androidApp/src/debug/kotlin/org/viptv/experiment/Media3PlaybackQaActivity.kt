package org.viptv.experiment

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

@UnstableApi
class Media3PlaybackQaActivity : Activity() {
    private lateinit var player: ExoPlayer
    private val handler = Handler(Looper.getMainLooper())
    private var lastFrames = 0
    private var lastAt = 0L
    private val report = object : Runnable {
        override fun run() {
            val now = SystemClock.elapsedRealtime()
            player.videoDecoderCounters?.let { counters ->
                counters.ensureUpdated()
                val frames = counters.renderedOutputBufferCount
                val fps = if (lastAt == 0L) 0.0 else (frames - lastFrames) * 1000.0 / (now - lastAt)
                Log.i("VIPTV_MEDIA3_QA", "rendered=$frames dropped=${counters.droppedBufferCount} skipped=${counters.skippedOutputBufferCount} fps=$fps state=${player.playbackState}")
                lastFrames = frames
            }
            lastAt = now
            handler.postDelayed(this, 2000)
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val uri = requireNotNull(intent.getStringExtra("qa_media_uri"))
        require(android.net.Uri.parse(uri).scheme == "https")
        player = ExoPlayer.Builder(this).build()
        setContentView(PlayerView(this).apply { useController = false; player = this@Media3PlaybackQaActivity.player })
        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                Log.w("VIPTV_MEDIA3_QA", "error_code=${error.errorCode}")
            }
            override fun onRenderedFirstFrame() { Log.i("VIPTV_MEDIA3_QA", "first_frame=true") }
        })
        player.setMediaItem(MediaItem.fromUri(uri))
        player.prepare(); player.play()
        handler.postDelayed(report, 2000)
    }
    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        player.release()
        super.onDestroy()
    }
}
