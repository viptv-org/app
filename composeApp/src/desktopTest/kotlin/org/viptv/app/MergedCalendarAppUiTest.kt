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
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.viptv.app.connection.*
import org.viptv.app.features.calendar.CALENDAR_FALLBACK
import org.viptv.app.preview.PreviewData
import org.viptv.app.resources.Res
import org.viptv.app.state.*
import org.viptv.engine.wire.*
import java.io.File
import kotlin.math.abs
import org.junit.Rule
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class, DelicateCoilApi::class)
class MergedCalendarAppUiTest {
    @get:Rule val uiThread = UiThreadRule()
    @get:Rule val clock = MergedCalendarWire.fixedClock()
    @Test fun phoneShowsYourShowsMergedAndFiltersThem() = merged(television = false, width = 390, height = 844, name = "phone")
    @Test fun tvShowsYourShowsMergedAndFiltersThem() = merged(television = true, width = 1920, height = 1080, name = "tv")
    @Test fun tabletShowsYourShowsMergedAndFiltersThem() = merged(television = false, width = 1024, height = 768, name = "tablet", details = false)
    @Test fun desktopShowsYourShowsMergedAndFiltersThem() = merged(television = false, width = 1280, height = 800, name = "desktop", details = false, desktopChrome = true)
    @Test fun webShowsYourShowsMergedAndFiltersThem() = merged(television = false, width = 1280, height = 800, name = "web", details = false, browser = true)

    @Test fun phoneLibraryOnlyProfileHasNoFilter() = single(MergedCalendarWire.page(listOf(
        MergedCalendarWire.library("lib-1", "Slow Horses", 5, 3, poster = art("poster3.jpg")),
        MergedCalendarWire.library("lib-2", "The Pitt", 2, 7, date = MergedCalendarWire.today(22), poster = art("poster4.jpg"))),
        listOf("library"), null), "phone-library-only") { nodes ->
        assertEquals(2, nodes.count(::marked))
        assertFalse(nodes.any { it.config.getOrNull(SemanticsProperties.Role) == Role.DropdownList && text(it).startsWith("Shows: ") },
            "One source needs no filter")
        assertFalse(nodes.any { text(it) == "Your shows" })
    }

    @Test fun phoneOldServerShowsThePublicScheduleUnmarked() = single(MergedCalendarWire.page(listOf(
        MergedCalendarWire.public("public-1", "Public show", poster = art("poster5.jpg"))), null, null), "phone-old-server") { nodes ->
        assertTrue(nodes.any { text(it).contains("Public show") })
        assertFalse(nodes.any { it.config.getOrNull(SemanticsProperties.Role) == Role.DropdownList && text(it).startsWith("Shows: ") })
        assertFalse(nodes.any { marked(it) || text(it) == "Your shows" })
    }

    private fun art(name: String) = Res.getUri("files/ui-preview/$name")

    private val metaOffsets = mutableListOf<Int>()
    private val slowHorses get() = MergedCalendarWire.seriesCard("Slow Horses").copy(poster = art("poster3.jpg"),
        landscape = art("landscape.jpg"), description = "Slow Horses, a scheduled series.")
    private val allEpisodes = (1L..5L).flatMap { season -> (1L..6L).map { season to it } }
    private fun detailPage(offset: Int, limit: Int): String {
        val series = slowHorses
        val window = allEpisodes.drop(offset).take(limit).map { (season, number) ->
            series.copy(identity = MediaIdentity(MergedCalendarWire.episodeId("Slow Horses", season.toInt(), number.toInt()), "series", emptyList(),
                EpisodeCoordinates("aired", season, number)), parent_identity = series.identity, title = "Episode $season-$number", poster = null, description = null)
        }
        val next = (offset + window.size).takeIf { it < allEpisodes.size }?.toString()
        return Json.encodeToString(DetailSnapshot(series, episodes = window, next_cursor = next,
            seasons = (1L..5L).map { EpisodeSeason(it, 6, (it - 1) * 6) }))
    }

