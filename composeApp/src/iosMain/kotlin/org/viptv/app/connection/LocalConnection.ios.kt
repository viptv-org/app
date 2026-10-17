package org.viptv.app.connection

actual fun localConnection(databasePath: String): LocalConnection = error("Local execution is unavailable on iOS in v1")
