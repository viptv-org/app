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
import androidx.compose.ui.text.AnnotatedString
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
import org.viptv.app.features.details.EPISODE_JUMP_CANCEL
import org.viptv.app.features.details.EPISODE_JUMP_CHIP_DESCRIPTION
import org.viptv.app.features.details.EPISODE_JUMP_ERROR
import org.viptv.app.features.details.EPISODE_JUMP_FIELD_LABEL
import org.viptv.app.features.details.EPISODE_JUMP_GO
import org.viptv.app.features.details.EPISODE_JUMP_TITLE
import org.viptv.app.preview.PreviewData
import org.viptv.app.state.*
import org.viptv.engine.wire.*
import org.junit.Rule
import kotlin.test.*

/**
 * The Android TV episode-row header's `Episode #` chip (app#409, AND-EPISODE-JUMP-001): the
 * chip sits right of the selected season badge, its dialog matches only the selected season
 * by exact episode number, and success scrolls the lazy row to that card instead of composing
 * the season. The real TV App shell over a fake host; the remote is sent as key events.
 */
@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class, DelicateCoilApi::class)
class TvEpisodeJumpTest {
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
    private fun episode(season: Long?, number: Long) = series.copy(
        identity = MediaIdentity("$show:$season:$number", "series", emptyList(), EpisodeCoordinates("aired", season, number)),
        parent_identity = series.identity, title = "Episode $number")
    private fun seasonOne(vararg numbers: Long) = DetailSnapshot(series, episodes = numbers.map { episode(1, it) })
    private fun twoSeasons() = DetailSnapshot(series,
        episodes = listOf(1L, 3L, 7L).map { episode(1, it) } + listOf(1L, 2L, 5L).map { episode(2, it) },
        seasons = listOf(EpisodeSeason(1, 3, 0), EpisodeSeason(2, 3, 3)))
    private fun longSeason(count: Long) = DetailSnapshot(series, episodes = (1L..count).map { episode(1, it) },
        seasons = listOf(EpisodeSeason(1, count, 0)))
    private fun label(number: Long) = "$number. Episode $number"

    private fun MockRequestHandleScope.json(text: String) = respond(text, headers = headersOf(HttpHeaders.ContentType, "application/json"))

