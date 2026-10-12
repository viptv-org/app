package org.viptv.app.player

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import io.github.yuroyami.kiteplayer.MediaItem
import io.github.yuroyami.kiteplayer.compose.KitePlayerVideo
import io.github.yuroyami.kiteplayer.compose.rememberKitePlayer
import kotlinx.coroutines.CancellationException
import kotlin.time.Duration.Companion.milliseconds

@Composable
actual fun PlayerSurface(input: PlaybackInput, paused: Boolean, modifier: Modifier, observe: (PlayerFact) -> Unit) {
    key(input.id) {
        val player = rememberKitePlayer()
        val latestObserve by rememberUpdatedState(observe)
        var opened by remember { mutableStateOf(false) }
        KitePlayerVideo(player, modifier)
        LaunchedEffect(input.id) {
            try {
                player.open(MediaItem(uri = input.uri, headers = input.headers, startPosition = input.positionMillis.milliseconds))
                opened = true
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { latestObserve(PlayerFact.Failed) }
        }
        LaunchedEffect(opened, paused) { if (opened) { if (paused) player.pause() else player.play() } }
        LaunchedEffect(player) {
            player.progress.collect { latestObserve(PlayerFact.Progress(it.positionMillis, player.state.value.durationMillis)) }
        }
        LaunchedEffect(player) {
            player.state.collect { latestObserve(if (it.error != null) PlayerFact.Failed else PlayerFact.Status(it.status.name)) }
        }
    }
}
