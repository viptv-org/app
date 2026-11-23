package org.viptv.app.state

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.viptv.app.connection.*

fun ApplicationController.restore(localDatabase: String? = null) = run {
    startupDatabase = localDatabase
    if (offlineAtStart()) {
        startOffline()
        return@run
    }
    updateState { it.copy(starting = true) }
    val operation = generation
    val origin = launchOrigin()
    if (origin != null) {
        val restored = launchRestore(operation, connect = { remoteFactory(origin, sessionStore) },
            restore = { remote -> remote.restoreBrowser() })
        if (generation != operation) return@run
        if (restored == null) { connection?.close(); connection = null; return@run }
        accept(restored)
        resumeNavigation()
        return@run
    }
    val savedConnection = try { sessionStore.savedConnection() }
    catch (error: CancellationException) { throw error }
    catch (_: Exception) {
        currentCoroutineContext().ensureActive()
        if (generation == operation) updateState { next -> next.copy(error = "Your saved connection could not be read from secure storage. Choose a connection to continue.") }
        null
    }
    currentCoroutineContext().ensureActive()
    if (generation != operation) return@run
    if (savedConnection == SavedConnection.Local) {
        if (localDatabase == null) return@run
        accept(openLocalConnection(localDatabase))
        resumeNavigation()
        return@run
    }
    val saved = (savedConnection as? SavedConnection.Remote)?.session ?: return@run
    val connect: suspend () -> RemoteConnection? = {
        try { remoteFactory(saved.origin, sessionStore) }
        catch (_: IllegalArgumentException) {
            withNativeSessionStoreLock(sessionStore) {
                if (sessionStore.savedConnection() == savedConnection) sessionStore.clear()
            }
            null
        }
    }
    if (!downloadEngine?.records().isNullOrEmpty()) {
        restoreOrGoOffline(operation, connect() ?: return@run)
        return@run
    }
    val restored = launchRestore(operation, connect, { remote -> remote.restoreFromStore() }, retryUnattended = true)
    if (generation != operation) return@run
    if (restored == null) { connection?.close(); connection = null; return@run }
    accept(restored)
    resumeNavigation()
}

internal class PendingRestore(val remote: RemoteConnection, val result: Deferred<Result<Bootstrap>>)

private fun ApplicationController.pendingRestore(remote: RemoteConnection) = PendingRestore(remote, scope.async {
    try { Result.success(remote.restoreFromStore()) }
    catch (error: CancellationException) { throw error }
    catch (error: Exception) { Result.failure(error) }
})

// The device refresh can't be cancelled without tearing a token rotation, so start-up stops waiting instead.
private suspend fun ApplicationController.restoreOrGoOffline(operation: Long, remote: RemoteConnection) {
    val pending = pendingRestore(remote)
    val early = withTimeoutOrNull(offlineStartMillis) { pending.result.await() }
    if (generation != operation) { pending.result.cancel(); remote.close(); return }
    val failure = early?.exceptionOrNull() as? Exception
    when {
        early == null -> startOffline(pending)
        failure == null -> { connection = remote; accept(early.getOrThrow()); resumeNavigation() }
        failure.rejectsSavedSession() -> { remote.close(); rejectSavedSession(failure) }
        else -> { remote.close(); startOffline() }
    }
}

private fun ApplicationController.rejectSavedSession(failure: Exception) {
    val expired = failure is RemoteFailure && failure.sessionExpired
    updateState { it.copy(startupRetryable = !expired, error = if (expired) "Session expired. Sign in again." else failureMessage(failure)) }
}

