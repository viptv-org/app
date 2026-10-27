package org.viptv.experiment

import android.content.Context
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.viptv.app.state.ApplicationController
import org.viptv.app.state.disconnect
import org.viptv.app.state.restore
import org.viptv.app.updates.AndroidAppUpdatePlatform
import org.viptv.app.updates.AppUpdater
import org.viptv.app.updates.updateFeedUrl
import org.viptv.app.updates.updateServerOrigin

/** Activity recreation must not disconnect the account or recreate its navigation state. */
class ApplicationModel : ViewModel() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val controller = ApplicationController(scope)
    private var started = false
    /** Lives with the controller, so a rotation does not restart a check or a download. */
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
    override fun onCleared() { controller.disconnect(); scope.cancel() }
}
