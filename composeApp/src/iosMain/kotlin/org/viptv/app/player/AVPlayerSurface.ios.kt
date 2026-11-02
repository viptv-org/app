package org.viptv.app.player

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
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
actual fun PlayerSurface(input: PlaybackInput, paused: Boolean, modifier: Modifier, observe: (PlayerFact) -> Unit, commands: kotlinx.coroutines.flow.Flow<PlayerCommand>) {
    val player = remember(input.id) { AVPlayer() }
    val lifetime = remember(player) { AVPlayerLifetime() }
    var captionScale by remember { mutableStateOf(input.preferences.subtitle_font_scale.toFloat().takeIf { it.isFinite() }?.coerceIn(.5f, 2f) ?: 1f) }
    val latestObserve by rememberUpdatedState(observe)

    UIKitView(
        modifier = modifier,
        factory = {
            AVPlayerViewController().apply {
                this.player = player
                showsPlaybackControls = false
                videoGravity = AVLayerVideoGravityResizeAspect
            }.view
        },
        update = { },
    )

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
        val timeObserver = player.addPeriodicTimeObserverForInterval(
            CMTimeMakeWithSeconds(0.5, 600), queue = null,
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
                val duration = CMTimeGetSeconds(item.duration).takeIf { it.isFinite() && it > 0 }?.let { (it * 1000).toLong() }
                latestObserve(input.mediaProgress((seconds * 1000).toLong(), duration))
            }
            latestObserve(PlayerFact.Presentation(PresentationProof.NativeRendererPresented))
        }
        latestObserve(PlayerFact.Tracks(PlaybackTracks()))
        onDispose {
            disposed = true
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
                    is PlayerCommand.Seek -> if (input.remoteSeek != null) input.remoteSeek.invoke(command.positionMillis)
                        else player.seekToTime(CMTimeMakeWithSeconds(command.positionMillis.coerceAtLeast(0) / 1000.0, 600))
                    is PlayerCommand.Volume -> player.volume = command.value.coerceIn(0f, 1f)
                    is PlayerCommand.SubtitleSize -> captionScale = command.value.takeIf { it.isFinite() }?.coerceIn(.5f, 2f) ?: 1f
                    is PlayerCommand.SelectTrack -> {
                        // v1: provider sidecar subtitle rendering on AVPlayer is a
                        // follow-up (embedded HLS legible tracks render natively).
                    }
                    is PlayerCommand.Speed -> player.rate = command.value.coerceIn(0.25, 2.0).toFloat()
                }
                latestObserve(PlayerFact.Properties(player.volume, player.rate.toDouble(), captionScale))
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { input.controlFailed(command.control, error.controlCause(), latestObserve) }
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
