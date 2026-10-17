package org.viptv.app

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.viptv.app.connection.*
import org.viptv.app.state.*
import org.viptv.engine.wire.IntegrationConnection
import uniffi.viptv_core.SecretStore
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.*

class NativeAddonCommitTest {
    @Test fun realNativeCommitCompletesBeforeAnInventoryFailureAndLegacyListApiStillWorks() = runBlocking {
        val directory = Files.createTempDirectory("viptv-native-addon-commit-")
        val secrets = object : SecretStore {
            private val values = ConcurrentHashMap<String, String>()
            override fun get(key: String) = values[key]
            override fun put(key: String, value: String) { values[key] = value }
            override fun remove(key: String) { values.remove(key) }
        }
        val client = HttpClient(MockEngine { request ->
            assertEquals("https://fixture.example/manifest.json", request.url.toString())
            respond("""{"id":"fixture.commit","name":"Committed fixture","version":"1.0.0","types":["movie"],"resources":["stream"],"catalogs":[]}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureTransport() }
        var failInventory = true; var inventoryReads = 0
        val native = object : NativeLocalConnection(directory.resolve("local.sqlite").toString(), secrets, client) {
            override suspend fun addons(): List<IntegrationConnection> {
                inventoryReads++
                if (failInventory) throw LocalFailure("Installed, but inventory could not refresh.")
                return super.addons()
            }
        }
        val controller = ApplicationController(this); controller.local = native
        controller.mutable.value = ApplicationState(screen = Screen.Settings, localMode = true, selectedProfile = "default")
        val results = mutableListOf<Boolean>()
        try {
            controller.installAddon("https://fixture.example/manifest.json", results::add); controller.job!!.join()
            assertEquals(listOf(true), results, "Ancillary native reads must not turn a committed installation into failure")
            assertEquals(1, inventoryReads, "Commit-only adapter must not read inventory")
            assertEquals("Installed, but inventory could not refresh.", controller.state.value.error)
            val persisted = Json.parseToJsonElement(native.engine.addons()).jsonArray.single().jsonObject
            assertEquals("Committed fixture", persisted.getValue("name").jsonPrimitive.content)
            val id = persisted.getValue("id").jsonPrimitive.content
            val assignment = Json.parseToJsonElement(native.engine.profile("default")).jsonObject
                .getValue("assignments").jsonArray.single().jsonObject
            assertEquals(id, assignment.getValue("connection_id").jsonPrimitive.content)
            assertTrue(assignment.getValue("enabled").jsonArray.isNotEmpty())
            assertFailsWith<LocalFailure> { native.installAddon("default", "https://fixture.example/manifest.json") }
            failInventory = false
            assertEquals(id, native.installAddon("default", "https://fixture.example/manifest.json").single().id,
                "Existing list-return callers retain their normal inventory result")
        } finally { controller.job?.join(); native.close(); directory.toFile().deleteRecursively() }
    }
}
