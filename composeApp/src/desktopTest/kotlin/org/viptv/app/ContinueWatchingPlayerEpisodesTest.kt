package org.viptv.app

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.client.request.HttpRequestData
import io.ktor.http.*
import io.ktor.http.content.TextContent
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.viptv.app.connection.*
import org.viptv.app.features.player.*
import org.viptv.app.player.PlaybackInput
import org.viptv.app.state.*
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.*
import kotlin.test.*

/**
 * Playback started from Continue Watching gets the same Episodes list and Next
 * episode as playback started from Details (app#10).
 *
 * The fixtures mirror the KMP dev host's real responses for a Stremio-imported
 * profile with Cinemeta: a Continue Watching row card is a `library` card with
 * no metadata origin, and its episode is past the first Details page (that page
 * holds the specials and most of season 1).
 */
@OptIn(ExperimentalComposeUiApi::class)
class ContinueWatchingPlayerEpisodesTest {
    private val store = object : RemoteSessionStore {
        override suspend fun read(): SavedRemoteSession? = null
        override suspend fun save(session: SavedRemoteSession) = Unit
        override suspend fun clear() = Unit
    }
    private val show = "tt13293588"
    /** Cinemeta's season index for the show: specials, then seasons 1-3. */
    private val seasons = listOf(0L to 4L, 1L to 23L, 2L to 24L, 3L to 14L)
    private val origin = """{"connection_id":"addon:21","incarnation":"${"c".repeat(32)}","provider_id":"stremio"}"""
    private val parentIdentity = """{"edition":null,"episode":null,"id":"$show","ids":[{"namespace":"imdb","value":"$show"},""" +
        """{"namespace":"tmdb","value":"94664"},{"namespace":"tvdb","value":"371310"}],"kind":"series"}"""
    private fun episodeIdentity(season: Long, episode: Long) =
        """{"edition":null,"episode":{"episode":$episode,"order":"aired","season":$season},"id":"$show:$season:$episode","ids":[],"kind":"series"}"""
    private val all = seasons.flatMap { (season, count) -> (1L..count).map { season to it } }

    /** A Cinemeta episode card exactly as `/api/meta` returns it. */
    private fun metaEpisode(season: Long, episode: Long) = """{"badges":[],"category":"series","description":null,
        "identity":${episodeIdentity(season, episode)},"landscape":null,"logo":null,"metadata_origin":$origin,
        "parent_identity":$parentIdentity,"poster":null,"progress":null,"provider_id":"addon:21","subtitle":null,
        "title":"Episode $season-$episode","view":{"badges":[],"compact_metadata_parts":[],"hero_badges":[],"hero_metadata_parts":[],
        "metadata_parts":["Series"],"progress":null,"saved":true,"tile_badge":null}}"""

    /** `/api/meta/series/<show>` paged like the host: a window over every episode, the season index on each page. */
    private fun metaPage(offset: Int, limit: Int): String {
        val window = all.drop(offset).take(limit)
        val next = (offset + window.size).takeIf { it < all.size }?.let { "\"$it\"" } ?: "null"
        var start = 0L
        val index = seasons.joinToString(",") { (season, count) ->
            """{"episode_count":$count,"offset":$start,"season":$season}""".also { start += count }
        }
        return """{"item":{"badges":[],"category":"series","identity":$parentIdentity,"metadata_origin":$origin,"parent_identity":null,
            "provider_id":"addon:21","subtitle":"2021–","title":"Mushoku Tensei: Jobless Reincarnation"},
            "episodes":[${window.joinToString(",") { (s, e) -> metaEpisode(s, e) }}],"next_cursor":$next,"recommendations":[],"seasons":[$index]}"""
    }

    /** A Continue Watching row card from `/api/bootstrap` for a Stremio-imported resume point. */
    private fun continueCard(season: Long, episode: Long) = """{"badges":[],"description":null,"identity":${episodeIdentity(season, episode)},
        "landscape":null,"logo":null,"parent_identity":{"edition":null,"episode":null,"id":"$show","ids":[],"kind":"series"},
        "poster":"https://images.metahub.space/poster/small/$show/img","progress":0.0105,"provider_id":"library",
        "subtitle":"S$season E$episode · 24m left","title":"Mushoku Tensei: Jobless Reincarnation",
        "view":{"badges":[],"compact_metadata_parts":[],"hero_badges":[],"hero_metadata_parts":[],"metadata_parts":["Series"],
        "preview":{"action":"play","background":null,"caption":"Resume S$season E$episode · 24m left","progress":0.0105},
        "progress":0.0105,"saved":true,"tile_badge":null}}"""

    private fun bootstrap(resume: Pair<Long, Long>) = """{"contract_version":3,
        "identity":{"account_id":"1","profile_id":"2","profiles":[{"id":"2","name":"Stremio import QA"}]},
        "home":{"complete":true,"featured":[],"hero":null,"revision":1818,
            "scope":{"account_id":"1","connection_id":"server","profile_id":"2","revision":0},
            "rows":[{"browse":{"query":[],"target":"Continue"},"cards":[${continueCard(resume.first, resume.second)}],"id":"continue",
                "message":null,"next_cursor":null,"provider_id":"library","state":"Ready","title":"Continue Watching",
                "view":{"card_shape":"landscape"}}]}}"""

