package org.viptv.experiment

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.viptv.app.state.ApplicationController
import org.viptv.app.state.disconnect
import org.viptv.app.state.restore

/** Activity recreation must not disconnect the account or recreate its navigation state. */
class ApplicationModel : ViewModel() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val controller = ApplicationController(scope)
    private var started = false
    fun start(localDatabase: String) {
        if (started) return
        started = true
        controller.restore(localDatabase)
    }
    override fun onCleared() { controller.disconnect(); scope.cancel() }
}
