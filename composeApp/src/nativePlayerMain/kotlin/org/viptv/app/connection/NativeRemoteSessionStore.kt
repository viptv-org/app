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
private val navigationCodec = Json { ignoreUnknownKeys = true; encodeDefaults = true }

internal class NativeRemoteSessionStore(vaultFactory: () -> SecretStore = ::platformSecrets) : RemoteSessionStore {
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
        vault.remove(NAVIGATION_KEY)
    }

    override suspend fun navigation(profile: String): SavedNavigation? = withContext(Dispatchers.IO) {
        navigationGate.withLock { readNavigationBook()[profile] }
    }

    override suspend fun saveNavigation(profile: String, record: SavedNavigation) = withContext(Dispatchers.IO) {
        navigationGate.withLock {
            val profiles = readNavigationBook().toMutableMap()
            if (profiles[profile] == record) return@withLock
            profiles[profile] = record
            vault.put(NAVIGATION_KEY, navigationCodec.encodeToString(SavedNavigationBook(profiles)))
        }
    }

    // A newer build may add fields, and a damaged record must not block the next screen.
    private fun readNavigationBook(): Map<String, SavedNavigation> = try {
        vault.get(NAVIGATION_KEY)
            ?.let { navigationCodec.decodeFromString<SavedNavigationBook>(it).profiles }
            .orEmpty()
    } catch (_: Exception) { emptyMap() }
}

actual fun remoteSessionStore(): RemoteSessionStore = NativeRemoteSessionStore()
