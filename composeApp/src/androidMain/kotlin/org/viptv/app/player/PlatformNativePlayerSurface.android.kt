package org.viptv.app.player

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow

@UnstableApi
@Composable
internal actual fun PlatformNativePlayerSurface(input: PlaybackInput, paused: Boolean, fill: Boolean, modifier: Modifier,
    observe: (PlayerFact) -> Unit, commands: Flow<PlayerCommand>) {
    key(input) {
        val context = LocalContext.current
        val activity = remember(context) { context.activity() }
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        val owner = requireNotNull(LocalViewModelStoreOwner.current)
        val model = remember(owner) { ViewModelProvider(owner)[AndroidPlaybackModel::class.java] }
        val latestObserve by rememberUpdatedState(observe)
        var starts by remember(lifecycle) { mutableIntStateOf(0) }
        MediaNotificationPermission()
        val session = remember(model, input) { model.acquire(context, input) { latestObserve(it) } }
        SideEffect { session.observe = { latestObserve(it) } }
        AndroidView(factory = { viewContext -> PlayerView(viewContext).apply {
            useController = false
            isFocusable = false
            descendantFocusability = android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS
            setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
            keepScreenOn = true
        } }, modifier = modifier, update = { view ->
            view.player = session.player
            session.view = view
        }, onRelease = { if (session.view === it) session.view = null; it.player = null })
        DisposableEffect(session, lifecycle) {
            AndroidPictureInPicture.bind(session)
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP) { session.progress(); session.player.pause() }
                // onStart can run before the pause is recomposed: apply the intent on the next composition, never feed a stale pause back.
                if (event == Lifecycle.Event.ON_START) starts++
            }
            lifecycle.addObserver(observer)
            onDispose {
                AndroidPictureInPicture.unbind(session)
                lifecycle.removeObserver(observer)
                if (activity?.isChangingConfigurations != true) model.release(session)
            }
        }
        LaunchedEffect(session) { session.open(paused || !lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
        LaunchedEffect(session, paused, starts) {
            if (paused) session.player.pause()
            else if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) session.player.play()
        }
        LaunchedEffect(session, commands) { commands.collect(session::command) }
        LaunchedEffect(session) { input.providerSubtitles.collect { session.sourcesChanged(it) } }
        LaunchedEffect(session) { while (true) { delay(200); session.progress() } }
        LaunchedEffect(session) { while (true) { delay(2000); session.diagnostics() } }
    }
}

private tailrec fun android.content.Context.activity(): android.app.Activity? = when (this) {
    is android.app.Activity -> this
    is android.content.ContextWrapper -> baseContext.activity()
    else -> null
}

// The media notification needs POST_NOTIFICATIONS on API 33+; a television has no notification shade to show it in.
@Composable
private fun MediaNotificationPermission() {
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    if (context.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK == Configuration.UI_MODE_TYPE_TELEVISION) return
    if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(context) { request.launch(Manifest.permission.POST_NOTIFICATIONS) }
}
