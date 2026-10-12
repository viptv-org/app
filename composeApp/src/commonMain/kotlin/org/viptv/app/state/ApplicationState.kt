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

enum class Screen { Connect, Profiles, Home, Search, Details, Sources, Player, Live, Library, History, Settings }
data class ApplicationState(
    val screen: Screen = Screen.Connect,
    val busy: Boolean = false,
    val error: String? = null,
    val profiles: List<Profile> = emptyList(),
    val selectedProfile: String? = null,
    val home: HomeSnapshot? = null,
    val results: List<Card> = emptyList(),
    val detail: DetailSnapshot? = null,
    val localMode: Boolean = false,
    val addons: List<IntegrationConnection> = emptyList(),
    val enabledAddonIds: Set<String> = emptySet(),
    val sourceTitle: Card? = null,
    val sources: List<SourceCandidate> = emptyList(),
    val playback: PlaybackInput? = null,
    val paused: Boolean = false,
)

class ApplicationController(private val scope: CoroutineScope) {
    private val mutable = MutableStateFlow(ApplicationState())
    val state = mutable.asStateFlow()
    private var connection: RemoteConnection? = null
    private var local: LocalConnection? = null
    private var job: Job? = null
    private var generation = 0L

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
        run { local = withContext(Dispatchers.Default) { localConnection(path) }; accept(requireNotNull(local).bootstrap()) }
    }
    fun selectProfile(id: String) = run { accept(local?.bootstrap(id) ?: requireNotNull(connection).selectProfile(id)) }
    fun refresh() = run { accept(local?.bootstrap(mutable.value.selectedProfile) ?: requireNotNull(connection).bootstrap()) }
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
    }
    fun toggleAddon(id: String, enabled: Boolean) = run {
        val profile=requireNotNull(mutable.value.selectedProfile)
        requireNotNull(local).setAddonEnabled(profile,id,enabled)
        mutable.value=mutable.value.copy(enabledAddonIds=requireNotNull(local).enabledAddons(profile))
        val updated=requireNotNull(local).bootstrap(profile)
        mutable.value=mutable.value.copy(home=updated.home)
    }
    fun removeAddon(id: String) = run {
        requireNotNull(local).removeAddon(id)
        val profile=requireNotNull(mutable.value.selectedProfile)
        mutable.value=mutable.value.copy(addons=requireNotNull(local).addons(),enabledAddonIds=requireNotNull(local).enabledAddons(profile))
        val updated=requireNotNull(local).bootstrap(profile)
        mutable.value=mutable.value.copy(home=updated.home)
    }
    fun search(term: String) = run {
        delay(300)
        val cards = if (term.isBlank()) emptyList() else local?.search(requireNotNull(mutable.value.selectedProfile), term)?.cards ?: requireNotNull(connection).search(term).cards
        mutable.value = mutable.value.copy(results = cards)
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
        mutable.value = mutable.value.copy(playback = input, paused = false, screen = Screen.Player)
    }
    fun pausePlayback() { mutable.value = mutable.value.copy(paused = !mutable.value.paused) }
    fun observePlayback(fact: PlayerFact) {
        if (fact is PlayerFact.Failed) mutable.value = mutable.value.copy(error = "This source could not play. Choose another source.")
    }
    fun closePlayer() { mutable.value = mutable.value.copy(playback = null, screen = Screen.Sources, error = null) }
    fun navigate(screen: Screen) {
        mutable.value = mutable.value.copy(screen = screen, error = null)
        if (screen == Screen.Settings && local != null) refreshAddons()
    }
    fun disconnect() {
        generation++; job?.cancel(); connection?.close(); connection = null; local?.close(); local = null
        mutable.value = ApplicationState()
    }
    private fun accept(value: Bootstrap) {
        mutable.value = ApplicationState(screen = if (value.selected == null) Screen.Profiles else Screen.Home,
            profiles = value.profiles, selectedProfile = value.selected, home = value.home,
            localMode = local != null, addons = value.addons, enabledAddonIds = value.enabledAddonIds)
    }
    private fun run(operation: suspend () -> Unit) {
        job?.cancel()
        val current = ++generation
        mutable.value = mutable.value.copy(busy = true, error = null)
        job = scope.launch {
            try { operation() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (generation == current) mutable.value = mutable.value.copy(error = when (e) {
                    is RemoteFailure -> e.message
                    is LocalFailure -> e.message
                    is IllegalArgumentException -> "Enter a valid HTTPS server address."
                    else -> if (local != null) "Could not complete this action on this device." else "Could not reach your server. Check the connection and try again."
                })
            } finally { if (generation == current) mutable.value = mutable.value.copy(busy = false) }
        }
    }
}
