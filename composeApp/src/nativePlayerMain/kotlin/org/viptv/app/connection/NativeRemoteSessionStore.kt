package org.viptv.app.connection

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val SESSION_KEY = "remote-session-v1"

private class NativeRemoteSessionStore : RemoteSessionStore {
    private val vault by lazy { platformSecrets() }

    override suspend fun read(): SavedRemoteSession? = withContext(Dispatchers.IO) {
        vault.get(SESSION_KEY)?.let { Json.decodeFromString<SavedRemoteSession>(it) }
    }

    override suspend fun save(session: SavedRemoteSession) = withContext(Dispatchers.IO) {
        vault.put(SESSION_KEY, Json.encodeToString(session))
    }

    override suspend fun clear() = withContext(Dispatchers.IO) { vault.remove(SESSION_KEY) }
}

actual fun remoteSessionStore(): RemoteSessionStore = NativeRemoteSessionStore()