    private inner class TvApp(private val fixture: DetailSnapshot) : AutoCloseable {
        private suspend fun MockRequestHandleScope.host(request: HttpRequestData) = when (request.url.encodedPath) {
            "/api/meta/series/$show" -> json(Json.encodeToString(fixture))
            "/api/streams" -> json("""{"id":"job-1"}""")
            "/api/streams/job-1" -> json("""{"done":true,"events":[]}""")
            else -> if (request.url.encodedPath.endsWith("/source-ranking")) json("""{"streams":[]}""")
                else respond("{\"error\":\"not in this fixture: ${request.url.toString().filter { it != '"' }}\"}",
                    HttpStatusCode.NotFound, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        private val client = HttpClient(MockEngine { request -> host(request) }) { configureTransport() }
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
        fun text(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString(" ") { it.text }
        private fun description(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
        fun focused() = nodes().filter { it.config.getOrNull(SemanticsProperties.Focused) == true }
            .map { text(it) + " " + description(it).joinToString(" ") }
        fun until(what: String, condition: () -> Boolean) {
            val deadline = System.nanoTime() + 10_000_000_000L
            while (!condition()) {
                check(System.nanoTime() < deadline) {
                    "timed out waiting for $what (focus: ${focused()}; ${state.screen} detail=${state.detail != null} " +
                        "busy=${state.busy} finding=${state.findingSources} error=${state.error})"
                }
                frames(2)
            }
        }
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
        fun ok() {
            val node = nodes().firstOrNull { it.config.getOrNull(SemanticsProperties.Focused) == true }
            assertTrue(requireNotNull(node) { "nothing is focused: ${focused()}" }.config[SemanticsActions.OnClick].action!!.invoke())
            frames()
        }
        fun request(label: String) {
            requireNotNull(nodes().firstOrNull { text(it) == label && it.config.contains(SemanticsActions.RequestFocus) }) {
                "no focusable $label among ${nodes().map(::text)}"
            }.config[SemanticsActions.RequestFocus].action!!.invoke()
            frames()
        }
        fun chip() = nodes().singleOrNull { EPISODE_JUMP_CHIP_DESCRIPTION in description(it) }
        fun seasonPill(season: Long) = nodes().singleOrNull { text(it) == "Season $season" && it.config.contains(SemanticsActions.OnClick) }
        fun click(label: String) {
            nodes().single { text(it) == label && it.config.contains(SemanticsActions.OnClick) }
                .config[SemanticsActions.OnClick].action!!.invoke()
            frames()
        }
        fun field() = nodes().singleOrNull { it.config.contains(SemanticsActions.SetText) }
        fun type(value: String) {
            requireNotNull(field()) { "no editable field: ${nodes().map(::text)}" }
                .config[SemanticsActions.SetText].action!!.invoke(AnnotatedString(value))
            frames()
        }
        fun typed() = field()?.config?.getOrNull(SemanticsProperties.EditableText)?.text
        fun dialogOpen() = nodes().any { text(it) == EPISODE_JUMP_TITLE }
        fun episodeCards() = nodes().filter { Regex("""\d+\. Episode \d+""").containsMatchIn(text(it)) }

        fun openDetails() {
            controller.open(series)
            until("Details") { state.screen == Screen.Details && state.detail != null && episodeCards().isNotEmpty() }
            until("the hero's Play") { focused().any { it.contains("Play") } }
        }
        /** Hero → the shelf's card at [index] in the selected season, the way the D-pad reaches it. */
        fun focusEpisode(index: Int) {
            press(Key.DirectionDown)
            until("the shelf") { focused().any { it.contains(label(1)) } }
            if (index == 0) return
            press(Key.DirectionRight, index)
            frames(20)
        }
        fun openJump() {
            request("Season 1")
            press(Key.DirectionRight)
            until("the Episode # chip") { focused().any { it.contains(EPISODE_JUMP_CHIP_DESCRIPTION) } }
            ok()
            until("the Jump to episode dialog") { dialogOpen() }
            until("the focused Episode number field") { field()?.config?.getOrNull(SemanticsProperties.Focused) == true }
        }
        fun assertFocused(expected: String, why: String) {
            frames(30)
            val now = focused()
            assertTrue(now.any { it.contains(expected) }, "$why: focus is on $expected, not $now")
        }

        override fun close() {
            scene.close(); controller.job?.cancel(); scope.cancel(); remote.close()
            appLoader?.shutdown(); previousLoader?.let { SingletonImageLoader.setUnsafe(it) }
        }
    }

    @Test fun theChipSitsRightOfTheSelectedSeasonBadge() {
        TvApp(twoSeasons()).use { app ->
            app.frames()
            app.openDetails()
            val first = requireNotNull(app.chip()) { "the header has no Episode # chip" }
            val seasonOne = requireNotNull(app.seasonPill(1)) { "the header has no Season 1 badge" }
            assertTrue(first.boundsInRoot.left >= seasonOne.boundsInRoot.right, "the chip follows Season 1")
            assertEquals(seasonOne.boundsInRoot.top, first.boundsInRoot.top, 1f, "the chip shares the badge's line")
            app.request("Season 2")
            app.ok()
            app.until("the chip to follow Season 2") {
                val chip = app.chip()?.boundsInRoot
                val pill = app.seasonPill(2)?.boundsInRoot
                chip != null && pill != null && chip.left >= pill.right
            }
            val second = requireNotNull(app.chip()) { "the chip left with the season badge" }
            val two = requireNotNull(app.seasonPill(2))
            assertTrue(second.boundsInRoot.left >= two.boundsInRoot.right, "the chip follows the newly selected Season 2")
            assertTrue((app.seasonPill(1)?.boundsInRoot?.right ?: 0f) <= second.boundsInRoot.left, "Season 1 stays left of the chip")
        }
    }

    @Test fun noEpisodesMeansNoChip() {
        TvApp(DetailSnapshot(series, episodes = emptyList())).use { app ->
            app.frames()
            app.controller.open(series)
            app.until("Details") { app.state.screen == Screen.Details && app.state.detail != null }
            app.frames(20)
            assertNull(app.chip(), "a title without episodes has no Episode # chip")
        }
    }

    @Test fun okOnTheChipOpensTheJumpDialogOnAnEmptyFocusedField() {
        TvApp(twoSeasons()).use { app ->
            app.frames()
            app.openDetails()
            app.openJump()
            assertTrue(app.dialogOpen())
            assertEquals("", app.typed(), "the field starts empty")
            assertTrue(app.field()?.config?.getOrNull(SemanticsProperties.Focused) == true, "the field is focused")
            assertNotNull(app.nodes().firstOrNull { app.text(it) == EPISODE_JUMP_GO && it.config.contains(SemanticsActions.OnClick) }, "Go")
            assertNotNull(app.nodes().firstOrNull { app.text(it) == EPISODE_JUMP_CANCEL && it.config.contains(SemanticsActions.OnClick) }, "Cancel")
            assertNotNull(app.nodes().firstOrNull { app.text(it) == EPISODE_JUMP_FIELD_LABEL }, "the field is labelled Episode number")
        }
    }

    @Test fun goFocusesTheExactEpisodeAndLeavesTheSeasonAlone() {
        TvApp(twoSeasons()).use { app ->
            app.frames()
            app.openDetails()
            app.openJump()
            app.type("7")
            app.click(EPISODE_JUMP_GO)
            app.until("episode 7") { app.focused().any { it.contains(label(7)) } }
            assertFalse(app.dialogOpen(), "a match closes the dialog")
            assertEquals(Screen.Details, app.state.screen, "jumping opens no source picker")
            assertFalse(app.state.findingSources, "jumping finds no sources")
            assertNull(app.state.sourceTitle, "no source selector was opened")
            app.frames(20)
            assertTrue(requireNotNull(app.chip()).boundsInRoot.left >= requireNotNull(app.seasonPill(1)).boundsInRoot.right,
                "season 1 stays selected")
        }
    }

    @Test fun anUnmatchedNumberKeepsTheDialogAndTheRowAsTheyWere() {
        TvApp(twoSeasons()).use { app ->
            app.frames()
            app.openDetails()
            app.focusEpisode(1)
            app.openJump()
            app.type("99")
            app.click(EPISODE_JUMP_GO)
            app.until("the error") { app.nodes().any { app.text(it) == EPISODE_JUMP_ERROR } }
            assertTrue(app.dialogOpen(), "the dialog stays open")
            assertEquals("99", app.typed(), "the typed text stays")
            assertTrue(app.field()?.config?.getOrNull(SemanticsProperties.Focused) == true, "the field keeps focus")
            app.click(EPISODE_JUMP_CANCEL)
            app.until("the chip") { app.focused().any { it.contains(EPISODE_JUMP_CHIP_DESCRIPTION) } }
            assertFalse(app.dialogOpen(), "Cancel closes the dialog")
            app.press(Key.DirectionDown)
            app.assertFocused(label(3), "an unmatched jump leaves the row where it was")
        }
    }

    @Test fun emptyMalformedAndOverflowInputShowTheErrorAndCanBeCorrected() {
        TvApp(twoSeasons()).use { app ->
            app.frames()
            app.openDetails()
            app.openJump()
            app.click(EPISODE_JUMP_GO)
            app.until("the error for empty input") { app.nodes().any { app.text(it) == EPISODE_JUMP_ERROR } }
            app.type("3x")
            app.click(EPISODE_JUMP_GO)
            app.until("the error for malformed input") { app.dialogOpen() && app.nodes().any { app.text(it) == EPISODE_JUMP_ERROR } }
            app.type("999999999999999999999999999999")
            app.click(EPISODE_JUMP_GO)
            app.until("the error for overflow") { app.dialogOpen() && app.nodes().any { app.text(it) == EPISODE_JUMP_ERROR } }
            app.type("3")
            app.click(EPISODE_JUMP_GO)
            app.until("episode 3") { app.focused().any { it.contains(label(3)) } }
            assertFalse(app.dialogOpen(), "a corrected value succeeds without reopening")
        }
    }

    @Test fun onlyTheSelectedSeasonIsSearched() {
        TvApp(twoSeasons()).use { app ->
            app.frames()
            app.openDetails()
            app.openJump()
            app.type("5")
            app.click(EPISODE_JUMP_GO)
            app.until("the error for season 2's episode 5") { app.nodes().any { app.text(it) == EPISODE_JUMP_ERROR } }
            assertEquals(Screen.Details, app.state.screen)
            app.click(EPISODE_JUMP_CANCEL)
            app.until("the chip") { app.focused().any { it.contains(EPISODE_JUMP_CHIP_DESCRIPTION) } }
        }
    }

    @Test fun episodeZeroIsFoundOnlyWhenTheSelectedSeasonListsIt() {
        TvApp(seasonOne(0L, 1L, 3L)).use { app ->
            app.frames()
            app.openDetails()
            app.openJump()
            app.type("2")
            app.click(EPISODE_JUMP_GO)
            app.until("the error for the gap at 2") { app.nodes().any { app.text(it) == EPISODE_JUMP_ERROR } }
            app.type("0")
            app.click(EPISODE_JUMP_GO)
            app.until("episode 0") { app.focused().any { it.contains(label(0)) } }
            assertFalse(app.dialogOpen())
        }
    }

    @Test fun cancelAndBackBothRestoreTheChip() {
        TvApp(twoSeasons()).use { app ->
            app.frames()
            app.openDetails()
            app.openJump()
            app.click(EPISODE_JUMP_CANCEL)
            app.until("the chip after Cancel") { app.focused().any { it.contains(EPISODE_JUMP_CHIP_DESCRIPTION) } }
            app.frames(20)
            assertFalse(app.dialogOpen(), "Cancel closes the dialog")

            app.ok()
            app.until("the dialog again") { app.dialogOpen() }
            app.press(Key.Escape)
            app.until("the chip after Back") { app.focused().any { it.contains(EPISODE_JUMP_CHIP_DESCRIPTION) } }
            assertFalse(app.dialogOpen(), "Back closes the dialog")
        }
    }

    @Test fun aJumpNearTheEndOfA1410EpisodeSeasonComposesOnlyWhatItShows() {
        TvApp(longSeason(1410)).use { app ->
            app.frames()
            app.openDetails()
            assertTrue(app.episodeCards().size < 80, "the shelf composes only its visible cards: ${app.episodeCards().size}")
            app.openJump()
            app.type("1400")
            app.click(EPISODE_JUMP_GO)
            app.until("episode 1400") { app.focused().any { it.contains(label(1400)) } }
            assertTrue(app.episodeCards().size < 80, "the jump composes only the target card: ${app.episodeCards().size}")

            app.openJump()
            app.type("2")
            app.click(EPISODE_JUMP_GO)
            app.until("episode 2") { app.focused().any { it.contains(label(2)) } }
        }
    }
}
