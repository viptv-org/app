package org.viptv.app.state

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.viptv.app.connection.*
import org.viptv.app.player.PlayerFact
import kotlinx.coroutines.sync.Mutex
import kotlin.time.TimeSource

class ApplicationController(internal val scope: CoroutineScope, internal val sessionStore: RemoteSessionStore = remoteSessionStore(),
    internal val playbackDiagnostics: ((String) -> Unit)? = null,
    internal val localFactory: (String) -> LocalConnection = ::localConnection,
    internal val remoteFactory: (String, RemoteSessionStore) -> RemoteConnection = { origin, store -> RemoteConnection(origin, sessionStore = store) }) {
    internal val mutable = MutableStateFlow(ApplicationState(starting = true))
    val state = mutable.asStateFlow()
    /** Outside [mutable]: signing in replaces the whole state, and a link can arrive before that. */
    internal val deviceLink = MutableStateFlow<DeviceCodeLink?>(null)
    internal var connection: RemoteConnection? = null
    internal var local: LocalConnection? = null
    internal var localPersistenceWarning: String? = null
    internal var job: Job? = null
    internal var generation = 0L
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
    internal var libraryImportRefresh: Job? = null
    internal var remotePlayback: RemotePlaybackLease? = null
    internal var tracking: PlaybackTracking? = null
    internal var playbackRenewal: Job? = null
    internal var subtitleDiscovery: Job? = null
    internal var nextEpisodeJob: Job? = null
    internal var nextEpisodeKey: NextEpisodeKey? = null
    internal var nextEpisodeGeneration = 0L
    internal var pairingJob: Job? = null
    internal var pairingGeneration = 0L
    internal var stremioImport: StremioImportSession? = null
    /** Set when the next playback open should start from the beginning instead of resuming. */
    internal var restartRequested = false
    internal var firstFrameSeen = false
    internal var frameAcknowledgment: Job? = null
    internal var lastProgress: PlayerFact.Progress? = null
    internal var backgroundPlayback: org.viptv.app.player.PlaybackInput? = null
    internal var completionSeen = false
    internal var progressSavedAt = TimeSource.Monotonic.markNow()

    internal fun run(operation: suspend () -> Unit) {
        val prior = job
        prior?.cancel()
        detailPagingJob?.cancel(); detailPagingJob = null
        val current = ++generation
        mutable.value = mutable.value.copy(busy = true, error = null, detailLoadingOffset = null, detailPageError = null)
        job = scope.launch {
            try { prior?.join(); operation() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (generation == current && e is RemoteFailure && e.status == 401 && local == null) {
                    releaseRemotePlayback()
                    resetLiveRequests()
                    discardStremioImport()
                    connection?.close(); connection = null
                    mutable.value = ApplicationState(error = "Session expired. Sign in again.")
                } else if (generation == current) mutable.value = mutable.value.copy(error = when (e) {
                    is RemoteFailure -> e.message
                    is LocalFailure -> e.message
                    is kotlinx.serialization.SerializationException -> "The server returned an incompatible response."
                    is IllegalArgumentException -> "Enter a valid HTTPS server address."
                    else -> if (local != null) "Could not complete this action on this device." else "Could not reach your server. Check the connection and try again."
                })
            } finally { if (generation == current) mutable.value = mutable.value.copy(starting = false, busy = false, sessionNotice = connection?.persistenceWarning ?: localPersistenceWarning) }
        }
    }}
