package org.viptv.app.player

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow

@UnstableApi
@Composable
internal actual fun PlatformNativePlayerSurface(input: PlaybackInput, paused: Boolean, modifier: Modifier,
    observe: (PlayerFact) -> Unit, commands: Flow<PlayerCommand>) {
    key(input) {
        val context = LocalContext.current.applicationContext
        val latestObserve by rememberUpdatedState(observe)
        val scope = rememberCoroutineScope()
        val session = remember { AndroidPlaybackSession(context, input, scope) { latestObserve(it) } }
        AndroidView(factory = { viewContext -> PlayerView(viewContext).apply {
            useController = false
            isFocusable = false
            descendantFocusability = android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS
            setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
            keepScreenOn = true
        } }, modifier = modifier, update = { view ->
            view.player = session.player
            session.view = view
        }, onRelease = { it.player = null })
        DisposableEffect(session) { onDispose { session.close() } }
        LaunchedEffect(session) { session.open(paused) }
        LaunchedEffect(session, paused) { session.player.playWhenReady = !paused }
        LaunchedEffect(session, commands) { commands.collect(session::command) }
        LaunchedEffect(session) { input.providerSubtitles.collect { session.sourcesChanged(it) } }
        LaunchedEffect(session) { while (true) { delay(200); session.progress() } }
        LaunchedEffect(session) { while (true) { delay(2000); session.diagnostics() } }
    }
}
