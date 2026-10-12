package org.viptv.app.connection

import kotlinx.serialization.Serializable

@Serializable
data class SavedRemoteSession(val origin: String, val refreshToken: String) {
    override fun toString(): String = "SavedRemoteSession(origin=$origin, refreshToken=<redacted>)"
}

interface RemoteSessionStore {
    suspend fun read(): SavedRemoteSession?
    suspend fun save(session: SavedRemoteSession)
    suspend fun clear()
}

expect fun remoteSessionStore(): RemoteSessionStore