private suspend fun ApplicationController.launchRestore(operation: Long, connect: suspend () -> RemoteConnection?,
    restore: suspend (RemoteConnection) -> Bootstrap?, retryDelays: List<Long> = startupRetryDelays,
    retryUnattended: Boolean = false): Bootstrap? {
    var attempt = 0
    while (true) {
        val remote = connect() ?: return null
        connection = remote
        val failure = try {
            val restored = restore(remote)
            currentCoroutineContext().ensureActive()
            if (generation != operation || connection !== remote) return null
            return restored
        }
        catch (error: CancellationException) { throw error }
        catch (error: Exception) {
            currentCoroutineContext().ensureActive()
            if (generation != operation || connection !== remote) return null
            // Release the failed connection before the next attempt or Connect so no token-bearing client survives the restore.
            remote.close(); connection = null
            error
        }
        if (!failure.retriesStartup() || attempt >= retryDelays.size) {
            if (retryUnattended && downloadEngine != null && !failure.rejectsSavedSession()) scheduleStartupRetry()
            when (failure) {
                is RemoteFailure -> updateState { next -> next.copy(startupRetryable = !failure.sessionExpired,
                    error = if (failure.sessionExpired) "Session expired. Sign in again." else failure.message) }
                else -> {
                    updateState { it.copy(startupRetryable = true) }
                    throw failure
                }
            }
            return null
        }
        delay(retryDelays[attempt++])
    }
}

private fun Exception.retriesStartup(): Boolean = when (this) {
    is RemoteFailure -> !sessionExpired && status >= 500
    is LocalFailure -> false
    is kotlinx.serialization.SerializationException -> true
    is IllegalArgumentException -> false
    else -> true
}

private fun Exception.rejectsSavedSession(): Boolean =
    (this is RemoteFailure && sessionExpired) || this is LocalFailure || this is IllegalArgumentException

internal fun ApplicationController.reconnectDelay(failures: Int): Long =
    reconnectDelays[(failures - 1).coerceIn(0, reconnectDelays.lastIndex)]

private fun ApplicationController.scheduleStartupRetry() {
    startupRetryJob?.cancel()
    val failures = ++startupFailures
    startupRetryJob = scope.launch {
        withTimeoutOrNull(reconnectDelay(failures)) { reconnectWake.receive() }
        retryStartup()
    }
}

// A saved session the server or the network failed to reach is retried from here: no sign-in and no new process.
fun ApplicationController.retryStartup() {
    if (offlineStart) { reconnectWake.trySend(Unit); return }
    if (!mutable.value.startupRetryable || mutable.value.busy) return
    updateState { it.copy(starting = true) }
    restore(startupDatabase)
}

internal fun ApplicationController.startOffline(pending: PendingRestore? = null) {
    offlineStart = true
    updateState { it.copy(starting = false, screen = Screen.Downloads, offline = true, error = null, startupRetryable = false) }
    reconnectJob?.cancel()
    reconnectJob = scope.launch {
        var failures = 0
        var inFlight = pending
        while (offlineStart) {
            if (failures > 0) withTimeoutOrNull(reconnectDelay(failures)) { reconnectWake.receive() }
            if (inFlight == null) downloadEngine?.network?.first { it != org.viptv.app.downloads.DownloadNetwork.None }
            if (!offlineStart || connection != null || local != null) { inFlight?.let { it.result.cancel(); it.remote.close() }; return@launch }
            if (reconnectOnce(inFlight)) return@launch
            inFlight = null
            failures++
        }
    }
}

private suspend fun ApplicationController.reconnectOnce(pending: PendingRestore?): Boolean {
    val attempt = pending ?: savedRemote()?.let(::pendingRestore)
    if (attempt == null) {
        offlineStart = false
        restore(startupDatabase)
        return true
    }
    val remote = attempt.remote
    val outcome = try { attempt.result.await() }
        catch (error: CancellationException) { attempt.result.cancel(); remote.close(); throw error }
    val failure = outcome.exceptionOrNull() as? Exception
    if (failure != null) {
        remote.close()
        if (!failure.rejectsSavedSession()) return false
        offlineStart = false
        mutable.value = ApplicationState(error = if (failure is RemoteFailure && failure.sessionExpired) "Session expired. Sign in again." else failureMessage(failure))
        return true
    }
    val restored = outcome.getOrThrow()
    try { mutable.first { it.screen != Screen.Player } }
    catch (error: CancellationException) { remote.close(); throw error }
    if (!offlineStart || connection != null || local != null) { remote.close(); return true }
    offlineStart = false
    connection = remote
    accept(restored, mutable.value.screen.takeIf { restored.selected != null })
    return true
}

