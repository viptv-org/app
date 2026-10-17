package org.viptv.app

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.content.TextContent
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.serialization.json.*
import org.viptv.app.connection.*
import org.viptv.app.state.*
import org.viptv.engine.wire.*
import kotlin.test.*

class SourceDiscoveryControllerTest {
    private val store = object : RemoteSessionStore {
        override suspend fun read(): SavedRemoteSession? = null
        override suspend fun save(session: SavedRemoteSession) = Unit
        override suspend fun clear() = Unit
    }
    private val title = Card(MediaIdentity("show", "series", emptyList()), provider_id = "stremio", title = "Show", badges = emptyList())
    private fun episode(season: Long, number: Long) = title.copy(identity = title.identity.copy(episode = EpisodeCoordinates("aired", season, number)))

    @Test fun titleHeroUsesRegularEpisodeAndExactCoordinatesSurviveSharedTitleIds() {
        val special = episode(0, 1)
        val first = episode(1, 1)
        val second = episode(1, 2)
        val detail = DetailSnapshot(title, episodes = listOf(special, episode(1, 0), first, second))
        assertEquals(first, sourceTarget(title, detail))
        assertEquals(second, sourceTarget(second, detail))
        assertEquals(special, sourceTarget(special, detail))
        assertFailsWith<LocalFailure> { sourceTarget(title, DetailSnapshot(title, episodes = emptyList())) }
    }

    private val searchOrigin = MetadataOrigin("stremio", "addon:search", "a".repeat(32))
    private val metadataOrigin = MetadataOrigin("stremio", "addon:metadata", "b".repeat(32))
    private fun publicTitle(kind: String = "movie") = title.copy(
        identity = MediaIdentity("tt1111111", kind, listOf(ExternalId("tmdb", "42"))),
        provider_id = "addon:search", metadata_origin = searchOrigin)
    private fun metadata(card: Card) = card.copy(provider_id = "addon:metadata", metadata_origin = metadataOrigin,
        identity = card.identity.copy(ids = card.identity.ids + ExternalId("imdb", "tt1111111")))

    @Test fun publicSearchTitleUsesVerifiedMetadataProducerAndAdditionalAssertions() {
        val selected = publicTitle()
        val actual = metadata(selected)
        assertEquals(actual, sourceTarget(selected, DetailSnapshot(actual, episodes = emptyList())))
        assertEquals(actual, sourceTarget(selected.copy(identity = selected.identity.copy(ids = listOf(ExternalId("tmdb", "00042")))), DetailSnapshot(actual, episodes = emptyList())))
    }

    @Test fun delegatedEpisodeOutsideFirstPageKeepsExactSelectorAndAuthoritativeParent() {
        val parent = publicTitle("series")
        val actual = metadata(parent)
        val selected = parent.copy(identity = MediaIdentity("tt1111111:1:1499", "series", emptyList(),
            EpisodeCoordinates("aired", 1, 1499)), parent_identity = parent.identity)
        val result = sourceTarget(selected, DetailSnapshot(actual, episodes = emptyList()))
        assertEquals(selected.identity, result.identity)
        assertEquals(actual.identity, result.parent_identity)
        assertEquals(metadataOrigin, result.metadata_origin)
        assertEquals(actual.provider_id, result.provider_id)
        val onPage = result.copy(title = "Episode 1499")
        assertEquals(onPage, sourceTarget(selected, DetailSnapshot(actual, episodes = listOf(onPage))))
        val claimed = selected.copy(identity = selected.identity.copy(ids = listOf(ExternalId("tvdb", "99"))))
        val conflicting = onPage.copy(identity = onPage.identity.copy(ids = listOf(ExternalId("tvdb", "100"))))
        assertFailsWith<LocalFailure> { sourceTarget(claimed, DetailSnapshot(actual, episodes = listOf(conflicting))) }
    }

    @Test fun opaqueAndPaddedEpisodeSelectorsCannotChangeMetadataProducer() {
        val opaque = publicTitle().let { it.copy(identity = it.identity.copy(id = "private:one")) }
        assertFailsWith<LocalFailure> { sourceTarget(opaque, DetailSnapshot(metadata(opaque), episodes = emptyList())) }
        val parent = publicTitle("series")
        val padded = parent.copy(identity = MediaIdentity("tt1111111:01:2", "series", emptyList(),
            EpisodeCoordinates("aired", 1, 2)), parent_identity = parent.identity)
        assertFailsWith<LocalFailure> { sourceTarget(padded, DetailSnapshot(metadata(parent), episodes = emptyList())) }
        assertFailsWith<LocalFailure> { sourceTarget(publicTitle(), DetailSnapshot(metadata(publicTitle()).copy(metadata_origin = null), episodes = emptyList())) }
    }

    @Test fun publicDelegationCannotChangeTitleParentClaimsOrEdition() {
        val selected = publicTitle()
        val actual = metadata(selected)
        listOf(actual.copy(identity = actual.identity.copy(id = "tt2222222")),
            actual.copy(identity = actual.identity.copy(ids = listOf(ExternalId("tmdb", "43")))))
            .forEach { assertFailsWith<LocalFailure> { sourceTarget(selected, DetailSnapshot(it, episodes = emptyList())) } }
        assertFailsWith<LocalFailure> { sourceTarget(selected.copy(identity = selected.identity.copy(edition = "extended")), DetailSnapshot(actual, episodes = emptyList())) }
        val parent = publicTitle("series")
        val episode = parent.copy(identity = MediaIdentity("tt1111111:1:1499", "series", emptyList(),
            EpisodeCoordinates("aired", 1, 1499)), parent_identity = parent.identity.copy(ids = listOf(ExternalId("tmdb", "43"))))
        assertFailsWith<LocalFailure> { sourceTarget(episode, DetailSnapshot(metadata(parent), episodes = emptyList())) }
    }

