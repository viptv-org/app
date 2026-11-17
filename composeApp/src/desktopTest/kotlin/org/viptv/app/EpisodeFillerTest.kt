package org.viptv.app

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import org.viptv.app.connection.*
import org.viptv.app.state.*
import org.viptv.engine.wire.*
import kotlin.test.*

class EpisodeFillerTest {
    private val store = object : RemoteSessionStore {
        override suspend fun read(): SavedRemoteSession? = null
        override suspend fun save(session: SavedRemoteSession) = Unit
        override suspend fun clear() = Unit
    }
    private val series = Card(MediaIdentity("tt0988824", "series", emptyList()), provider_id = "addon:7", title = "Naruto: Shippûden",
        badges = emptyList(), metadata = CardMetadata(alternate_titles = listOf("Naruto Shippuden", "")))
    private fun episode(season: Long?, number: Long, parent: MediaIdentity? = series.identity) = Card(
        MediaIdentity("tt0988824:$season:$number", "series", emptyList(), EpisodeCoordinates("aired", season, number)),
        provider_id = "addon:7", title = "Episode $number", badges = emptyList(), parent_identity = parent)
    private val seasons = listOf(EpisodeSeason(0, 3, 0), EpisodeSeason(1, 32, 3), EpisodeSeason(2, 21, 35), EpisodeSeason(3, 18, 56))
    private val detail = DetailSnapshot(series, episodes = listOf(episode(1, 1)), seasons = seasons)

    @Test fun seasonEpisodesCountFromTheNumberedSeasonsBeforeThem() {
        assertEquals(1, absoluteEpisode(episode(1, 1), seasons))
        assertEquals(32, absoluteEpisode(episode(1, 32), seasons))
        assertEquals(33, absoluteEpisode(episode(2, 1), seasons))
        assertEquals(57, absoluteEpisode(episode(3, 4), seasons))
        assertEquals(12, absoluteEpisode(episode(null, 12), emptyList()))
        assertNull(absoluteEpisode(episode(0, 2), seasons))
    }

    @Test fun aSeasonNumberedAbsolutelyKeepsItsNumbers() {
        assertEquals(40, absoluteEpisode(episode(2, 40), seasons))
        assertEquals(53, absoluteEpisode(episode(2, 21), seasons))
    }

    @Test fun labelsBelongToTheDetailSeriesOnly() {
        val filler = EpisodeFiller(cardLookupKey(series), mapOf(33L to FillerKind.Filler, 57L to FillerKind.Mixed))
        val labels = EpisodeFillerLabels.of(detail, filler)
        assertEquals(FillerKind.Filler, labels.of(episode(2, 1)))
        assertEquals(FillerKind.Mixed, labels.of(episode(3, 4)))
        assertNull(labels.of(episode(1, 1)))
        assertNull(labels.of(episode(2, 1, MediaIdentity("other", "series", emptyList()))))
        assertNull(labels.of(episode(0, 33)))
        assertSame(EpisodeFillerLabels.Empty, EpisodeFillerLabels.of(detail, filler.copy(series = "other")))
        assertSame(EpisodeFillerLabels.Empty, EpisodeFillerLabels.of(null, filler))
    }

    @Test fun theDetailTitlesAskTheServerOnceAndLandInState() = runBlocking<Unit> {
        val queries = mutableListOf<String>()
        val client = HttpClient(MockEngine { request ->
            assertEquals("/api/anime/filler", request.url.encodedPath)
            queries += request.url.parameters.getAll("title").orEmpty().joinToString("|")
            respond("""{"show":"naruto-shippuden","episodes":[{"number":33,"kind":"filler"},{"number":57,"kind":"mixed"},""" +
                """{"number":0,"kind":"filler"},{"number":60,"kind":"canon"}]}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureTransport() }
        val remote = RemoteConnection("https://watch.example", store, client, browserMode = false)
        val controller = ApplicationController(this, store); controller.connection = remote
        controller.mutable.value = ApplicationState(screen = Screen.Details, selectedProfile = "owner", detail = detail)
        try {
            controller.loadEpisodeFiller(); controller.episodeFillerJob!!.join()
            assertEquals(listOf("Naruto: Shippûden|Naruto Shippuden"), queries)
            assertEquals(EpisodeFiller(cardLookupKey(series), mapOf(33L to FillerKind.Filler, 57L to FillerKind.Mixed)),
                controller.state.value.episodeFiller)
            controller.mutable.value = controller.state.value.copy(episodeFiller = null)
            controller.loadEpisodeFiller()
            assertEquals(1, queries.size)
            assertEquals(2, controller.state.value.episodeFiller?.kinds?.size)
            assertNull(controller.state.value.error)
        } finally { remote.close() }
    }

    @Test fun aFailedLookupLeavesNoLabelsAndNoError() = runBlocking<Unit> {
        var requests = 0
        val client = HttpClient(MockEngine { requests++; respond("""{"error_code":"not_found"}""", HttpStatusCode.NotFound,
            headersOf(HttpHeaders.ContentType, "application/json")) }) { configureTransport() }
        val remote = RemoteConnection("https://watch.example", store, client, browserMode = false)
        val controller = ApplicationController(this, store); controller.connection = remote
        controller.mutable.value = ApplicationState(screen = Screen.Details, selectedProfile = "owner", detail = detail)
        try {
            controller.loadEpisodeFiller(); controller.episodeFillerJob!!.join()
            assertNull(controller.state.value.episodeFiller); assertNull(controller.state.value.error)
            controller.loadEpisodeFiller(); controller.episodeFillerJob!!.join()
            assertEquals(2, requests)
        } finally { remote.close() }
    }

    @Test fun moviesAndLocalModeNeverAsk() = runBlocking<Unit> {
        val controller = ApplicationController(this, store)
        controller.mutable.value = ApplicationState(screen = Screen.Details, selectedProfile = "owner", detail = detail)
        controller.loadEpisodeFiller()
        assertNull(controller.episodeFillerJob)
        val client = HttpClient(MockEngine { fail("a movie has no episodes to label") }) { configureTransport() }
        val remote = RemoteConnection("https://watch.example", store, client, browserMode = false)
        controller.connection = remote
        controller.mutable.value = controller.state.value.copy(detail = DetailSnapshot(series.copy(identity = MediaIdentity("tt1", "movie", emptyList())), episodes = emptyList()))
        try { controller.loadEpisodeFiller(); assertNull(controller.episodeFillerJob) } finally { remote.close() }
    }
}
