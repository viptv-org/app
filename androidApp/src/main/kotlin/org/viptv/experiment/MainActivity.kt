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
        // The foreground hook (app#75); App.kt skips it because ApplicationModel owns the controller.
        // ON_START dispatches after onStart's resumePlayback, ON_STOP after onPause's suspendPlayback.
        lifecycle.addObserver(model.controller.foregroundObserver(onReturn = model::foregroundReturned))
        // A recreated activity gets the launching intent again; only the first creation may act on it.
        if (savedInstanceState == null) {
            offerDeviceCode(intent)
            // Android replaced the app and UpdateRelaunchReceiver reopened it (app#139).
            model.updater?.announceUpdated(intent.getStringExtra(UpdateRelaunchReceiver.EXTRA_UPDATED_TO))
        }
        setContent {
            val controller = model.controller
            val state by controller.state.collectAsState()
            var entryBack by remember { mutableStateOf<(() -> Unit)?>(null) }
            LaunchedEffect(state.screen) { configurePlayerWindow(state.screen == Screen.Player, tv) }
            val updater = model.updater
            // App updates (docs/APP_UPDATES.md): checks follow the connection, and a download waits while the player is open.
            if (updater != null) {
                LaunchedEffect(state.screen == Screen.Player) { updater.playingChanged(state.screen == Screen.Player) }
                // Not while starting: the restored session is not known yet, and About would flash "Sign in to a server".
                LaunchedEffect(state.starting, state.localMode, controller.updateServerOrigin()) { if (!state.starting) updater.connectionChanged() }
            }
            // app#139/app#146: Back cannot cancel an install that is already committing, and on the
            // permission step it is Later; the page behind the Updating screen never takes the press.
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

    /** The QR code on a TV's sign-in screen is https://server/device?code=...; phones and tablets approve it in the app. */
    private fun offerDeviceCode(intent: Intent) {
        val link = intent.data ?: return
        if (television || intent.action != Intent.ACTION_VIEW || link.scheme != "https" || link.path != "/device") return
        val host = link.host ?: return
        val code = link.getQueryParameter("code") ?: return
        applicationModel?.controller?.offerDeviceCode("https://$host", code)
    }

    override fun onPause() {
        // Lifecycle ON_STOP observers may pause the renderer before onStop is
        // called. Capture whether playback should resume before that feedback.
        applicationModel?.controller?.suspendPlayback()
        super.onPause()
    }
    override fun onStop() {
        applicationModel?.controller?.suspendPlayback()
        super.onStop()
    }
    override fun onResume() {
        super.onResume()
        // A translucent Activity can pause us without stopping us. onStart is
        // not called on that return; only a lifecycle-owned pause may resume.
        applicationModel?.controller?.resumePlayback()
    }
    override fun onStart() {
        super.onStart()
        applicationModel?.controller?.resumePlayback()
    }
}
