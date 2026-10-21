package org.viptv.app.connection

/** Browsers use the gateway for torrents; they never acquire them locally. */
internal actual fun localTorrents(): LocalTorrents? = null