    private val mergedPage get() = MergedCalendarWire.page(listOf(
        MergedCalendarWire.library("lib-1", "Slow Horses", 5, 3, date = MergedCalendarWire.today(3), poster = art("poster3.jpg")),
        MergedCalendarWire.public("public-1", "Public premiere", source = "public", date = MergedCalendarWire.today(18), poster = art("poster5.jpg")),
        MergedCalendarWire.library("lib-2", "The Pitt", 2, 7, date = MergedCalendarWire.today(21), poster = art("poster4.jpg"))),
        listOf("library", "public"), null)

    private fun merged(television: Boolean, width: Int, height: Int, name: String, details: Boolean = true,
        desktopChrome: Boolean = false, browser: Boolean = false) = scene(mergedPage, television, width, height, desktopChrome, browser) { s ->
        val phone = !television && width < 640
        if (phone) s.showToday()
        s.until("the merged rows") { s.nodes().any { text(it).contains("Slow Horses") } && s.nodes().any { text(it).contains("The Pitt") } }
        assertEquals(2, s.nodes().count { marked(it) && text(it).contains("Chapter") }, "Both library rows are marked")
        assertFalse(s.nodes().any { text(it).contains("Public premiere") }, "$name: Your shows hides the public schedule")
        assertEquals("Shows: Your shows", s.showsLabel(), "$name: the Shows chip is the filter and states the default")
        if (television) assertTrue(s.showsTrigger()!!.config.contains(SemanticsActions.RequestFocus), "The remote reaches the filter")
        s.save("$name-calendar-your-shows")
        s.chooseShows("All")
        s.until("the public schedule") { s.nodes().any { text(it).contains("Public premiere") } }
        assertEquals("Shows: All", s.showsLabel(), "$name: the chip shows the picked source")
        assertFalse(s.nodes().any { marked(it) && text(it).contains("Public premiere") }, "$name: a public row is not marked as yours")
        assertFalse(s.nodes().any { text(it) == CALENDAR_FALLBACK }, "$name: All needs no fallback line")
        s.save("$name-calendar-all")
        s.chooseShows("Your shows")
        s.until("Your shows again") { s.nodes().none { text(it).contains("Public premiere") } && s.nodes().any { text(it).contains("Slow Horses") } }
        assertEquals("Shows: Your shows", s.showsLabel(), "$name: the chip switches back")
        if (!details) return@scene
        val row = s.nodes().first { text(it).contains("Slow Horses") && it.config.contains(SemanticsActions.OnClick) }
        row.config[SemanticsActions.OnClick].action!!.invoke()
        s.until("Details") { s.controller.state.value.screen == Screen.Details && s.controller.state.value.detail?.episodes?.isNotEmpty() == true }
        assertEquals(MergedCalendarWire.episodeId("Slow Horses", 5, 3), s.controller.state.value.calendarEpisode?.identity?.id)
        assertEquals(MergedCalendarWire.seriesId("Slow Horses"), s.controller.state.value.detail?.item?.identity?.id)
        s.until("season 5's episodes") { s.nodes().any { text(it).contains("Episode 5-3") } }
        assertTrue(s.nodes().any { text(it).contains("Season 5") }, "Season 5 is selected")
        assertTrue(24 in synchronized(metaOffsets) { metaOffsets.toList() }, "Season 5 loads through Details paging: $metaOffsets")
        s.save("$name-details-from-calendar")
    }

    private fun single(page: String, name: String, check: (List<SemanticsNode>) -> Unit) = scene(page, false, 390, 844) { s ->
        s.until("the rows") { s.controller.state.value.calendarEntries.isNotEmpty() && !s.controller.state.value.busy }
        s.frames(20)
        check(s.nodes())
        s.save(name)
    }

