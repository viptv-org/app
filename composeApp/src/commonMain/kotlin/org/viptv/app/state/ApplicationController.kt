package org.viptv.app.state

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.viptv.app.connection.*
import org.viptv.app.player.PlayerFact
import kotlinx.coroutines.sync.Mutex
import kotlin.time.TimeSource

class ApplicationController(internal val scope: CoroutineScope, internal val sessionStore: RemoteSessionStore = remoteSessionStore()) {
    internal val mutable = MutableStateFlow(ApplicationState(starting = true))
    val state = mutable.asStateFlow()
    internal var connection: RemoteConnection? = null
    internal var local: LocalConnection? = null
    internal var job: Job? = null
    internal var generation = 0L
    internal val progressGate = Mutex()
    internal var progressJob: Job? = null
    internal var liveGuideJob: Job? = null
    internal var libraryHydration: Job? = null
    internal var remotePlayback: RemotePlaybackLease? = null
    internal var tracking: PlaybackTracking? = null
    internal var playbackRenewal: Job? = null
    internal var subtitleDiscovery: Job? = null
    internal var firstFrameSeen = false
    internal var frameAcknowledgment: Job? = null
    internal var lastProgress: PlayerFact.Progress? = null
    internal var completionSeen = false
    internal var progressSavedAt = TimeSource.Monotonic.markNow()

    internal fun run(operation: suspend () -> Unit) {
        val prior = job
        prior?.cancel()
        val current = ++generation
        mutable.value = mutable.value.copy(busy = true, error = null)
        job = scope.launch {
            try { prior?.join(); operation() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (generation == current && e is RemoteFailure && e.status == 401 && local == null) {
                    releaseRemotePlayback()
                    connection?.close(); connection = null
                    mutable.value = ApplicationState(error = "Session expired. Sign in again.")
                } else if (generation == current) mutable.value = mutable.value.copy(error = when (e) {
                    is RemoteFailure -> e.message
                    is LocalFailure -> e.message
                    is kotlinx.serialization.SerializationException -> "The server returned an incompatible response."
                    is IllegalArgumentException -> "Enter a valid HTTPS server address."
                    else -> if (local != null) "Could not complete this action on this device." else "Could not reach your server. Check the connection and try again."
                })
            } finally { if (generation == current) mutable.value = mutable.value.copy(starting = false, busy = false, sessionNotice = connection?.persistenceWarning) }
        }
    }}
