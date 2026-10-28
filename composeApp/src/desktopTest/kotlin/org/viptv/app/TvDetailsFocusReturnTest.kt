package org.viptv.app

import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.compose.LocalPlatformContext
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.client.request.HttpRequestData
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import org.viptv.app.connection.*
import org.viptv.app.preview.PreviewData
import org.viptv.app.state.*
import org.viptv.engine.wire.*
import org.junit.Rule
import kotlin.test.*

/**
 * On TV, Back to a title's Details puts focus on the episode the viewer last focused in the
 * shelf, not on the hero (app#66): Back from the source selector, and Back from the player
 * (which closes onto the selector). If that episode is gone, focus falls back to the
 * continued episode (app#40), then to the hero. First entry into Details keeps the hero.
 *
 * The real TV App shell over a fake host; the remote's D-pad is sent as key events.
 */
@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class, DelicateCoilApi::class)
class TvDetailsFocusReturnTest {
    @get:Rule val uiThread = UiThreadRule()
    private val store = object : RemoteSessionStore {
        override suspend fun read(): SavedRemoteSession? = null
        override suspend fun save(session: SavedRemoteSession) = Unit
        override suspend fun clear() = Unit
    }
    private val show = "tt0903747"
    private val origin = MetadataOrigin("stremio", "addon:21", "c".repeat(32))
    private val series = Card(MediaIdentity(show, "series", listOf(ExternalId("imdb", show))), provider_id = "addon:21",
        title = "Breaking Bad", badges = emptyList(), metadata_origin = origin)
    private fun episode(number: Long, progress: Double? = null) = series.copy(
        identity = MediaIdentity("$show:1:$number", "series", emptyList(), EpisodeCoordinates("aired", 1, number)),
        parent_identity = series.identity, title = "Episode $number", progress = progress)
    private fun label(number: Long) = "$number. Episode $number"
    private val detail = DetailSnapshot(series, episodes = (1L..10L).map { episode(it) })
    /** The same season served in two pages: episodes 11-16 come from `offset=10`. */
    private val pagedDetail = detail.copy(next_cursor = "10", seasons = listOf(EpisodeSeason(1, 16, 0)))
    private val secondPage = DetailSnapshot(series, episodes = (11L..16L).map { episode(it) })
    private val related = series.copy(identity = MediaIdentity("tt3032476", "series", emptyList()), title = "Better Call Saul",
        parent_identity = null)

    private fun MockRequestHandleScope.json(text: String) = respond(text, headers = headersOf(HttpHeaders.ContentType, "application/json"))

