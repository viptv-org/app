package org.viptv.experiment

import android.content.Context
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.viptv.app.state.ApplicationController
import org.viptv.app.state.Screen
import org.viptv.app.state.disconnect
import org.viptv.app.state.restore
import org.viptv.app.updates.AndroidAppUpdatePlatform
import org.viptv.app.updates.AppUpdater
import org.viptv.app.updates.updateFeedUrl
import org.viptv.app.updates.updateServerOrigin

class ApplicationModel : ViewModel() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val controller = ApplicationController(scope)
    private var started = false
    var updater: AppUpdater? = null
        private set
    fun start(localDatabase: String, context: Context) {
        if (started) return
        started = true
        if (BuildConfig.APP_UPDATES) updater = AppUpdater(AndroidAppUpdatePlatform(context), scope) {
            updateFeedUrl(controller.updateServerOrigin(), BuildConfig.UPDATE_FEED)
        }
        controller.restore(localDatabase)
    }
    fun foregroundReturned() {
        val current = controller.state.value
        if (!current.starting) updater?.foreground(playing = current.screen == Screen.Player)
    }
    override fun onCleared() { controller.disconnect(); scope.cancel() }
}
