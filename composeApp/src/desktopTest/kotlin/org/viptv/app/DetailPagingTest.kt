package org.viptv.app

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import org.viptv.app.connection.*
import org.viptv.app.state.*
import org.viptv.engine.wire.*
import kotlin.test.*

class DetailPagingTest {
    private val store = object : RemoteSessionStore {
        override suspend fun read(): SavedRemoteSession? = null
        override suspend fun save(session: SavedRemoteSession) = Unit
        override suspend fun clear() = Unit
    }
    private val title = Card(identity = MediaIdentity("long-series", "series", emptyList()), provider_id = "stremio", title = "Long series", badges = emptyList())
    private fun episode(number: Long) = title.copy(identity = title.identity.copy(episode = EpisodeCoordinates("aired", 1, number)))
    private fun snapshot(episodes: List<Card>) = DetailSnapshot(title, episodes = episodes, seasons = listOf(EpisodeSeason(1, 1242, 0)))

    @Test fun largeSeasonUsesThirteenBackgroundPagesWithoutBlockingNavigation() = runBlocking {
        val started = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        var requests = 0
        val client = HttpClient(MockEngine { request ->
            requests++
            val offset = requireNotNull(request.url.parameters["offset"]).toLong()
            assertEquals("96", request.url.parameters["limit"])
            if (offset == 24L) { started.complete(Unit); release.await() }
            val end = minOf(offset + 96, 1242)
            respond(Json.encodeToString(snapshot((offset + 1..end).map(::episode))
                .copy(next_cursor = end.takeIf { it < 1242 }?.toString())), headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureTransport() }
        val remote = RemoteConnection("https://watch.example", store, client, browserMode = false)
        val controller = ApplicationController(this, store)
        controller.connection = remote
        controller.mutable.value = ApplicationState(screen = Screen.Details, selectedProfile = "owner", detail = snapshot((1L..24L).map(::episode)))
        try {
            controller.detailPage(24); started.await(); controller.detailPage(24)
            assertFalse(controller.state.value.busy)
            assertEquals(1, requests)
            controller.mutable.value = controller.state.value.copy(detail = controller.state.value.detail!!.copy(item = title.copy(title = "Current presentation")))
            release.complete(Unit); controller.detailPagingJob!!.join()
            while (controller.state.value.detail!!.episodes.size < 1242) {
                controller.detailPage(controller.state.value.detail!!.episodes.size.toLong())
                controller.detailPagingJob!!.join()
            }
            assertEquals(13, requests)
            assertEquals(1242, controller.state.value.detail!!.episodes.map { it.identity }.distinct().size)
            assertEquals("Current presentation", controller.state.value.detail!!.item.title)
            assertEquals(Screen.Details, controller.state.value.screen)
            assertNull(controller.state.value.detailLoadingOffset)
        } finally { remote.close() }
    }

    @Test fun responseForPriorProfileCannotAppendEpisodesToCurrentProfile() = runBlocking {
        val started = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val client = HttpClient(MockEngine {
            started.complete(Unit); release.await()
            respond(Json.encodeToString(snapshot(listOf(episode(25)))), headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureTransport() }
        val remote = RemoteConnection("https://watch.example", store, client, browserMode = false)
        val controller = ApplicationController(this, store)
        controller.connection = remote
        controller.mutable.value = ApplicationState(screen = Screen.Details, selectedProfile = "first", detail = snapshot(listOf(episode(1))))
        try {
            controller.detailPage(24); started.await()
            controller.mutable.value = ApplicationState(screen = Screen.Details, selectedProfile = "second", detail = snapshot(emptyList()))
            release.complete(Unit); controller.detailPagingJob!!.join()
            assertEquals("second", controller.state.value.selectedProfile)
            assertTrue(controller.state.value.detail!!.episodes.isEmpty())
            assertNull(controller.state.value.detailLoadingOffset)
        } finally { remote.close() }
    }
}
