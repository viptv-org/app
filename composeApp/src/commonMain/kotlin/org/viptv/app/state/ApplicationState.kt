package org.viptv.app.state

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.viptv.app.connection.*
import org.viptv.engine.wire.HomeSnapshot
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.DetailSnapshot

enum class Screen { Connect, Profiles, Home, Search, Details, Live, Library, History, Settings }
data class ApplicationState(
    val screen: Screen = Screen.Connect,
    val busy: Boolean = false,
    val error: String? = null,
    val profiles: List<Profile> = emptyList(),
    val selectedProfile: String? = null,
    val home: HomeSnapshot? = null,
    val results: List<Card> = emptyList(),
    val detail: DetailSnapshot? = null,
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
    fun search(term: String) = run {
        delay(300)
        val cards = if (term.isBlank()) emptyList() else requireNotNull(connection).search(term).cards
        mutable.value = mutable.value.copy(results = cards)
    }
    fun open(card: Card) {
        mutable.value = mutable.value.copy(screen = Screen.Details, detail = null)
        run { mutable.value = mutable.value.copy(detail = requireNotNull(connection).detail(card)) }
    }
    fun navigate(screen: Screen) { mutable.value = mutable.value.copy(screen = screen, error = null) }
    fun disconnect() {
        generation++; job?.cancel(); connection?.close(); connection = null; local?.close(); local = null
        mutable.value = ApplicationState()
    }
    private fun accept(value: Bootstrap) {
        mutable.value = ApplicationState(screen = if (value.selected == null) Screen.Profiles else Screen.Home,
            profiles = value.profiles, selectedProfile = value.selected, home = value.home)
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
                    is IllegalArgumentException -> "Enter a valid HTTPS server address."
                    else -> "Could not reach your server. Check the connection and try again."
                })
            } finally { if (generation == current) mutable.value = mutable.value.copy(busy = false) }
        }
    }
}
