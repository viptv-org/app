package org.viptv.app

import org.junit.Rule
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import org.viptv.app.connection.*
import org.viptv.app.features.home.HomeScreen
import org.viptv.app.features.home.TvHomeShelf
import org.viptv.app.state.*
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.*
import kotlin.test.*

/**
 * Activating a Continue Watching card goes straight to the source picker for
 * exactly that episode (or movie), on every platform: phone/tablet/web tap,
 * desktop click and TV D-pad OK. Other rows keep opening Details.
 */
@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class ContinueWatchingActivationTest {
    @get:Rule val uiThread = UiThreadRule()
    private val origin = MetadataOrigin("stremio", "addon:4", "c".repeat(32))
    private val series = Card(MediaIdentity("tt38262097", "series", listOf(ExternalId("imdb", "tt38262097"))),
        provider_id = "addon:4", title = "Level 999 no Murabito", badges = emptyList(), metadata_origin = origin)
    private fun episode(number: Long) = series.copy(
        identity = MediaIdentity("tt38262097:1:$number", "series", emptyList(), EpisodeCoordinates("aired", 1, number)),
        parent_identity = series.identity, title = "Episode $number")
    /** The bootstrap CW card for the owner's show: S1 E3 at 65 %. */
    private val resume = episode(3).copy(title = series.title, subtitle = "S1 E3 · 9m left", progress = 0.6581)
    private val movie = Card(MediaIdentity("tt0111161", "movie", emptyList()), provider_id = "addon:4",
        title = "A Movie", badges = emptyList(), progress = 0.4, metadata_origin = origin)
    private val saved = Card(MediaIdentity("tt0000001", "series", emptyList()), provider_id = "addon:4",
        title = "Saved Show", badges = emptyList())
    private val home = HomeSnapshot(
        scope = Scope("viptv:remote", "account", "1", 1), revision = 1,
        rows = listOf(
            HomeRow(id = "continue", title = "Continue Watching", provider_id = "library", state = RowState.READY, cards = listOf(resume, movie)),
            HomeRow(id = "favorites", title = "My List", provider_id = "library", state = RowState.READY, cards = listOf(saved))),
        complete = true)

    private fun onEdt(block: suspend CoroutineScope.() -> Unit) = runBlocking(block = block)
    private fun nodes(scene: ImageComposeScene): List<SemanticsNode> = scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(true) }
    private fun label(node: SemanticsNode) = (node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() +
        node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }).joinToString(" ")
    private fun clickable(scene: ImageComposeScene, title: String) = nodes(scene).first {
        it.config.contains(SemanticsActions.OnClick) && (label(it).contains(title) ||
            it.children.any { child -> label(child).contains(title) })
    }
    private fun tap(scene: ImageComposeScene, title: String) {
        assertTrue(clickable(scene, title).config[SemanticsActions.OnClick].action?.invoke() == true, "$title is not activatable")
    }

    @Test fun phoneDesktopAndWebContinueWatchingCardsPlayInsteadOfOpeningDetails() = onEdt {
        for ((width, height) in listOf(390 to 900, 1280 to 900)) {
            val opened = mutableListOf<Card>(); val played = mutableListOf<Card>()
            val scene = ImageComposeScene(width, height, Density(1f)) { ViptvTheme {
                Box(Modifier.fillMaxSize().background(Palette.background)) {
                    HomeScreen(home, television = false, retry = {}, open = { opened += it }, play = { played += it }, busy = false)
                }
            } }
            try {
                repeat(6) { scene.render(it * 16_666_667L).close() }
                tap(scene, "S1 E3"); tap(scene, "A Movie"); tap(scene, "Saved Show")
                assertEquals(listOf(resume, movie), played, "${width}dp: CW cards go to sources, with their exact identity")
                assertEquals(listOf(saved), opened, "${width}dp: other rows still open Details")
            } finally { scene.close() }
        }
    }

    @Test fun tvContinueWatchingOkPlaysTheEpisodeAndOtherShelvesOpenDetails() = onEdt {
        val opened = mutableListOf<Card>(); val played = mutableListOf<Card>()
        val first = FocusRequester(); val savedFirst = FocusRequester()
        val scene = ImageComposeScene(1920, 900, Density(1f)) { ViptvTheme(true) {
            Column(Modifier.fillMaxSize().background(Palette.background)) {
                TvHomeShelf(home.rows[0], 1f, first, up = null, focus = {}, open = { opened += it },
                    play = { played += it })
                TvHomeShelf(home.rows[1], 1f, savedFirst, up = null, focus = {}, open = { opened += it },
                    play = { played += it })
            }
        } }
        var frame = 0L
        fun render() = repeat(6) { scene.render(frame++ * 16_666_667L).close() }
        fun ok() { scene.sendKeyEvent(KeyEvent(Key.DirectionCenter, KeyEventType.KeyDown)); scene.sendKeyEvent(KeyEvent(Key.DirectionCenter, KeyEventType.KeyUp)) }
        try {
            render(); delay(30)
            first.requestFocus(); render(); ok(); render()
            assertEquals(listOf(resume), played, "D-pad OK on the CW card opens its sources")
            assertTrue(opened.isEmpty(), "CW never detours through Details: $opened")
            savedFirst.requestFocus(); render(); ok(); render()
            assertEquals(listOf(saved), opened, "My List keeps opening Details")
            assertEquals(1, played.size)
        } finally { scene.close() }
    }

    private val store = object : RemoteSessionStore {
        override suspend fun read(): SavedRemoteSession? = null
        override suspend fun save(session: SavedRemoteSession) = Unit
        override suspend fun clear() = Unit
    }

    @Test fun playingTheResumeCardOpensSourcesForThatEpisodeNotTheFirstOne() = runBlocking<Unit> {
        // The metadata window already holds E1-E5; E3 must be the source target.
        val detail = DetailSnapshot(series, episodes = (1L..5L).map(::episode), next_cursor = null)
        val paths = mutableListOf<String>()
        val client = HttpClient(MockEngine { request ->
            synchronized(paths) { paths += request.url.encodedPath }
            val expected = request.url.parameters["card"]?.let { Json.decodeFromString<Card>(it) }
            // Host rule: the metadata expectation must name the title being looked up.
            if (request.url.encodedPath == "/api/meta/series/tt38262097" && expected?.identity != series.identity)
                respond("{\"error\":\"Metadata expectation does not match lookup\"}", HttpStatusCode.BadRequest, headersOf(HttpHeaders.ContentType, "application/json"))
            else if (request.url.encodedPath == "/api/meta/series/tt38262097")
                respond(Json.encodeToString(detail), headers = headersOf(HttpHeaders.ContentType, "application/json"))
            else respond("{\"error\":\"not in this fixture\"}", HttpStatusCode.NotFound, headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureTransport() }
        val remote = RemoteConnection("https://watch.example", store, client, browserMode = false)
        val controller = ApplicationController(this, store); controller.connection = remote
        controller.mutable.value = ApplicationState(screen = Screen.Home, selectedProfile = "1", home = home)
        try {
            controller.playTitle(resume)
            // The selector opens on the tap; the title's Details fill in behind it (app#11).
            withTimeout(5_000) { while (controller.state.value.screen != Screen.Sources || controller.state.value.detail == null) delay(10) }
            val state = controller.state.value
            assertEquals(resume.identity, state.sourceTitle?.identity, "sources are for S1 E3")
            assertEquals(series.identity, state.sourceTitle?.parent_identity)
            assertEquals(Screen.Details, state.sourceReturnScreen, "Back from sources lands on the title's Details")
            assertEquals(Screen.Home, state.detailReturnScreen, "and Back again returns Home")
            // Discovery for this scoped episode runs beside the Details read, so either may reach the host first.
            assertTrue("/api/meta/series/tt38262097" in synchronized(paths) { paths.toList() })
        } finally { controller.job?.cancel(); remote.close() }
    }
}
