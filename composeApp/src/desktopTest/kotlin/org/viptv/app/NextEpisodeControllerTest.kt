package org.viptv.app

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.*
import io.ktor.http.content.TextContent
import kotlinx.serialization.json.*
import org.viptv.app.connection.*
import org.viptv.app.player.PlaybackInput
import org.viptv.app.player.PlayerFact
import org.viptv.app.state.*
import org.viptv.engine.wire.*
import kotlin.test.*

class NextEpisodeControllerTest {
    private val store = object : RemoteSessionStore {
        override suspend fun read(): SavedRemoteSession? = null
        override suspend fun save(session: SavedRemoteSession) = Unit
        override suspend fun clear() = Unit
    }
    private val parent = Card(MediaIdentity("parent", "series", emptyList()), provider_id = "addon:7", title = "Series", badges = emptyList())
    private fun episode(number: Long) = parent.copy(identity = MediaIdentity("episode-$number", "series", emptyList(), EpisodeCoordinates("aired", 1, number)), parent_identity = parent.identity)
    private fun detail() = DetailSnapshot(parent, episodes = (1L..24L).map(::episode), next_cursor = "24")
    private fun input(id: String = "attempt", profile: String = "owner") = PlaybackInput(id, "https://media.example/fixture.mp4", profileId = profile)
    private fun ApplicationController.prepare(input: PlaybackInput, card: Card, profile: String = "owner") {
        mutable.value = ApplicationState(screen = Screen.Player, selectedProfile = profile, playback = input, sourceTitle = card, detail = detail())
    }

