package org.viptv.app.connection

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import uniffi.viptv_core.SecretStore

private const val SESSION_KEY = "remote-session-v1"
private const val LOCAL_CONNECTION = "local-mode-v1"
private const val NAVIGATION_KEY = "navigation-v1"
private const val RECENT_SEARCHES_KEY = "recent-searches-v1"
private val vaultCodec = Json { ignoreUnknownKeys = true; encodeDefaults = true }

internal class NativeRemoteSessionStore(private val remembersNavigation: Boolean = false,
    vaultFactory: () -> SecretStore = ::platformSecrets) : RemoteSessionStore {
    private val vault by lazy(vaultFactory)
    private val navigationGate = Mutex()

    override suspend fun read(): SavedRemoteSession? = (savedConnection() as? SavedConnection.Remote)?.session

    override suspend fun savedConnection(): SavedConnection = withContext(Dispatchers.IO) {
        when (val value = vault.get(SESSION_KEY)) {
            null -> SavedConnection.None
            LOCAL_CONNECTION -> SavedConnection.Local
            else -> SavedConnection.Remote(Json.decodeFromString<SavedRemoteSession>(value))
        }
    }

    // One vault record selects the connection. Remote rotation/login atomically
    // replaces the marker; old remote JSON remains readable without migration.
    override suspend fun rememberLocalConnection() = withContext(Dispatchers.IO) { vault.put(SESSION_KEY, LOCAL_CONNECTION) }

    override suspend fun save(session: SavedRemoteSession) = withContext(Dispatchers.IO) {
        vault.put(SESSION_KEY, Json.encodeToString(session))
    }

    override suspend fun clear() = withContext(Dispatchers.IO) {
        vault.remove(SESSION_KEY)
        if (remembersNavigation) vault.remove(NAVIGATION_KEY)
        vault.remove(RECENT_SEARCHES_KEY)
    }

    override suspend fun navigation(profile: String): SavedNavigation? {
        if (!remembersNavigation) return null
        return withContext(Dispatchers.IO) { navigationGate.withLock { readNavigationBook()[profile] } }
    }

    override suspend fun saveNavigation(profile: String, record: SavedNavigation) {
        if (!remembersNavigation) return
        withContext(Dispatchers.IO) {
            navigationGate.withLock {
                val profiles = readNavigationBook().toMutableMap()
                if (profiles[profile] == record) return@withLock
                profiles[profile] = record
                vault.put(NAVIGATION_KEY, vaultCodec.encodeToString(SavedNavigationBook(profiles)))
            }
        }
    }

    override suspend fun recentSearches(profile: String): List<String> = withContext(Dispatchers.IO) {
        navigationGate.withLock { readRecentSearchesBook()[profile].orEmpty() }
    }

    override suspend fun saveRecentSearches(profile: String, recent: List<String>) = withContext(Dispatchers.IO) {
        navigationGate.withLock {
            val profiles = readRecentSearchesBook().toMutableMap()
            if (profiles[profile] == recent) return@withLock
            profiles[profile] = recent
            vault.put(RECENT_SEARCHES_KEY, vaultCodec.encodeToString(SavedRecentSearchesBook(profiles)))
        }
    }

    // A newer build may add fields, and a damaged record must not block the next screen.
    private fun readNavigationBook(): Map<String, SavedNavigation> = try {
        vault.get(NAVIGATION_KEY)
            ?.let { vaultCodec.decodeFromString<SavedNavigationBook>(it).profiles }
            .orEmpty()
    } catch (_: Exception) { emptyMap() }

    private fun readRecentSearchesBook(): Map<String, List<String>> = try {
        vault.get(RECENT_SEARCHES_KEY)
            ?.let { vaultCodec.decodeFromString<SavedRecentSearchesBook>(it).profiles }
            .orEmpty()
    } catch (_: Exception) { emptyMap() }
}