private suspend fun ApplicationController.savedRemote(): RemoteConnection? {
    val saved = try { (sessionStore.savedConnection() as? SavedConnection.Remote)?.session }
        catch (error: CancellationException) { throw error }
        catch (_: Exception) { null }
    return saved?.let { session -> try { remoteFactory(session.origin, sessionStore) } catch (_: IllegalArgumentException) { null } }
}

private fun ApplicationController.stopReconnecting() {
    offlineStart = false
    reconnectJob?.cancel(); reconnectJob = null
    startupRetryJob?.cancel(); startupRetryJob = null
    startupFailures = 0
    healthProbe?.cancel(); healthProbe = null
}

fun ApplicationController.connect(origin: String, username: String, password: String) {
    stopReconnecting()
    discardStremioImport()
    cancelDevicePairing()
    libraryImportRefresh?.cancel()
    calendarReread?.cancel(); calendarReread = null
    val finishing = if (completionSeen) progressJob else saveProgress(false, final = true)
    val stopping = releaseRemotePlayback()
    val priorRemote = connection
    val priorLocal = local
    connection = null; local = null
    run {
        finishing?.join(); stopping?.join(); priorRemote?.retire(); priorLocal?.close()
        val remote = remoteFactory(origin, sessionStore)
        connection = remote
        val bootstrap = try {
            if (username.isBlank()) remote.bootstrap() else remote.login(username, password)
        } catch (error: Exception) {
            if (connection === remote) connection = null
            remote.close()
            throw error
        }
        try { withNativeSessionStoreLock(sessionStore) {
            if (sessionStore.savedConnection() == SavedConnection.Local) sessionStore.clear()
        } }
        catch (error: CancellationException) { throw error }
        catch (_: Exception) { }
        accept(bootstrap)
    }
}

fun ApplicationController.useLocal(path: String) = startLocal(path)

internal fun ApplicationController.startLocal(path: String) {
    val priorRemote = connection
    disconnect()
    run {
        priorRemote?.retire()
        try { withNativeSessionStoreLock(sessionStore) { sessionStore.clear() } }
        catch (error: CancellationException) { throw error }
        catch (_: Exception) { }
        val bootstrap = openLocalConnection(path)
        // Finish the connection choice before publishing Profiles, or a quick profile selection cancels its pending vault write.
        try { withNativeSessionStoreLock(sessionStore) { sessionStore.rememberLocalConnection() } }
        catch (error: CancellationException) { throw error }
        catch (_: Exception) { localPersistenceWarning = "This connection could not be remembered. Choose local mode again after restarting." }
        accept(bootstrap)
    }
}

private suspend fun ApplicationController.openLocalConnection(path: String): Bootstrap {
    val owner = generation
    var created: LocalConnection? = null
    try {
        local = withContext(Dispatchers.Default) { localFactory(path).also { created = it } }
        created = null
    } finally { created?.close() }
    val result = requireNotNull(local).bootstrap()
    currentCoroutineContext().ensureActive()
    if (generation != owner) throw CancellationException("Local startup replaced")
    return result
}

fun ApplicationController.leaveLocal() {
    if (mutable.value.busy) return
    run {
        withNativeSessionStoreLock(sessionStore) {
            if (sessionStore.savedConnection() == SavedConnection.Local) sessionStore.clear()
        }
        disconnect()
    }
}

fun ApplicationController.disconnect() {
    stopReconnecting()
    discardStremioImport()
    cancelDevicePairing()
    libraryImportRefresh?.cancel()
    calendarReread?.cancel(); calendarReread = null
    libraryHydration?.cancel(); resetLiveRequests()
    quietJob?.cancel(); quietJob = null; freshness.reset()
    cancelDetailPaging()
    val stopping = releasePlaybackSession()
    val priorRemote = connection
    val prior=local
    val finishing=if(completionSeen) progressJob else saveProgress(false, final = true)
    val priorJob = job
    generation++; priorJob?.cancel(); connection = null; local = null
    localPersistenceWarning = null
    priorRemote?.close()
    scope.launch(start = CoroutineStart.UNDISPATCHED) { withContext(NonCancellable) {
        withTimeoutOrNull(6_000) { priorJob?.join(); finishing?.join(); stopping?.join() }
        priorRemote?.close()
    } }
    if(finishing!=null && prior!=null) scope.launch(start = CoroutineStart.UNDISPATCHED) { withContext(NonCancellable) { finishing.join(); prior.close() } } else prior?.close()
    mutable.value = ApplicationState()
}

