package org.viptv.app.state

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.viptv.app.connection.*
import org.viptv.engine.wire.HomeSnapshot
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.DetailSnapshot
import org.viptv.engine.wire.IntegrationConnection
import org.viptv.engine.wire.SourceCandidate
import org.viptv.app.player.PlaybackInput
import org.viptv.app.player.PlayerFact
import org.viptv.engine.wire.HistoryEntry
import org.viptv.app.connection.HistoryDisplay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.TimeSource
import kotlin.time.Duration.Companion.seconds

enum class Screen { Connect, Profiles, ProfilePin, ProfileEdit, Home, Discover, Movies, Series, Calendar, Search, Details, Sources, Player, Live, Library, History, Settings }
data class ApplicationState(
    val screen: Screen = Screen.Connect,
    val busy: Boolean = false,
    val error: String? = null,
    val profiles: List<Profile> = emptyList(),
    val profileToOpen: String? = null,
    val profileToEdit: String? = null,
    val managingProfiles: Boolean = false,
    val selectedProfile: String? = null,
    val home: HomeSnapshot? = null,
    val results: List<Card> = emptyList(),
    val searchTerm: String = "",
    val detail: DetailSnapshot? = null,
    val localMode: Boolean = false,
    val sessionNotice: String? = null,
    val addons: List<IntegrationConnection> = emptyList(),
    val enabledAddonIds: Set<String> = emptySet(),
    val sourceTitle: Card? = null,
    val sources: List<SourceCandidate> = emptyList(),
    val playback: PlaybackInput? = null,
    val paused: Boolean = false,
    val history: List<HistoryDisplay> = emptyList(),
)

class ApplicationController(private val scope: CoroutineScope) {
    private val mutable = MutableStateFlow(ApplicationState())
    val state = mutable.asStateFlow()
    private var connection: RemoteConnection? = null
    private var local: LocalConnection? = null
    private var job: Job? = null
    private var generation = 0L
    private val progressGate = Mutex()
    private var progressJob: Job? = null
    private var lastProgress: PlayerFact.Progress? = null
    private var completionSeen = false
    private var progressSavedAt = TimeSource.Monotonic.markNow()

