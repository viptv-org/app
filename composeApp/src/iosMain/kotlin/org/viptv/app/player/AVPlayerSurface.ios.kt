package org.viptv.app.player

import androidx.compose.runtime.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.interop.UIKitView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CancellationException
import platform.AVFoundation.*
import platform.AVKit.AVPlayerViewController
import platform.CoreMedia.CMTime
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMakeWithSeconds
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSURL
import platform.darwin.NSObject

private class AVPlayerLifetime { var active = false; var playRequest = 0L; var appliedInitialSeek = false }

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun PlayerSurface(input: PlaybackInput, paused: Boolean, fill: Boolean, modifier: Modifier, observe: (PlayerFact) -> Unit, commands: kotlinx.coroutines.flow.Flow<PlayerCommand>) {
    val player = remember(input.id) { AVPlayer() }
    val lifetime = remember(player) { AVPlayerLifetime() }
    var captionScale by remember { mutableStateOf(input.preferences.subtitle_font_scale.toFloat().takeIf { it.isFinite() }?.coerceIn(.5f, 2f) ?: 1f) }
    val latestObserve by rememberUpdatedState(observe)
    val controller = remember { AVPlayerViewController() }
    val scope = rememberCoroutineScope()
    val subtitles = remember(player) { AVProviderSubtitles(player, input, scope) { latestObserve(it) } }
    var captionBackground by remember { mutableStateOf<String?>(input.preferences.subtitle_background_color) }

    Box(modifier) {
      UIKitView(
        modifier = Modifier.fillMaxSize(),
        factory = {
            controller.apply {
                this.player = player
                showsPlaybackControls = false
                videoGravity = fillVideoGravity(fill)
            }.view
        },
        update = { controller.videoGravity = fillVideoGravity(fill) },
    )
      if (subtitles.text.isNotEmpty()) Text(subtitles.text,
          modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 24.dp, vertical = 32.dp)
              .background(Color(captionBoxArgb(captionBackground) ?: 0)).padding(horizontal = 6.dp, vertical = 2.dp),
          color = Color(captionTextArgb(input.preferences.subtitle_text_color)),
          fontSize = (22 * captionScale).sp, textAlign = TextAlign.Center)
    }

    LaunchedEffect(subtitles, input.providerSubtitles) {
        input.providerSubtitles.collect { subtitles.sourcesChanged() }
    }

    DisposableEffect(player) {
        lifetime.active = true
        lifetime.appliedInitialSeek = false
        var disposed = false
        val url = NSURL.URLWithString(input.uri)
        if (url == null) {
            latestObserve(PlayerFact.Failed)
        }
        val item = url?.let { AVPlayerItem(uRL = it) }
        player.replaceCurrentItemWithPlayerItem(item)
        val center = NSNotificationCenter.defaultCenter
        val endToken = center.addObserverForName(AVPlayerItemDidPlayToEndTimeNotification, `object` = item, queue = null) { _ ->
            if (!disposed) latestObserve(PlayerFact.Status("Ended"))
        }
        val failToken = center.addObserverForName(AVPlayerItemFailedToPlayToEndTimeNotification, `object` = item, queue = null) { _ ->
            if (!disposed) latestObserve(PlayerFact.Failed)
        }
        var progressTicks = 0
        val timeObserver = player.addPeriodicTimeObserverForInterval(
            CMTimeMakeWithSeconds(0.05, 600), queue = null,
        ) { time: CValue<CMTime> ->
            if (disposed) return@addPeriodicTimeObserverForInterval
            if (!input.live && !lifetime.appliedInitialSeek && input.positionMillis > 0 && item != null) {
                val itemDuration = CMTimeGetSeconds(item.duration)
                if (itemDuration.isFinite() && itemDuration > 0) {
                    lifetime.appliedInitialSeek = true
                    player.seekToTime(CMTimeMakeWithSeconds(input.positionMillis / 1000.0, 600))
                }
            }
            val seconds = CMTimeGetSeconds(time)
            if (seconds.isFinite() && item != null) {
                subtitles.render(((seconds * 1000).toLong() + input.timelineOffsetMillis) * 1000)
                if (progressTicks++ % 10 != 0) return@addPeriodicTimeObserverForInterval
                subtitles.refresh()
                val duration = CMTimeGetSeconds(item.duration).takeIf { it.isFinite() && it > 0 }?.let { (it * 1000).toLong() }
                latestObserve(input.mediaProgress((seconds * 1000).toLong(), duration))
            }
            latestObserve(PlayerFact.Presentation(PresentationProof.NativeRendererPresented))
        }
        latestObserve(PlayerFact.Tracks(PlaybackTracks()))
        onDispose {
            disposed = true
            subtitles.close()
            lifetime.active = false; lifetime.playRequest++
            center.removeObserver(endToken)
            center.removeObserver(failToken)
            player.removeTimeObserver(timeObserver)
            player.pause()
            player.replaceCurrentItemWithPlayerItem(null)
        }
    }
    LaunchedEffect(player, commands) {
        commands.collect { command ->
            try {
                when (command) {
                    is PlayerCommand.Seek -> {
                        input.subtitleSync.discontinuity()
                        if (input.remoteSeek != null) input.remoteSeek.invoke(command.positionMillis)
                        else player.seekToTime(CMTimeMakeWithSeconds(command.positionMillis.coerceAtLeast(0) / 1000.0, 600))
                    }
                    is PlayerCommand.Volume -> player.volume = command.value.coerceIn(0f, 1f)
                    is PlayerCommand.SubtitleSize -> captionScale = command.value.takeIf { it.isFinite() }?.coerceIn(.5f, 2f) ?: 1f
                    is PlayerCommand.SubtitleBackground -> captionBackground = command.color
                    is PlayerCommand.SelectTrack -> subtitles.select(command.kind, command.id)
                    is PlayerCommand.Speed -> player.rate = command.value.coerceIn(0.25, 2.0).toFloat()
                }
                latestObserve(PlayerFact.Properties(player.volume, player.rate.toDouble(), captionScale))
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { input.controlFailed(command.control, error.controlCause(), command.failureObserver(latestObserve)) }
        }
    }
    LaunchedEffect(paused, player) {
        val request = ++lifetime.playRequest
        if (paused) {
            player.pause()
            latestObserve(PlayerFact.Status("Paused"))
        } else {
            player.play()
            if (lifetime.active && lifetime.playRequest == request) latestObserve(PlayerFact.Status("Playing"))
        }
    }
}
