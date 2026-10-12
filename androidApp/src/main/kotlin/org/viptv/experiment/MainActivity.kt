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
import org.viptv.app.connection.AndroidSecrets

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AndroidSecrets.attach(applicationContext)
        val tv = getSystemService(UiModeManager::class.java).currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
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
            App(television = tv, localDatabase = filesDir.resolve("local.sqlite").absolutePath, providedController = controller)
        }
    }
}
