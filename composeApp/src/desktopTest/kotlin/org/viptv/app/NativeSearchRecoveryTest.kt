package org.viptv.app

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.*
import org.viptv.app.connection.*
import org.viptv.app.features.search.searchResultGroups
import org.viptv.app.state.*
import uniffi.viptv_core.SecretStore
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.*

class NativeSearchRecoveryTest {
    private class Fixture : AutoCloseable {
        val directory = Files.createTempDirectory("viptv-native-search-")
        var failAll = false
        val calls = ConcurrentHashMap<String, Int>()
        val vault = object : SecretStore {
            val values = ConcurrentHashMap<String, String>()
            override fun get(key: String) = values[key]
            override fun put(key: String, value: String) { values[key] = value }
            override fun remove(key: String) { values.remove(key) }
        }
        val client = HttpClient(MockEngine { request ->
            calls.merge(request.url.host, 1, Int::plus)
            if (failAll || request.url.host == "broken.example" || request.url.encodedPath.contains("/series/"))
                respond("private upstream body", HttpStatusCode.ServiceUnavailable)
            else respond("""{"metas":[{"id":"opaque","type":"movie","name":"Native result"}]}""", headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureTransport() }
        val local = NativeLocalConnection(directory.resolve("local.sqlite").toString(), vault, client)
        init {
            for ((host, name) in listOf("partial.example" to "Mixed provider", "broken.example" to "Offline provider")) {
                local.engine.installAddon("default", "https://$host/manifest.json",
                    """{"id":"$host","name":"$name","version":"1","resources":["catalog"],"types":["movie","series"],"catalogs":[{"id":"all","type":"movie","extra":[{"name":"search"}]},{"id":"all","type":"series","extra":[{"name":"search"}]}]}""")
            }
        }
        override fun close() { local.close(); directory.toFile().deleteRecursively() }
    }

    @Test fun actualNativeSearchPublishesPartialAndFailedProvidersWithoutLosingCardOrigin() = runBlocking<Unit> {
        Fixture().use { fixture ->
            val controller = ApplicationController(this)
            controller.local = fixture.local
            controller.mutable.value = ApplicationState(screen = Screen.Search, selectedProfile = "default", localMode = true)
            try {
                controller.search("fixture"); controller.job?.join()
                assertNull(controller.state.value.error)
                val rows = controller.state.value.searchRows
                assertEquals(4, rows.size)
                val partial = rows.single { it.title == "Mixed provider · Movies" }
                val partialSeries = rows.single { it.title == "Mixed provider · Series" }
                assertEquals("Ready", partial.state); assertNull(partial.message)
                assertEquals("Failed", partialSeries.state); assertTrue(partialSeries.cards.isEmpty())
                assertEquals(2, rows.count { it.title.startsWith("Offline provider") && it.state == "Failed" })
                val card = partial.cards.single()
                assertNotNull(card.metadata_origin)
                assertEquals(partial.provider_id, card.provider_id)
                assertEquals(card.provider_id, card.metadata_origin?.connection_id)
                assertEquals(listOf(card), controller.state.value.results)
                assertEquals(listOf(2, 2), fixture.calls.values.sorted(), "One plan should fetch each catalog exactly once")
                assertFalse(searchResultGroups(rows).any { it.message.orEmpty().contains("private") })
            } finally { controller.job?.cancelAndJoin(); controller.local = null }
        }
    }

    @Test fun allFailedNativeProvidersRemainNamedFailuresInsteadOfNoMatchesAndRetryRecovers() = runBlocking<Unit> {
        Fixture().use { fixture ->
            fixture.failAll = true
            val failed = requireNotNull(fixture.local.searchResults("default", "fixture"))
            assertEquals(4, failed.rows.size); assertTrue(failed.complete)
            assertTrue(failed.rows.all { it.state == "Failed" && it.cards.isEmpty() })
            assertTrue(searchResultGroups(failed.rows).all { it.message != null })
            fixture.failAll = false
            val recovered = requireNotNull(fixture.local.searchResults("default", "fixture"))
            assertEquals(1, recovered.rows.sumOf { it.cards.size })
            assertEquals(failed.rows.map { it.id }, recovered.rows.map { it.id })
        }
    }
}