    /**
     * `/continue/next` as the host answers it: the following aired episode, or
     * CaughtUp after the last one. A request without the metadata origin gets a
     * card in the host's own `stremio` scope, as it does for a raw library card.
     */
    private fun nextEpisode(body: JsonObject): String {
        val coordinates = body.getValue("identity").jsonObject.getValue("episode").jsonObject
        val here = coordinates.getValue("season").jsonPrimitive.long to coordinates.getValue("episode").jsonPrimitive.long
        val next = all.getOrNull(all.indexOf(here) + 1) ?: return """{"status":"CaughtUp","item":null}"""
        val card = metaEpisode(next.first, next.second)
        return """{"status":"Next","item":${if (body["metadata_origin"] != null) card
            else card.replace("\"metadata_origin\":$origin", "\"metadata_origin\":null").replace("\"provider_id\":\"addon:21\"", "\"provider_id\":\"stremio\"")}}"""
    }

    private fun MockRequestHandleScope.json(text: String) = respond(text, headers = headersOf(HttpHeaders.ContentType, "application/json"))
    private fun MockRequestHandleScope.host(request: HttpRequestData) = when (request.url.encodedPath) {
        "/api/meta/series/$show" -> json(metaPage(request.url.parameters["offset"]?.toInt() ?: 0, request.url.parameters["limit"]?.toInt() ?: 24))
        "/api/profiles/2/continue/next" -> json(nextEpisode(Json.parseToJsonElement((request.body as TextContent).text).jsonObject))
        else -> respond("{\"error\":\"not in this fixture\"}", HttpStatusCode.NotFound, headersOf(HttpHeaders.ContentType, "application/json"))
    }

    private class Started(val controller: ApplicationController, val remote: RemoteConnection) {
        val state get() = controller.state.value
    }

    /** Tap the Continue Watching card, let sources open, then start playing like a chosen source does. */
    private suspend fun CoroutineScope.playFromContinueWatching(resume: Pair<Long, Long>, bootstrapJson: String = bootstrap(resume)): Started {
        val client = HttpClient(MockEngine { request ->
            if (request.url.encodedPath == "/api/bootstrap") json(bootstrapJson) else host(request)
        }) { configureTransport() }
        val remote = RemoteConnection("https://watch.example", store, client, browserMode = false)
        val boot = remote.bootstrap()
        val card = boot.home!!.rows.single { it.id == "continue" }.cards.single()
        assertEquals("library", card.provider_id, "fixture: a real CW row card is a library card")
        assertNull(card.metadata_origin, "fixture: an imported CW row card has no metadata origin")
        val controller = ApplicationController(this, store); controller.connection = remote
        controller.mutable.value = ApplicationState(screen = Screen.Home, selectedProfile = boot.selected ?: "2", home = boot.home)
        controller.playTitle(card)
        withTimeout(5_000) { while (controller.state.value.screen != Screen.Sources || controller.state.value.busy) delay(10) }
        controller.mutable.value = controller.state.value.copy(screen = Screen.Player,
            playback = PlaybackInput("attempt", "https://media.example/fixture.mp4", profileId = "2"))
        controller.loadNextEpisode(); controller.loadPlaybackSeason()
        withTimeout(5_000) { controller.nextEpisodeJob?.join(); controller.playbackSeasonJob?.join() }
        return Started(controller, remote)
    }

    @Test fun stremioImportedContinueWatchingEpisodeGetsItsSeasonAndNextEpisode() = runBlocking<Unit> {
        val started = playFromContinueWatching(2L to 20L)
        try {
            val state = started.state
            val playing = assertNotNull(state.sourceTitle)
            assertEquals("$show:2:20", playing.identity.id, "sources and playback are for S2 E20")
            assertEquals("$show:2:21", state.nextEpisode?.identity?.id, "Next episode is S2 E21")
            assertEquals(ContinuationStatus.NEXT, state.nextEpisodeStatus)
            val episodes = playerEpisodes(playing, state.detail)
            assertEquals((1L..24L).map { "$show:2:$it" }, episodes.map { it.identity.id }, "Episodes lists season 2")
            assertTrue(episodes.any { cardLookupKey(it) == cardLookupKey(playing) }, "the playing episode is in the list (marked)")
            val pick = episodes.single { it.identity.id == "$show:2:22" }
            val played = phoneChoices(playing, episodes, state.nextEpisode, nextEnabled = true, pick = pick, capture = "cw-player-controls")
            assertEquals(listOf(pick, state.nextEpisode), played, "Episodes and Next episode hand over the exact cards")
            // PlayerRoute's playEpisode: leave the player, then open sources for that episode.
            for (card in played) {
                started.controller.closePlayer(); started.controller.chooseSource(card)
                withTimeout(5_000) { while (started.controller.state.value.busy) delay(10) }
                assertEquals(Screen.Sources, started.state.screen)
                assertEquals(cardLookupKey(card!!), started.state.sourceTitle?.let(::cardLookupKey), "sources are for the chosen episode")
            }
        } finally { started.controller.job?.cancel(); started.controller.cancelNextEpisode(); started.remote.close() }
    }

