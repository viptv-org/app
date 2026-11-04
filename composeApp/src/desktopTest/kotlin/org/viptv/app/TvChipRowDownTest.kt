package org.viptv.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.scene.ComposeScene
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.viptv.app.connection.BrowseQuery
import org.viptv.app.connection.DiscoveryCatalog
import org.viptv.app.connection.DiscoveryControls
import org.viptv.app.connection.FeatureAvailability
import org.viptv.app.features.browse.TvBrowseScreen
import org.viptv.app.features.calendar.CalendarFilter
import org.viptv.app.features.calendar.TvCalendarScreen
import org.viptv.app.features.library.TvLibraryScreen
import org.viptv.app.features.live.LiveChannel
import org.viptv.app.features.live.LiveProgram
import org.viptv.app.features.live.LiveScreen
import org.viptv.app.features.sources.TvSourceInfo
import org.viptv.app.features.sources.TvSourcesScreen
import org.viptv.app.features.sources.qualityLabel
import org.viptv.app.preview.PreviewData
import org.viptv.app.preview.PreviewTvCalendar
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.Choice
import org.viptv.engine.wire.ControlKind
import org.viptv.engine.wire.MediaIdentity
import org.viptv.engine.wire.QueryControl
import org.junit.Rule
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class TvChipRowDownTest {
    @get:Rule val uiThread = UiThreadRule()
    private fun onEdt(block: suspend CoroutineScope.() -> Unit) = runBlocking(block = block)

    private class Scenario(val name: String, val content: @Composable () -> Unit, val chips: List<String>, val landed: String)

    private class Fixture(val content: @Composable () -> Unit) : AutoCloseable {
        private var frame = 0L
        private val sceneField = ImageComposeScene::class.java.getDeclaredField("scene").apply { isAccessible = true }
        private fun composeScene() = sceneField.get(scene) as ComposeScene
        private val scene = ImageComposeScene(1920, 1080, Density(1f)) {
            ViptvTheme(television = true) {
                Surface(Modifier.fillMaxSize(), color = Palette.background) { content() }
            }
        }.also { s -> repeat(20) { s.render(frame++ * 16_666_667L).close() } }

        private fun nodes() = scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(true) }
        private fun text(node: SemanticsNode): String = listOf(
            node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ").orEmpty(),
            node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty(),
        ).filter { it.isNotBlank() }.joinToString(" ")
        fun focusedText() = nodes().firstOrNull { it.config.getOrNull(SemanticsProperties.Focused) == true }?.let(::text).orEmpty()
        suspend fun settle() = repeat(4) { repeat(8) { scene.render(frame++ * 16_666_667L).close() }; delay(10) }

        private fun pressKey(key: Key) {
            val direction = when (key) {
                Key.DirectionRight -> FocusDirection.Right
                Key.DirectionUp -> FocusDirection.Up
                else -> FocusDirection.Down
            }
            val consumed = scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyDown))
            scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyUp))
            if (!consumed) composeScene().focusManager.takeFocus(direction)
        }

        suspend fun focusFirstChip(label: String) {
            val node = nodes().single { text(it) == label && it.config.contains(SemanticsActions.RequestFocus) }
            check(node.config[SemanticsActions.RequestFocus].action?.invoke() == true) { "$label refused focus" }
            settle()
            assertEquals(label, focusedText(), "the chip row should open on '$label'")
        }

        suspend fun right(n: Int) = repeat(n) { pressKey(Key.DirectionRight); settle() }
        suspend fun down() { pressKey(Key.DirectionDown); settle() }

        fun assertFocused(label: String) = assertEquals(label, focusedText())
        fun assertLanded(what: String, expected: String) =
            assertTrue(focusedText().contains(expected), "$what: Down must land on '$expected', focused '${focusedText()}'")

        override fun close() = scene.close()
    }

    private fun liveChannel() = LiveChannel(
        Card(MediaIdentity("one", "live", emptyList()), provider_id = "m3u:1", title = "Fixture news", badges = emptyList()),
        id = "one", connectionId = "m3u:1", programs = listOf(LiveProgram("p1", "Morning news", 0, 180)))

    private fun browseFixtures(): Triple<List<Card>, DiscoveryControls, BrowseQuery> {
        val sort = QueryControl("sort", "Sort", ControlKind.SINGLECHOICE, true,
            listOf(Choice("popular", "Popular"), Choice("new", "New")))
        val genre = QueryControl("genre", "Genre", ControlKind.SINGLECHOICE, false,
            listOf("Action", "Comedy").map { Choice(it.lowercase(), it) })
        val catalog = DiscoveryCatalog("top", "Popular", "movie", "c1", controls = listOf(sort, genre), connection_name = "Cinemeta")
        val controls = DiscoveryControls("stremio", listOf("movie"), emptyList(), catalogs = listOf(catalog),
            discovery = FeatureAvailability("Available"), search = FeatureAvailability("Available"),
            filters = FeatureAvailability("Available"), calendar = FeatureAvailability("Unsupported"))
        return Triple(PreviewData.posters, controls, BrowseQuery("movie", "top", "c1"))
    }

    private fun scenarios(): List<Scenario> {
        val (browseCards, browseControls, browseQuery) = browseFixtures()
        val sourceInfo = PreviewData.filterSources.associate { it.id to TvSourceInfo(quality = it.qualityLabel()) }
        return listOf(
            Scenario("Calendar", {
                TvCalendarScreen(PreviewTvCalendar.days, PreviewTvCalendar.entries, {}, {}, controls = true,
                    filter = CalendarFilter.YourShows, actions = PreviewTvCalendar.actions)
            }, listOf("Type: All", "Shows: Your shows", "Previous week", "This week", "Next week"), "Today"),
            Scenario("Live", {
                LiveScreen(listOf(liveChannel()), television = true, start = null, now = 0, watch = {}, manage = {},
                    groupLabels = mapOf("news" to "News", "sport" to "Sport"), groupChanged = {}, favoritesFilter = {})
            }, listOf("Group: All", "Favourites"), "Fixture news"),
            Scenario("Browse", {
                TvBrowseScreen(browseCards, false, {}, controls = browseControls, query = browseQuery,
                    filterChanged = { _, _ -> }, multipleChanged = { _, _ -> }, applyQuery = {})
            }, listOf("Catalog: Popular Movies", "Sort: Required", "Genre: Any"), "The Whisper Man"),
            Scenario("My List", {
                TvLibraryScreen(PreviewData.posters, true, {}, {})
            }, listOf("Type: All", "Sort: Date added"), "The Whisper Man"),
            Scenario("Sources", {
                TvSourcesScreen(null, PreviewData.filterSources, false, null, {}, {}, info = sourceInfo)
            }, listOf("Quality: All", "Source: All sources"), "Torrentio"),
        )
    }

    @Test fun downFromEveryChipRowLandsInTheGridOrRail() = onEdt {
        for (scenario in scenarios()) {
            Fixture(scenario.content).use { f ->
                f.settle()
                for ((index, chip) in scenario.chips.withIndex()) {
                    f.focusFirstChip(scenario.chips.first())
                    f.right(index)
                    f.assertFocused(chip)
                    f.down()
                    f.assertLanded("${scenario.name} '$chip'", scenario.landed)
                }
            }
        }
    }
}