package org.viptv.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import org.viptv.app.connection.BrowseQuery
import org.viptv.app.connection.DiscoveryCatalog
import org.viptv.app.connection.DiscoveryControls
import org.viptv.app.connection.FeatureAvailability
import org.viptv.app.connection.defaultLibraryQuery
import org.viptv.app.connection.withSelections
import org.viptv.app.features.browse.TvBrowseScreen
import org.viptv.app.features.library.TvLibraryScreen
import org.viptv.app.features.sources.TvSourceInfo
import org.viptv.app.features.sources.TvSourcesScreen
import org.viptv.app.features.sources.qualityLabel
import org.viptv.app.preview.PreviewData
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.*
import org.junit.Rule
import java.io.File
import kotlin.test.*

@OptIn(InternalComposeUiApi::class)
class TvFilterBarTest {
    @get:Rule val uiThread = UiThreadRule()

    private val posters = PreviewData.posters.map { it.copy(poster = null) }
    private val genre = QueryControl("genre", "Genre", ControlKind.SINGLECHOICE, false,
        listOf("Action", "Adventure", "Animation", "Comedy", "Crime", "Documentary", "Drama", "Family", "Fantasy", "Horror", "Thriller")
            .map { Choice(it.lowercase(), it) })
    private val sort = QueryControl("sort", "Sort", ControlKind.SINGLECHOICE, true,
        listOf(Choice("popular", "Popular"), Choice("new", "New"), Choice("rated", "Top rated")))
    private val languages = QueryControl("language", "Languages", ControlKind.MULTICHOICE, false,
        listOf("English", "Japanese", "Korean", "French", "Spanish").map { Choice(it.lowercase(), it) }, maximum_selections = 2)
    private val free = QueryControl("free", "Free only", ControlKind.TOGGLE, false, emptyList())
    private val search = QueryControl("search", "Search", ControlKind.TEXT, false, emptyList())
    private val popular = DiscoveryCatalog("top", "Popular", "movie", "c1", controls = listOf(sort, genre, languages, free, search), connection_name = "Cinemeta")
    private val featured = DiscoveryCatalog("featured", "Featured", "movie", "c2", controls = listOf(genre), connection_name = "TMDB")
    private val controls = DiscoveryControls("stremio", listOf("movie"), emptyList(), catalogs = listOf(popular, featured),
        discovery = FeatureAvailability("Available"), search = FeatureAvailability("Available"),
        filters = FeatureAvailability("Available"), calendar = FeatureAvailability("Unsupported"))

    private inner class Browse {
        var query by mutableStateOf(BrowseQuery("movie", "top", "c1"))
        val requests = mutableListOf<String>()
        val changed: (String, String) -> Unit = { id, value ->
            requests += "$id=$value"
            query = query.withSelections(id, listOf(value).filter(String::isNotBlank))
        }
        val multiple: (String, List<String>) -> Unit = { id, values -> requests += "$id=${values.joinToString(",")}"; query = query.withSelections(id, values) }
        val apply: (BrowseQuery) -> Unit = { requests += "query"; query = it }
    }

