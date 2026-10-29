package org.viptv.app.connection

import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.utils.io.errors.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay

internal suspend fun acknowledgePlaybackFrame(active: () -> Boolean, send: suspend () -> Unit) {
    repeat(3) { attempt ->
        if (!active()) return
        try { send(); return }
        catch (error: CancellationException) { throw error }
        catch (error: Exception) {
            if (!error.transientFailure() || attempt == 2) throw error
            if (!active()) return
            delay(250L * (attempt + 1))
        }
    }
}

internal fun Exception.transientFailure(): Boolean =
    (this is RemoteFailure && status in 500..599) || this is IOException || this is HttpRequestTimeoutException ||
        this is TimeoutCancellationException
