package org.viptv.experiment

import android.app.UiModeManager
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.*
import org.viptv.app.state.ApplicationController
import org.viptv.app.state.Screen
import org.viptv.app.App
import org.viptv.app.preview.UiPreview
import org.viptv.app.connection.AndroidSecrets

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AndroidSecrets.attach(applicationContext)
        val tv = getSystemService(UiModeManager::class.java).currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
        if (intent.getBooleanExtra("ui_preview", false)) {
            val screen = runCatching { Screen.valueOf(intent.getStringExtra("ui_screen") ?: "Home") }.getOrDefault(Screen.Home)
            setContent {
                var current by remember { mutableStateOf(screen) }
                LaunchedEffect(current) { configurePlayerWindow(current == Screen.Player, tv) }
                BackHandler(current != Screen.Home) { current = Screen.Home }
                UiPreview(screen = current, navigate = { current = it }, television = tv, handheld = !tv)
            }
            return
        }
        setContent {
            val scope = rememberCoroutineScope()
            val controller = remember { ApplicationController(scope) }
            val state by controller.state.collectAsState()
            BackHandler(enabled = state.screen !in listOf(Screen.Connect, Screen.Profiles)) {
                when (state.screen) {
                    Screen.Player -> controller.closePlayer()
                    Screen.ProfilePin, Screen.ProfileEdit, Screen.Home -> controller.navigate(Screen.Profiles)
                    Screen.Sources -> controller.navigate(if (state.detail == null) Screen.Home else Screen.Details)
                    else -> controller.navigate(Screen.Home)
                }
            }
            LaunchedEffect(state.screen) { configurePlayerWindow(state.screen == Screen.Player, tv) }
            App(television = tv, localDatabase = filesDir.resolve("local.sqlite").absolutePath, providedController = controller)
        }
    }
}
