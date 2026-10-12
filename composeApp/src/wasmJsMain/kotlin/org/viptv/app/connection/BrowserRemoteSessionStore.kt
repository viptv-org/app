package org.viptv.app.connection

private object BrowserRemoteSessionStore : RemoteSessionStore {
    override suspend fun read(): SavedRemoteSession? = null
    override suspend fun save(session: SavedRemoteSession) = Unit
    override suspend fun clear() = Unit
}

actual fun remoteSessionStore(): RemoteSessionStore = BrowserRemoteSessionStore
