package org.viptv.app

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.client.request.HttpRequestData
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.Json
import org.viptv.app.connection.*
import org.viptv.app.state.*
import org.viptv.engine.wire.*
import kotlin.test.*

class LiveControllerTest {
    private fun channel(id: String) = LiveCatalogChannel(id, "m3u:1", Card(MediaIdentity(id, "live", emptyList()), provider_id = "m3u:1", title = id, badges = emptyList()))
    private fun page(vararg ids: String, next: String? = null) = Json.encodeToString(LiveCatalogPage(ids.map(::channel), next_cursor = next, complete = next == null, availability = "Available"))
    private fun guide(id: String, available: Boolean = true) = Json.encodeToString(LiveGuideSnapshot(id,
        programs = if (available) listOf(LiveGuideProgram("program", "Program", 1000, 2000)) else emptyList(),
        availability = if (available) "Available" else "Unsupported", reason = if (available) null else "No XMLTV guide configured for this channel"))
    private inner class Fixture(scope: CoroutineScope, scheduler: TestCoroutineScheduler, reply: suspend (HttpRequestData) -> String) {
        val store = object : RemoteSessionStore {
            override suspend fun read(): SavedRemoteSession? = null
            override suspend fun save(session: SavedRemoteSession) = Unit
            override suspend fun clear() = Unit
        }
        val client = HttpClient(MockEngine) {
            engine { dispatcher = StandardTestDispatcher(scheduler); addHandler { request -> respond(reply(request), headers = headersOf(HttpHeaders.ContentType, "application/json")) } }
            configureTransport()
        }
        val remote = RemoteConnection("https://watch.example", store, client, browserMode = false)
        val controller = ApplicationController(scope, store).also {
            it.connection = remote
            it.mutable.value = ApplicationState(screen = Screen.Live, selectedProfile = "1", livePage = LiveCatalogPage(listOf(channel("one"), channel("two")), availability = "Available"), liveFavoritesAvailable = true)
        }
        fun close() { controller.job?.cancel(); controller.liveGuideJob?.cancel(); controller.liveFavoriteJobs.values.toList().forEach { it.cancel() }; remote.close() }
    }

    @Test fun favoriteToggleIsDeduplicatedUsesProfileRoutesAndDoesNotEnterVodLibrary() = runTest {
        val finish = CompletableDeferred<Unit>(); val writes = mutableListOf<HttpMethod>()
        val f = Fixture(this, testScheduler) { request ->
            assertEquals("/api/profiles/1/live/one/favorite", request.url.encodedPath)
            writes += request.method
            if (request.method == HttpMethod.Put) { finish.await(); """{"channel_ids":["one"]}""" } else """{"channel_ids":[]}"""
        }
        try {
            f.controller.toggleLiveFavorite("one"); f.controller.toggleLiveFavorite("one")
            runCurrent(); assertEquals(listOf(HttpMethod.Put), writes); assertEquals(setOf("one"), f.controller.state.value.liveFavoritePending)
            finish.complete(Unit); advanceUntilIdle()
            assertEquals(setOf("one"), f.controller.state.value.liveFavorites); assertTrue(f.controller.state.value.liveFavoritePending.isEmpty())
            assertTrue(f.controller.state.value.library.isEmpty())
            f.controller.toggleLiveFavorite("one"); advanceUntilIdle()
            assertEquals(listOf(HttpMethod.Put, HttpMethod.Delete), writes); assertTrue(f.controller.state.value.liveFavorites.isEmpty())
        } finally { finish.complete(Unit); f.close() }
    }

    @Test fun siblingMutationResponsesCannotUndoEachOtherWhenSnapshotsArriveOutOfOrder() = runTest {
        val first = CompletableDeferred<Unit>(); val second = CompletableDeferred<Unit>()
        val f = Fixture(this, testScheduler) { request ->
            if (request.url.encodedPath.contains("/one/")) { first.await(); """{"channel_ids":["one"]}""" }
            else { second.await(); """{"channel_ids":["one","two"]}""" }
        }
        try {
            f.controller.toggleLiveFavorite("one"); f.controller.toggleLiveFavorite("two"); runCurrent()
            second.complete(Unit); runCurrent(); assertEquals(setOf("two"), f.controller.state.value.liveFavorites)
            first.complete(Unit); advanceUntilIdle(); assertEquals(setOf("one", "two"), f.controller.state.value.liveFavorites)
        } finally { first.complete(Unit); second.complete(Unit); f.close() }
    }

    @Test fun lateFavoriteResponseCannotChangeAnotherProfile() = runTest {
        val finish = CompletableDeferred<Unit>()
        val f = Fixture(this, testScheduler) { finish.await(); """{"channel_ids":["one"]}""" }
        try {
            f.controller.toggleLiveFavorite("one"); runCurrent()
            f.controller.mutable.value = ApplicationState(screen = Screen.Live, selectedProfile = "2", liveFavorites = setOf("other"))
            finish.complete(Unit); advanceUntilIdle()
            assertEquals(setOf("other"), f.controller.state.value.liveFavorites); assertTrue(f.controller.state.value.liveFavoritePending.isEmpty())
        } finally { finish.complete(Unit); f.close() }
    }

