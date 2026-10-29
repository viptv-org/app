package org.viptv.app.connection

actual fun remoteSessionStore(): RemoteSessionStore = NativeRemoteSessionStore()
