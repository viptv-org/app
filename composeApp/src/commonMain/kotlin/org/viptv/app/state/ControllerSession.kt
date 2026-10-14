package org.viptv.app.state

import kotlinx.coroutines.*
import org.viptv.app.connection.*

fun ApplicationController.restore() = run {
    val origin = browserOrigin()
    if (origin != null) {
        val remote = RemoteConnection(origin, sessionStore = sessionStore)
        connection = remote
        val restored = remote.restoreBrowser()
        if (restored == null) { remote.close(); connection = null }
        else accept(restored)
    } else {
        val saved = try { sessionStore.read() }
        catch (error: CancellationException) { throw error }
        catch (_: Exception) { null }
        if (saved == null) return@run
        val remote = try { RemoteConnection(saved.origin, sessionStore = sessionStore) }
        catch (_: IllegalArgumentException) { sessionStore.clear(); return@run }
        connection = remote
        try { accept(remote.restore(saved)) }
        catch (error: RemoteFailure) {
            if (error.status != 401) throw error
            remote.close(); connection = null
            mutable.value = mutable.value.copy(error = "Session expired. Sign in again.")
        }
    }
}


fun ApplicationController.connect(origin: String, username: String, password: String) {
    pendingIptv = null
    cancelDevicePairing()
    libraryImportRefresh?.cancel()
    val finishing = if (completionSeen) progressJob else saveProgress(false, final = true)
    val stopping = releaseRemotePlayback()
    val priorRemote = connection
    val priorLocal = local
    connection = null; local = null
    run {
        finishing?.join(); stopping?.join(); priorRemote?.close(); priorLocal?.close()
        val remote = RemoteConnection(origin, sessionStore = sessionStore)
        connection = remote
        accept(if (username.isBlank()) remote.bootstrap() else remote.login(username, password))
    }
}

fun ApplicationController.useLocal(path: String) = startLocal(path)

internal fun ApplicationController.startLocal(path: String, iptv: IptvCredentials? = null) {
    disconnect()
    pendingIptv = iptv
    run {
        try { sessionStore.clear() }
        catch (error: CancellationException) { throw error }
        catch (_: Exception) { /* Local mode remains available when the OS vault is unavailable. */ }
        var created: LocalConnection? = null
        try {
            local = withContext(Dispatchers.Default) { localFactory(path).also { created = it } }
            created = null
        } finally { created?.close() }
        val bootstrap = requireNotNull(local).bootstrap()
        val only = bootstrap.profiles.singleOrNull()?.takeUnless { it.locked }
        if (iptv != null && only != null) openProfile(only.id) else accept(bootstrap)
    }
}

fun ApplicationController.disconnect() {
    pendingIptv = null
    cancelDevicePairing()
    libraryImportRefresh?.cancel()
    cancelDetailPaging()
    val stopping = releaseRemotePlayback()
    val priorRemote = connection
    val prior=local
    val finishing=if(completionSeen) progressJob else saveProgress(false, final = true)
    val priorJob = job
    generation++; priorJob?.cancel(); connection = null; local = null
    scope.launch(start = CoroutineStart.UNDISPATCHED) { withContext(NonCancellable) {
        withTimeoutOrNull(6_000) { priorJob?.join(); finishing?.join(); stopping?.join() }
        priorRemote?.close()
    } }
    if(finishing!=null && prior!=null) scope.launch(start = CoroutineStart.UNDISPATCHED) { withContext(NonCancellable) { finishing.join(); prior.close() } } else prior?.close()
    mutable.value = ApplicationState()
}

fun ApplicationController.signOut() = run {
    pendingIptv = null
    libraryImportRefresh?.cancel()
    if (!completionSeen) saveProgress(false, final = true)?.join() else progressJob?.join()
    releaseRemotePlayback()?.join()
    connection?.signOut()
    connection?.close(); connection = null
    local?.close(); local = null
    mutable.value = ApplicationState()
}

internal fun ApplicationController.accept(value: Bootstrap, screen: Screen? = null, busy: Boolean = false) {
    libraryHydration?.cancel()
    liveGuideJob?.cancel()
    mutable.value = ApplicationState(screen = screen ?: if (value.selected == null) Screen.Profiles else Screen.Home, busy = busy,
        profiles = value.profiles, profileManagement = value.profileManagement, selectedProfile = value.selected, home = value.home,
        profileNotice = if (pendingIptv != null) "Choose a profile for this provider. Details stay on this device." else null,
        localMode = local != null, sessionNotice = connection?.persistenceWarning,
        plugins = value.plugins, addons = value.addons, enabledAddonIds = value.enabledAddonIds, preferences = value.preferences)
    value.library?.let { updateLibrary(it, value.libraryMembership?.identities ?: it.map { card -> card.identity }) }
    startLibraryImportRefresh(value.libraryImport)
    if (value.library != null) return
    val profile = value.selected ?: return
    val remote = connection
    val device = local
    libraryHydration = scope.launch {
        try {
            val cards = device?.library(profile) ?: remote?.library(profile) ?: return@launch
            val identities = remote?.libraryMembership(profile)?.identities ?: cards.map { it.identity }
            if (mutable.value.selectedProfile == profile && connection === remote && local === device)
                updateLibrary(cards, identities)
        } catch (error: CancellationException) { throw error }
        catch (_: Exception) { /* The library route offers an explicit retry; Home remains usable. */ }
    }
}
