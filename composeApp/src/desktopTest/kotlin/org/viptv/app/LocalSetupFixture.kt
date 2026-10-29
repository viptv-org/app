package org.viptv.app

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.viptv.app.connection.*
import org.viptv.app.state.*
import uniffi.viptv_core.SecretStore
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap

internal class LocalSetupFixture {
    val directory = Files.createTempDirectory("viptv-local-entry-")
    val path = directory.resolve("local.sqlite").toString()
    val secrets = object : SecretStore {
        private val values = ConcurrentHashMap<String, String>()
        override fun get(key: String) = values[key]
        override fun put(key: String, value: String) { values[key] = value }
        override fun remove(key: String) { values.remove(key) }
    }
    val native = NativeLocalConnection(path, secrets)
    var liveReads = 0
    @Volatile var closed = false
    val local = object : LocalConnection by native {
        override suspend fun liveChannels(profile: String, query: LiveQuery): LiveCatalogPage {
            liveReads++
            return LiveCatalogPage(availability = "Available")
        }
        override fun close() { if (!closed) { closed = true; native.close() } }
    }
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val store = object : RemoteSessionStore {
        override suspend fun read(): SavedRemoteSession? = null
        override suspend fun save(session: SavedRemoteSession) = Unit
        override suspend fun clear() = Unit
    }
    fun controller(factory: (String) -> LocalConnection = { local }) =
        ApplicationController(scope, store, localFactory = factory)
    suspend fun idle(controller: ApplicationController) = withTimeout(5000) { controller.state.first { !it.busy } }
    fun close(controller: ApplicationController) {
        controller.disconnect()
        runBlocking { scope.coroutineContext[Job]?.cancelAndJoin() }
        local.close()
        directory.toFile().deleteRecursively()
    }
}
