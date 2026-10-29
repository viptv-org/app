package org.viptv.app.connection

import kotlinx.serialization.Serializable

@Serializable
data class SavedRemoteSession(val origin: String, val refreshToken: String) {
    override fun toString(): String = "SavedRemoteSession(origin=$origin, refreshToken=<redacted>)"
}

sealed interface SavedConnection {
    data class Remote(val session: SavedRemoteSession) : SavedConnection
    data object Local : SavedConnection
    data object None : SavedConnection
}

interface RemoteSessionStore {
    suspend fun read(): SavedRemoteSession?
    suspend fun save(session: SavedRemoteSession)
    suspend fun clear()
    suspend fun savedConnection(): SavedConnection = read()?.let { SavedConnection.Remote(it) } ?: SavedConnection.None
    suspend fun rememberLocalConnection() = Unit
    suspend fun navigation(profile: String): SavedNavigation? = null
    suspend fun saveNavigation(profile: String, record: SavedNavigation) = Unit
}

expect fun remoteSessionStore(): RemoteSessionStore