    @Test fun tvMoviesFiltersAreChipsWithARightPanelAndRemoteNavigation() {
        val browse = Browse()
        Page {
            TvBrowseScreen(posters, false, {}, controls = controls, query = browse.query,
                filterChanged = browse.changed, multipleChanged = browse.multiple, applyQuery = browse.apply)
        }.use {
            assertEquals(listOf("Catalog: Popular Movies", "Sort: Required", "Genre: Any", "Languages: Any", "Search: Any"), it.triggers(),
                "one row of dropdown chips, the catalog first")
            assertFalse(it.has("Filters"), "no Filters chip")
            assertFalse(it.has("Show results"), "no Show results step")
            it.capture("tv-browse-chips")

            it.focusChip("Catalog: Popular Movies")
            it.press(Key.DirectionRight)
            assertEquals("Sort: Required", it.focusedChip(), "Right moves along the row")
            it.press(Key.DirectionRight)
            assertEquals("Genre: Any", it.focusedChip(), "Right keeps moving")
            it.press(Key.DirectionLeft)
            assertEquals("Sort: Required", it.focusedChip(), "Left moves back")

            it.press(Key.DirectionCenter)
            assertTrue(it.panel("Sort"), "OK opens the panel")
            assertEquals("Popular", it.focusedRow(), "a required sort opens on its first option")
            it.focusRow("New")
            it.press(Key.DirectionCenter)
            assertEquals(listOf("sort=new"), browse.requests, "OK picks and applies at once")
            assertFalse(it.panel("Sort"), "a single pick closes the panel")
            assertEquals("Sort: New", it.focusedChip(), "focus returns to the chip that opened the panel")

            it.press(Key.DirectionCenter)
            assertEquals("New", it.focusedRow(), "the panel opens on the current option")
            it.capture("tv-browse-panel-open")
            it.press(Key.Back)
            assertFalse(it.panel("Sort"), "Back closes the panel")
            assertEquals("Sort: New", it.focusedChip(), "Back returns focus to the chip")
            assertEquals(listOf("sort=new"), browse.requests, "closing the panel changes nothing")

            it.press(Key.DirectionRight)
            it.press(Key.DirectionCenter)
            assertEquals("Any", it.focusedRow(), "an unset genre opens on Any")
            it.focusRow("Adventure")
            it.press(Key.DirectionCenter)
            assertEquals(listOf("sort=new", "genre=adventure"), browse.requests, "the genre applies at once")
            assertEquals("Genre: Adventure", it.focusedChip(), "the chip shows the value and keeps focus")

            for (step in 0 until it.triggers().size) it.press(Key.DirectionRight)
            assertEquals("Clear", it.focusedAction(), "Clear ends the chip row")
            it.press(Key.DirectionCenter)
            assertEquals(BrowseQuery("movie", "top", "c1"), browse.query, "Clear drops every facet and keeps the catalog")
            assertFalse(it.has("Clear"), "nothing left to clear")
            assertEquals("Catalog: Popular Movies", it.focusedChip(), "the remote stays on the row after Clear")
        }
    }

    private val library = LibrarySnapshot(Scope("t", "t", "t", 1), 1, defaultLibraryQuery(), posters, complete = true,
        controls = listOf(
            QueryControl("source", "List", ControlKind.SINGLECHOICE, false, listOf(Choice("favorites", "My List"), Choice("trakt", "Trakt watchlist"))),
            QueryControl("sort", "Sort by", ControlKind.SINGLECHOICE, false, listOf(Choice("last_watched", "Last watched"),
                Choice("name_asc", "Name A–Z"), Choice("date_added", "Date added"))),
            QueryControl("type", "Type", ControlKind.SINGLECHOICE, false, listOf(Choice("all", "All"), Choice("movie", "Movies"), Choice("series", "Series"))),
            QueryControl("status", "Status", ControlKind.SINGLECHOICE, false, listOf(Choice("", "Any"), Choice("watching", "Watching"), Choice("completed", "Completed"))),
            QueryControl("genre", "Genre", ControlKind.SINGLECHOICE, false, listOf(Choice("", "Any")) + genre.choices.map { Choice(it.label, it.label) })),
        sources = listOf(LibrarySource("favorites", "My List", Availability.AVAILABLE), LibrarySource("trakt", "Trakt watchlist", Availability.AVAILABLE)),
        availability = Availability.AVAILABLE)

    @Test fun tvMyListFiltersAreTheSameChips() {
        var query by mutableStateOf(defaultLibraryQuery())
        val changes = mutableListOf<LibraryQuery>()
        Page {
            TvLibraryScreen(posters, true, {}, {}, snapshot = library, query = query, changeQuery = { changes += it; query = it })
        }.use {
            assertEquals(listOf("List: My List", "Type: All", "Sort by: Last watched", "Status: Any", "Genre: Any"), it.triggers())
            assertFalse(it.has("Filters") || it.has("Apply"), "no Filters button and no Apply step")
            it.capture("tv-mylist-chips")

            it.focusChip("List: My List")
            it.press(Key.DirectionCenter)
            assertTrue(it.panel("List"))
            assertEquals("My List", it.focusedRow(), "the panel opens on the current list")
            it.press(Key.Back)

            it.press(Key.DirectionRight)
            it.press(Key.DirectionCenter)
            assertTrue(it.panel("Type"))
            assertEquals("All", it.focusedRow(), "the current type opens focused")
            it.capture("tv-mylist-panel-open")
            it.focusRow("Movies")
            it.press(Key.DirectionCenter)
            assertEquals("movie", changes.last().type, "the type applies at once")
            assertFalse(it.panel("Type"), "a single pick closes the panel")
            assertEquals("Type: Movies", it.focusedChip(), "focus returns to the chip")
        }
    }

