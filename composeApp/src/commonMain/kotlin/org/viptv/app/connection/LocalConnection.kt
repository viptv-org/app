package org.viptv.app.connection

import org.viptv.engine.wire.IntegrationConnection
import org.viptv.engine.wire.CatalogPage
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.DetailSnapshot

interface LocalConnection : AutoCloseable {
    suspend fun bootstrap(profile: String? = null): Bootstrap
    suspend fun addons(): List<IntegrationConnection>
    suspend fun installAddon(profile: String, manifestUrl: String): List<IntegrationConnection>
    suspend fun search(profile: String, term: String): CatalogPage
    suspend fun detail(profile: String, card: Card, offset: Int = 0): DetailSnapshot
}
expect fun localConnection(databasePath: String): LocalConnection

class LocalFailure(message: String) : Exception(message)
