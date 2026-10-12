package org.viptv.app.connection

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.viptv.engine.wire.HomeSnapshot
import org.viptv.engine.wire.ProfileConfiguration
import uniffi.viptv_core.LocalEngine

actual fun localConnection(databasePath: String): LocalConnection = NativeLocalConnection(databasePath)

private class NativeLocalConnection(path: String) : LocalConnection {
    private val engine = LocalEngine(path)
    private val json = Json { ignoreUnknownKeys = true }
    override suspend fun bootstrap(profile: String?): Bootstrap = withContext(Dispatchers.IO) {
        val profiles = json.decodeFromString<List<ProfileConfiguration>>(engine.profiles()).map { Profile(it.scope.profile_id, it.name, null) }
        Bootstrap(profiles, profile, profile?.let { json.decodeFromString<HomeSnapshot>(engine.home(it)) })
    }
    override fun close() { engine.close() }
}