    @Test fun detailsHeroSendsEpisodeAndProgressiveSourcesPublishBeforeDiscoveryFinishes() = runBlocking {
        val pending = CompletableDeferred<Unit>(); val finish = CompletableDeferred<Unit>()
        val first = episode(1, 1)
        val client = HttpClient(MockEngine { request ->
            val body = when {
                request.url.encodedPath == "/api/streams" -> {
                    val query = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
                    assertEquals(1L, query.getValue("season").jsonPrimitive.long)
                    assertEquals(1L, query.getValue("episode").jsonPrimitive.long)
                    """{"id":"job"}"""
                }
                request.url.encodedPath.endsWith("/source-ranking") -> """{"streams":[{"id":"early"},{"id":"late"}]}"""
                request.url.parameters["after"] == "0" -> """{"events":[{"source":"addon:early","streams":[{"id":"early","name":"First source"}]}],"done":false}"""
                else -> { pending.complete(Unit); finish.await(); """{"events":[{"source":"addon:late","streams":[{"id":"late","name":"Second source"}]}],"done":true}""" }
            }
            respond(body, headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureTransport() }
        val remote = RemoteConnection("https://watch.example", store, client, browserMode = false)
        val controller = ApplicationController(this, store)
        controller.connection = remote
        controller.mutable.value = ApplicationState(screen = Screen.Details, selectedProfile = "profile",
            detail = DetailSnapshot(title, episodes = listOf(episode(0, 0), first)), preferences = PreferencesSnapshot())
        try {
            controller.chooseSource(title)
            pending.await()
            assertEquals(first, controller.state.value.sourceTitle)
            assertEquals(listOf("early"), controller.state.value.sources.map { it.id })
            assertTrue(controller.state.value.findingSources)
            assertEquals(Screen.Sources, controller.state.value.screen)
            finish.complete(Unit); controller.job!!.join()
            assertEquals(listOf("early", "late"), controller.state.value.sources.map { it.id })
            assertFalse(controller.state.value.findingSources)
        } finally { finish.complete(Unit); controller.job?.cancelAndJoin(); remote.close() }
    }

    @Test fun discoveryKeepsPollingForSdkBatchesAfterThirtySeconds() = runTest {
        var poll = 0
        var cancelled = false
        val client = HttpClient(MockEngine) {
            engine {
                dispatcher = StandardTestDispatcher(testScheduler)
                addHandler { request ->
                    val body = when (request.method) {
                        HttpMethod.Post -> """{"id":"job"}"""
                        HttpMethod.Delete -> { cancelled = true; "{}" }
                        else -> {
                            delay(10_000)
                            poll++
                            when (poll) {
                                1 -> """{"events":[{"source":"addon:fast","streams":[{"id":"early","name":"Early"}]}],"done":false}"""
                                5 -> """{"events":[{"source":"vsources:late","streams":[{"id":"late","name":"Late"}]}],"done":true}"""
                                else -> """{"events":[],"done":false}"""
                            }
                        }
                    }
                    respond(body, headers = headersOf(HttpHeaders.ContentType, "application/json"))
                }
            }
            configureTransport()
        }
        val remote = RemoteConnection("https://watch.example", store, client, browserMode = false)
        val published = mutableListOf<List<String>>()
        try {
            val sources = remote.sources(title.copy(identity = title.identity.copy(kind = "movie"))) {
                published += it.map { source -> source.id }
            }
            assertEquals(listOf("early", "late"), sources.map { it.id })
            assertEquals(listOf(listOf("early"), listOf("early", "late")), published)
            assertFalse(cancelled)
            assertTrue(testScheduler.currentTime > 45_000)
        } finally { remote.close() }
    }

    @Test fun discoveryDeadlineKeepsPublishedChoicesAndExplainsUnfinishedProviders() = runTest {
        var polls = 0
        var cancelled = false
        val client = HttpClient(MockEngine) {
            engine {
                dispatcher = StandardTestDispatcher(testScheduler)
                addHandler { request ->
                    val body = when (request.method) {
                        HttpMethod.Post -> """{"id":"job"}"""
                        HttpMethod.Delete -> { cancelled = true; "{}" }
                        else -> {
                            delay(10_000); polls++
                            if (polls == 1) """{"events":[{"source":"addon:fast","streams":[{"id":"early","name":"Early"}]}],"done":false}"""
                            else """{"events":[],"done":false}"""
                        }
                    }
                    respond(body, headers = headersOf(HttpHeaders.ContentType, "application/json"))
                }
            }
            configureTransport()
        }
        val remote = RemoteConnection("https://watch.example", store, client, browserMode = false)
        val controller = ApplicationController(this, store)
        controller.connection = remote
        controller.mutable.value = ApplicationState(screen = Screen.Details, selectedProfile = "profile", preferences = PreferencesSnapshot())
        try {
            controller.chooseSource(title.copy(identity = title.identity.copy(kind = "movie")))
            controller.job!!.join()
            assertEquals(listOf("early"), controller.state.value.sources.map { it.id })
            assertFalse(controller.state.value.busy)
            assertFalse(controller.state.value.findingSources)
            assertContains(controller.state.value.error.orEmpty(), "Available sources are ready to play")
            assertTrue(cancelled)
        } finally { remote.close() }
    }
}
