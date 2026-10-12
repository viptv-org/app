package org.viptv.app.connection

interface LocalConnection : AutoCloseable {
    suspend fun bootstrap(profile: String? = null): Bootstrap
}
expect fun localConnection(databasePath: String): LocalConnection
