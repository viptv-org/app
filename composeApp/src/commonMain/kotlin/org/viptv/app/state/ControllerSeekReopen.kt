package org.viptv.app.state

import kotlinx.coroutines.*
import org.viptv.app.connection.RemoteFailure
import org.viptv.engine.wire.SourceCandidate

private val capacityCodes = setOf("gateway_capacity", "playback_capacity", "viewer_capacity")

internal val Exception.capacityFull: Boolean get() = this is RemoteFailure && code in capacityCodes

internal fun ApplicationController.seekByReopening(source: SourceCandidate, profile: String): (Long) -> Unit = { target ->
    if (mutable.value.playingSource?.id == source.id && mutable.value.selectedProfile == profile) {
        cancelSeekReopen()
        seekReopenSettle = scope.launch {
            delay(seekReopenSettleMillis)
            seekReopenSettle = null
            if (mutable.value.playingSource?.id != source.id || mutable.value.selectedProfile != profile) return@launch
            saveProgress(false)
            openSource(source, forceGateway = true, positionMillis = target, pausedAfterOpen = mutable.value.paused, retryFullCapacity = true)
            seekReopenOpen = job
        }
    }
}

internal fun ApplicationController.cancelSeekReopen() {
    seekReopenSettle?.cancel(); seekReopenSettle = null
    seekReopenOpen?.let { open -> if (open === job) open.cancel() }
    seekReopenOpen = null
}

internal suspend fun ApplicationController.retryingFullCapacity(open: suspend () -> Unit) {
    for (wait in seekCapacityRetryDelays) {
        try { return open() }
        catch (error: CancellationException) { throw error }
        catch (error: Exception) { if (!error.capacityFull) throw error }
        delay(wait)
    }
    open()
}