    @Test fun failedGuideCanRetryButValidGuideUsesCacheAndForcedRetryDoesNotReloadChannels() = runTest {
        var reads = 0
        val f = Fixture(this, testScheduler) { request ->
            assertTrue(request.url.encodedPath.endsWith("/guide")); reads++
            if (reads == 1) """{"channel_id":"one","availability":"TemporarilyUnavailable","reason":"Try again"}""" else guide("one")
        }
        try {
            f.controller.showLiveGuide("one"); advanceUntilIdle(); assertEquals(1, reads)
            f.controller.showLiveGuide("one"); advanceUntilIdle(); assertEquals(2, reads)
            f.controller.showLiveGuide("one"); advanceUntilIdle(); assertEquals(2, reads)
            f.controller.retryLiveGuide(); advanceUntilIdle(); assertEquals(3, reads)
            assertEquals("Available", f.controller.state.value.liveGuides["one"]?.availability); assertFalse(f.controller.state.value.liveGuideLoading)
        } finally { f.close() }
    }

    @Test fun guideResponseIsFencedByCurrentChannelProfileAndVisibleLiveRoute() = runTest {
        val finish = CompletableDeferred<Unit>()
        val f = Fixture(this, testScheduler) { finish.await(); guide("one") }
        try {
            f.controller.showLiveGuide("one"); advanceTimeBy(250); runCurrent()
            f.controller.mutable.value = f.controller.state.value.copy(screen = Screen.Home)
            finish.complete(Unit); advanceUntilIdle(); assertTrue(f.controller.state.value.liveGuides.isEmpty())
            f.controller.mutable.value = ApplicationState(screen = Screen.Live, selectedProfile = "2", livePage = LiveCatalogPage(listOf(channel("two"))))
            f.controller.showLiveGuide("one"); advanceUntilIdle(); assertTrue(f.controller.state.value.liveGuides.isEmpty())
        } finally { finish.complete(Unit); f.close() }
    }

    @Test fun favoritesFilterAutomaticallyTraversesEmptyFilteredPagesWithoutManualLoadMore() = runTest {
        val offsets = mutableListOf<String>()
        val f = Fixture(this, testScheduler) { request -> when {
            request.url.encodedPath.endsWith("/favorites") -> """{"channel_ids":["two"]}"""
            request.url.encodedPath.endsWith("/guide") -> guide("two", false)
            else -> { val offset = request.url.parameters["offset"] ?: "0"; offsets += offset; if (offset == "0") page("one", next = "100") else page("two", next = "200") }
        } }
        try {
            f.controller.filterLiveFavorites(); advanceUntilIdle()
            assertEquals(listOf("0", "100"), offsets); assertTrue(f.controller.state.value.liveFavoritesOnly)
            assertEquals(listOf("two"), f.controller.state.value.livePage!!.channels.filter { it.id in f.controller.state.value.liveFavorites }.map { it.id })
            f.controller.moreLive(); advanceUntilIdle(); assertEquals(listOf("0", "100"), offsets, "All known favourites are loaded; do not scan unrelated pages")
            assertEquals("two", f.controller.state.value.liveGuideChannel)
            assertEquals("Unsupported", f.controller.state.value.liveGuides["two"]?.availability)
            assertTrue(f.controller.state.value.liveFavoritesAvailable, "A missing EPG does not disable host favourites")
        } finally { f.close() }
    }

    @Test fun emptyFavoriteSnapshotDoesNotScanEntireDirectoryAndRepeatedCursorFailsSafely() = runTest {
        var reads = 0; var saved = false
        val f = Fixture(this, testScheduler) { request -> when {
            request.url.encodedPath.endsWith("/favorites") -> if (saved) """{"channel_ids":["two"]}""" else """{"channel_ids":[]}"""
            request.url.encodedPath.endsWith("/guide") -> guide("one", false)
            else -> { reads++; page("one", next = "0") }
        } }
        try {
            f.controller.filterLiveFavorites(); advanceUntilIdle(); assertEquals(1, reads); assertNull(f.controller.state.value.error)
            saved = true; f.controller.filterLiveFavorites(); advanceUntilIdle()
            assertEquals(2, reads); assertContains(f.controller.state.value.error.orEmpty(), "continue loading channels")
        } finally { f.close() }
    }
    @Test fun sparseFavoritePaginationContinuesAcrossPagesWithNoNewVisibleMatches() = runTest {
        val offsets = mutableListOf<String>()
        val f = Fixture(this, testScheduler) { request -> when {
            request.url.encodedPath.endsWith("/favorites") -> """{"channel_ids":["one","four"]}"""
            request.url.encodedPath.endsWith("/guide") -> guide("one", false)
            else -> { val offset = request.url.parameters["offset"] ?: "0"; offsets += offset
                when (offset) { "0" -> page("one", next = "100"); "100" -> page("two", next = "200"); else -> page("four") }
            }
        } }
        try {
            f.controller.filterLiveFavorites(); advanceUntilIdle(); assertEquals(listOf("0"), offsets)
            f.controller.moreLive(); advanceUntilIdle()
            assertEquals(listOf("0", "100", "200"), offsets)
            assertEquals(setOf("one", "four"), f.controller.state.value.livePage!!.channels.filter { it.id in f.controller.state.value.liveFavorites }.map { it.id }.toSet())
        } finally { f.close() }
    }

