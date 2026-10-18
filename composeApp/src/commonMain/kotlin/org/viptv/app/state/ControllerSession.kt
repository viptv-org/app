package org.viptv.app.state

import kotlinx.coroutines.*
import org.viptv.app.connection.*

fun ApplicationController.restore(localDatabase: String? = null) = run {
    val operation = generation
    val origin = browserOrigin()
    if (origin != null) {
        val remote = remoteFactory(origin, sessionStore)
        connection = remote
        val restored = remote.restoreBrowser()
        currentCoroutineContext().ensureActive()
        if (generation != operation || connection !== remote) return@run
        if (restored == null) { remote.close(); connection = null }
        else accept(restored)
    } else {
        val savedConnection = try { sessionStore.savedConnection() }
        catch (error: CancellationException) { throw error }
        catch (_: Exception) {
            currentCoroutineContext().ensureActive()
            if (generation == operation) mutable.value = mutable.value.copy(error = "Your saved connection could not be read from secure storage. Choose a connection to continue.")
            null
        }
        currentCoroutineContext().ensureActive()
        if (generation != operation) return@run
        if (savedConnection == SavedConnection.Local) {
            if (localDatabase == null) return@run
            accept(openLocalConnection(localDatabase))
            return@run
        }
        val saved = (savedConnection as? SavedConnection.Remote)?.session ?: return@run
        val remote = try { remoteFactory(saved.origin, sessionStore) }
        catch (_: IllegalArgumentException) {
            withNativeSessionStoreLock {
                if (sessionStore.savedConnection() == savedConnection) sessionStore.clear()
            }
            return@run
        }
        connection = remote
        try {
            val restored = remote.restoreFromStore()
            currentCoroutineContext().ensureActive()
            if (generation == operation && connection === remote) accept(restored)
        }
        catch (error: RemoteFailure) {
            currentCoroutineContext().ensureActive()
            if (generation != operation || connection !== remote) return@run
            if (error.status != 401) throw error
            remote.close(); connection = null
            mutable.value = mutable.value.copy(error = "Session expired. Sign in again.")
        }
        catch (error: LocalFailure) {
            if (generation == operation && connection === remote) { remote.close(); connection = null }
            throw error
        }
    }
}


fun ApplicationController.connect(origin: String, username: String, password: String) {
    pendingIptv = null
    discardStremioImport()
    cancelDevicePairing()
    libraryImportRefresh?.cancel()
    val finishing = if (completionSeen) progressJob else saveProgress(false, final = true)
    val stopping = releaseRemotePlayback()
    val priorRemote = connection
    val priorLocal = local
    connection = null; local = null
    run {
        finishing?.join(); stopping?.join(); priorRemote?.retire(); priorLocal?.close()
        val remote = remoteFactory(origin, sessionStore)
        connection = remote
        val bootstrap = if (username.isBlank()) remote.bootstrap() else remote.login(username, password)
        try { withNativeSessionStoreLock {
            if (sessionStore.savedConnection() == SavedConnection.Local) sessionStore.clear()
        } }
        catch (error: CancellationException) { throw error }
        catch (_: Exception) { /* A usable in-memory remote session may outlive unavailable secure storage. */ }
        accept(bootstrap)
    }
}

fun ApplicationController.useLocal(path: String) = startLocal(path)

internal fun ApplicationController.startLocal(path: String, iptv: IptvCredentials? = null) {
    val priorRemote = connection
    disconnect()
    pendingIptv = iptv
    run {
        priorRemote?.retire()
        try { withNativeSessionStoreLock { sessionStore.clear() } }
        catch (error: CancellationException) { throw error }
        catch (_: Exception) { /* Local mode remains available when the OS vault is unavailable. */ }
        val bootstrap = openLocalConnection(path)
        // Publishing Profiles clears busy. Finish the connection choice first,
        // otherwise a quick profile selection can cancel its pending vault write.
        try { withNativeSessionStoreLock { sessionStore.rememberLocalConnection() } }
        catch (error: CancellationException) { throw error }
        catch (_: Exception) { localPersistenceWarning = "This connection could not be remembered. Choose local mode again after restarting." }
        val only = bootstrap.profiles.singleOrNull()?.takeUnless { it.locked }
        if (iptv != null && only != null) openProfile(only.id) else accept(bootstrap)
    }
}

/** A remembered local connection still opens Profiles, including its existing PIN gate. */
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
        withNativeSessionStoreLock {
            if (sessionStore.savedConnection() == SavedConnection.Local) sessionStore.clear()
        }
        disconnect()
    }
}

fun ApplicationController.disconnect() {
    pendingIptv = null
    discardStremioImport()
    cancelDevicePairing()
    libraryImportRefresh?.cancel()
    libraryHydration?.cancel(); resetLiveRequests()
    cancelDetailPaging()
    val stopping = releaseRemotePlayback()
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
    pendingIptv = null
    discardStremioImport()
    libraryImportRefresh?.cancel()
    if (!completionSeen) saveProgress(false, final = true)?.join() else progressJob?.join()
    releaseRemotePlayback()?.join()
    val remote = connection
    val device = local
    remote?.signOut()
    // Revocation has completed even when navigation cancelled the caller while
    // it was in flight. Retire only this session; a replacement keeps its state.
    remote?.close()
    if (connection === remote && local === device) {
        resetLiveRequests()
        connection = null
        device?.close(); local = null
        mutable.value = ApplicationState()
    }
}

internal fun ApplicationController.accept(value: Bootstrap, screen: Screen? = null, busy: Boolean = false) {
    libraryHydration?.cancel()
    resetLiveRequests()
    mutable.value = ApplicationState(screen = screen ?: if (value.selected == null) Screen.Profiles else Screen.Home, busy = busy,
        profiles = value.profiles, profileManagement = value.profileManagement, selectedProfile = value.selected, home = value.home,
        profileNotice = if (pendingIptv != null) "Choose a profile for this provider. Details stay on this device." else null,
        localMode = local != null, sessionNotice = connection?.persistenceWarning,
        plugins = value.plugins, addons = value.addons, enabledAddonIds = value.enabledAddonIds, preferences = value.preferences)
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
        catch (_: Exception) { /* The library route offers an explicit retry; Home remains usable. */ }
    }
}
