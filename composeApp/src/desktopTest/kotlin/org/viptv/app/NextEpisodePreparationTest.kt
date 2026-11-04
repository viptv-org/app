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

    private fun preparedSource(rows: List<SourceCandidate> = listOf(nextSource)) =
        PreparedNextSource(rows.first(), RankedSources(rows, SourceGroups.of(rows.map { it.id }, rows.first().id), bestMatchFullyMatches = true))

    private class Memory(var raw: String? = null) : SourceMemoryStore {
        override fun read(): String? = raw
        override fun write(value: String) { raw = value }
    }

    private fun row(id: String, languages: List<String> = emptyList()) =
        SourceCandidate(id, "addon:1", "Source $id", audio_languages = languages)

    private class PlaybackServer {
        var prepares = 0
        var opens = 0
        var promotes = 0
        var heartbeats = 0
        val events = mutableListOf<String>()
        var replaced = mutableListOf<String>()
        var failPromote = false
        var preparedReleased = false
    }

    private fun serverPreparer(server: PlaybackServer) = NextEpisodeDeliveryPreparer { remote, source, profile ->
        server.prepares++
        PreparedPlaybackLease("prep-${server.prepares}", 20, requireNotNull(remote), profile,
            PlaybackAdmission(buildJsonObject {
                put("request_id", "req-${server.prepares}"); put("stream_id", source.id)
                put("force_gateway", false); put("position", 0.0); put("resume", true)
            }, null, true))
    }

    private fun TestScope.controller(initial: ApplicationState = state(), server: PlaybackServer = PlaybackServer(), local: LocalConnection? = null): ApplicationController {
        val client = HttpClient(MockEngine) {
            engine {
                dispatcher = StandardTestDispatcher(testScheduler)
                addHandler { request ->
                    val path = request.url.encodedPath
                    fun json(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
                        respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
                    when {
                        path == "/api/playback" && request.method == HttpMethod.Post -> {
                            server.opens++; server.events += "open"
                            val body = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
                            json("""{"id":"lease-${server.opens}","status":"ready","renew_after_seconds":20,
                                "delivery":{"kind":"direct","url":"https://media.example/${body.getValue("stream_id").jsonPrimitive.content}.mp4","position":0}}""")
                        }
                        path.endsWith("/promote") && request.method == HttpMethod.Post -> {
                            server.promotes++; server.events += "promote"
                            val id = path.removePrefix("/api/playback/").removeSuffix("/promote")
                            val body = (request.body as? TextContent)?.text?.takeIf(String::isNotBlank)?.let { Json.parseToJsonElement(it).jsonObject }
                            body?.get("replaces")?.jsonPrimitive?.contentOrNull?.let { server.replaced += it }
                            if (server.failPromote) json("""{"id":"$id","status":"failed"}""")
                            else json("""{"id":"$id","status":"starting"}""")
                        }
                        path.endsWith("/heartbeat") && request.method == HttpMethod.Post -> {
                            server.heartbeats++; server.events += "heartbeat"
                            if (server.preparedReleased) respond("{}", HttpStatusCode.NotFound, headersOf(HttpHeaders.ContentType, "application/json"))
                            else json("{}")
                        }
                        request.method == HttpMethod.Get && path.startsWith("/api/playback/") && !path.removePrefix("/api/playback/").contains('/') -> {
                            val id = path.removePrefix("/api/playback/")
                            json("""{"id":"$id","status":"ready","renew_after_seconds":20,
                                "delivery":{"kind":"direct","url":"https://media.example/$id.mp4","position":0}}""")
                        }
                        request.method == HttpMethod.Delete && path.startsWith("/api/playback/") -> {
                            server.events += "delete:${path.removePrefix("/api/playback/")}"
                            json("{}")
                        }
                        path == "/api/auth/logout" && request.method == HttpMethod.Post -> json("{}")
                        path.endsWith("/progress") -> json("{}")
                        else -> json("{}", HttpStatusCode.NotFound)
                    }
                }
            }
            configureTransport()
        }
        val remote = RemoteConnection("https://watch.example", store, client, browserMode = false, workerDispatcher = StandardTestDispatcher(testScheduler))
        val controller = ApplicationController(this, store)
        controller.connection = remote
        controller.local = local
        controller.capability = { DeviceCapability() }
        controller.leaseOpener = PlaybackLeaseOpener { connection, candidate, profile, forceGateway, position, stage ->
            connection.openPlayback(candidate, profile, buildJsonObject { put("platform", "desktop"); put("can_play_direct", true) },
                forceGateway, position, torrents = null, stage = stage)
        }
        controller.nextEpisodeDeliveryPreparer = serverPreparer(server)
        controller.mutable.value = initial
        return controller
    }

    private fun ApplicationController.progress(position: Long, duration: Long? = 1_400_000) =
        observePlaybackFor(state.value.playback, PlayerFact.Progress(position, duration))

    private fun ApplicationController.playingLease() =
        RemotePlaybackLease(input(), 20, requireNotNull(connection))

    @Test fun theLastThreeMinutesPrepareOneDeliveryAndNextPromotesIt() = runTest {
        val server = PlaybackServer()
        val controller = controller(server = server)
        controller.remotePlayback = controller.playingLease()
        var prepared = 0
        controller.nextSourcePreparer = NextSourcePreparer { _, _, _, _, _, _ ->
            prepared++
            preparedSource()
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
        assertEquals(1, server.prepares, "One server delivery is prepared")
        assertEquals(0, server.opens, "Preparing never starts playback")
        assertNotNull(controller.nextEpisodePreparedLease, "The prepared delivery is held for handoff")

        controller.playEpisode(episode(2)); advanceTimeBy(2_000); runCurrent()
        val state = controller.state.value
        assertEquals(Screen.Player, state.screen, "No source picker")
        assertEquals("episode-2", state.sourceTitle?.identity?.id)
        assertEquals("next-stream", state.playingSource?.id)
        assertEquals("prep-1", state.playback?.id); assertEquals(0, server.opens, "The handoff promotes, it never starts fresh")
        assertEquals(1, server.promotes, "The handoff promotes the prepared delivery")
        assertEquals(listOf("attempt"), server.replaced, "Promotion releases the outgoing playback first")
        assertNull(state.nextEpisodePreparation, "Handoff consumes the preparation")
        controller.closePlayer()
    }

    @Test fun seekingIntoTheLastThreeMinutesStartsPreparation() = runTest {
        val server = PlaybackServer()
        val controller = controller(server = server)
        var prepared = 0
        controller.nextSourcePreparer = NextSourcePreparer { _, _, _, _, _, _ -> prepared++; preparedSource() }
        controller.progress(900_000); runCurrent(); assertEquals(0, prepared)
        controller.progress(1_230_000); runCurrent()
        assertEquals(1, prepared)
        assertEquals(1, server.prepares, "A prepared server delivery is admitted alongside the source pick")
        assertEquals(NextEpisodePreparationStatus.Ready, controller.state.value.nextEpisodePreparation?.status)
        controller.closePlayer()
    }

    @Test fun unknownDurationMoviesLiveStreamsAndFinalesNeverPrepare() = runTest {
        val server = PlaybackServer()
        val movie = parent.copy(identity = MediaIdentity("movie", "movie", emptyList()))
        val cases = listOf(
            Triple("unknown duration", state(), null),
            Triple("movie", state().let { it.copy(sourceTitle = movie) }, 1_400_000L),
            Triple("live", state(playing = input(live = true)), 1_400_000L),
            Triple("finale", state(next = null), 1_400_000L),
        )
        for ((label, initial, duration) in cases) {
            val controller = controller(initial = initial, server = server)
            var prepared = 0
            controller.nextSourcePreparer = NextSourcePreparer { _, _, _, _, _, _ -> prepared++; null }
            controller.observePlaybackFor(initial.playback, PlayerFact.Progress(1_350_000, duration))
            runCurrent()
            assertEquals(0, prepared, label)
            assertEquals(0, server.prepares, label)
            assertNull(controller.state.value.nextEpisodePreparation, label)
            controller.closePlayer()
        }
    }

    @Test fun alwaysAskProfilesPrepareTheControlledSuccessorAndHandItOff() = runTest {
        val server = PlaybackServer()
        val controller = controller(initial = state(preferences = ProfilePreferences(source_selection = "ask")), server = server)
        controller.remotePlayback = controller.playingLease()
        controller.nextSourcePreparer = NextSourcePreparer { _, _, _, _, _, _ -> preparedSource() }
        controller.progress(1_300_000); runCurrent()
        assertEquals(NextEpisodePreparationStatus.Ready, controller.state.value.nextEpisodePreparation?.status)
        controller.playEpisode(episode(2)); advanceTimeBy(2_000); runCurrent()
        assertEquals(Screen.Player, controller.state.value.screen)
        assertEquals("prep-1", controller.state.value.playback?.id)
        assertEquals(1, server.promotes); assertEquals(0, server.opens)
        controller.closePlayer()
    }

    @Test fun anotherEpisodeFromThePlayerStillAsksForASource() = runTest {
        val controller = controller(initial = state(preferences = ProfilePreferences(source_selection = "ask")))
        controller.nextSourcePreparer = NextSourcePreparer { _, _, _, _, _, _ -> preparedSource() }
        controller.progress(1_300_000); runCurrent()
        assertEquals(NextEpisodePreparationStatus.Ready, controller.state.value.nextEpisodePreparation?.status)
        controller.playEpisode(episode(3)); advanceTimeBy(2_000); runCurrent()
        assertEquals(Screen.Sources, controller.state.value.screen)
        assertNull(controller.state.value.playback)
        controller.closePlayer()
    }

    @Test fun aFailedHandoffStartFallsBackToTheNextPreparedRow() = runTest {
        val server = PlaybackServer()
        val controller = controller(server = server)
        controller.remotePlayback = controller.playingLease()
        val backup = SourceCandidate("backup-stream", "addon:2", "Next 720p")
        controller.nextSourcePreparer = NextSourcePreparer { _, _, _, _, _, _ -> preparedSource(listOf(nextSource, backup)) }
        controller.progress(1_300_000); runCurrent()
        controller.playEpisode(episode(2)); advanceTimeBy(2_000); runCurrent()
        val first = assertNotNull(controller.state.value.playback)
        assertEquals("prep-1", first.id)
        assertEquals("next-stream", controller.state.value.playingSource?.id)
        assertNotNull(controller.state.value.bestMatch, "The handoff opens as a Best match start")
        controller.observePlaybackFor(first, PlayerFact.Failed); runCurrent()
        assertEquals("backup-stream", assertNotNull(controller.state.value.fallbackNotice).next.id)
        advanceTimeBy(2_100); runCurrent()
        assertEquals("backup-stream", controller.state.value.playingSource?.id)
        assertEquals("lease-1", controller.state.value.playback?.id)
        assertEquals(1, server.opens)
        assertNull(controller.state.value.fallbackNotice)
        controller.closePlayer()
    }

    @Test fun aSourcePreferenceChangeReleasesThePreparedSourceAndPreparesAgain() = runTest {
        val server = PlaybackServer()
        val controller = controller(server = server)
        var prepared = 0
        controller.nextSourcePreparer = NextSourcePreparer { _, _, _, _, _, _ -> prepared++; preparedSource() }
        controller.progress(1_300_000); runCurrent(); assertEquals(1, prepared)
        controller.mutable.value = controller.state.value.copy(preferences = PreferencesSnapshot(2, ProfilePreferences(source_selection = "best")))
        controller.progress(1_310_000); runCurrent()
        assertEquals(2, prepared)
        assertEquals(NextEpisodePreparationStatus.Ready, controller.state.value.nextEpisodePreparation?.status)
        controller.mutable.value = controller.state.value.copy(preferences = PreferencesSnapshot(3, ProfilePreferences(source_selection = "ask")))
        controller.progress(1_320_000); runCurrent()
        assertEquals(3, prepared, "A source preference change reprepares under the new revision")
        controller.closePlayer()
    }

    @Test fun aPreparedDeliveryIsHeartbeatedAndNeverReprepared() = runTest {
        val server = PlaybackServer()
        val controller = controller(server = server)
        var prepared = 0
        controller.nextSourcePreparer = NextSourcePreparer { _, _, _, _, _, _ -> prepared++; preparedSource() }
        controller.progress(1_300_000); runCurrent()
        assertEquals(1, prepared); assertEquals(1, server.prepares)
        advanceTimeBy(15 * 60_000L); runCurrent()
        assertEquals(1, prepared, "No client-side refresh timer reprepares the delivery")
        assertTrue(server.heartbeats >= 1, "The prepared delivery is kept alive by heartbeat")
        assertEquals(NextEpisodePreparationStatus.Ready, controller.state.value.nextEpisodePreparation?.status)
        controller.closePlayer()
    }

    @Test fun aFailedPromoteFallsBackToAFreshStart() = runTest {
        val server = PlaybackServer().also { it.failPromote = true }
        val controller = controller(server = server)
        controller.remotePlayback = controller.playingLease()
        controller.nextSourcePreparer = NextSourcePreparer { _, _, _, _, _, _ -> preparedSource() }
        controller.progress(1_300_000); runCurrent()
        assertEquals(1, server.prepares)
        controller.playEpisode(episode(2)); advanceTimeBy(2_000); runCurrent()
        val state = controller.state.value
        assertEquals(Screen.Player, state.screen)
        assertEquals("next-stream", state.playingSource?.id)
        assertEquals("lease-1", state.playback?.id, "A failed promote starts fresh")
        assertEquals(1, server.promotes); assertEquals(1, server.opens)
        val beforeOpen = server.events.takeWhile { it != "open" }
        assertTrue("delete:attempt" in beforeOpen, "The fallback releases the outgoing playback first")
        assertTrue("delete:prep-1" in beforeOpen, "The fallback releases the prepared delivery first")
        controller.closePlayer()
    }

    @Test fun aReleasedPreparationFallsBackToAFreshStart() = runTest {
        val server = PlaybackServer()
        val controller = controller(server = server)
        controller.remotePlayback = controller.playingLease()
        controller.nextSourcePreparer = NextSourcePreparer { _, _, _, _, _, _ -> preparedSource() }
        controller.progress(1_300_000); runCurrent()
        assertEquals(1, server.prepares)
        assertNotNull(controller.nextEpisodePreparedLease)
        server.preparedReleased = true
        advanceTimeBy(21_000); runCurrent()
        assertNull(controller.nextEpisodePreparedLease, "A released delivery is dropped after a failed heartbeat")
        assertEquals(NextEpisodePreparationStatus.Ready, controller.state.value.nextEpisodePreparation?.status)

        controller.playEpisode(episode(2)); advanceTimeBy(2_000); runCurrent()
        val state = controller.state.value
        assertEquals(Screen.Player, state.screen)
        assertEquals("lease-1", state.playback?.id, "A released preparation falls back to a fresh start")
        assertEquals(1, server.opens); assertEquals(0, server.promotes)
        controller.closePlayer()
    }

    @Test fun withoutAFullyMatchingSourceNextFallsBackToThePicker() = runTest {
        val server = PlaybackServer()
        val controller = controller(server = server)
        controller.nextSourcePreparer = NextSourcePreparer { _, _, _, _, _, _ -> null }
        controller.progress(1_300_000); runCurrent()
        assertEquals(NextEpisodePreparationStatus.Unavailable, controller.state.value.nextEpisodePreparation?.status)
        assertNull(controller.state.value.nextEpisodePreparation?.source)
        assertEquals(0, server.prepares, "No source means no delivery is prepared")
        controller.playEpisode(episode(2)); advanceTimeBy(2_000); runCurrent()
        assertEquals(Screen.Sources, controller.state.value.screen)
        assertNull(controller.state.value.playback, "The picker opens instead of a prepared handoff")
        controller.closePlayer()
    }

    @Test fun aFailedPreparationNeverBreaksTheCurrentEpisode() = runTest {
        val controller = controller()
        controller.nextSourcePreparer = NextSourcePreparer { _, _, _, _, _, _ -> throw java.io.IOException("offline") }
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
        controller.nextSourcePreparer = NextSourcePreparer { _, _, _, _, _, _ -> started.complete(Unit); awaitCancellation() }
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
        val remote = RemoteConnection("https://watch.example", store, client, browserMode = false, workerDispatcher = StandardTestDispatcher(testScheduler))
        val controller = ApplicationController(this, store); controller.connection = remote
        controller.capability = { DeviceCapability() }
        controller.nextEpisodeDeliveryPreparer = NextEpisodeDeliveryPreparer { _, _, _ -> null }
        controller.mutable.value = state()
        controller.progress(1_300_000); advanceTimeBy(2_000); runCurrent()
        val prepared = assertNotNull(controller.state.value.nextEpisodePreparation)
        assertEquals(NextEpisodePreparationStatus.Ready, prepared.status)
        assertEquals("s2", prepared.source?.id)
        assertEquals(listOf("s2", "s1"), prepared.ranked?.rows?.map { it.id })

        fully = false
        controller.closePlayer(); runCurrent()
        controller.mutable.value = state()
        controller.progress(1_300_000); advanceTimeBy(2_000); runCurrent()
        assertEquals(NextEpisodePreparationStatus.Unavailable, controller.state.value.nextEpisodePreparation?.status)
        assertNull(controller.state.value.nextEpisodePreparation?.source)
        controller.closePlayer()
    }

    @Test fun thePreparerReceivesTheRankingContextWithTheSeriesMemory() = runTest {
        val controller = controller()
        controller.rememberStartedSource("profile", episode(1), SourceCandidate("row", "addon:a", "EN 1080p", binge_group = "group-1"))
        var received: RankingContext? = null
        controller.nextSourcePreparer = NextSourcePreparer { _, _, _, _, _, context -> received = context; preparedSource() }
        controller.progress(1_300_000); runCurrent()
        assertEquals(SeriesMemory("addon:a", "group-1"), received?.seriesMemory)
        assertEquals(NextEpisodePreparationStatus.Ready, controller.state.value.nextEpisodePreparation?.status)
        controller.closePlayer()
    }

    @Test fun thePreparerReceivesTheProfileAndSeriesRememberedChoice() = runTest {
        val controller = controller()
        controller.sourceMemory = SourceMemoryBook(object : SourceMemoryStore {
            var raw: String? = null
            override fun read(): String? = raw
            override fun write(value: String) { raw = value }
        })
        controller.sourceMemory.remember("profile", episode(2), SourceCandidate("remembered", "addon:a", "EN 720p", audio_languages = listOf("en")))
        var received: RememberedSource? = null
        controller.nextSourcePreparer = NextSourcePreparer { _, _, _, _, remembered, _ -> received = remembered; preparedSource() }
        controller.progress(1_300_000); runCurrent()
        assertEquals(RememberedSource("addon:a", listOf("en")), received)
        assertEquals(NextEpisodePreparationStatus.Ready, controller.state.value.nextEpisodePreparation?.status)
        controller.closePlayer()
    }

    private fun localDevice(fixture: LocalSetupFixture, rows: List<SourceCandidate>, bestMatch: String?) =
        object : LocalConnection by fixture.local {
            override suspend fun detail(profile: String, card: Card, offset: Int) = DetailSnapshot(parent, episodes = (1L..4L).map { episode(it) })
            override suspend fun sources(profile: String, card: Card, publish: (SourcesOutcome) -> Unit): SourcesOutcome {
                publish(SourcesOutcome(rows))
                return SourcesOutcome(rows)
            }
            override suspend fun rankSources(profile: String, sources: List<SourceCandidate>, context: RankingContext): RankedSources =
                RankedSources(sources, SourceGroups.of(sources.map { it.id }, bestMatch), bestMatchFullyMatches = bestMatch != null)
            override suspend fun openSource(id: String) = PlaybackInput(id, "https://media.example/$id", profileId = "profile")
            override suspend fun profilePreferences(profile: String) = PreferencesSnapshot(1, ProfilePreferences(source_selection = "best"))
        }

    @Test fun aRememberedChoiceWithNoRowInItsAudioPreparesNothingAndNextAsks() = runTest {
        val fixture = LocalSetupFixture()
        val controller = controller(local = localDevice(fixture, listOf(row("ja-only", languages = listOf("ja"))), bestMatch = "ja-only"))
        try {
            controller.sourceMemory = SourceMemoryBook(Memory()).also { it.remember("profile", episode(2), row("remembered", languages = listOf("en"))) }
            controller.progress(1_300_000); advanceTimeBy(2_000); runCurrent()
            val prepared = assertNotNull(controller.state.value.nextEpisodePreparation)
            assertEquals(NextEpisodePreparationStatus.Unavailable, prepared.status, "Another audio never stands in for the remembered choice")
            assertNull(prepared.source, "No generic best match is readied")

            controller.playEpisode(episode(2)); advanceTimeBy(2_000); runCurrent()
            val state = controller.state.value
            assertEquals(Screen.Sources, state.screen, "Next asks, exactly as an unprepared successor does")
            assertNull(state.playback, "Nothing starts outside the remembered audio")
            assertEquals(listOf("ja-only"), state.sources.map { it.id })
            controller.closePlayer()
        } finally { fixture.close(controller) }
    }

    @Test fun aSeriesWithNoRememberedChoiceStillPreparesAndHandsOffTheBestMatch() = runTest {
        val fixture = LocalSetupFixture()
        val controller = controller(local = localDevice(fixture, listOf(row("best", languages = listOf("ja"))), bestMatch = "best"))
        try {
            controller.sourceMemory = SourceMemoryBook(Memory())
            controller.progress(1_300_000); advanceTimeBy(2_000); runCurrent()
            val prepared = assertNotNull(controller.state.value.nextEpisodePreparation)
            assertEquals(NextEpisodePreparationStatus.Ready, prepared.status)
            assertEquals("best", prepared.source?.id, "A fully matching best match is prepared as before")

            controller.playEpisode(episode(2)); advanceTimeBy(2_000); runCurrent()
            val state = controller.state.value
            assertEquals(Screen.Player, state.screen)
            assertEquals("best", state.playingSource?.id)
            assertNotNull(state.playback)
            controller.closePlayer()
        } finally { fixture.close(controller) }
    }

    @Test fun disablingReuseNeverPreparesAndTheSuccessorAsks() = runTest {
        val fixture = LocalSetupFixture()
        val controller = controller(local = localDevice(fixture, listOf(row("best", languages = listOf("ja"))), bestMatch = "best"))
        try {
            controller.mutable.value = controller.state.value.copy(preferences = PreferencesSnapshot(2, ProfilePreferences(source_selection = "ask", remembered_source_reuse = false)))
            controller.progress(1_300_000); advanceTimeBy(2_000); runCurrent()
            assertNull(controller.state.value.nextEpisodePreparation, "No successor is prepared once reuse is disabled")

            controller.playEpisode(episode(2)); advanceTimeBy(2_000); runCurrent()
            val state = controller.state.value
            assertEquals(Screen.Sources, state.screen, "The successor asks for a source instead of starting on its own")
            assertNull(state.playback)
            controller.closePlayer()
        } finally { fixture.close(controller) }
    }

    private fun TestScope.preparedController(server: PlaybackServer = PlaybackServer()): ApplicationController {
        val controller = controller(server = server)
        controller.nextSourcePreparer = NextSourcePreparer { _, _, _, _, _, _ -> preparedSource() }
        controller.progress(1_300_000); runCurrent()
        assertEquals(1, server.prepares, "One prepared delivery is admitted")
        assertNotNull(controller.nextEpisodePreparedLease)
        return controller
    }

    private fun TestScope.assertReleasedOnExit(server: PlaybackServer, controller: ApplicationController, exit: () -> Unit) {
        advanceTimeBy(21_000); runCurrent()
        val heartbeats = server.heartbeats
        assertTrue(heartbeats >= 1, "The prepared delivery is heartbeated before the exit")
        exit()
        runCurrent()
        assertTrue("delete:prep-1" in server.events, "The exit releases the prepared delivery")
        assertNull(controller.nextEpisodePreparedLease)
        assertNull(controller.nextEpisodePreparationRenewal)
        assertNull(controller.state.value.nextEpisodePreparation)
        advanceTimeBy(120_000); runCurrent()
        assertEquals(heartbeats, server.heartbeats, "No heartbeat renew happens after the exit")
    }

    @Test fun closingThePlayerReleasesThePreparedDelivery() = runTest {
        val server = PlaybackServer()
        val controller = preparedController(server)
        assertReleasedOnExit(server, controller) { controller.closePlayer() }
    }

    @Test fun navigatingAwayReleasesThePreparedDelivery() = runTest {
        val server = PlaybackServer()
        val controller = preparedController(server)
        assertReleasedOnExit(server, controller) { controller.navigate(Screen.Home) }
    }

    @Test fun disconnectingReleasesThePreparedDelivery() = runTest {
        val server = PlaybackServer()
        val controller = preparedController(server)
        assertReleasedOnExit(server, controller) { controller.disconnect() }
    }

    @Test fun signingOutReleasesThePreparedDelivery() = runTest {
        val server = PlaybackServer()
        val controller = preparedController(server)
        assertReleasedOnExit(server, controller) { controller.signOut() }
    }

    @Test fun switchingProfileReleasesThePreparedDelivery() = runTest {
        val server = PlaybackServer()
        val controller = preparedController(server)
        assertReleasedOnExit(server, controller) { controller.selectProfile("other") }
    }

    @Test fun anExpiredSessionReleasesThePreparedDelivery() = runTest {
        val server = PlaybackServer()
        val controller = preparedController(server)
        assertReleasedOnExit(server, controller) { controller.expireSession(requireNotNull(controller.connection), null) }
    }
}
