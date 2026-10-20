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
import org.viptv.app.preview.UiPreview
import org.viptv.app.connection.AndroidSecrets
import org.viptv.app.connection.AndroidAccountAuthorization
import androidx.lifecycle.ViewModelProvider

open class MainActivity : ComponentActivity() {
    private var applicationModel: ApplicationModel? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AndroidSecrets.attach(applicationContext)
        AndroidAccountAuthorization.attach(applicationContext)
        org.viptv.app.connection.observeAddonFetches { android.util.Log.i("VIPTV_ADDON", it) }
        org.viptv.app.connection.observeSourceFailures { android.util.Log.w("VIPTV_SOURCES", it) }
        val tv = getSystemService(UiModeManager::class.java).currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
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
        model.start(filesDir.resolve("local.sqlite").absolutePath)
        setContent {
            val controller = model.controller
            val state by controller.state.collectAsState()
            var entryBack by remember { mutableStateOf<(() -> Unit)?>(null) }
            BackHandler(enabled = entryBack != null || state.screen !in listOf(Screen.Connect, Screen.Profiles)) {
                if (entryBack != null) { entryBack?.invoke(); return@BackHandler }
                when (state.screen) {
                    Screen.Player -> controller.closePlayer()
                    Screen.ProfilePin, Screen.ProfileEdit, Screen.Home -> controller.navigate(Screen.Profiles)
                    Screen.Details -> controller.backDetails()
                    Screen.Sources -> controller.backSources()
                    else -> controller.navigate(Screen.Home)
                }
            }
            LaunchedEffect(state.screen) { configurePlayerWindow(state.screen == Screen.Player, tv) }
            App(television = tv, localDatabase = filesDir.resolve("local.sqlite").absolutePath, providedController = controller,
                manageControllerLifecycle = false, entryBackChanged = { entryBack = it }, defaultServer = BuildConfig.DEFAULT_SERVER)
        }
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
