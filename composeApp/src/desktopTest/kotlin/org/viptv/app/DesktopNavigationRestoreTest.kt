package org.viptv.app

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.viptv.app.connection.*
import org.viptv.app.state.*
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.CatalogPage
import org.viptv.engine.wire.MediaIdentity
import uniffi.viptv_core.SecretStore
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.*

class DesktopNavigationRestoreTest {
    private class MemoryStore(var connection: SavedConnection = SavedConnection.None) : RemoteSessionStore {
        val records = mutableMapOf<String, SavedNavigation>()
        override suspend fun read() = (connection as? SavedConnection.Remote)?.session
        override suspend fun savedConnection() = connection
        override suspend fun save(session: SavedRemoteSession) { connection = SavedConnection.Remote(session) }
        override suspend fun clear() { connection = SavedConnection.None; records.clear() }
        override suspend fun rememberLocalConnection() { connection = SavedConnection.Local }
        override suspend fun navigation(profile: String) = records[profile]
        override suspend fun saveNavigation(profile: String, record: SavedNavigation) { records[profile] = record }
    }

    private val controls = DiscoveryControls("stremio", listOf("movie", "series"), emptyList(),
        catalogs = listOf(DiscoveryCatalog("top", "Top", "movie")),
        discovery = FeatureAvailability("Available"), search = FeatureAvailability("Available"),
        filters = FeatureAvailability("Available"), calendar = FeatureAvailability("Unsupported"))

    private fun client(requests: MutableList<String> = mutableListOf()) = HttpClient(MockEngine { request ->
        val path = request.url.encodedPath
        requests += path
        val (body, status) = when {
            path == "/api/auth/device/refresh" -> """{"access_token":"a2","refresh_token":"r2"}""" to HttpStatusCode.OK
            path == "/api/bootstrap" -> """{"identity":{"profiles":[{"id":"p1","name":"Member"}],"profile_id":"p1"},"home":null}""" to HttpStatusCode.OK
            path == "/api/discovery-controls" -> """{"provider_id":"stremio","media_types":["movie"],"controls":[],
                "catalogs":[{"id":"top","name":"Top","type":"movie"}],"discovery":{"availability":"Available"},
                "search":{"availability":"Available"},"filters":{"availability":"Available"},
                "calendar":{"availability":"Unsupported"}}""" to HttpStatusCode.OK
            path == "/api/discover" -> """{"cards":[],"complete":true}""" to HttpStatusCode.OK
            path.startsWith("/api/meta/") -> """{}""" to HttpStatusCode.NotFound
            path.endsWith("/library") -> """{"cards":[],"complete":true}""" to HttpStatusCode.OK
            path.endsWith("/membership") -> """{"identities":[],"revision":1,"complete":true}""" to HttpStatusCode.OK
            else -> """{}""" to HttpStatusCode.OK
        }
        respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
    }) { configureTransport() }

    @Test fun remoteProfileReopensOnTheBrowseScreenWithItsQuery() = runBlocking<Unit> {
        val store = MemoryStore(SavedConnection.Remote(SavedRemoteSession("https://fixture.example", "r1")))
        val first = ApplicationController(this, store, remoteFactory = { origin, storage ->
            RemoteConnection(origin, storage, client(), browserMode = false)
        })
        try {
            first.restore(); requireNotNull(first.job).join()
            assertEquals(Screen.Home, first.state.value.screen)
            assertEquals("p1", first.state.value.selectedProfile)
            first.navigate(Screen.Discover, browse = BrowseQuery("movie", genre = "Drama"))
            requireNotNull(first.job).join()
            first.navigationSaveJob?.join()
            assertEquals("Discover", store.records.getValue("p1").screen)
            assertEquals("Drama", store.records.getValue("p1").browse?.genre)
        } finally { first.disconnect() }

        val second = ApplicationController(this, store, remoteFactory = { origin, storage ->
            RemoteConnection(origin, storage, client(), browserMode = false)
        })
        try {
            second.restore(); requireNotNull(second.job).join(); second.navigationRestoreJob?.join()
            withTimeout(2000) { second.state.first { it.browseControls != null } }
            assertEquals(Screen.Discover, second.state.value.screen)
            assertEquals("movie", second.state.value.browseQuery.type)
            assertEquals("Drama", second.state.value.browseQuery.genre)
        } finally { second.disconnect() }
    }

    @Test fun titleThatIsNoLongerAvailableFallsBackToHomeWithoutAnError() = runBlocking<Unit> {
        val store = MemoryStore(SavedConnection.Remote(SavedRemoteSession("https://fixture.example", "r1")))
        val card = Card(MediaIdentity("gone", "movie", emptyList()), provider_id = "fixture", title = "Gone", badges = emptyList())
        store.records["p1"] = SavedNavigation("Details", BrowseQuery("movie"), detail = card, detailReturn = "Home")
        val requests = mutableListOf<String>()
        val controller = ApplicationController(this, store, remoteFactory = { origin, storage ->
            RemoteConnection(origin, storage, client(requests), browserMode = false)
        })
        try {
            controller.restore(); requireNotNull(controller.job).join(); controller.navigationRestoreJob?.join()
            withTimeout(2000) { controller.state.first { it.screen == Screen.Home } }
            assertTrue("/api/meta/movie/gone" in requests)
            assertNull(controller.state.value.detail)
            assertNull(controller.state.value.error)
        } finally { controller.disconnect() }
    }

    private class Vault : SecretStore {
        val values = ConcurrentHashMap<String, String>()
        override fun get(key: String) = values[key]
        override fun put(key: String, value: String) { values[key] = value }
        override fun remove(key: String) { values.remove(key) }
    }

    @Test fun localProfileReopensOnTheBrowseScreenAfterItIsChosen() = runBlocking<Unit> {
        val directory = Files.createTempDirectory("viptv-navigation-local-")
        val path = directory.resolve("local.sqlite").toString()
        val vault = Vault(); val store = MemoryStore()
        fun device() = object : LocalConnection by NativeLocalConnection(path, vault) {
            override suspend fun discoveryControls(profile: String, type: String) = controls
            override suspend fun browse(profile: String, query: BrowseQuery) = CatalogPage(emptyList(), complete = true)
        }
        val first = ApplicationController(this, store, localFactory = { device() })
        try {
            first.useLocal(path); requireNotNull(first.job).join()
            assertEquals(Screen.Profiles, first.state.value.screen)
            first.selectProfile("default"); requireNotNull(first.job).join()
            assertEquals(Screen.Home, first.state.value.screen)
            first.navigate(Screen.Discover, browse = BrowseQuery("movie", search = "dune"))
            requireNotNull(first.job).join()
            first.navigationSaveJob?.join()
            assertEquals("Discover", store.records.getValue("default").screen)
            assertEquals("dune", store.records.getValue("default").browse?.search)
        } finally { first.disconnect() }
        val second = ApplicationController(this, store, localFactory = { device() })
        try {
            second.restore(path); requireNotNull(second.job).join()
            assertEquals(Screen.Profiles, second.state.value.screen)
            second.selectProfile("default"); requireNotNull(second.job).join(); second.navigationRestoreJob?.join()
            assertEquals(Screen.Discover, second.state.value.screen)
            assertEquals("dune", second.state.value.browseQuery.search)
        } finally { second.disconnect(); directory.toFile().deleteRecursively() }
    }
}
