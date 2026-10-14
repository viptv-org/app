package org.viptv.app.player

import android.content.Context
import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.CoroutineScope
import org.viptv.engine.wire.SubtitleCandidate

@UnstableApi
internal class AndroidPlaybackSession(context: Context, private val input: PlaybackInput,
    scope: CoroutineScope, private val emit: (PlayerFact) -> Unit) {
    val player: ExoPlayer = ExoPlayer.Builder(context, androidx.media3.exoplayer.DefaultRenderersFactory(context)
        .setExtensionRendererMode(androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER))
        .setMediaSourceFactory(DefaultMediaSourceFactory(context).setDataSourceFactory(
            androidx.media3.datasource.DefaultDataSource.Factory(context,
                DefaultHttpDataSource.Factory().setDefaultRequestProperties(input.headers))))
        .build()
    var view: PlayerView? = null
    private val tracks = Media3Tracks(player, input, emit)
    private val subtitles = Media3ProviderSubtitles(context, player, input, scope, tracks) { emit(PlayerFact.ControlFailed) }
    private var audioDecoder = "pending"
    private var videoDecoder = "pending"
    private var presented = false
    private var subtitleScale = 1f
    private var closed = false
    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) = status()
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) = status()
        override fun onTracksChanged(value: Tracks) { tracks.changed(); subtitles.applySelection(); subtitles.autoSelect(input.providerSubtitles.value) }
        override fun onRenderedFirstFrame() {
            if (!presented) { presented = true; progress(); emit(PlayerFact.Presentation(PresentationProof.NativeRendererPresented, exact = false)) }
        }
        override fun onPlayerError(error: PlaybackException) {
            reportNativePlaybackFailure("Media3 playback error: ${error.errorCodeName}")
            emit(PlayerFact.Failed)
        }
        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) = properties()
        override fun onVolumeChanged(volume: Float) = properties()
    }
    init {
        val selection = player.trackSelectionParameters.buildUpon()
            .setPreferredAudioLanguage(input.preferences.preferred_audio_language)
            .setPreferredTextLanguage(input.preferences.preferred_subtitle_language)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !input.preferences.subtitles_enabled)
        input.preferences.max_quality_height?.takeIf { it > 0 }?.let { selection.setMaxVideoSize(Int.MAX_VALUE, it) }
        player.trackSelectionParameters = selection.build()
        player.setAudioAttributes(AudioAttributes.DEFAULT, true)
        player.setHandleAudioBecomingNoisy(true)
        player.addListener(listener)
        player.addAnalyticsListener(object : androidx.media3.exoplayer.analytics.AnalyticsListener {
            override fun onAudioDecoderInitialized(eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
                decoderName: String, initializedTimestampMs: Long, initializationDurationMs: Long) { audioDecoder = decoderName }
            override fun onVideoDecoderInitialized(eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
                decoderName: String, initializedTimestampMs: Long, initializationDurationMs: Long) { videoDecoder = decoderName }
        })
    }
    fun open(paused: Boolean) {
        input.diagnostics?.invoke("ffmpeg_audio_available=${androidx.media3.decoder.ffmpeg.FfmpegLibrary.isAvailable()} ffmpeg_version=${androidx.media3.decoder.ffmpeg.FfmpegLibrary.getVersion()}")
        emit(PlayerFact.Status("Opening"))
        player.setMediaItem(MediaItem.Builder().setUri(input.uri).setMimeType(input.mimeType).build(), input.positionMillis.coerceAtLeast(0))
        player.playWhenReady = !paused
        player.prepare()
        properties()
    }
    private fun status() {
        if (closed || player.playerError != null) return
        emit(PlayerFact.Status(when (player.playbackState) {
            Player.STATE_BUFFERING -> "Buffering"
            Player.STATE_ENDED -> "Ended"
            Player.STATE_READY -> if (player.playWhenReady) "Playing" else "Paused"
            else -> "Opening"
        }))
    }
    fun progress() {
        if (!closed && player.playbackState != Player.STATE_IDLE) emit(input.mediaProgress(player.currentPosition,
            player.duration.takeIf { it != C.TIME_UNSET && it > 0 }))
    }
    private fun properties() = emit(PlayerFact.Properties(player.volume, player.playbackParameters.speed.toDouble(), subtitleScale))
    suspend fun command(command: PlayerCommand) {
        try { when (command) {
            is PlayerCommand.Seek -> if (input.remoteSeek != null) input.remoteSeek.invoke(command.positionMillis)
                else player.seekTo((command.positionMillis - input.timelineOffsetMillis).coerceAtLeast(0))
            is PlayerCommand.Volume -> player.volume = command.value.coerceIn(0f, 1f)
            is PlayerCommand.Speed -> player.setPlaybackSpeed(command.value.toFloat().coerceIn(.25f, 2f))
            is PlayerCommand.SubtitleSize -> {
                subtitleScale = command.value.coerceIn(.5f, 2f)
                view?.subtitleView?.setFractionalTextSize(androidx.media3.ui.SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * subtitleScale)
                properties()
            }
            is PlayerCommand.SelectTrack -> {
                if (command.kind == PlaybackTrackKind.Subtitle) subtitles.select(command.id)
                else tracks.select(command.kind, command.id)
            }
        } } catch (error: kotlinx.coroutines.CancellationException) { throw error }
        catch (_: Exception) { emit(PlayerFact.ControlFailed) }
    }
    fun sourcesChanged(values: List<SubtitleCandidate>) { tracks.changed(); subtitles.autoSelect(values) }
    private var diagnosticFrames = 0
    private var diagnosticAt = android.os.SystemClock.elapsedRealtime()
    fun diagnostics() {
        if (closed) return
        input.diagnostics?.let { report -> player.videoDecoderCounters?.let { counters ->
            counters.ensureUpdated()
            val now = android.os.SystemClock.elapsedRealtime()
            val frames = counters.renderedOutputBufferCount
            report("engine=Media3 rendered=$frames dropped=${counters.droppedBufferCount} fps=${(frames-diagnosticFrames)*1000.0/(now-diagnosticAt).coerceAtLeast(1)} width=${player.videoFormat?.width} height=${player.videoFormat?.height} source_fps=${player.videoFormat?.frameRate} audio_decoder=$audioDecoder video_decoder=$videoDecoder")
            diagnosticFrames = frames; diagnosticAt = now
        } }
    }
    fun close() {
        closed = true; player.removeListener(listener); subtitles.close(); player.release(); view = null
    }
}