    fun restore() = run {
        val origin = browserOrigin()
        if (origin != null) {
            val remote = RemoteConnection(origin)
            connection = remote
            val restored = remote.restoreBrowser()
            if (restored == null) { remote.close(); connection = null }
            else accept(restored)
        } else {
            val saved = try { remoteSessionStore().read() }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) { null }
            if (saved == null) return@run
            val remote = try { RemoteConnection(saved.origin) }
            catch (_: IllegalArgumentException) { remoteSessionStore().clear(); return@run }
            connection = remote
            try { accept(remote.restore(saved)) }
            catch (error: RemoteFailure) {
                if (error.status != 401) throw error
                remote.close(); connection = null
                mutable.value = mutable.value.copy(error = "Session expired. Sign in again.")
            }
        }
    }

    fun connect(origin: String, username: String, password: String) {
        job?.cancel(); connection?.close(); connection = null; local?.close(); local = null
        run {
            val remote = RemoteConnection(origin)
            connection = remote
            accept(if (username.isBlank()) remote.bootstrap() else remote.login(username, password))
        }
    }
    fun useLocal(path: String) {
        disconnect()
        run {
            try { remoteSessionStore().clear() }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) { /* Local mode remains available when the OS vault is unavailable. */ }
            local = withContext(Dispatchers.Default) { localConnection(path) }
            accept(requireNotNull(local).bootstrap())
        }
    }
    fun selectProfile(id: String) {
        if (local != null && mutable.value.profiles.any { it.id == id && it.locked }) {
            mutable.value = mutable.value.copy(screen = Screen.ProfilePin, profileToOpen = id, error = null)
            return
        }
        run { openProfile(id) }
    }
    fun submitProfilePin(pin: String) = run {
        val id = mutable.value.profileToOpen ?: throw LocalFailure("Choose a profile first.")
        if (!requireNotNull(local).verifyProfilePin(id, pin)) throw LocalFailure("Incorrect PIN or too many attempts. Try again later.")
        openProfile(id)
    }
    private suspend fun openProfile(id: String) {
        progressJob?.join()
        val result=local?.bootstrap(id) ?: requireNotNull(connection).selectProfile(id)
        accept(result)
        local?.let { mutable.value=mutable.value.copy(home=it.refreshHome(id)) }
    }
    fun manageProfiles() { mutable.value=mutable.value.copy(managingProfiles=true,error=null) }
    fun stopManagingProfiles() { mutable.value=mutable.value.copy(managingProfiles=false,error=null) }
    fun editProfile(id: String) { mutable.value=mutable.value.copy(screen=Screen.ProfileEdit,profileToEdit=id,error=null) }
    fun createProfile(name: String) = run {
        requireNotNull(local).createProfile(name)
        val next=requireNotNull(local).bootstrap()
        mutable.value=mutable.value.copy(profiles=next.profiles,error=null)
    }
    fun renameProfile(name: String, pin: String) = run {
        val id=mutable.value.profileToEdit ?: throw LocalFailure("Choose a profile first.")
        requireNotNull(local).renameProfile(id,name,pin)
        val next=requireNotNull(local).bootstrap()
        mutable.value=mutable.value.copy(profiles=next.profiles,screen=Screen.Profiles,managingProfiles=true)
    }
    fun setProfileAvatar(avatarId: String, pin: String) = run {
        val id=mutable.value.profileToEdit ?: throw LocalFailure("Choose a profile first.")
        requireNotNull(local).setProfileAvatar(id,avatarId,pin)
        val next=requireNotNull(local).bootstrap()
        mutable.value=mutable.value.copy(profiles=next.profiles)
    }
    fun deleteProfile(pin: String) = run {
        val id=mutable.value.profileToEdit ?: throw LocalFailure("Choose a profile first.")
        requireNotNull(local).deleteProfile(id,pin)
        val next=requireNotNull(local).bootstrap()
        mutable.value=mutable.value.copy(profiles=next.profiles,screen=Screen.Profiles,managingProfiles=true,
            selectedProfile=mutable.value.selectedProfile.takeUnless { it==id })
    }
    fun setProfilePin(oldPin: String, newPin: String) = run {
        val id=mutable.value.profileToEdit ?: throw LocalFailure("Choose a profile first.")
        requireNotNull(local).setProfilePin(id,oldPin,newPin)
        val next=requireNotNull(local).bootstrap()
        mutable.value=mutable.value.copy(profiles=next.profiles,screen=Screen.Profiles,managingProfiles=true)
    }
    fun refresh() = run {
        progressJob?.join()
        val profile=mutable.value.selectedProfile
        val result=local?.bootstrap(profile) ?: requireNotNull(connection).bootstrap()
        accept(result)
        if(profile!=null) local?.let { mutable.value=mutable.value.copy(home=it.refreshHome(profile)) }
    }
    fun refreshAddons() = run {
        val selected=mutable.value.selectedProfile
        mutable.value = mutable.value.copy(addons = local?.addons() ?: emptyList(), enabledAddonIds = if (selected != null) local?.enabledAddons(selected).orEmpty() else emptySet())
    }
    fun installAddon(url: String) = run {
        val profile = mutable.value.selectedProfile ?: throw LocalFailure("Choose a profile first.")
        val addons = requireNotNull(local).installAddon(profile, url)
        mutable.value=mutable.value.copy(addons=addons,enabledAddonIds=requireNotNull(local).enabledAddons(profile))
        val updated = requireNotNull(local).bootstrap(profile)
        mutable.value = mutable.value.copy(addons = addons, home = updated.home, screen = Screen.Settings)
        mutable.value=mutable.value.copy(home=requireNotNull(local).refreshHome(profile))
    }
    fun toggleAddon(id: String, enabled: Boolean) = run {
        val profile=requireNotNull(mutable.value.selectedProfile)
        requireNotNull(local).setAddonEnabled(profile,id,enabled)
        mutable.value=mutable.value.copy(enabledAddonIds=requireNotNull(local).enabledAddons(profile))
        val updated=requireNotNull(local).bootstrap(profile)
        mutable.value=mutable.value.copy(home=updated.home)
        mutable.value=mutable.value.copy(home=requireNotNull(local).refreshHome(profile))
    }
    fun removeAddon(id: String) = run {
        requireNotNull(local).removeAddon(id)
        val profile=requireNotNull(mutable.value.selectedProfile)
        mutable.value=mutable.value.copy(addons=requireNotNull(local).addons(),enabledAddonIds=requireNotNull(local).enabledAddons(profile))
        val updated=requireNotNull(local).bootstrap(profile)
        mutable.value=mutable.value.copy(home=updated.home)
        mutable.value=mutable.value.copy(home=requireNotNull(local).refreshHome(profile))
    }
    fun search(term: String) {
        mutable.value = mutable.value.copy(searchTerm = term)
        run {
        delay(300)
        val cards = if (term.isBlank()) emptyList() else local?.search(requireNotNull(mutable.value.selectedProfile), term)?.cards ?: requireNotNull(connection).search(term).cards
        mutable.value = mutable.value.copy(results = cards)
        }
    }
    fun open(card: Card) {
        mutable.value = mutable.value.copy(screen = Screen.Details, detail = null)
        run { mutable.value = mutable.value.copy(detail = local?.detail(requireNotNull(mutable.value.selectedProfile), card) ?: requireNotNull(connection).detail(card)) }
    }
    fun chooseSource(card: Card) {
        mutable.value = mutable.value.copy(screen = Screen.Sources, sourceTitle = card, sources = emptyList())
        run { mutable.value = mutable.value.copy(sources = requireNotNull(local).sources(requireNotNull(mutable.value.selectedProfile), card)) }
    }
    fun playSource(source: SourceCandidate) = run {
        val input=requireNotNull(local).openSource(source.id)
        lastProgress=null
        completionSeen=false
        progressSavedAt=TimeSource.Monotonic.markNow()
        mutable.value = mutable.value.copy(playback = input, paused = false, screen = Screen.Player)
    }
    fun pausePlayback() {
        val paused=!mutable.value.paused
        if (!paused) completionSeen = false
        mutable.value = mutable.value.copy(paused = paused)
        if(paused && !completionSeen) saveProgress(false)
    }
    fun observePlayback(fact: PlayerFact) {
        if (fact is PlayerFact.Progress) {
            lastProgress=fact
            if (fact.positionMillis>0 && progressSavedAt.elapsedNow()>=15.seconds) saveProgress(false)
        }
        if (fact is PlayerFact.Status && fact.name in listOf("Playing", "Paused") && mutable.value.playback != null) {
            mutable.value = mutable.value.copy(paused = fact.name == "Paused")
        }
        if (fact is PlayerFact.Status && fact.name=="Ended" && !completionSeen) {
            completionSeen=true
            mutable.value = mutable.value.copy(paused = true)
            saveProgress(true)
        }
        if (fact is PlayerFact.Failed) mutable.value = mutable.value.copy(error = "This source could not play. Choose another source.")
    }
    fun closePlayer() {
        if(!completionSeen) saveProgress(false)
        mutable.value = mutable.value.copy(playback = null, screen = Screen.Sources, error = null)
        lastProgress=null
    }
    fun clearHistory() = run {
        val profile=requireNotNull(mutable.value.selectedProfile)
        requireNotNull(local).clearHistory(profile)
        mutable.value=mutable.value.copy(history=emptyList())
        val updated=requireNotNull(local).bootstrap(profile)
        mutable.value=mutable.value.copy(home=updated.home)
    }
    private fun saveProgress(completed: Boolean): Job? {
        val input=mutable.value.playback ?: return null
        val title=mutable.value.sourceTitle ?: return null
        val snapshot=lastProgress ?: return null
        val store=local ?: return null
        val position=if(completed && snapshot.durationMillis!=null) snapshot.durationMillis else snapshot.positionMillis
        if(position<=0)return null
        progressSavedAt=TimeSource.Monotonic.markNow()
        return scope.launch {
            progressGate.withLock {
                try { store.recordProgress(input,title,position,snapshot.durationMillis) }
                catch (_: Exception) {
                    if(mutable.value.playback?.id==input.id) mutable.value=mutable.value.copy(error="Could not save your viewing position.")
                }
            }
        }.also { progressJob=it }
    }
    fun navigate(screen: Screen) {
        mutable.value = mutable.value.copy(screen = screen, error = null)
        if (screen == Screen.Settings && local != null) refreshAddons()
        if (screen == Screen.History && local != null) run {
            progressJob?.join()
            mutable.value=mutable.value.copy(history=requireNotNull(local).history(requireNotNull(mutable.value.selectedProfile)))
        }
    }
    fun disconnect() {
        val prior=local
        val finishing=if(completionSeen) progressJob else saveProgress(false)
        generation++; job?.cancel(); connection?.close(); connection = null; local = null
        if(finishing!=null && prior!=null) scope.launch { finishing.join(); prior.close() } else prior?.close()
        mutable.value = ApplicationState()
    }
    fun signOut() = run {
        connection?.signOut()
        connection?.close(); connection = null
        local?.close(); local = null
        mutable.value = ApplicationState()
    }
    private fun accept(value: Bootstrap) {
        mutable.value = ApplicationState(screen = if (value.selected == null) Screen.Profiles else Screen.Home,
            profiles = value.profiles, selectedProfile = value.selected, home = value.home,
            localMode = local != null, sessionNotice = connection?.persistenceWarning,
            addons = value.addons, enabledAddonIds = value.enabledAddonIds)
    }
    private fun run(operation: suspend () -> Unit) {
        job?.cancel()
        val current = ++generation
        mutable.value = mutable.value.copy(busy = true, error = null)
        job = scope.launch {
            try { operation() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (generation == current && e is RemoteFailure && e.status == 401 && local == null) {
                    connection?.close(); connection = null
                    mutable.value = ApplicationState(error = "Session expired. Sign in again.")
                } else if (generation == current) mutable.value = mutable.value.copy(error = when (e) {
                    is RemoteFailure -> e.message
                    is LocalFailure -> e.message
                    is IllegalArgumentException -> "Enter a valid HTTPS server address."
                    else -> if (local != null) "Could not complete this action on this device." else "Could not reach your server. Check the connection and try again."
                })
            } finally { if (generation == current) mutable.value = mutable.value.copy(busy = false, sessionNotice = connection?.persistenceWarning) }
        }
    }
}
