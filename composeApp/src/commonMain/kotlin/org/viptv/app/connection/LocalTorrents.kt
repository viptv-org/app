package org.viptv.app.connection

import kotlinx.serialization.json.JsonObject

internal expect fun localTorrents(): LocalTorrents?

internal interface LocalTorrents {
    val capability: JsonObject

    suspend fun negotiate(remote: RemoteConnection): Boolean

    suspend fun acquire(remote: RemoteConnection, id: String, request: JsonObject, stage: (String) -> Unit = {}): LocalTorrentPlayback
}

internal interface LocalTorrentPlayback {
    val mediaUrl: String
    val positionSeconds: Double
    suspend fun renew()
    suspend fun firstFrame()
    suspend fun close()
}
