package org.viptv.app.connection

import kotlinx.serialization.json.JsonObject

/**
 * This device's own torrent transport: the shared torrent runtime behind native
 * protocol v2 (design SRC-TORRENT-RUNTIME-002). Android phone and TV acquire a
 * granted torrent locally and play its loopback media URL; they never fall back
 * to a server gateway. Null where no runtime is bundled (browser, iOS, desktop).
 */
internal expect fun localTorrents(): LocalTorrents?

internal interface LocalTorrents {
    /** The `client.native_torrent` capability this runtime advertises. */
    val capability: JsonObject

    /** Whether this server and session accept native v2 grants (negotiated before each start). */
    suspend fun negotiate(remote: RemoteConnection): Boolean

    /**
     * Adopts the ready native grant of playback [id] and acquires its media locally.
     * [request] is the exact start body sent. Returns once the local media URL is
     * ready; the grant is renewed throughout and released by [LocalTorrentPlayback.close].
     * [stage] receives core copy for each measured startup stage ("Finding peers…").
     */
    suspend fun acquire(remote: RemoteConnection, id: String, request: JsonObject, stage: (String) -> Unit = {}): LocalTorrentPlayback
}

/** One locally acquired torrent playback; private grant data never leaves it. */
internal interface LocalTorrentPlayback {
    val mediaUrl: String
    val positionSeconds: Double
    /** Heartbeat the backend lease and extend the runtime's authority. */
    suspend fun renew()
    /** The first decoded frame ends the runtime's startup deadline. */
    suspend fun firstFrame()
    /** Revoke the local endpoint; the caller releases the backend lease. */
    suspend fun close()
}
