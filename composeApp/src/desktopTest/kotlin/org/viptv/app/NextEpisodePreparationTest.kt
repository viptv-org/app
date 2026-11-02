package org.viptv.app

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.content.TextContent
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.*
import org.viptv.app.connection.*
import org.viptv.app.player.PlaybackInput
import org.viptv.app.player.PlayerFact
import org.viptv.app.state.*
import org.viptv.engine.wire.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class NextEpisodePreparationTest {
    private val store = object : RemoteSessionStore {
        override suspend fun read(): SavedRemoteSession? = null
        override suspend fun save(session: SavedRemoteSession) = Unit
        override suspend fun clear() = Unit
    }
    private val parent = Card(MediaIdentity("parent", "series", emptyList()), provider_id = "addon:7", title = "Series", badges = emptyList())
    private fun episode(number: Long) = parent.copy(identity = MediaIdentity("episode-$number", "series", emptyList(), EpisodeCoordinates("aired", 1, number)),
        parent_identity = parent.identity)
    private fun detail() = DetailSnapshot(parent, episodes = (1L..4L).map(::episode))
    private val nextSource = SourceCandidate("next-stream", "addon:1", "Next 1080p")

    private fun input(id: String = "attempt", live: Boolean = false) =
        PlaybackInput(id, "https://media.example/fixture.mp4", profileId = "profile", live = live)

    private fun state(card: Long = 1, next: Card? = episode(2), playing: PlaybackInput = input(),
        preferences: ProfilePreferences = ProfilePreferences(source_selection = "best")) = ApplicationState(
        screen = Screen.Player, selectedProfile = "profile", playback = playing, sourceTitle = episode(card), nextEpisode = next,
        detail = detail(), preferences = PreferencesSnapshot(1, preferences))

    private fun TestScope.controller(initial: ApplicationState = state(), server: IntArray = IntArray(1)): ApplicationController {
        val client = HttpClient(MockEngine) {
            engine {
                dispatcher = StandardTestDispatcher(testScheduler)
                addHandler { request ->
                    val path = request.url.encodedPath
                    fun json(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
                        respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
                    when {
                        path == "/api/playback" && request.method == HttpMethod.Post -> {
                            val body = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
                            server[0]++
                            json("""{"id":"lease-${server[0]}","status":"ready","renew_after_seconds":20,
                                "delivery":{"kind":"direct","url":"https://media.example/${body.getValue("stream_id").jsonPrimitive.content}.mp4","position":0}}""")
                        }
                        path.endsWith("/heartbeat") || path == "/api/playback" -> json("{}")
                        path.startsWith("/api/playback/") && request.method == HttpMethod.Delete -> json("{}")
                        path.endsWith("/progress") -> json("{}")
                        else -> json("{}", HttpStatusCode.NotFound)
                    }
                }
            }
            configureTransport()
        }
        val remote = RemoteConnection("https://watch.example", store, client, browserMode = false)
        val controller = ApplicationController(backgroundScope, store)
        controller.connection = remote
        controller.leaseOpener = PlaybackLeaseOpener { connection, candidate, profile, forceGateway, position, stage ->
            connection.openPlayback(candidate, profile, buildJsonObject { put("platform", "desktop"); put("can_play_direct", true) },
                forceGateway, position, torrents = null, stage = stage)
        }
        controller.mutable.value = initial
        return controller
    }

    private fun ApplicationController.progress(position: Long, duration: Long? = 1_400_000) =
        observePlaybackFor(state.value.playback, PlayerFact.Progress(position, duration))

    @Test fun theLastThreeMinutesPrepareOneNextSourceAndNextHandsOffToIt() = runTest {
        val opens = IntArray(1); val controller = controller(server = opens)
        var prepared = 0
        controller.nextSourcePreparer = NextSourcePreparer { _, _, _, _ ->
            prepared++
            PreparedNextSource(nextSource, listOf(nextSource), SourceGroups.of(listOf(nextSource.id), nextSource.id))
        }
        controller.progress(800_000); runCurrent()
        assertEquals(0, prepared, "Six minutes left is too early")
        assertNull(controller.state.value.nextEpisodePreparation)
        controller.progress(1_220_000); runCurrent()
        assertEquals(1, prepared)
        val ready = assertNotNull(controller.state.value.nextEpisodePreparation)
        assertEquals(NextEpisodePreparationStatus.Ready, ready.status)
        assertEquals(nextSource, ready.source); assertEquals("episode-2", ready.card.identity.id)
        controller.progress(1_300_000); controller.progress(1_320_000); runCurrent()
        assertEquals(1, prepared, "One preparation per episode and preference generation")
        assertEquals(0, opens[0], "Preparing never opens a source")

        controller.playNextEpisode(episode(2)); runCurrent()
        val state = controller.state.value
        assertEquals(Screen.Player, state.screen, "No source picker")
        assertEquals("episode-2", state.sourceTitle?.identity?.id)
        assertEquals("next-stream", state.playingSource?.id)
        assertEquals("lease-1", state.playback?.id); assertEquals(1, opens[0], "No duplicate open")
        assertNull(state.nextEpisodePreparation, "Handoff consumes the preparation")
        controller.closePlayer()
    }

    @Test fun seekingIntoTheLastThreeMinutesStartsPreparation() = runTest {
        val controller = controller()
        var prepared = 0
        controller.nextSourcePreparer = NextSourcePreparer { _, _, _, _ -> prepared++; PreparedNextSource(nextSource, listOf(nextSource), SourceGroups()) }
        controller.progress(900_000); runCurrent(); assertEquals(0, prepared)
        controller.progress(1_230_000); runCurrent()
        assertEquals(1, prepared)
        assertEquals(NextEpisodePreparationStatus.Ready, controller.state.value.nextEpisodePreparation?.status)
        controller.closePlayer()
    }

    @Test fun unknownDurationMoviesLiveStreamsAndFinalesNeverPrepare() = runTest {
        val movie = parent.copy(identity = MediaIdentity("movie", "movie", emptyList()))
        val cases = listOf(
            Triple("unknown duration", state(), null),
            Triple("movie", state().let { it.copy(sourceTitle = movie) }, 1_400_000L),
            Triple("live", state(playing = input(live = true)), 1_400_000L),
            Triple("finale", state(next = null), 1_400_000L),
            Triple("always ask", state(preferences = ProfilePreferences(source_selection = "ask")), 1_400_000L),
        )
        for ((label, initial, duration) in cases) {
            val controller = controller(initial = initial)
            var prepared = 0
            controller.nextSourcePreparer = NextSourcePreparer { _, _, _, _ -> prepared++; null }
            controller.observePlaybackFor(initial.playback, PlayerFact.Progress(1_350_000, duration))
            runCurrent()
            assertEquals(0, prepared, label)
            assertNull(controller.state.value.nextEpisodePreparation, label)
            controller.closePlayer()
        }
    }

    @Test fun aSourcePreferenceChangeReleasesThePreparedSourceAndPreparesAgain() = runTest {
        val controller = controller()
        var prepared = 0
        controller.nextSourcePreparer = NextSourcePreparer { _, _, _, _ -> prepared++; PreparedNextSource(nextSource, listOf(nextSource), SourceGroups()) }
        controller.progress(1_300_000); runCurrent(); assertEquals(1, prepared)
        controller.mutable.value = controller.state.value.copy(preferences = PreferencesSnapshot(2, ProfilePreferences(source_selection = "best")))
        controller.progress(1_310_000); runCurrent()
        assertEquals(2, prepared)
        assertEquals(NextEpisodePreparationStatus.Ready, controller.state.value.nextEpisodePreparation?.status)
        controller.mutable.value = controller.state.value.copy(preferences = PreferencesSnapshot(3, ProfilePreferences(source_selection = "ask")))
        controller.progress(1_320_000); runCurrent()
        assertEquals(2, prepared); assertNull(controller.state.value.nextEpisodePreparation)
        controller.closePlayer()
    }

    @Test fun anExpiredPreparationIsPreparedAgainBeforeItIsUsed() = runTest {
        val controller = controller()
        var now = 5_000_000L
        controller.clockMillis = { now }
        var prepared = 0
        controller.nextSourcePreparer = NextSourcePreparer { _, _, _, _ -> prepared++; PreparedNextSource(nextSource, listOf(nextSource), SourceGroups()) }
        controller.progress(1_300_000); runCurrent(); assertEquals(1, prepared)
        now += PREPARED_NEXT_EPISODE_TTL_MILLIS - 1
        controller.progress(1_310_000); runCurrent()
        assertEquals(1, prepared, "A live preparation is reused")
        now += 1
        controller.progress(1_320_000); runCurrent()
        assertEquals(2, prepared)
        assertEquals(NextEpisodePreparationStatus.Ready, controller.state.value.nextEpisodePreparation?.status)
        controller.closePlayer()
    }

    @Test fun withoutAFullyMatchingSourceNextFallsBackToThePicker() = runTest {
        val controller = controller()
        controller.nextSourcePreparer = NextSourcePreparer { _, _, _, _ -> null }
        controller.progress(1_300_000); runCurrent()
        assertEquals(NextEpisodePreparationStatus.Unavailable, controller.state.value.nextEpisodePreparation?.status)
        assertNull(controller.state.value.nextEpisodePreparation?.source)
        controller.playNextEpisode(episode(2)); runCurrent()
        assertEquals(Screen.Sources, controller.state.value.screen)
        assertNull(controller.state.value.playback, "The picker opens instead of a prepared handoff")
        controller.closePlayer()
    }

    @Test fun aFailedPreparationNeverBreaksTheCurrentEpisode() = runTest {
        val controller = controller()
        controller.nextSourcePreparer = NextSourcePreparer { _, _, _, _ -> throw java.io.IOException("offline") }
        val playing = controller.state.value.playback
        controller.progress(1_300_000); runCurrent()
        val state = controller.state.value
        assertEquals(NextEpisodePreparationStatus.Unavailable, state.nextEpisodePreparation?.status)
        assertSame(playing, state.playback); assertFalse(state.paused); assertNull(state.error); assertEquals(Screen.Player, state.screen)
        controller.closePlayer()
    }

    @Test fun leavingThePlayerReleasesThePreparationAndItsRequest() = runTest {
        val controller = controller()
        val started = CompletableDeferred<Unit>()
        controller.nextSourcePreparer = NextSourcePreparer { _, _, _, _ -> started.complete(Unit); awaitCancellation() }
        controller.progress(1_300_000); runCurrent()
        assertTrue(started.isCompleted)
        val job = assertNotNull(controller.nextEpisodePreparationJob)
        controller.closePlayer(); runCurrent()
        assertTrue(job.isCancelled)
        assertNull(controller.state.value.nextEpisodePreparation)
        assertEquals(Screen.Sources, controller.state.value.screen)
    }

    @Test fun theDefaultPreparerPicksTheFullyMatchingBestSource() = runTest {
        val json = headersOf(HttpHeaders.ContentType, "application/json")
        var fully = true
        val client = HttpClient(MockEngine) {
            engine {
                dispatcher = StandardTestDispatcher(testScheduler)
                addHandler { request ->
                    when {
                        request.url.encodedPath == "/api/streams" && request.method == HttpMethod.Post ->
                            respond("""{"id":"job-1"}""", HttpStatusCode.OK, json)
                        request.url.encodedPath == "/api/streams/job-1" -> respond(
                            """{"events":[{"source":"p1","streams":[{"id":"s1","title":"2160p"}]},{"source":"p2","streams":[{"id":"s2","title":"1080p"}]}],"done":true}""",
                            HttpStatusCode.OK, json)
                        request.url.encodedPath.endsWith("/source-ranking") -> respond(
                            """{"streams":[{"id":"s2"},{"id":"s1"}],"best_match_ids":["s2"],"best_match_id":"s2","best_match_fully_matches":$fully}""",
                            HttpStatusCode.OK, json)
                        else -> respond("{}", HttpStatusCode.NotFound, json)
                    }
                }
            }
            configureTransport()
        }
        val remote = RemoteConnection("https://watch.example", store, client, browserMode = false)
        val controller = ApplicationController(backgroundScope, store); controller.connection = remote
        controller.mutable.value = state()
        controller.progress(1_300_000); advanceTimeBy(2_000); runCurrent()
        val prepared = assertNotNull(controller.state.value.nextEpisodePreparation)
        assertEquals(NextEpisodePreparationStatus.Ready, prepared.status)
        assertEquals("s2", prepared.source?.id)
        assertEquals(listOf("s2", "s1"), prepared.sources.map { it.id })

        fully = false
        controller.closePlayer(); runCurrent()
        controller.mutable.value = state()
        controller.progress(1_300_000); advanceTimeBy(2_000); runCurrent()
        assertEquals(NextEpisodePreparationStatus.Unavailable, controller.state.value.nextEpisodePreparation?.status)
        assertNull(controller.state.value.nextEpisodePreparation?.source)
        controller.closePlayer()
    }
}