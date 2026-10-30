package org.viptv.experiment

import android.app.UiModeManager
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.*
import org.viptv.app.state.*
import org.viptv.app.state.Screen
import org.viptv.app.App
import org.viptv.app.updates.AppWithUpdates
import org.viptv.app.updates.UpdateRelaunchReceiver
import org.viptv.app.updates.UpdatingBack
import org.viptv.app.updates.updatingBack
import org.viptv.app.updates.updatePromptAllowed
import org.viptv.app.updates.updateServerOrigin
import org.viptv.app.preview.UiPreview
import org.viptv.app.connection.AndroidSecrets
import org.viptv.app.player.AndroidPictureInPicture
import org.viptv.app.connection.AndroidAccountAuthorization
import androidx.lifecycle.ViewModelProvider
import android.content.Intent

open class MainActivity : ComponentActivity() {
    private var applicationModel: ApplicationModel? = null
    private var television = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AndroidSecrets.attach(applicationContext)
        AndroidAccountAuthorization.attach(applicationContext)
        org.viptv.app.connection.AndroidDisplayCapability.attach(applicationContext)
        org.viptv.app.connection.AndroidTorrentRuntime.attach(applicationContext, TorrentRuntimeService::class.java)
        org.viptv.app.connection.observeAddonFetches { android.util.Log.i("VIPTV_ADDON", it) }
        org.viptv.app.connection.observeSourceFailures { android.util.Log.w("VIPTV_SOURCES", it) }
        val tv = getSystemService(UiModeManager::class.java).currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
        television = tv
        if (intent.getBooleanExtra("ui_preview", false)) {
            val screen = runCatching { Screen.valueOf(intent.getStringExtra("ui_screen") ?: "Home") }.getOrDefault(Screen.Home)
            setContent {
                var current by remember { mutableStateOf(screen) }
                var entryBack by remember { mutableStateOf<(() -> Unit)?>(null) }
                LaunchedEffect(current) { configurePlayerWindow(current == Screen.Player, tv) }
                BackHandler(current != Screen.Home || entryBack != null) { entryBack?.invoke() ?: run { current = Screen.Home } }
                UiPreview(screen = current, navigate = { current = it }, television = tv, handheld = !tv,
                    tvDetailVariant = intent.getStringExtra("ui_variant") ?: "Movie", entryBackChanged = { entryBack = it })
            }
            return
        }
        val model = ViewModelProvider(this)[ApplicationModel::class.java]
        applicationModel = model
        model.start(filesDir.resolve("local.sqlite").absolutePath, applicationContext)
        AndroidPictureInPicture.attach(this, model.controller, tv)
        // ON_START runs after onStart's resumePlayback, ON_STOP after onPause's suspendPlayback; App.kt skips this hook.
        lifecycle.addObserver(model.controller.foregroundObserver(onReturn = model::foregroundReturned))
        // A recreated Activity gets the launching intent again: only the first creation may act on it.
        if (savedInstanceState == null) {
            offerDeviceCode(intent)
            model.updater?.announceUpdated(intent.getStringExtra(UpdateRelaunchReceiver.EXTRA_UPDATED_TO))
        }
        setContent {
            val controller = model.controller
            val state by controller.state.collectAsState()
            var entryBack by remember { mutableStateOf<(() -> Unit)?>(null) }
            LaunchedEffect(state.screen) { configurePlayerWindow(state.screen == Screen.Player, tv) }
            val updater = model.updater
            if (updater != null) {
                LaunchedEffect(state.screen == Screen.Player) { updater.playingChanged(state.screen == Screen.Player) }
                // Not while starting: the restored session is unknown and About would flash "Sign in to a server".
                LaunchedEffect(state.starting, state.localMode, controller.updateServerOrigin()) { if (!state.starting) updater.connectionChanged() }
            }
            val updateBack = updater?.state?.collectAsState()?.value?.let { updatingBack(it.status) } ?: UpdatingBack.Page
            BackHandler(enabled = updateBack != UpdatingBack.Page || entryBack != null || state.systemBackStaysInApp()) {
                when (updateBack) {
                    UpdatingBack.Ignored -> Unit
                    UpdatingBack.Later -> updater?.dismissPendingInstall()
                    UpdatingBack.Page -> entryBack?.invoke() ?: controller.navigateSystemBack()
                }
            }
            AppWithUpdates(updater, tv, promptAllowed = controller.updatePromptAllowed()) {
                App(television = tv, localDatabase = filesDir.resolve("local.sqlite").absolutePath, providedController = controller,
                    manageControllerLifecycle = false, entryBackChanged = { entryBack = it }, defaultServer = BuildConfig.DEFAULT_SERVER)
            }
        }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        offerDeviceCode(intent)
    }

    private fun offerDeviceCode(intent: Intent) {
        val link = intent.data ?: return
        if (television || intent.action != Intent.ACTION_VIEW || link.scheme != "https" || link.path != "/device") return
        val host = link.host ?: return
        val code = link.getQueryParameter("code") ?: return
        applicationModel?.controller?.offerDeviceCode("https://$host", code)
    }

    override fun onPause() {
        // ON_STOP observers can pause the renderer before onStop runs: capture the resume intent first.
        suspendUnlessTheWindowKeepsPlaying()
        super.onPause()
    }
    override fun onStop() {
        suspendUnlessTheWindowKeepsPlaying()
        super.onStop()
    }
    // A picture-in-picture window plays on while this activity is paused, but closing it finishes the activity, which must save as leaving does.
    private fun suspendUnlessTheWindowKeepsPlaying() {
        if (isInPictureInPictureMode && !isFinishing) return
        applicationModel?.controller?.suspendPlayback()
    }
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        AndroidPictureInPicture.onUserLeaveHint()
    }
    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        AndroidPictureInPicture.modeChanged(isInPictureInPictureMode)
    }
    override fun onDestroy() {
        AndroidPictureInPicture.detach()
        super.onDestroy()
    }
    override fun onResume() {
        super.onResume()
        // A translucent Activity pauses without stopping us and onStart is not called: only a lifecycle-owned pause may resume.
        applicationModel?.controller?.resumePlayback()
    }
    override fun onStart() {
        super.onStart()
        applicationModel?.controller?.resumePlayback()
    }
}