    @Test fun seriesFinaleFromContinueWatchingKeepsEpisodesAndShowsNextUnavailable() = runBlocking<Unit> {
        val started = playFromContinueWatching(3L to 14L)
        try {
            val state = started.state
            val playing = assertNotNull(state.sourceTitle)
            val episodes = playerEpisodes(playing, state.detail)
            assertEquals((1L..14L).map { "$show:3:$it" }, episodes.map { it.identity.id }, "Episodes lists season 3")
            assertNull(state.nextEpisode, "nothing after the finale")
            assertEquals(ContinuationStatus.CAUGHTUP, state.nextEpisodeStatus)
            val pick = episodes[episodes.size - 2]
            assertEquals(listOf(pick), phoneChoices(playing, episodes, null, nextEnabled = false, pick = pick, capture = "cw-player-finale"))
        } finally { started.controller.job?.cancel(); started.controller.cancelNextEpisode(); started.remote.close() }
    }

    @Test fun anEpisodeAlreadyOnTheFirstDetailsPageListsOnlyItsSeason() = runBlocking<Unit> {
        // S1 E3 is on the first page, which also holds the specials and S1 E1-E20 only.
        val started = playFromContinueWatching(1L to 3L)
        try {
            val state = started.state
            val playing = assertNotNull(state.sourceTitle)
            assertEquals((1L..23L).map { "$show:1:$it" }, playerEpisodes(playing, state.detail).map { it.identity.id },
                "the whole of season 1, without the specials")
            assertEquals("$show:1:4", state.nextEpisode?.identity?.id)
        } finally { started.controller.job?.cancel(); started.controller.cancelNextEpisode(); started.remote.close() }
    }

    @Test fun anOffPageLibraryEpisodeTakesTheDetailsScopeOnlyForItsOwnParent() {
        val codec = Json { ignoreUnknownKeys = true }
        val detail = codec.decodeFromString<DetailSnapshot>(metaPage(0, 24))
        val resume = codec.decodeFromString<Card>(continueCard(2, 20))
        val target = sourceTarget(resume, detail)
        assertEquals(resume.identity, target.identity, "still exactly S2 E20")
        assertEquals(detail.item.provider_id, target.provider_id)
        assertEquals(detail.item.metadata_origin, target.metadata_origin)
        assertEquals(detail.item.identity, target.parent_identity)
        val stranger = resume.copy(parent_identity = resume.parent_identity!!.copy(id = "tt0000001"))
        assertEquals(stranger, sourceTarget(stranger, detail), "another title's episode is never re-scoped")
    }

    /**
     * Renders the phone controls for this state, checks which buttons are enabled,
     * then picks [pick] from Episodes and, when enabled, confirms Next episode.
     * Returns the cards the player handed to `playEpisode`, in that order.
     * `VIPTV_PHONE_PLAYER_CAPTURE_DIR` saves what the controls show.
     */
    private fun phoneChoices(title: Card, episodes: List<Card>, next: Card?, nextEnabled: Boolean, pick: Card, capture: String): List<Card> {
        val played = mutableListOf<Card>()
        val scene = ImageComposeScene(844, 390, Density(1f)) { ViptvTheme {
            PlayerScreen(null, true, null, {}, {}, {}, title = title, phone = true,
                tvExtras = TvPlaybackExtras(episodes = episodes, nextEpisode = next), playEpisode = { played += it })
        } }
        var frame = 0
        fun frames() = repeat(16) { scene.render(frame++ * 16_666_667L).close(); Thread.sleep(4) }
        fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
        fun button(label: String) = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }.single {
            it.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { text -> text.text } == label && it.config.contains(SemanticsActions.OnClick)
        }
        fun click(label: String) { assertTrue(button(label).config[SemanticsActions.OnClick].action!!.invoke(), label); frames() }
        try {
            frames()
            assertFalse(button("Episodes").config.contains(SemanticsProperties.Disabled), "Episodes is enabled")
            assertEquals(!nextEnabled, button("Next episode").config.contains(SemanticsProperties.Disabled), "Next episode enabled = $nextEnabled")
            System.getenv("VIPTV_PHONE_PLAYER_CAPTURE_DIR")?.let { path ->
                java.io.File(path).mkdirs()
                scene.render(3_000_000_000L).use { image -> image.encodeToData()!!.use { java.io.File(path, "$capture.png").writeBytes(it.bytes) } }
            }
            click("Episodes"); click("${pick.identity.episode!!.episode}. ${pick.title} Play episode")
            if (nextEnabled) { click("Next episode"); click("Next episode") }
            return played
        } finally { scene.close() }
    }
}