    @Test fun tvSourcePickerUsesTheSameChips() {
        val sources = PreviewData.filterSources
        val info = sources.associate { it.id to TvSourceInfo(quality = it.qualityLabel()) }
        Page {
            TvSourcesScreen(null, sources, false, null, {}, {}, info = info)
        }.use {
            assertEquals(listOf("Quality: All", "Source: All sources"), it.triggers(), "the picker filters through the same bar")
            assertFalse(it.has("1080p 6"), "the old quality pill row is gone")
            it.capture("tv-source-chips")

            it.focusChip("Quality: All")
            it.press(Key.DirectionCenter)
            assertTrue(it.panel("Quality"))
            assertEquals("All", it.focusedRow(), "the panel opens on All, the current quality")
            it.focusRow("4K")
            it.press(Key.DirectionCenter)
            assertEquals("Quality: 4K", it.focusedChip(), "the quality chip shows the picked value")
            assertEquals(1, it.triggers().count { it.startsWith("Quality:") }, "still one quality chip")
        }
    }

    private class Page(content: @Composable () -> Unit) : AutoCloseable {
        private var focusManager: FocusManager? = null
        private var frame = 0L
        val scene = ImageComposeScene(1920, 1080, Density(1f)) {
            ViptvTheme(television = true) {
                focusManager = LocalFocusManager.current
                Surface(Modifier.fillMaxSize(), color = Palette.background) { content() }
            }
        }.also { s -> repeat(20) { s.render(frame++ * 16_666_667L).close() } }

        fun frames(count: Int = 20) = repeat(count) { scene.render(frame++ * 16_666_667L).close(); Thread.sleep(2) }
        fun nodes() = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
        private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
        fun text(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString(" ") { it.text }
        fun has(label: String) = nodes().any { text(it) == label }
        fun triggers() = nodes().filter { it.config.getOrNull(SemanticsProperties.Role) == Role.DropdownList }
            .sortedBy { it.boundsInRoot.left }.map(::text)
        private fun rows() = nodes().filter { it.config.getOrNull(SemanticsProperties.Role) in listOf(Role.RadioButton, Role.Checkbox) }
            .sortedBy { it.boundsInRoot.top }
        private fun rowLabel(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text).orEmpty().first().text.removeSuffix(" · unavailable")
        fun focusedChip() = nodes().single {
            it.config.getOrNull(SemanticsProperties.Focused) == true && it.config.getOrNull(SemanticsProperties.Role) == Role.DropdownList
        }.let(::text)
        fun focusedAction() = nodes().single {
            it.config.getOrNull(SemanticsProperties.Focused) == true && it.config.getOrNull(SemanticsProperties.Role) == Role.Button
        }.let(::text)
        fun focusedRow() = nodes().single {
            it.config.getOrNull(SemanticsProperties.Focused) == true && it.config.getOrNull(SemanticsProperties.Role) in listOf(Role.RadioButton, Role.Checkbox)
        }.let(::rowLabel)
        fun panel(title: String) = nodes().any { it.config.contains(SemanticsProperties.Heading) && text(it) == title }

        private fun request(node: SemanticsNode) {
            node.config[SemanticsActions.RequestFocus].action!!.invoke(); frames()
        }
        fun focusChip(label: String) = request(nodes().single { it.config.getOrNull(SemanticsProperties.Role) == Role.DropdownList && text(it) == label })
        fun focusRow(label: String) = request(rows().single { rowLabel(it) == label })

        // Desktop maps no arrow key to a focus move, so an unconsumed D-pad press has to drive the focus manager.
        fun press(key: Key) {
            val consumed = scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyDown))
            scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyUp))
            frames()
            if (!consumed && key in listOf(Key.DirectionLeft, Key.DirectionRight, Key.DirectionUp, Key.DirectionDown)) {
                focusManager!!.moveFocus(when (key) {
                    Key.DirectionLeft -> FocusDirection.Left
                    Key.DirectionRight -> FocusDirection.Right
                    Key.DirectionUp -> FocusDirection.Up
                    else -> FocusDirection.Down
                })
                frames()
            }
        }

        fun capture(name: String) { System.getenv("VIPTV_TV_FILTER_CAPTURE_DIR")?.let { path ->
            File(path).mkdirs()
            scene.render(frame++ * 16_666_667L).use { image -> image.encodeToData()!!.use { File(path, "$name.png").writeBytes(it.bytes) } }
        } }
        override fun close() = scene.close()
    }
}