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
import org.viptv.app.crash.CrashFacts
import org.viptv.app.crash.CrashReportStore
import kotlinx.coroutines.sync.Mutex
import kotlin.concurrent.Volatile
import kotlin.time.TimeSource

class ApplicationController(internal val scope: CoroutineScope, internal val sessionStore: RemoteSessionStore = remoteSessionStore(),
    internal val playbackDiagnostics: ((String) -> Unit)? = null,
    internal val localFactory: (String) -> LocalConnection = ::localConnection,
    internal val remoteFactory: (String, RemoteSessionStore) -> RemoteConnection = { origin, store -> RemoteConnection(origin, sessionStore = store) },
    internal val freshness: Freshness = Freshness(),
    internal val backend: BackendHealth = BackendHealth(freshness.time),
    internal val bestMatchNoticeMillis: Long = 2_000L,
    internal val bestMatchGraceMillis: Long = BEST_MATCH_GRACE_MILLIS,
    internal val bestMatchCapMillis: Long = BEST_MATCH_CAP_MILLIS,
    internal val startDeadlineMillis: Long = 20_000L,
    internal val searchStreamTimeoutMillis: Long = 60_000L,
    internal val startupRetryDelays: List<Long> = listOf(1_000L, 2_000L, 4_000L),
    internal val progressRetryDelays: List<Long> = listOf(2_000L, 4_000L, 8_000L, 15_000L, 30_000L, 60_000L),
    internal val launchOrigin: () -> String? = ::browserOrigin) {
    internal val mutable = MutableStateFlow(ApplicationState(starting = true))
    val state = mutable.asStateFlow()
    internal val deviceLink = MutableStateFlow<DeviceCodeLink?>(null)
    internal var connection: RemoteConnection? = null
        set(value) {
            field = value
            value?.onClientUpdateRequired = { updateState { it.copy(updateRequired = true) } }
            value?.onBackendReachability = ::backendOutcome
        }
    internal var local: LocalConnection? = null
    internal var localPersistenceWarning: String? = null
    @Volatile internal var job: Job? = null
    @Volatile internal var generation = 0L
    internal var lastSearchedTerm: String? = null
    private val runGate = SynchronizedObject()
    internal var navigationGeneration = 0L
    internal var pageRequestJob: Job? = null
    internal val progressGate = Mutex()
    internal var progressJob: Job? = null
    internal var progressSequence = 0L
    internal var pendingProgress: ProgressWrite? = null
    internal var progressRetryJob: Job? = null
    internal var liveGuideJob: Job? = null
    internal var liveGuideRequest = 0L
    internal var liveDirectoryRequest = 0L
    internal val liveGuideFetchedAt = mutableMapOf<String, kotlin.time.TimeMark>()
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
    // A newer notice restarts the toast's timer, so each one carries its own serial.
    internal var noticeSerial = 0L
    internal var libraryImportRefresh: Job? = null
    internal var calendarReread: Job? = null
    internal var remotePlayback: RemotePlaybackLease? = null
    internal var livePlaybackChannel: String? = null
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
    internal var nextEpisodePreparationJob: Job? = null
    internal var nextEpisodePreparationKey: NextEpisodePreparationKey? = null
    internal var nextEpisodePreparationGeneration = 0L
    internal var nextEpisodePreparationRenewal: Job? = null
    internal var nextEpisodePreparedLease: org.viptv.app.connection.PreparedPlaybackLease? = null
    internal var nextSourcePreparer: NextSourcePreparer = NextSourcePreparer.Device
    internal var nextEpisodeDeliveryPreparer: NextEpisodeDeliveryPreparer = NextEpisodeDeliveryPreparer.Server
    internal var clockMillis: () -> Long = ::epochMillis
    internal var capability: suspend () -> DeviceCapability = { deviceCapability() }
    internal var playbackSeasonJob: Job? = null
    internal var playbackSeasonKey: PlaybackSeasonKey? = null
    internal var playbackSeasonGeneration = 0L
    internal var sourceDetailJob: Job? = null
    internal var sourcePreviewSession: DetailSourcePreviewSession? = null
    internal var sourcesScrolled = false
    internal var sourcesStartPlayback = false
    internal var pairingJob: Job? = null
    internal var vodMatchesJob: Job? = null
    internal var vodMatchesRequest = 0L
    internal var pairingGeneration = 0L
    internal var pairingCode: PairingCode? = null
    internal var stremioImport: StremioImportSession? = null
    internal var restartRequested = false
    internal var firstFrameSeen = false
    internal var fallbackJob: Job? = null
    internal var startDeadlineJob: Job? = null
    internal var playbackStall: Job? = null
    internal var playbackStallPosition = 0L
    internal var playbackWaiting = false
    internal var sourceOpenJob: Job? = null
    internal var sourceChoice = SourceChoice.None
    internal var sourceMemory = SourceMemory.book()
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
    internal val mutationJobs = mutableMapOf<MutationTarget, Job>()
    internal val mutationRetries = mutableMapOf<MutationTarget, MutationRetry>()
    internal var householdPinAction: (suspend () -> Unit)? = null
    internal var crashReportChecked = false
    internal var startupDatabase: String? = null
    internal val seriesMemory = mutableMapOf<SeriesMemoryKey, SeriesMemory>()

    // The quiet lane and Back wait on the controller's operation job, so a write that must not be reloaded under
    // hands claims it instead of leaving it pointing at nothing; the write then owns the busy its claim replaced.
    internal fun claimOperation(task: Job) {
        val current = synchronized(runGate) { job = task; ++generation }
        task.invokeOnCompletion { settle(current) }
    }

    internal fun run(operation: suspend () -> Unit) {
        detailPagingJob?.cancel(); detailPagingJob = null
        val claimed = synchronized(runGate) {
            val prior = job
            val current = ++generation
            updateState { it.copy(busy = true, error = null, errorCode = null, startupRetryable = false, detailLoadingOffset = null, detailPageError = null, detailPageRetryOffset = null, detailRetryable = true, devicesRetryable = true) }
            val started = scope.launch(start = CoroutineStart.LAZY) {
                try { prior?.join(); operation() }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) {
                    if (generation == current && e is RemoteFailure && e.sessionExpired && local == null) {
                        releasePlaybackSession()
                        resetLiveRequests()
                        discardStremioImport()
                        connection?.close(); connection = null
                        mutable.value = ApplicationState(error = "Session expired. Sign in again.")
                    } else if (generation == current) {
                        sourceChoice = SourceChoice.None
                        updateState { it.copy(error = failureMessage(e), errorCode = (e as? RemoteFailure)?.code,
                            detailRetryable = e !is RemoteFailure || !e.metadataNotFound,
                            devicesRetryable = e !is RemoteFailure || !e.deviceListUnavailable, openingSource = null, pendingAction = null) }
                    }
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
            checkPendingCrashReport()
        }
    }
}

private class RunClaim(val prior: Job?, val generation: Long, val job: Job)

// A write built from a snapshot drops whatever another coroutine published in between, so every state change publishes atomically.
internal fun ApplicationController.updateState(change: (ApplicationState) -> ApplicationState) {
    mutable.update { before ->
        val after = change(before)
        CrashFacts.lastScreen = after.screen.name
        if (before.playback == null && after.playback != null) {
            // A native crash between playback start and a clean stop has no stack: this marker attributes it to the source opening.
            CrashFacts.playbackAttempt?.let { CrashReportStore.writePlaybackMarker(it) }
        } else if (before.playback != null && after.playback == null) {
            CrashReportStore.clearPlaybackMarker()
        }
        after
    }
}

internal fun ApplicationController.failureMessage(error: Exception): String? = when (error) {
    is RemoteFailure -> error.message
    is LocalFailure -> error.message
    is kotlinx.serialization.SerializationException -> "The server returned an incompatible response."
    is IllegalArgumentException -> SERVER_ADDRESS_ERROR
    else -> if (local != null) "Could not complete this action on this device." else "Could not reach your server. Check the connection and try again."
}

// Local mode has no signed-in account: keep the report on disk until one exists.
fun ApplicationController.checkPendingCrashReport() {
    if (crashReportChecked || connection == null) return
    crashReportChecked = true
    val report = CrashReportStore.pendingReport() ?: return
    updateState { it.copy(pendingCrashReport = report) }
}

fun ApplicationController.submitCrashReport() {
    val report = mutable.value.pendingCrashReport ?: return
    val remote = connection ?: return
    scope.launch {
        val sent = try { remote.submitCrashReport(report) }
        catch (error: CancellationException) { throw error }
        catch (_: Exception) { false }
        if (sent) CrashReportStore.deletePending()
        // A missing endpoint leaves the report on disk for the next launch; this one hides the notice either way.
        if (mutable.value.pendingCrashReport === report) updateState { it.copy(pendingCrashReport = null) }
    }
}

fun ApplicationController.discardCrashReport() {
    CrashReportStore.deletePending()
    updateState { it.copy(pendingCrashReport = null) }
}