    @Test fun nextBeyondTheLoadedPageIsResolvedWithoutInterruptingCurrentPlayback() = runBlocking<Unit> {
        var requests = 0
        val client = HttpClient(MockEngine { request ->
            requests++; assertEquals("/api/profiles/owner/continue/next", request.url.encodedPath)
            respond(Json.encodeToString(NextEpisodeResult(ContinuationStatus.NEXT, episode(25))), headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureTransport() }
        val remote = RemoteConnection("https://watch.example", store, client, browserMode = false)
        val controller = ApplicationController(this, store); controller.connection = remote
        val playing = input(); controller.prepare(playing, episode(24))
        try {
            controller.loadNextEpisode(); controller.loadNextEpisode(); controller.nextEpisodeJob!!.join()
            assertEquals(1, requests); assertEquals("episode-25", controller.state.value.nextEpisode?.identity?.id)
            assertEquals(24, controller.state.value.detail?.episodes?.size)
            assertSame(playing, controller.state.value.playback); assertEquals(Screen.Player, controller.state.value.screen)
            assertFalse(controller.state.value.busy); assertFalse(controller.state.value.nextEpisodeLoading); assertNull(controller.state.value.error)
        } finally { controller.cancelNextEpisode(); remote.close() }
    }

    @Test fun aLateResponseCannotReplaceTheNextEpisodeOfAnotherProfileAndPlaybackInput() = runBlocking<Unit> {
        val started = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val client = HttpClient(MockEngine { request ->
            val card = if (request.url.encodedPath.contains("/owner/")) withContext(NonCancellable) {
                started.complete(Unit); release.await(); episode(25)
            } else episode(3)
            respond(Json.encodeToString(NextEpisodeResult(ContinuationStatus.NEXT, card)), headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureTransport() }
        val remote = RemoteConnection("https://watch.example", store, client, browserMode = false)
        val controller = ApplicationController(this, store); controller.connection = remote
        controller.prepare(input(), episode(24))
        try {
            controller.loadNextEpisode(); val oldJob = controller.nextEpisodeJob!!; withTimeout(2_000) { started.await() }
            val replacement = input("replacement", "other"); controller.prepare(replacement, episode(2), "other")
            controller.loadNextEpisode(); withTimeout(2_000) { controller.nextEpisodeJob!!.join() }
            release.complete(Unit); oldJob.join()
            assertEquals("episode-3", controller.state.value.nextEpisode?.identity?.id)
            assertSame(replacement, controller.state.value.playback); assertEquals("other", controller.state.value.selectedProfile)
            assertNull(controller.state.value.error)
        } finally { release.complete(Unit); controller.cancelNextEpisode(); remote.close() }
    }

    @Test fun unavailableAndWrongProviderResultsNeverCreateAnActionOrMediaError() = runBlocking<Unit> {
        for (result in listOf(NextEpisodeResult(ContinuationStatus.UPCOMING, episode(25)), NextEpisodeResult(ContinuationStatus.NEXT, episode(25).copy(provider_id = "addon:8")))) {
            val client = HttpClient(MockEngine { respond(Json.encodeToString(result), headers = headersOf(HttpHeaders.ContentType, "application/json")) }) { configureTransport() }
            val remote = RemoteConnection("https://watch.example", store, client, browserMode = false)
            val controller = ApplicationController(this, store); controller.connection = remote
            val playing = input(); controller.prepare(playing, episode(24))
            try {
                controller.loadNextEpisode(); controller.nextEpisodeJob!!.join()
                assertNull(controller.state.value.nextEpisode); assertSame(playing, controller.state.value.playback); assertNull(controller.state.value.error)
                assertEquals(if (result.status == ContinuationStatus.UPCOMING) ContinuationStatus.UPCOMING else ContinuationStatus.UNAVAILABLE, controller.state.value.nextEpisodeStatus)
            } finally { controller.cancelNextEpisode(); remote.close() }
        }
    }

    @Test fun oldServersUseOnlyAnActualLoadedSuccessorAndNeverInventTheUnloadedOne() = runBlocking<Unit> {
        val client = HttpClient(MockEngine { respond("{}", HttpStatusCode.NotFound, headersOf(HttpHeaders.ContentType, "application/json")) }) { configureTransport() }
        val remote = RemoteConnection("https://watch.example", store, client, browserMode = false)
        val controller = ApplicationController(this, store); controller.connection = remote
        try {
            controller.prepare(input(), episode(1)); controller.loadNextEpisode(); controller.nextEpisodeJob!!.join()
            assertEquals("episode-2", controller.state.value.nextEpisode?.identity?.id)
            controller.prepare(input("last"), episode(24)); controller.loadNextEpisode(); controller.nextEpisodeJob!!.join()
            assertNull(controller.state.value.nextEpisode); assertEquals(ContinuationStatus.UNAVAILABLE, controller.state.value.nextEpisodeStatus)
        } finally { controller.cancelNextEpisode(); remote.close() }
    }

    @Test fun localPlaybackAutomaticallyPagesActualMetadataForTheNextEpisode() = runBlocking<Unit> {
        val fixture = LocalSetupFixture(); var pages = 0
        val local = object : LocalConnection by fixture.local {
            override suspend fun detailPage(profile: String, card: Card, offset: Int, limit: Int): DetailSnapshot {
                pages++; assertEquals(24, offset); assertEquals(96, limit)
                return DetailSnapshot(parent, episodes = (25L..120L).map(::episode), next_cursor = "120")
            }
        }
        val controller = fixture.controller(); controller.local = local; controller.prepare(input(), episode(24))
        try {
            controller.loadNextEpisode(); withTimeout(5_000) { controller.nextEpisodeJob!!.join() }
            assertEquals(1, pages); assertEquals("episode-25", controller.state.value.nextEpisode?.identity?.id)
            assertNull(controller.state.value.error)
        } finally { fixture.close(controller) }
    }

    @Test fun closingPlaybackCancelsTheOptionalLookupAndClearsItsAction() = runBlocking<Unit> {
        val started = CompletableDeferred<Unit>()
        val client = HttpClient(MockEngine { started.complete(Unit); awaitCancellation() }) { configureTransport() }
        val remote = RemoteConnection("https://watch.example", store, client, browserMode = false)
        val controller = ApplicationController(this, store); controller.connection = remote; controller.prepare(input(), episode(24))
        try {
            controller.loadNextEpisode(); val pending = controller.nextEpisodeJob!!; withTimeout(2_000) { started.await() }
            controller.closePlayer(); withTimeout(2_000) { pending.join() }
            assertNull(controller.state.value.nextEpisode); assertFalse(controller.state.value.nextEpisodeLoading)
            assertEquals(Screen.Sources, controller.state.value.screen)
        } finally { controller.cancelNextEpisode(); remote.close() }
    }
    @Test fun endOfPlaybackRetriesAnUnavailableNextWithoutStartingIt() = runBlocking<Unit> {
        var requests = 0
        val client = HttpClient(MockEngine {
            val result = if (++requests == 1) NextEpisodeResult(ContinuationStatus.UPCOMING) else NextEpisodeResult(ContinuationStatus.NEXT, episode(2))
            respond(Json.encodeToString(result), headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureTransport() }
        val remote = RemoteConnection("https://watch.example", store, client, browserMode = false)
        val controller = ApplicationController(this, store); controller.connection = remote
        val playing = input(); controller.prepare(playing, episode(1))
        try {
            controller.loadNextEpisode(); controller.nextEpisodeJob!!.join(); assertNull(controller.state.value.nextEpisode)
            controller.observePlaybackFor(playing, PlayerFact.Status("Ended")); controller.nextEpisodeJob!!.join()
            assertEquals(2, requests); assertEquals("episode-2", controller.state.value.nextEpisode?.identity?.id)
            assertSame(playing, controller.state.value.playback); assertTrue(controller.state.value.paused); assertEquals(Screen.Player, controller.state.value.screen)
        } finally { controller.cancelNextEpisode(); remote.close() }
    }

    /** app#41: the end panel's Next and the Next button both leave through closePlayer; the ended episode stays watched. */
    @Test fun theEndRecordsTheEpisodeWatchedAndMovingOnDoesNotOverwriteIt() = runBlocking<Unit> {
        val saved = java.util.Collections.synchronizedList(mutableListOf<JsonObject>())
        val json = headersOf(HttpHeaders.ContentType, "application/json")
        val client = HttpClient(MockEngine { request ->
            when {
                request.url.encodedPath == "/api/profiles/owner/progress" -> {
                    saved += Json.parseToJsonElement((request.body as TextContent).text).jsonObject; respond("{\"ok\":true}", headers = json)
                }
                request.url.encodedPath == "/api/profiles/owner/continue/next" ->
                    respond(Json.encodeToString(NextEpisodeResult(ContinuationStatus.NEXT, episode(25))), headers = json)
                else -> respond("{}", HttpStatusCode.NotFound, json)
            }
        }) { configureTransport() }
        val remote = RemoteConnection("https://watch.example", store, client, browserMode = false)
        val lane = CoroutineScope(coroutineContext + SupervisorJob())
        val controller = ApplicationController(lane, store); controller.connection = remote
        val playing = input(); controller.prepare(playing, episode(24))
        try {
            controller.observePlaybackFor(playing, PlayerFact.Progress(1_795_000, 1_800_000))
            controller.observePlaybackFor(playing, PlayerFact.Status("Ended"))
            val watched = controller.progressJob!!
            controller.closePlayer(); watched.join(); controller.progressJob?.join()
            assertEquals(1, saved.size, "Only the end of the episode is recorded")
            assertTrue(saved.single().getValue("ended").jsonPrimitive.boolean)
            assertEquals(1_800_000L, saved.single().getValue("record").jsonObject.getValue("position_millis").jsonPrimitive.long)
        } finally { controller.cancelNextEpisode(); lane.cancel(); remote.close() }
    }

}
