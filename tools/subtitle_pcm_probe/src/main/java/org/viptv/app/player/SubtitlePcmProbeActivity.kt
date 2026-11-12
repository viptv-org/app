package org.viptv.app.player

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sin

@UnstableApi
class SubtitlePcmProbeActivity : Activity() {
    private var player: ExoPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    @Volatile private var probe: Media3PcmProbe? = null
    private var worker: AndroidSubtitleSyncWorker? = null
    private val sync = SubtitleSyncSession()
    private val delivery = MutableStateFlow(false)
    private var cues: SidecarCueTimeline<String>? = null
    private var synchronization = false
    private var spans = 0
    private var bytes = 0L
    private var firstUs = -1L
    private var lastUs = -1L
    private var previousEpoch = -1L
    private var epochs = 0
    private var ordered = true
    private var nonzero = false
    private var stage = 0
    private var finished = false
    private var transitions = 0
    private var initialPlayer = 0
    private var defaultOff = true
    private var acceptedOffsetUs: Long? = null
    private var acceptedFrames = 0L
    private var manualPassed = false
    private var offPassed = false
    private var resetPassed = false
    private var stalePassed = false
    private var didTimingChecks = false
    private lateinit var status: TextView

    private val drain = object : Runnable {
        override fun run() {
            if (!synchronization) {
                while (true) {
                    val span = probe?.poll() ?: break
                    if (firstUs < 0) firstUs = span.startUs
                    if (span.epoch != previousEpoch) { previousEpoch = span.epoch; epochs++; lastUs = -1 }
                    if (span.startUs < lastUs) ordered = false
                    lastUs = span.startUs
                    spans++; bytes += span.pcm16.size
                    nonzero = nonzero || span.pcm16.any { it.toInt() != 0 }
                }
            }
            val current = player ?: return
            if (synchronization) {
                val titleUs = current.currentPosition * 1000
                var active = cues?.cuesAt(sync.subtitleTimeUs(titleUs)) ?: emptyList()
                if (active.isEmpty()) active = cues?.cuesAt(sync.subtitleTimeUs(titleUs, cueGap = true)) ?: emptyList()
                if (stage == 0 && current.currentPosition >= 2000) {
                    stage = 1
                    defaultOff = !sync.ui.value.automatic && worker?.stats?.value?.spans == 0L
                    sync.setAutomatic(true)
                }
                val stats = worker?.stats?.value
                if (!didTimingChecks && sync.ui.value.corrected && stats?.acceptedOffsetUs != null) {
                    didTimingChecks = true
                    val oldEpoch = sync.epoch
                    acceptedOffsetUs = titleUs - sync.subtitleTimeUs(titleUs, cueGap = true)
                    acceptedFrames = stats.processedFrames
                    sync.adjustManual(500_000)
                    manualPassed = sync.manual && titleUs - sync.subtitleTimeUs(titleUs) == acceptedOffsetUs!! + 500_000
                    stalePassed = !sync.offer(oldEpoch, Long.MAX_VALUE, SubtitleSyncAvailability.Accepted, SubtitleCorrection(0, 0))
                    sync.setAutomatic(false)
                    offPassed = titleUs - sync.subtitleTimeUs(titleUs) == 500_000L
                    sync.setAutomatic(true)
                    resetPassed = sync.epoch > oldEpoch && sync.subtitleTimeUs(titleUs) == titleUs && !sync.manual
                    val beforeReset = sync.epoch
                    sync.adjustManual(-500_000)
                    sync.resetTiming()
                    resetPassed = resetPassed && sync.epoch > beforeReset && sync.subtitleTimeUs(titleUs) == titleUs && !sync.manual
                    stage = 3
                }
                status.text = "${sync.ui.value.availability} ${stats?.processedFrames ?: 0} frames\n${active.joinToString("\n")}"
            } else if (stage == 0 && current.currentPosition >= 2500) {
                stage = 1; current.seekTo(5000)
            } else if (stage == 1 && current.currentPosition >= 6500) {
                stage = 2; current.seekTo(1000)
            } else if (stage == 2 && current.currentPosition >= 2500) {
                stage = 3; current.setPlaybackSpeed(1.25f)
            }
            if (current.playbackState == Player.STATE_ENDED && !finished) {
                val report = if (synchronization) {
                    val stats = worker?.stats?.value
                    "state=ended mode=nativeSync defaultOff=$defaultOff acceptedOffsetUs=$acceptedOffsetUs acceptedFrames=$acceptedFrames manual=$manualPassed off=$offPassed reset=$resetPassed stale=$stalePassed mediaTransitions=$transitions samePlayer=${System.identityHashCode(current) == initialPlayer} workerThread=${stats?.workerThread} nativeStatus=${stats?.nativeStatus} gaps=${probe?.gaps?.get()} sdk=${android.os.Build.VERSION.SDK_INT} media3=1.11.1 speed=${current.playbackParameters.speed} accuracy=syntheticWiringOnly"
                } else "spans=$spans bytes=$bytes firstUs=$firstUs lastUs=$lastUs ordered=$ordered nonzero=$nonzero gaps=${probe?.gaps?.get()} stage=$stage epochs=$epochs sdk=${android.os.Build.VERSION.SDK_INT} media3=1.11.1 state=ended"
                finishReport(report)
            }
            if (!finished) handler.postDelayed(this, 20)
        }
    }

    private fun finishReport(value: String) {
        finished = true
        File(filesDir, "result.txt").writeText(value)
        status.text = value
        android.util.Log.i("SubtitlePcmProbe", value)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        File(filesDir, "result.txt").delete()
        status = TextView(this); status.text = "Running owned local PCM fixture"; setContentView(status)
        synchronization = intent.getBooleanExtra("subtitleSync", false)
        val fixture = if (synchronization) {
            val subtitle = File(filesDir, "sync.vtt")
            cues = parseProviderTextCues(subtitle.readBytes())
            sync.select(SubtitleSyncIdentity("owned-fixture", "audio-0", "owned-cues"), cues!!.intervals())
            File(filesDir, "sync.wav").also { require(it.isFile) }
        } else toneFixture()
        val factory = Media3SubtitleRenderersFactory(this) { value -> probe = value; if (!synchronization) value.observe(true) }
        if (synchronization) worker = AndroidSubtitleSyncWorker(scope, sync, { probe }, delivery,
            { value -> android.util.Log.i("SubtitlePcmProbe", value) })
        player = ExoPlayer.Builder(this, factory).build().also {
            initialPlayer = System.identityHashCode(it)
            it.addListener(object : Player.Listener {
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) { transitions++ }
                override fun onPlaybackStateChanged(playbackState: Int) { delivery.value = playbackState == Player.STATE_READY }
                override fun onPlayerError(error: PlaybackException) { finishReport("state=failed error=${error.errorCodeName}") }
            })
            it.setMediaItem(MediaItem.fromUri(android.net.Uri.fromFile(fixture)))
            if (synchronization) it.setPlaybackSpeed(2f)
            it.prepare(); it.play()
        }
        handler.post(drain)
        handler.postDelayed({
            if (!finished) { finishReport("state=timeout spans=$spans bytes=$bytes native=${worker?.stats?.value}"); player?.pause() }
        }, if (synchronization) 150_000 else 30_000)
    }

    private fun toneFixture(): File {
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
        return fixture
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        worker?.close(); scope.cancel(); player?.release(); player = null
        super.onDestroy()
    }
}
