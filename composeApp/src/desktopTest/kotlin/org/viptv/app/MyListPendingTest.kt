package org.viptv.app

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.Json
import org.viptv.app.connection.*
import org.viptv.app.state.*
import org.viptv.engine.wire.*
import kotlin.test.*

class MyListPendingTest {
    private fun card(id: String) = Card(MediaIdentity(id, "movie", emptyList()), provider_id = "addon:1", title = id, badges = emptyList())

    private fun library(vararg ids: String) = Json.encodeToString(CatalogPage(ids.map(::card), next_cursor = null, complete = true))
    private fun membership(vararg ids: String) = Json.encodeToString(LibraryMembershipSnapshot(ids.map { MediaIdentity(it, "movie", emptyList()) }, 1, true))

    private val json = headersOf(HttpHeaders.ContentType, "application/json")

    private inner class Fixture(scope: CoroutineScope, scheduler: TestCoroutineScheduler, reply: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) {
        val store = object : RemoteSessionStore {
            override suspend fun read(): SavedRemoteSession? = null
            override suspend fun save(session: SavedRemoteSession) = Unit
            override suspend fun clear() = Unit
        }
        val writes = mutableListOf<String>()
        val client = HttpClient(MockEngine) {
            engine { dispatcher = StandardTestDispatcher(scheduler); addHandler(reply) }
            configureTransport()
        }
        val remote = RemoteConnection("https://watch.example", store, client, browserMode = false)
        val controller = ApplicationController(scope, store).also {
            it.connection = remote
            it.mutable.value = ApplicationState(screen = Screen.Search, selectedProfile = "1",
                results = listOf(card("one"), card("two")), favoriteKeys = emptySet())
        }
        fun close() { controller.mutationJobs.values.toList().forEach { it.cancel() }; remote.close() }
    }

    @Test fun pressingAddFlipsTheControlInTheFrameDropsTheRepeatAndSettlesOnTheHost() = runTest {
        val release = CompletableDeferred<Unit>()
        val writes = mutableListOf<String>()
        val f = Fixture(this, testScheduler) { request ->
            when {
                request.method == HttpMethod.Put -> { writes += "put"; release.await(); respond("{}", headers = json) }
                request.url.encodedPath.endsWith("/membership") -> respond(membership("one"), headers = json)
                else -> respond(library("one"), headers = json)
            }
        }
        try {
            val target = saveTarget(card("one"))
            f.controller.toggleFavorite(card("one"))
            runCurrent()
            assertTrue(f.controller.state.value.isSaved(card("one")), "the Add control flips in the frame of the press")
            assertTrue(f.controller.state.value.mutations.isPending(target))
            assertNull(f.controller.state.value.mutations.failure(target))

            f.controller.toggleFavorite(card("one"))
            runCurrent()
            assertEquals(1, writes.size, "a repeat press on the pending title is dropped")

            release.complete(Unit)
            advanceUntilIdle()
            assertTrue(f.controller.state.value.isSaved(card("one")))
            assertFalse(f.controller.state.value.mutations.isPending(target))
            assertTrue(f.controller.state.value.savedOverrides.isEmpty(), "the host's answer replaces the optimistic value")
        } finally { release.complete(Unit); f.close() }
    }

    @Test fun aFailedAddRevertsTheControlKeepsTheMessageAndRetriesOnce() = runTest {
        var puts = 0
        val f = Fixture(this, testScheduler) { request ->
            when {
                request.method == HttpMethod.Put -> { puts++; if (puts == 1) respond("nope", HttpStatusCode.InternalServerError) else respond("{}", headers = json) }
                request.url.encodedPath.endsWith("/membership") -> respond(membership(), headers = json)
                else -> respond(library(), headers = json)
            }
        }
        try {
            val target = saveTarget(card("one"))
            f.controller.toggleFavorite(card("one"))
            advanceUntilIdle()
            assertFalse(f.controller.state.value.isSaved(card("one")), "a failed write puts the control back")
            assertFalse(f.controller.state.value.mutations.isPending(target))
            assertNotNull(f.controller.state.value.mutations.failure(target), "the failure stays beside the control")

            f.controller.retryMutation(target)
            advanceUntilIdle()
            assertEquals(2, puts, "Retry runs the same write again")
            assertNull(f.controller.state.value.mutations.failure(target))
        } finally { f.close() }
    }

    @Test fun removingFromTheLibraryLeavesTheRowAtOnceAndComesBackWhenTheWriteFails() = runTest {
        val release = CompletableDeferred<Unit>()
        val f = Fixture(this, testScheduler) { request ->
            when {
                request.method == HttpMethod.Delete -> { release.await(); respond("nope", HttpStatusCode.InternalServerError) }
                request.url.encodedPath.endsWith("/membership") -> respond(membership("one"), headers = json)
                else -> respond(library("one"), headers = json)
            }
        }
        try {
            f.controller.updateLibrary(listOf(card("one")))
            assertTrue(f.controller.state.value.isSaved(card("one")))
            f.controller.removeFromMyList(card("one"))
            runCurrent()
            assertFalse(f.controller.state.value.isSaved(card("one")))
            assertTrue(f.controller.state.value.library.isEmpty(), "the row leaves in the frame of the press")
            release.complete(Unit)
            advanceUntilIdle()
            assertTrue(f.controller.state.value.library.isNotEmpty(), "a failed removal brings the row back")
            assertTrue(f.controller.state.value.isSaved(card("one")))
            assertNotNull(f.controller.state.value.mutations.failure(saveTarget(card("one"))))
        } finally { release.complete(Unit); f.close() }
    }
}