    /** The real TV App shell over a fake host serving [fixture]; a later Details page waits for [releasePage]. */
    private inner class TvApp(val fixture: DetailSnapshot = detail) : AutoCloseable {
        val releasePage = CompletableDeferred<Unit>()
        val pageOffsets = java.util.concurrent.CopyOnWriteArrayList<String>()
        private fun MockRequestHandleScope.host(request: HttpRequestData) = when (request.url.encodedPath) {
            "/api/meta/series/$show" -> request.url.parameters["offset"]?.takeIf { it != "0" }.let { offset ->
                if (offset == null) json(Json.encodeToString(fixture)) else { pageOffsets += offset; null }
            }
            // No stream add-ons: the selector opens and finishes with nothing.
            "/api/streams" -> json("""{"id":"job-1"}""")
            "/api/streams/job-1" -> json("""{"done":true,"events":[]}""")
            else -> if (request.url.encodedPath.endsWith("/source-ranking")) json("""{"streams":[]}""")
                else respond("{\"error\":\"not in this fixture\"}", HttpStatusCode.NotFound, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        private val client = HttpClient(MockEngine { request ->
            host(request) ?: run { releasePage.await(); json(Json.encodeToString(secondPage)) }
        }) { configureTransport() }
        val remote = RemoteConnection("https://watch.example", store, client, browserMode = false)
        val controller = ApplicationController(scope, store)
        private var previousLoader: ImageLoader? = null
        private var appLoader: ImageLoader? = null
        val scene: ImageComposeScene
        private var frame = 0L
        private var focusManager: FocusManager? = null

        init {
            controller.connection = remote
            controller.mutable.value = ApplicationState(screen = Screen.Home, selectedProfile = PreviewData.profile.id,
                profiles = PreviewData.profiles, home = PreviewData.home, preferences = PreferencesSnapshot())
            scene = ImageComposeScene(1920, 1080, Density(1f)) {
                val context = LocalPlatformContext.current
                remember { previousLoader = SingletonImageLoader.get(context); SingletonImageLoader.reset() }
                App(television = true, providedController = controller, manageControllerLifecycle = false)
                focusManager = LocalFocusManager.current
                appLoader = SingletonImageLoader.get(context)
            }
        }

        val state get() = controller.state.value
        fun frames(count: Int = 10) = repeat(count) { scene.render(frame++ * 16_666_667L).close(); Thread.sleep(5) }
        fun nodes() = scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(true) }
        fun focused() = nodes().filter { it.config.getOrNull(SemanticsProperties.Focused) == true }
            .map { text(it) + " " + it.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().joinToString(" ") }
        fun until(what: String, condition: () -> Boolean) {
            val deadline = System.nanoTime() + 10_000_000_000L
            while (!condition()) { check(System.nanoTime() < deadline) { "timed out waiting for $what (focus: ${focused()})" }; frames(2) }
        }
        /** A remote key. Desktop has no D-pad focus search, so an unconsumed arrow moves focus as Android TV would. */
        fun press(key: Key, times: Int = 1) = repeat(times) {
            val consumed = scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyDown)); scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyUp))
            val direction = when (key) {
                Key.DirectionUp -> FocusDirection.Up; Key.DirectionDown -> FocusDirection.Down
                Key.DirectionLeft -> FocusDirection.Left; Key.DirectionRight -> FocusDirection.Right
                else -> null
            }
            if (!consumed && direction != null) requireNotNull(focusManager).moveFocus(direction)
            frames(6)
        }
        fun assertFocus(text: String, why: String) {
            frames(30)
            val now = focused()
            assertTrue(now.any { it.contains(text) }, "$why: focus is on $text, not $now")
        }

        /** Home's Continue Watching row becomes these cards, as the host's bootstrap sends it. */
        fun continueWatching(vararg cards: Card) {
            val home = requireNotNull(state.home)
            controller.mutable.value = state.copy(home = home.copy(rows = home.rows.map { row ->
                if (row.id == org.viptv.app.features.home.CONTINUE_ROW) row.copy(state = RowState.READY, cards = cards.toList()) else row }))
        }
        /** Down from the billboard into the Continue Watching row, then OK on its card for [title]. */
        fun tapContinueWatching(title: String) {
            press(Key.DirectionDown)
            until("the Continue Watching card") { focused().any { it.contains(title) } }
            press(Key.Enter)
        }

        /** Home → the title's Details, with focus on the hero's Play. */
        fun openDetails() {
            controller.open(series)
            until("Details") { state.screen == Screen.Details && state.detail != null && nodes().any { text(it).contains(". Episode ") } }
            until("the hero's Play") { focused().any { it.contains("Play") } }
        }
        /** Down into the shelf, then Right to episode [number]. */
        fun focusEpisode(number: Long) {
            press(Key.DirectionDown)
            until("the shelf") { focused().any { it.contains(label(1)) } }
            press(Key.DirectionRight, (number - 1).toInt())
            assertFocus(label(number), "the viewer moved to episode $number")
        }
        /** OK on the focused episode opens the source selector for it. */
        fun openSources(number: Long) {
            press(Key.Enter)
            until("the source selector") { state.screen == Screen.Sources }
            assertEquals(episode(number).identity, state.sourceTitle?.identity, "the selector is for episode $number")
            selectorTakesFocus()
        }
        /** Discovery finds nothing, so the selector's empty panel takes focus away from Details. */
        fun selectorTakesFocus() = until("the selector's focus") { focused().any { it.contains("Check again") } }
        /** The selector plays a source; the player's Back closes onto the selector, whose Back returns to Details. */
        fun playThenBack() {
            controller.mutable.value = state.copy(screen = Screen.Player)
            frames(20)
            controller.closePlayer()
            frames(20)
            assertEquals(Screen.Sources, state.screen)
            selectorTakesFocus()
            controller.backSources()
            frames()
            assertEquals(Screen.Details, state.screen)
        }

        override fun close() {
            scene.close(); controller.job?.cancel(); scope.cancel(); remote.close()
            appLoader?.shutdown(); previousLoader?.let { SingletonImageLoader.setUnsafe(it) }
        }
    }

    @Test fun firstEntryFocusesTheHero() {
        TvApp().use { app ->
            app.frames()
            app.openDetails()
            app.assertFocus("Play", "first entry into Details")
        }
    }

    @Test fun backFromSourcesFocusesTheLastFocusedEpisode() {
        TvApp().use { app ->
            app.frames()
            app.openDetails()
            app.focusEpisode(7)
            app.openSources(7)
            app.controller.backSources()
            assertEquals(Screen.Details, app.state.screen)
            app.assertFocus(label(7), "Back from Sources")
        }
    }

    @Test fun backFromThePlayerFocusesTheLastFocusedEpisode() {
        TvApp().use { app ->
            app.frames()
            app.openDetails()
            app.focusEpisode(7)
            app.openSources(7)
            app.playThenBack()
            app.assertFocus(label(7), "Back from the player, then from Sources")
        }
    }

    @Test fun backFromSourcesOpenedFromTheHeroFocusesTheHero() {
        TvApp().use { app ->
            app.frames()
            app.openDetails()
            app.press(Key.Enter)
            app.until("the source selector") { app.state.screen == Screen.Sources }
            app.selectorTakesFocus()
            app.controller.backSources()
            app.assertFocus("Play", "Back to Details the viewer left from the hero")
        }
    }

    @Test fun reopeningTheTitleFromHomeIsAFirstEntry() {
        TvApp().use { app ->
            app.frames()
            app.openDetails()
            app.focusEpisode(7)
            app.openSources(7)
            app.playThenBack()
            app.assertFocus(label(7), "Back from the player")
            app.controller.backDetails()
            app.until("Home") { app.state.screen == Screen.Home }
            app.frames(20)
            app.openDetails()
            app.assertFocus("Play", "a new visit to the same title starts on the hero")
        }
    }

    /**
     * Home → Continue Watching card → Sources (Details load behind it) → Back (app#112).
     * The page was never on screen before the selector, so Back is its first entry: it
     * opens onto the episode being continued, with the shelf on its season, not the hero.
     */
    @Test fun backFromSourcesAfterAContinueWatchingTapFocusesTheContinuedEpisode() {
        TvApp().use { app ->
            app.frames()
            app.continueWatching(episode(4, 0.45))
            app.frames()
            app.tapContinueWatching("Episode 4")
            app.until("the source selector") { app.state.screen == Screen.Sources }
            app.selectorTakesFocus()
            app.until("the Details loading behind the selector") { app.state.detail != null }
            assertEquals(Screen.Details, app.state.sourceReturnScreen, "Back from sources lands on the title's Details")
            app.controller.backSources()
            assertEquals(Screen.Details, app.state.screen)
            app.assertFocus(label(4), "Back from Sources after a Continue Watching tap")
        }
    }

    /**
     * The same tap after an earlier visit to this title's shelf left another episode
     * remembered: the continued episode wins, not the remembered one (app#112).
     */
    @Test fun aContinueWatchingTapIgnoresTheEpisodeAnEarlierVisitLeftFocused() {
        TvApp().use { app ->
            app.frames()
            app.openDetails()
            app.focusEpisode(7)
            app.controller.backDetails()
            app.until("Home") { app.state.screen == Screen.Home }
            app.until("the hero's Play") { app.focused().any { it.contains("Play") } }
            app.continueWatching(episode(4, 0.45))
            app.frames()
            app.tapContinueWatching("Episode 4")
            app.until("the source selector") { app.state.screen == Screen.Sources }
            app.selectorTakesFocus()
            app.until("the Details loading behind the selector") { app.state.detail != null }
            app.controller.backSources()
            assertEquals(Screen.Details, app.state.screen)
            app.assertFocus(label(4), "Back from Sources after a Continue Watching tap, episode 7 was focused earlier")
        }
    }

    @Test fun aMissingEpisodeFallsBackToTheContinuedEpisodeThenTheHero() {
        TvApp().use { app ->
            app.frames()
            app.openDetails()
            app.focusEpisode(7)
            app.openSources(7)
            // While the selector shows, Details refresh without episode 7; episode 3 is half watched.
            app.controller.mutable.value = app.state.copy(detail = detail.copy(
                episodes = detail.episodes.filter { it.identity.episode?.episode != 7L }.map { if (it.identity.episode?.episode == 3L) episode(3, 0.5) else it }))
            app.frames(20)
            app.controller.backSources()
            app.assertFocus(label(3), "episode 7 is gone: the continued episode")
        }
        TvApp().use { app ->
            app.frames()
            app.openDetails()
            app.focusEpisode(7)
            app.openSources(7)
            app.controller.mutable.value = app.state.copy(detail = detail.copy(episodes = detail.episodes.filter { it.identity.episode?.episode != 7L }))
            app.frames(20)
            app.controller.backSources()
            app.assertFocus("Play", "episode 7 is gone and nothing is continued: the hero")
        }
    }

    @Test fun episodesPagingInAfterTheReturnDoNotMoveFocus() {
        TvApp(pagedDetail).use { app ->
            app.frames()
            app.openDetails()
            app.focusEpisode(7)
            app.openSources(7)
            app.controller.backSources()
            app.assertFocus(label(7), "Back from Sources")
            // Opening the selector cancelled the held page; shown again, Details page on and it arrives.
            app.until("the next Details page is requested") { app.pageOffsets.contains("10") }
            app.releasePage.complete(Unit)
            app.until("episodes 11-16") { app.state.detail!!.episodes.size == 16 }
            app.assertFocus(label(7), "a later page arrived")
            assertEquals(1, app.focused().size, "one focused node: ${app.focused()}")
        }
    }

    @Test fun leavingFromAnotherRowReturnsToTheHero() {
        TvApp(detail.copy(recommendations = listOf(related))).use { app ->
            app.frames()
            app.openDetails()
            app.focusEpisode(1)
            app.press(Key.DirectionDown)
            app.assertFocus("Better Call Saul", "the viewer moved to More Like This")
            // Only an episode or the hero opens the selector today; any other row still returns to the hero.
            app.controller.mutable.value = app.state.copy(screen = Screen.Sources, sourceReturnScreen = Screen.Details,
                sourceTitle = episode(1), findingSources = false)
            app.selectorTakesFocus()
            app.controller.backSources()
            app.assertFocus("Play", "Back to Details left from More Like This")
        }
    }

    private fun text(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString(" ") { it.text }
}
