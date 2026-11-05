package org.viptv.app

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.*
import org.viptv.app.connection.*
import org.viptv.app.state.*
import org.viptv.engine.wire.*
import uniffi.viptv_core.SecretStore
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.*

class MergedDiscoverCursorTest {
    private class Fixture : AutoCloseable {
        private val directory = Files.createTempDirectory("viptv-merged-discover-")
        val requests = java.util.Collections.synchronizedList(mutableListOf<Pair<String, Int>>())
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        private val client = HttpClient(MockEngine { request ->
            val host = request.url.host
            val skip = request.url.parameters["skip"]?.toIntOrNull()
                ?: request.url.encodedPath.split("skip=").getOrNull(1)?.takeWhile(Char::isDigit)?.toIntOrNull()
                ?: 0
            requests += host to skip
            val body = when (host) {
                "pageable.example" -> metas("a", skip, minOf(skip + 20, 40))
                "single.example" -> metas("b", 0, 10)
                else -> error("Unexpected catalog request ${request.url}")
            }
            respond(body, headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureTransport() }
        private val secrets = object : SecretStore {
            private val values = ConcurrentHashMap<String, String>()
            override fun get(key: String) = values[key]
            override fun put(key: String, value: String) { values[key] = value }
            override fun remove(key: String) { values.remove(key) }
        }
        private val native = NativeLocalConnection(directory.resolve("local.sqlite").toString(), secrets, client)
        @Volatile private var closed = false
        private fun guardClose() { if (!closed) { closed = true; native.close() } }
        private val device = object : LocalConnection by native {
            // A profile whose plugin publishes no catalog list browses the type-only default, which
            // the engine merges from one catalog per connection.
            override suspend fun discoveryControls(profile: String, type: String): DiscoveryControls =
                native.discoveryControls(profile, type).copy(catalogs = emptyList())
            override fun close() { guardClose() }
        }
        private val store = object : RemoteSessionStore {
            override suspend fun read(): SavedRemoteSession? = null
            override suspend fun save(session: SavedRemoteSession) = Unit
            override suspend fun clear() = Unit
        }

        init {
            native.engine.installAddon("default", "https://pageable.example/manifest.json",
                """{"id":"pageable","name":"Pageable","version":"1","resources":["catalog"],"types":["movie"],"catalogs":[{"id":"twenty","type":"movie","name":"Twenty","extra":[{"name":"skip"}]}]}""")
            native.engine.installAddon("default", "https://single.example/manifest.json",
                """{"id":"single","name":"Single","version":"1","resources":["catalog"],"types":["movie"],"catalogs":[{"id":"ten","type":"movie","name":"Ten"}]}""")
        }

        fun controller(merged: Boolean = true): ApplicationController {
            val connection = if (merged) device else native
            return ApplicationController(scope, store, localFactory = { connection }).also { it.local = connection }
        }

        suspend fun catalog(name: String) = native.discoveryControls("default", "movie").catalogs.first { it.id == name }

        fun finish(controller: ApplicationController) {
            controller.disconnect()
            runBlocking { scope.coroutineContext[Job]?.cancelAndJoin() }
            guardClose()
        }

        override fun close() { directory.toFile().deleteRecursively() }

        private fun metas(prefix: String, from: Int, until: Int): String =
            """{"metas":[""" + (from until until).joinToString(",") { """{"id":"$prefix$it","type":"movie","name":"$prefix$it"}""" } + "]}"
    }

    private suspend fun await(what: String, predicate: () -> Boolean) {
        withTimeout(10_000) { while (!predicate()) delay(10) }
    }

    @Test fun aMergedDiscoverForwardsItsOpaqueCursorSoEveryCatalogIsPagedOnce() = runBlocking<Unit> {
        Fixture().use { fixture ->
            val controller = fixture.controller()
            try {
                controller.mutable.value = ApplicationState(screen = Screen.Discover, selectedProfile = "default",
                    localMode = true, browseQuery = BrowseQuery("series"))
                controller.applyBrowse(BrowseQuery("movie"))
                await("first page") { controller.state.value.browseCards.size == 30 && !controller.state.value.busy }
                val first = requireNotNull(controller.state.value.browseNext)
                assertNull(first.toIntOrNull(), "a merged page carries one cursor for every catalog: $first")
                assertEquals(listOf("pageable.example" to 0, "single.example" to 0), fixture.requests.sortedBy { it.first })

                controller.browseMore()
                await("second page") { controller.state.value.browseCards.size == 50 && !controller.state.value.busy }
                assertEquals(listOf("pageable.example" to 20), fixture.requests.drop(2), "the finished catalog is not asked again")

                controller.browseMore()
                await("last page") { controller.state.value.browseNext == null && !controller.state.value.busy }
                val ids = controller.state.value.browseCards.map { it.identity.id }
                assertEquals(ids.toSet().size, ids.size, "the feed never repeats an entry")
                assertEquals((0 until 40).map { "a$it" }.toSet() + (0 until 10).map { "b$it" },
                    ids.toSet(), "every entry of both catalogs is shown exactly once")
                assertEquals(listOf("pageable.example" to 40), fixture.requests.drop(3))
            } finally { fixture.finish(controller) }
        }
    }

    @Test fun aSingleCatalogCursorStillPagesThroughSkip() = runBlocking<Unit> {
        Fixture().use { fixture ->
            val controller = fixture.controller(merged = false)
            try {
                controller.mutable.value = ApplicationState(screen = Screen.Discover, selectedProfile = "default",
                    localMode = true, browseQuery = BrowseQuery("series"))
                val catalog = fixture.catalog("twenty")
                controller.applyBrowse(BrowseQuery("movie", catalog = catalog.id, connection_id = catalog.connection_id))
                await("first page") { controller.state.value.browseCards.size == 20 && !controller.state.value.busy }
                assertEquals("20", controller.state.value.browseNext)

                controller.browseMore()
                await("second page") { controller.state.value.browseCards.size == 40 && !controller.state.value.busy }
                assertEquals("40", controller.state.value.browseNext)

                controller.browseMore()
                await("last page") { controller.state.value.browseNext == null && !controller.state.value.busy }
                assertEquals((0 until 40).map { "a$it" }, controller.state.value.browseCards.map { it.identity.id })
                assertEquals(listOf("pageable.example" to 0, "pageable.example" to 20, "pageable.example" to 40), fixture.requests)
            } finally { fixture.finish(controller) }
        }
    }
}