    private class Scene(val scene: ImageComposeScene, val controller: ApplicationController, val capture: File?, val television: Boolean,
        val focusManager: FocusManager?) {
        var frame = 0L
        fun frames(count: Int = 10) = repeat(count) { scene.render(frame++ * 16_666_667L).close(); Thread.sleep(5) }
        fun nodes() = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
        fun until(what: String, condition: () -> Boolean) {
            val deadline = System.nanoTime() + 10_000_000_000L
            while (!condition()) { check(System.nanoTime() < deadline) { "timed out waiting for $what" }; frames(2) }
        }

        fun showsTrigger() = nodes().firstOrNull {
            it.config.getOrNull(SemanticsProperties.Role) == Role.DropdownList && text(it).startsWith("Shows: ") }
        fun showsLabel(): String? = showsTrigger()?.let(::text)

        private fun rows() = nodes().filter {
            it.config.getOrNull(SemanticsProperties.Role) in listOf(Role.RadioButton, Role.Checkbox) }
        private fun rowLabel(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text).orEmpty()
            .firstOrNull()?.text?.removeSuffix(" · unavailable").orEmpty()

        private fun focusedRow() = rows().firstOrNull {
            it.config.getOrNull(SemanticsProperties.Focused) == true }?.let(::rowLabel)

        private fun request(node: SemanticsNode) {
            assertTrue(node.config[SemanticsActions.RequestFocus].action!!.invoke(), "a focus stop"); frames(10)
        }

        private fun focusedText() = nodes().firstOrNull {
            it.config.getOrNull(SemanticsProperties.Focused) == true }?.let(::text)

        private fun focusedChip() = nodes().firstOrNull {
            it.config.getOrNull(SemanticsProperties.Role) == Role.DropdownList &&
                it.config.getOrNull(SemanticsProperties.Focused) == true }?.let(::text)

        fun openShows() {
            if (television) {
                if (focusedChip() == null) {
                    until("the remote's focus") { focusedText() != null }
                    press(Key.DirectionDown)
                    until("the page") { focusedChip() != null || focusedText() == "Today" }
                    if (focusedChip() == null) press(Key.DirectionUp)
                    until("the chip row") { focusedChip() != null }
                }
                var guard = 0
                while (focusedChip()?.startsWith("Shows: ") != true && guard++ < 8) press(Key.DirectionRight)
                assertTrue(focusedChip()?.startsWith("Shows: ") == true,
                    "the remote reaches the Shows chip; focused=" + focusedChip())
                press(Key.DirectionCenter)
            } else {
                val chip = requireNotNull(showsTrigger()) { "no Shows chip; texts=" + nodes().map(::text).filter(String::isNotBlank) }
                assertTrue(chip.config[SemanticsActions.OnClick].action!!.invoke(), "the chip opens its list")
            }
            frames(10)
            val deadline = System.nanoTime() + 10_000_000_000L
            while (rows().isEmpty()) {
                check(System.nanoTime() < deadline) { "the Shows list did not open; focused=" +
                    nodes().filter { it.config.getOrNull(SemanticsProperties.Focused) == true }.map(::text) +
                    "; texts=" + nodes().map(::text).filter(String::isNotBlank) }
                frames(2)
            }
            if (television) until("the panel to take focus") { rows().any { it.config.getOrNull(SemanticsProperties.Focused) == true } }
        }

        fun chooseShows(label: String) {
            openShows()
            if (television) {
                require(rows().any { rowLabel(it) == label }) { "no $label row; rows=" + rows().map(::rowLabel) }
                val moves = rows().indexOfFirst { rowLabel(it) == label } - rows().indexOfFirst { it.config.getOrNull(SemanticsProperties.Focused) == true }
                repeat(abs(moves)) { press(if (moves > 0) Key.DirectionDown else Key.DirectionUp) }
                if (focusedRow() != label) request(rows().single { rowLabel(it) == label })
                assertEquals(label, focusedRow(), "the remote reaches $label")
                press(Key.DirectionCenter)
            } else {
                val row = rows().single { rowLabel(it) == label }
                assertTrue(row.config[SemanticsActions.OnClick].action!!.invoke(), "$label is a pick")
            }
            frames(10)
            until("the Shows list to close") { rows().isEmpty() }
        }

        // Desktop maps no arrow key to a focus move, so drive the focus manager.
        private fun press(key: Key) {
            val consumed = scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyDown))
            scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyUp))
            frames(10)
            if (!consumed && key in listOf(Key.DirectionLeft, Key.DirectionRight, Key.DirectionUp, Key.DirectionDown)) {
                focusManager!!.moveFocus(when (key) {
                    Key.DirectionLeft -> FocusDirection.Left
                    Key.DirectionRight -> FocusDirection.Right
                    Key.DirectionUp -> FocusDirection.Up
                    else -> FocusDirection.Down
                })
                frames(10)
            }
        }

        fun showToday() {
            until("the calendar rows") { controller.state.value.calendarEntries.isNotEmpty() && !controller.state.value.busy }
            val today = controller.state.value.calendarDays.single { it.label == "Today" }
            fun date() = nodes().firstOrNull { it.config.contains(SemanticsActions.OnClick) && text(it).startsWith("${today.weekday} ${today.number} ") }
            until("today's date") { date() != null }
            val date = requireNotNull(date())
            assertTrue(date.config[SemanticsActions.OnClick].action!!.invoke()); frames(40)
        }
        fun save(variant: String) {
            val dir = capture ?: return
            frames(60); dir.mkdirs()
            scene.render(frame++ * 16_666_667L).use { image -> image.encodeToData()!!.use { File(dir, "$variant.png").writeBytes(it.bytes) } }
        }
    }

    private fun scene(page: String, television: Boolean, width: Int, height: Int, desktopChrome: Boolean = false, browser: Boolean = false,
        body: (Scene) -> Unit) = runBlocking {
        val client = HttpClient(MockEngine { request ->
            val path = request.url.encodedPath
            val response = when {
                path == "/api/calendar" -> page
                path.startsWith("/api/meta/series/") -> {
                    val offset = request.url.parameters["offset"]?.toInt() ?: 0
                    synchronized(metaOffsets) { metaOffsets += offset }
                    detailPage(offset, request.url.parameters["limit"]?.toInt() ?: 24)
                }
                else -> return@MockEngine respond("{}", HttpStatusCode.NotFound, headersOf(HttpHeaders.ContentType, "application/json"))
            }
            respond(response, headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureTransport() }
        val store = object : RemoteSessionStore {
            override suspend fun read(): SavedRemoteSession? = null
            override suspend fun save(session: SavedRemoteSession) = Unit
            override suspend fun clear() = Unit
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val remote = RemoteConnection("https://watch.example", store, client, browserMode = false)
        val controller = ApplicationController(scope, store)
        controller.connection = remote
        controller.mutable.value = ApplicationState(screen = Screen.Library, selectedProfile = PreviewData.profile.id,
            profiles = PreviewData.profiles, home = PreviewData.home, preferences = PreferencesSnapshot())
        val capture = System.getenv("VIPTV_CALENDAR_CAPTURE_DIR")?.let(::File)
        val density = if (capture != null && !television) 2f else 1f
        var previousLoader: ImageLoader? = null
        var appLoader: ImageLoader? = null
        var focusManager: FocusManager? = null
        val scene = ImageComposeScene((width * density).toInt(), (height * density).toInt(), Density(density)) {
            val context = LocalPlatformContext.current
            remember { previousLoader = SingletonImageLoader.get(context); SingletonImageLoader.reset() }
            focusManager = LocalFocusManager.current
            App(television = television, browser = browser, desktopChrome = desktopChrome, providedController = controller, manageControllerLifecycle = false)
            appLoader = SingletonImageLoader.get(context)
        }
        try {
            controller.navigate(Screen.Calendar)
            body(Scene(scene, controller, capture, television, focusManager))
        } finally {
            scene.close(); scope.cancel(); remote.close()
            appLoader?.shutdown(); previousLoader?.let { SingletonImageLoader.setUnsafe(it) }
        }
    }

    private companion object {
        fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
        fun text(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString(" ") { it.text }
        fun marked(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text == "Your show" }
    }
}
