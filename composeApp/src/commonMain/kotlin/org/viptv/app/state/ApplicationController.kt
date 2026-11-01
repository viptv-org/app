package org.viptv.app.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.viptv.app.connection.*
import org.viptv.app.player.PlayerFact
import kotlinx.coroutines.sync.Mutex
import kotlin.concurrent.Volatile
import kotlin.time.TimeSource

class ApplicationController(internal val scope: CoroutineScope, internal val sessionStore: RemoteSessionStore = remoteSessionStore(),
    internal val playbackDiagnostics: ((String) -> Unit)? = null,
    internal val localFactory: (String) -> LocalConnection = ::localConnection,
    internal val remoteFactory: (String, RemoteSessionStore) -> RemoteConnection = { origin, store -> RemoteConnection(origin, sessionStore = store) },
    internal val freshness: Freshness = Freshness()) {
    internal val mutable = MutableStateFlow(ApplicationState(starting = true))
    val state = mutable.asStateFlow()
    internal val deviceLink = MutableStateFlow<DeviceCodeLink?>(null)
    internal var connection: RemoteConnection? = null
    internal var local: LocalConnection? = null
    internal var localPersistenceWarning: String? = null
    @Volatile internal var job: Job? = null
    @Volatile internal var generation = 0L
    private val runGate = SynchronizedObject()
    internal var navigationGeneration = 0L
    internal var pageRequestJob: Job? = null
    internal val progressGate = Mutex()
    internal var progressJob: Job? = null
    internal var liveGuideJob: Job? = null
    internal var liveGuideRequest = 0L
    internal var liveDirectoryRequest = 0L
    internal val liveGuideFetchedAt = mutableMapOf<String, kotlin.time.TimeMark>()
    internal val liveFavoriteJobs = mutableMapOf<String, Job>()
    internal var liveFavoriteVersion = 0L
    internal var liveFavoriteScope = 0L
    internal var detailPagingJob: Job? = null
    internal var libraryHydration: Job? = null
    internal var navigationSaveJob: Job? = null
    internal var navigationRestoreJob: Job? = null
    internal var rememberedNavigation: SavedNavigation? = null
    internal var rememberedNavigationProfile: String? = null
    internal var continueChecked: ContinueCheck? = null
    internal var continueVisibilityJob: Job? = null
    internal var libraryImportRefresh: Job? = null
    internal var calendarReread: Job? = null
    internal var remotePlayback: RemotePlaybackLease? = null
    internal var tracking: PlaybackTracking? = null
    internal var playbackRenewal: Job? = null
    internal var leaseOpener: PlaybackLeaseOpener = PlaybackLeaseOpener.Server
    internal var resumeAfterRenewal: org.viptv.app.player.PlaybackInput? = null
    internal val inFront = MutableStateFlow(true)
    internal var viewerPresent by androidx.compose.runtime.mutableStateOf(true)
    internal val viewerReturned = kotlinx.coroutines.channels.Channel<Unit>(kotlinx.coroutines.channels.Channel.CONFLATED)
    internal val leaseWake = MutableStateFlow(0L)
    internal var subtitleDiscovery: Job? = null
    internal var playbackSegmentsJob: Job? = null
    internal var nextEpisodeJob: Job? = null
    internal var nextEpisodeKey: NextEpisodeKey? = null
    internal var nextEpisodeGeneration = 0L
    internal var playbackSeasonJob: Job? = null
    internal var playbackSeasonKey: PlaybackSeasonKey? = null
    internal var playbackSeasonGeneration = 0L
    internal var sourceDetailJob: Job? = null
    internal var sourcesScrolled = false
    internal var pairingJob: Job? = null
    internal var pairingGeneration = 0L
    internal var pairingCode: PairingCode? = null
    internal var stremioImport: StremioImportSession? = null
    internal var restartRequested = false
    internal var firstFrameSeen = false
    internal var frameAcknowledgment: Job? = null
    internal var lastProgress: PlayerFact.Progress? = null
    internal var backgroundPlayback: org.viptv.app.player.PlaybackInput? = null
    internal var completionSeen = false
    internal var playerVisitTracks: ServerTracks? = null
    internal var progressSavedAt = TimeSource.Monotonic.markNow()
    internal var quietJob: Job? = null
    internal var quietOwner: QuietOwner? = null
    internal var quietPending: QuietWork? = null
    internal var quietDetail = false
    internal var freshnessWatcher: Job? = null
    internal var addonOperation: Job? = null

    internal fun run(operation: suspend () -> Unit) {
        detailPagingJob?.cancel(); detailPagingJob = null
        val claimed = synchronized(runGate) {
            val prior = job
            val current = ++generation
            updateState { it.copy(busy = true, error = null, errorCode = null, detailLoadingOffset = null, detailPageError = null, detailRetryable = true) }
            val started = scope.launch(start = CoroutineStart.LAZY) {
                try { prior?.join(); operation() }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) {
                    if (generation == current && e is RemoteFailure && e.status == 401 && local == null) {
                        releaseRemotePlayback()
                        resetLiveRequests()
                        discardStremioImport()
                        connection?.close(); connection = null
                        mutable.value = ApplicationState(error = "Session expired. Sign in again.")
                    } else if (generation == current) updateState { it.copy(error = failureMessage(e), errorCode = (e as? RemoteFailure)?.code,
                        detailRetryable = e !is RemoteFailure || !e.metadataNotFound) }
                }
            }
            job = started
            RunClaim(prior, current, started)
        }
        claimed.prior?.cancel()
        // A job cancelled before it is dispatched never enters its body, so completion, not a finally, clears busy.
        claimed.job.invokeOnCompletion { settle(claimed.generation) }
        claimed.job.start()
    }

    private fun settle(current: Long) {
        synchronized(runGate) {
            if (generation != current) return
            updateState { it.copy(starting = false, busy = false, findingSources = false,
                sessionNotice = connection?.persistenceWarning ?: localPersistenceWarning) }
        }
    }
}

private class RunClaim(val prior: Job?, val generation: Long, val job: Job)

// A write built from a snapshot drops whatever another coroutine published in between, so every state change publishes atomically.
internal fun ApplicationController.updateState(change: (ApplicationState) -> ApplicationState) {
    mutable.update(change)
}

internal fun ApplicationController.failureMessage(error: Exception): String? = when (error) {
    is RemoteFailure -> error.message
    is LocalFailure -> error.message
    is kotlinx.serialization.SerializationException -> "The server returned an incompatible response."
    is IllegalArgumentException -> "Enter a valid HTTPS server address."
    else -> if (local != null) "Could not complete this action on this device." else "Could not reach your server. Check the connection and try again."
}
