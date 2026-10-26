package org.viptv.app.player

import android.content.Context
import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.CoroutineScope
import org.viptv.app.connection.subtitleMode
import org.viptv.engine.wire.SubtitleCandidate

@UnstableApi
internal class AndroidPlaybackSession(context: Context, private val input: PlaybackInput,
    scope: CoroutineScope, var observe: (PlayerFact) -> Unit) {
    private fun emit(fact: PlayerFact) = observe(fact)
    private val sourceFactory = DefaultMediaSourceFactory(context).setDataSourceFactory(
        androidx.media3.datasource.DefaultDataSource.Factory(context,
            DefaultHttpDataSource.Factory().setDefaultRequestProperties(input.headers)
                // A torrent read can wait for peers well past Media3's 8 s default.
                .apply { if (input.localTorrent) setReadTimeoutMs(LOCAL_TORRENT_READ_TIMEOUT_MS) }))
    val player: ExoPlayer = ExoPlayer.Builder(context, androidx.media3.exoplayer.DefaultRenderersFactory(context)
        .setExtensionRendererMode(androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER))
        .setMediaSourceFactory(sourceFactory)
        .build()
    var view: PlayerView? = null
        set(value) { field = value; subtitles.bind(value); value?.subtitleView?.profileStyle(input.preferences, subtitleScale) }
    private val tracks = Media3Tracks(player, input, ::emit)
    private val subtitles = Media3ProviderSubtitles(player, input, scope, tracks, ::titlePositionMillis) { emit(PlayerFact.ControlFailed) }
    private var audioDecoder = "pending"
    private var videoDecoder = "pending"
    private var presented = false
    private var subtitleScale = input.preferences.subtitle_font_scale.toFloat().coerceIn(.5f, 2f)
    private var closed = false
    private var opened = false
    private var mediaTransitions = 0
    private var timelineReports = 0
    private val window = Timeline.Window()
    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) = status()
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) = status()
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) { mediaTransitions++ }
        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            if (input.diagnostics != null && !timeline.isEmpty && timelineReports++ < 5) {
                val value = timeline.getWindow(player.currentMediaItemIndex, Timeline.Window())
                input.diagnostics.invoke("timeline_default_ms=${value.defaultPositionMs} timeline_position_ms=${player.currentPosition} timeline_offset_ms=${value.positionInFirstPeriodMs} timeline_placeholder=${value.isPlaceholder} timeline_live=${value.isLive} timeline_dynamic=${value.isDynamic}")
            }
        }
        override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
            input.diagnostics?.invoke("discontinuity_reason=$reason discontinuity_old_ms=${oldPosition.positionMs} discontinuity_new_ms=${newPosition.positionMs}")
        }
        override fun onTracksChanged(value: Tracks) { tracks.changed(); subtitles.autoSelect(input.providerSubtitles.value) }
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
            // "When audio isn't …" starts with text off; Media3ProviderSubtitles turns it on once the audio is known.
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, input.preferences.subtitleMode != "always")
        input.preferences.max_quality_height?.takeIf { it > 0 }?.let { selection.setMaxVideoSize(Int.MAX_VALUE, it) }
        player.trackSelectionParameters = selection.build()
        player.setAudioAttributes(AudioAttributes.DEFAULT, true)
        player.setHandleAudioBecomingNoisy(true)
        player.addListener(listener)
        player.addAnalyticsListener(object : androidx.media3.exoplayer.analytics.AnalyticsListener {
            override fun onRenderedFirstFrame(eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
                output: Any, renderTimeMs: Long) {
                if (!presented) {
                    presented = true
                    progress()
                    emit(PlayerFact.Presentation(PresentationProof.NativeRendererPresented,
                        atNanos = renderTimeMs * 1_000_000L, exact = false))
                }
            }
            override fun onAudioDecoderInitialized(eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
                decoderName: String, initializedTimestampMs: Long, initializationDurationMs: Long) { audioDecoder = decoderName }
            override fun onVideoDecoderInitialized(eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
                decoderName: String, initializedTimestampMs: Long, initializationDurationMs: Long) { videoDecoder = decoderName }
        })
    }
    fun open(paused: Boolean) {
        if (opened) {
            player.playWhenReady = !paused
            progress(); properties(); tracks.changed(); status()
            if (presented) emit(PlayerFact.Presentation(PresentationProof.NativeRendererPresented, exact = false))
            return
        }
        opened = true
        input.diagnostics?.invoke("opening_position_ms=${input.positionMillis} opening_timeline_offset_ms=${input.timelineOffsetMillis} opening_live=${input.live}")
        input.diagnostics?.invoke("ffmpeg_audio_available=${androidx.media3.decoder.ffmpeg.FfmpegLibrary.isAvailable()} ffmpeg_version=${androidx.media3.decoder.ffmpeg.FfmpegLibrary.getVersion()}")
        emit(PlayerFact.Status("Opening"))
        val source = sourceFactory.createMediaSource(MediaItem.Builder().setUri(input.uri).setMimeType(input.mimeType).build())
        player.setMediaSource(if (!input.live && source is HlsMediaSource) TitleHlsMediaSource(source) else source,
            input.positionMillis.coerceAtLeast(0))
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
            player.duration.takeIf { it != C.TIME_UNSET && it > 0 }, windowOffsetMillis()))
    }
    private fun windowOffsetMillis(): Long = if (input.live || player.currentTimeline.isEmpty) 0 else
        player.currentTimeline.getWindow(player.currentMediaItemIndex, window).positionInFirstPeriodMs.coerceAtLeast(0)
    private fun titlePositionMillis(): Long = input.mediaProgress(player.currentPosition, null, windowOffsetMillis()).positionMillis
    private fun properties() = emit(PlayerFact.Properties(player.volume, player.playbackParameters.speed.toDouble(), subtitleScale))
    suspend fun command(command: PlayerCommand) {
        try { when (command) {
            is PlayerCommand.Seek -> if (input.remoteSeek != null) input.remoteSeek.invoke(command.positionMillis)
                else player.seekTo(input.mediaSeekPosition(command.positionMillis, windowOffsetMillis()))
            is PlayerCommand.Volume -> player.volume = command.value.coerceIn(0f, 1f)
            is PlayerCommand.Speed -> player.setPlaybackSpeed(command.value.toFloat().coerceIn(.25f, 2f))
            is PlayerCommand.SubtitleSize -> {
                subtitleScale = command.value.coerceIn(.5f, 2f)
                view?.subtitleView?.profileStyle(input.preferences, subtitleScale)
                subtitles.size(subtitleScale)
                properties()
            }
            is PlayerCommand.SelectTrack -> {
                if (command.kind == PlaybackTrackKind.Subtitle) subtitles.select(command.id)
                else tracks.select(command.kind, command.id)
            }
        } } catch (error: kotlinx.coroutines.CancellationException) { throw error }
        catch (_: Exception) { emit(PlayerFact.ControlFailed) }
    }
    fun sourcesChanged(values: List<SubtitleCandidate>) { tracks.changed(); subtitles.sourcesChanged(values) }
    private var diagnosticFrames = 0
    private var diagnosticAt = android.os.SystemClock.elapsedRealtime()
    fun diagnostics() {
        if (closed) return
        input.diagnostics?.let { report -> player.videoDecoderCounters?.let { counters ->
            counters.ensureUpdated()
            val now = android.os.SystemClock.elapsedRealtime()
            val frames = counters.renderedOutputBufferCount
            report("engine=Media3 rendered=$frames dropped=${counters.droppedBufferCount} fps=${(frames-diagnosticFrames)*1000.0/(now-diagnosticAt).coerceAtLeast(1)} width=${player.videoFormat?.width} height=${player.videoFormat?.height} source_fps=${player.videoFormat?.frameRate} audio_decoder=$audioDecoder video_decoder=$videoDecoder media_item_transitions=$mediaTransitions window_offset_ms=${windowOffsetMillis()}")
            diagnosticFrames = frames; diagnosticAt = now
        } }
    }
    fun close() {
        if (closed) return
        closed = true; player.removeListener(listener); subtitles.close(); player.release(); view = null
    }
}

private const val LOCAL_TORRENT_READ_TIMEOUT_MS = 60_000