fun ApplicationController.signOut() = run {
    stopReconnecting()
    discardStremioImport()
    libraryImportRefresh?.cancel()
    calendarReread?.cancel(); calendarReread = null
    if (!completionSeen) saveProgress(false, final = true)?.join() else progressJob?.join()
    releasePlaybackSession()?.join()
    val remote = connection
    val device = local
    remote?.signOut()
    remote?.close()
    if (connection === remote && local === device) {
        resetLiveRequests()
        connection = null
        device?.close(); local = null
        mutable.value = ApplicationState()
    }
}

internal fun ApplicationController.expireSession(remote: RemoteConnection?, device: LocalConnection?) {
    if (remote == null || connection !== remote || local !== device) return
    stopReconnecting()
    releasePlaybackSession()
    resetLiveRequests()
    connection = null
    remote.close()
    mutable.value = ApplicationState(error = "Session expired. Sign in again.")
}

internal fun ApplicationController.accept(value: Bootstrap, screen: Screen? = null, busy: Boolean = false) {
    startupRetryJob?.cancel(); startupRetryJob = null
    startupFailures = 0
    startSnapshot(value)
    val switchesProfile = value.selected != null && value.selected != mutable.value.selectedProfile
    mutable.value = ApplicationState(screen = screen ?: if (value.selected == null) Screen.Profiles else Screen.Home, busy = busy,
        profiles = value.profiles, profileManagement = value.profileManagement, selectedProfile = value.selected, home = value.home,
        localMode = local != null, sessionNotice = connection?.persistenceWarning,
        manageAccount = connection?.accountManagementEnabled == true, accountOwner = connection?.accountOwner == true,
        manageIntegrations = connection?.integrationManagementEnabled,
        accountRole = connection?.accountRole ?: "member",
        plugins = value.plugins, addons = value.addons, enabledAddonIds = value.enabledAddonIds, preferences = value.preferences)
    if (switchesProfile) value.selected?.let { startBrowsingConfiguration(it) }
    adoptSnapshot(value)
}

// A write that re-reads the profile already open republishes the content only: the Back stack, the settings page,
// the account reads and the write in flight belong to the session, not to the snapshot.
internal fun ApplicationController.refreshSnapshot(value: Bootstrap, busy: Boolean = false) {
    startSnapshot(value)
    updateState { next -> next.copy(busy = busy, profiles = value.profiles,
        profileManagement = value.profileManagement ?: next.profileManagement,
        selectedProfile = value.selected ?: next.selectedProfile, home = value.home,
        preferences = value.preferences ?: next.preferences, policyRefreshNeeded = false,
        manageIntegrations = connection?.integrationManagementEnabled, accountAccess = null) }
    adoptSnapshot(value)
}

private fun ApplicationController.startSnapshot(value: Bootstrap) {
    libraryHydration?.cancel()
    calendarReread?.cancel(); calendarReread = null
    resetLiveRequests()
    freshness.reset()
    if (local != null) freshness.unsupported()
    if (value.home != null) freshness.homeBuilt(value.revisions)
}

private fun ApplicationController.adoptSnapshot(value: Bootstrap) {
    value.selected?.let { loadRecentSearches(it) }
    adoptContinuation(value.continuation)
    discardReplacedStremioImport()
    value.library?.let { updateLibrary(it, membership = value.libraryMembership) }
    startLibraryImportRefresh(value.libraryImport)
    if (value.library != null) return
    val profile = value.selected ?: return
    val remote = connection
    val device = local
    libraryHydration = scope.launch {
        try {
            val cards = device?.library(profile) ?: remote?.library(profile) ?: return@launch
            val membership = device?.libraryMembership(profile) ?: remote?.libraryMembership(profile)
            if (mutable.value.selectedProfile == profile && connection === remote && local === device)
                updateLibrary(cards, membership = membership)
        } catch (error: CancellationException) { throw error }
        catch (error: Exception) {
            if (error is RemoteFailure && error.sessionExpired) expireSession(remote, device)
        }
    }
}
