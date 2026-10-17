package org.viptv.app.connection

import platform.Foundation.NSUserDefaults
import platform.darwin.NSObject
import platform.Foundation.NSNull

// v1 session store: NSUserDefaults-backed (non-secret refresh token storage
// acceptable for QA/simulator builds; Keychain migration is a follow-up).
// Mirrors the browser's localStorage-backed BrowserRemoteSessionStore shape.
private object IOSRemoteSessionStore : RemoteSessionStore {
    private const val key = "viptv.remote.session"

    override suspend fun read(): SavedRemoteSession? {
        val raw = NSUserDefaults.standardUserDefaults.stringForKey(key) ?: return null
        val parts = raw.split('\n', limit = 2)
        if (parts.size != 2) return null
        return SavedRemoteSession(origin = parts[0], refreshToken = parts[1])
    }

    override suspend fun save(session: SavedRemoteSession) {
        NSUserDefaults.standardUserDefaults.setObject("${session.origin}\n${session.refreshToken}", key)
    }

    override suspend fun clear() {
        NSUserDefaults.standardUserDefaults.removeObjectForKey(key)
    }
}

actual fun remoteSessionStore(): RemoteSessionStore = IOSRemoteSessionStore
