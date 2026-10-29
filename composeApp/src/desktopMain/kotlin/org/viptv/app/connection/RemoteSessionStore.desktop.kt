package org.viptv.app.connection

/** Desktop only: Android TV and phone always reopen on Home. */
actual fun remoteSessionStore(): RemoteSessionStore = NativeRemoteSessionStore(remembersNavigation = true)
