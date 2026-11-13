package org.viptv.app

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.content.TextContent
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.*
import org.viptv.app.connection.*
import org.viptv.app.state.*
import org.viptv.engine.wire.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class ManualSourceDiscoveryTest {
    private val store = object : RemoteSessionStore {
        override suspend fun read(): SavedRemoteSession? = null
        override suspend fun save(session: SavedRemoteSession) = Unit
        override suspend fun clear() = Unit
    }
    private val movie = Card(MediaIdentity("movie", "movie", emptyList()), provider_id = "catalog", title = "Movie", badges = emptyList())

    @Test fun openingThePickerReplacesPreferredDiscoveryAndKeepsAllProviderBatches() = runTest {
        val requests = EngineRequests<JsonObject>()
        val cancelled = EngineRequests<String>()
        val preferredPending = CompletableDeferred<Unit>()
        val late = CompletableDeferred<Unit>()
        var playbackRequests = 0
        val client = HttpClient(MockEngine) {
            engine {
                dispatcher = StandardTestDispatcher(testScheduler)
                addHandler { request ->
                    val path = request.url.encodedPath
                    val body = when {
                        path == "/api/streams" && request.method == HttpMethod.Post -> {
                            val query = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
                            requests += query
                            """{"id":"${if ("remembered_source" in query) "preferred" else "all"}"}"""
                        }
                        request.method == HttpMethod.Delete -> { cancelled += path; "{}" }
                        path.endsWith("/source-ranking") -> {
                            val ids = Json.parseToJsonElement((request.body as TextContent).text).jsonObject.getValue("ids").jsonArray
                            buildJsonObject {
                                put("streams", JsonArray(ids.map { buildJsonObject { put("id", it) } }))
                                put("source_reuse_supported", true)
                                put("source_reuse_ready", false)
                                put("best_match_fully_matches", true)
                                put("best_match_ids", ids)
                            }.toString()
                        }
                        path == "/api/streams/preferred" && request.url.parameters["after"] == "0" ->
                            """{"events":[{"source":"addon:saved","streams":[{"id":"saved","name":"Saved supplier"}]}],"done":false,"phase":"preferred"}"""
                        path == "/api/streams/preferred" -> { preferredPending.complete(Unit); awaitCancellation() }
                        path == "/api/streams/all" && request.url.parameters["after"] == "0" ->
                            """{"events":[{"source":"addon:early","streams":[{"id":"early","name":"Early supplier"}]}],"done":false}"""
                        path == "/api/streams/all" -> {
                            late.await()
                            """{"events":[{"source":"addon:late","streams":[{"id":"late","name":"Late supplier"}]}],"done":true}"""
                        }
                        path == "/api/playback" -> { playbackRequests++; error("Manual picker started playback") }
                        else -> error("Unexpected fixture request $path")
                    }
                    respond(body, headers = headersOf(HttpHeaders.ContentType, "application/json"))
                }
            }
            configureTransport()
        }
        val remote = RemoteConnection("https://watch.example", store, client, browserMode = true)
        remote.preferredSourceDiscoverySupported = true
        val controller = ApplicationController(backgroundScope, store)
        controller.connection = remote
        controller.sourceMemory.remember("profile", movie, SourceCandidate("old", "addon:saved", "Saved", audio_languages = listOf("en")))
        controller.mutable.value = ApplicationState(screen = Screen.Details, selectedProfile = "profile",
            detail = DetailSnapshot(movie, episodes = emptyList()), preferences = PreferencesSnapshot())
        try {
            controller.playTitle(movie)
            preferredPending.await()
            assertTrue("remembered_source" in requests.single())
            controller.showMatchingSources()
            runCurrent()
            assertEquals(2, requests.size, "Manual selection must ask all providers instead of retaining the preferred-only lookup")
            assertFalse("remembered_source" in requests.last())
            assertFalse("cross_title_source" in requests.last())
            assertEquals(listOf("/api/streams/preferred"), cancelled)
            advanceTimeBy(350); runCurrent()
            assertEquals(listOf("early"), controller.state.value.sources.map { it.id })
            assertTrue(controller.state.value.findingSources)
            late.complete(Unit)
            controller.job!!.join()
            assertEquals(listOf("early", "late"), controller.state.value.sources.map { it.id })
            assertFalse(controller.state.value.findingSources)
            assertEquals(Screen.Sources, controller.state.value.screen)
            assertEquals(movie, controller.state.value.sourceTitle)
            assertEquals("profile", controller.state.value.selectedProfile)
            assertEquals(0, playbackRequests)
        } finally { late.complete(Unit); controller.job?.cancelAndJoin(); remote.close() }
    }

    @Test fun retryShowsDiscoveryAndBackCancelsTheRetriedLookup() = runTest {
        var started = 0
        var cancelled = false
        val pending = CompletableDeferred<Unit>()
        val client = HttpClient(MockEngine) {
            engine {
                dispatcher = StandardTestDispatcher(testScheduler)
                addHandler { request ->
                    val body = when {
                        request.method == HttpMethod.Delete -> { cancelled = true; "{}" }
                        request.url.encodedPath == "/api/streams" -> { started++; """{"id":"lookup"}""" }
                        request.url.encodedPath.endsWith("/source-ranking") -> """{"streams":[{"id":"early"}]}"""
                        started == 1 -> """{"events":[{"source":"addon:early","streams":[{"id":"early","name":"Early supplier"}]}],"done":true}"""
                        else -> { pending.complete(Unit); awaitCancellation() }
                    }
                    respond(body, headers = headersOf(HttpHeaders.ContentType, "application/json"))
                }
            }
            configureTransport()
        }
        val remote = RemoteConnection("https://watch.example", store, client, browserMode = true)
        val controller = ApplicationController(backgroundScope, store)
        controller.connection = remote
        controller.mutable.value = ApplicationState(screen = Screen.Details, selectedProfile = "profile",
            detail = DetailSnapshot(movie, episodes = emptyList()), preferences = PreferencesSnapshot())
        try {
            controller.chooseSource(movie)
            controller.job!!.join()
            assertFalse(controller.state.value.findingSources)
            controller.retrySources()
            assertTrue(controller.state.value.findingSources, "Retry must show provider discovery rather than source opening")
            pending.await()
            controller.backSources()
            runCurrent()
            assertTrue(cancelled)
            assertEquals(Screen.Details, controller.state.value.screen)
            assertFalse(controller.state.value.findingSources)
            assertFalse(controller.state.value.busy)
        } finally { controller.job?.cancelAndJoin(); remote.close() }
    }

}
