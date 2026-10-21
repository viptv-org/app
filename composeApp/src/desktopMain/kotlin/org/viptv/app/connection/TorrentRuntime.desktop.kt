package org.viptv.app.connection

/** Desktop does not bundle the shared torrent runtime yet; torrent sources keep the existing refusal. */
internal actual fun platformTorrentRuntime(): TorrentRuntimeHost? = null
