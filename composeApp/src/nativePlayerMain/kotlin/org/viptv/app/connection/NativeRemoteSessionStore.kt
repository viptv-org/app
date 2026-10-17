package org.viptv.app.connection

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import uniffi.viptv_core.SecretStore

private const val SESSION_KEY = "remote-session-v1"
private const val LOCAL_CONNECTION = "local-mode-v1"

internal class NativeRemoteSessionStore(vaultFactory: () -> SecretStore = ::platformSecrets) : RemoteSessionStore {
    private val vault by lazy(vaultFactory)

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

    override suspend fun clear() = withContext(Dispatchers.IO) { vault.remove(SESSION_KEY) }
}

actual fun remoteSessionStore(): RemoteSessionStore = NativeRemoteSessionStore()