    private fun connections() = listOf("m3u:1" to "Playlist", "xtream:2" to "Other feed").map { (id, name) ->
        IntegrationConnection(id, if (id.startsWith("m3u")) "m3u" else "xtream", "local", "1", name, true, 0, listOf(Capability.LIVETV))
    }
    private fun feedPage(feed: String, vararg ids: String, next: String? = null) = Json.encodeToString(LiveCatalogPage(
        ids.map { channel(it).let { channel -> channel.copy(connection_id = feed, card = channel.card.copy(provider_id = feed)) } },
        next_cursor = next, complete = next == null, availability = "Available", connections = connections()))

    @Test fun advertisedHostConnectionsSelectTheFeedAndResetItsGuideAndPaging() = runTest {
        val queries = mutableListOf<Parameters>()
        val f = Fixture(this, testScheduler) { request -> when {
            request.url.encodedPath.endsWith("/favorites") -> """{"channel_ids":["one"]}"""
            request.url.encodedPath.endsWith("/guide") -> guide(if (request.url.encodedPath.contains("/one/")) "one" else "three", false)
            else -> { queries += request.url.parameters; val feed = request.url.parameters["connection_id"] ?: "m3u:1"; feedPage(feed, if (feed == "m3u:1") "one" else "three", next = "100") }
        } }
        try {
            f.controller.refreshLive(); advanceUntilIdle()
            assertEquals(listOf("m3u:1", "xtream:2"), f.controller.state.value.livePage!!.connections.map { it.id })
            assertEquals("m3u:1", f.controller.state.value.liveQuery.connection_id)
            f.controller.mutable.value = f.controller.state.value.copy(liveQuery = f.controller.state.value.liveQuery.copy(group_id = "old-group", offset = 500))
            f.controller.selectLiveConnection("xtream:2"); advanceUntilIdle()
            assertEquals("xtream:2", queries.last()["connection_id"]); assertEquals("0", queries.last()["offset"]); assertNull(queries.last()["group_id"])
            assertEquals(listOf("three"), f.controller.state.value.livePage!!.channels.map { it.id })
            assertFalse(f.controller.state.value.liveGuides.containsKey("one"))
            val before = queries.size; f.controller.selectLiveConnection("foreign-feed"); advanceUntilIdle(); assertEquals(before, queries.size)
        } finally { f.close() }
    }

    @Test fun favoritesFromAnotherFeedScanOnlySelectedFeedAndNeverExposeItsChannels() = runTest {
        val feeds = mutableListOf<String>()
        val f = Fixture(this, testScheduler) { request -> when {
            request.url.encodedPath.endsWith("/favorites") -> """{"channel_ids":["from-other-feed"]}"""
            else -> { val feed = request.url.parameters["connection_id"] ?: "m3u:1"; feeds += feed
                if (request.url.parameters["offset"] == "100") feedPage(feed, "two") else feedPage(feed, "one", next = "100") }
        } }
        try {
            f.controller.mutable.value = f.controller.state.value.copy(livePage = LiveCatalogPage(connections = connections()))
            f.controller.filterLiveFavorites(); advanceUntilIdle()
            assertEquals(listOf("m3u:1", "m3u:1"), feeds); assertTrue(f.controller.state.value.livePage!!.complete)
            assertTrue(f.controller.state.value.livePage!!.channels.none { it.id in f.controller.state.value.liveFavorites })
            assertTrue(f.controller.state.value.livePage!!.channels.all { it.connection_id == "m3u:1" }); assertNull(f.controller.state.value.error)
        } finally { f.close() }
    }

    @Test fun lateGuideCannotPublishAfterSelectingADifferentFeed() = runTest {
        val old = CompletableDeferred<Unit>()
        val f = Fixture(this, testScheduler) { request -> when {
            request.url.encodedPath.endsWith("/favorites") -> """{"channel_ids":[]}"""
            request.url.encodedPath.contains("/one/guide") -> withContext(NonCancellable) { old.await(); guide("one") }
            request.url.encodedPath.endsWith("/guide") -> guide("three", false)
            else -> feedPage("xtream:2", "three")
        } }
        try {
            f.controller.mutable.value = f.controller.state.value.copy(livePage = LiveCatalogPage(listOf(channel("one")), connections = connections()))
            f.controller.showLiveGuide("one"); advanceTimeBy(250); runCurrent()
            f.controller.selectLiveConnection("xtream:2"); runCurrent(); old.complete(Unit); advanceUntilIdle()
            assertFalse(f.controller.state.value.liveGuides.containsKey("one")); assertEquals("three", f.controller.state.value.liveGuideChannel)
        } finally { old.complete(Unit); f.close() }
    }

}
