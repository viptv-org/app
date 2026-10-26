package org.viptv.app.connection

import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.utils.io.errors.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/** Recheck authority immediately before each idempotent dispatch, including retries. */
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

/** A network drop, timeout or server-side 5xx: worth retrying the same request shortly. */
internal fun Exception.transientFailure(): Boolean =
    (this is RemoteFailure && status in 500..599) || this is IOException || this is HttpRequestTimeoutException
