package org.viptv.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Density
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.InternalComposeUiApi
import org.viptv.app.connection.BrowseQuery
import org.viptv.app.connection.DiscoveryCatalog
import org.viptv.app.connection.DiscoveryControls
import org.viptv.app.connection.FeatureAvailability
import org.viptv.app.connection.defaultLibraryQuery
import org.viptv.app.connection.withSelections
import org.viptv.app.features.browse.BrowseScreen
import org.viptv.app.features.browse.BrowserBrowseScreen
import org.viptv.app.features.library.LibraryScreen
import org.viptv.app.preview.PreviewData
import org.viptv.app.state.Screen
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.*
import org.junit.Rule
import java.io.File
import kotlin.test.*

/**
 * app#117: every filter on Movies (phone, tablet, web) and My List is a dropdown chip in one
 * bar, picking applies at once, and "Clear" resets everything. Set
 * `VIPTV_FILTER_CAPTURE_DIR` to save the closed and open renders used in the PR.
 */
@OptIn(InternalComposeUiApi::class)
class FilterBarPagesTest {
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

    /** A fake Browse controller: every change lands in [query] at once, as the app's does. */
    private inner class Browse {
        var query by mutableStateOf(BrowseQuery("movie", "top", "c1"))
        val requests = mutableListOf<String>()
        val changed: (String, String) -> Unit = { id, value -> requests += "$id=$value"; query = query.withSelections(id, listOf(value).filter(String::isNotBlank)) }
        val multiple: (String, List<String>) -> Unit = { id, values -> requests += "$id=${values.joinToString(",")}"; query = query.withSelections(id, values) }
        val apply: (BrowseQuery) -> Unit = { requests += "query"; query = it }
    }

    @Test fun phoneMoviesIsOneChipBarWithSheetsAndNoFiltersToggle() {
        val browse = Browse()
        Page(390, 844, density = 1f, pointer = false) {
            BrowseScreen(Screen.Movies, null, false, {}, posters, controls, browse.query, filterChanged = browse.changed,
                multipleChanged = browse.multiple, applyQuery = browse.apply)
        }.use {
            // The row scrolls: only the chips on screen are composed.
            assertEquals(listOf("Catalog: Popular", "Sort: Required", "Genre: Any"), it.triggers().take(3))
            assertFalse(it.has("Filters"), "no Filters toggle")
            assertFalse(it.has("Apply"), "no Apply button")
            it.capture("phone-movies-closed")
            it.open("Genre: Any")
            assertTrue(it.sheet("Genre"), "a phone opens the bottom sheet")
            it.capture("phone-movies-open")
            it.pick("Comedy")
            assertEquals(listOf("genre=comedy"), browse.requests, "picking applies at once")
            assertFalse(it.sheet("Genre"), "a single pick closes the sheet")
            assertTrue("Genre: Comedy" in it.triggers())
        }
    }

    @Test fun tabletMoviesUsesThePopoverAppliesEachTickAndClears() {
        val browse = Browse()
        Page(1024, 768, pointer = false) {
            BrowseScreen(Screen.Movies, null, false, {}, posters, controls, browse.query, filterChanged = browse.changed,
                multipleChanged = browse.multiple, applyQuery = browse.apply)
        }.use {
            assertFalse(it.has("Clear"), "nothing to clear yet")
            it.capture("tablet-movies-closed")
            it.open("Genre: Any")
            assertFalse(it.sheet("Genre"), "a tablet anchors the popover")
            it.capture("tablet-movies-open")
            it.pick("Drama")
            it.open("Languages: Any")
            it.pick("English"); it.pick("Japanese")
            assertEquals(listOf("genre=drama", "language=english", "language=english,japanese"), browse.requests, "each pick applies")
            assertTrue(it.disabled("Korean"), "maximum_selections caps the list")
            it.key(Key.Escape)
            assertTrue("Languages: English, Japanese" in it.triggers())
            it.press("Clear")
            assertEquals(BrowseQuery("movie", "top", "c1"), browse.query, "Clear drops every facet and keeps the catalog")
            assertFalse(it.has("Clear"))
        }
    }

    @Test fun webMoviesHasTheSameBarInsteadOfARail() {
        val browse = Browse()
        Page(1440, 900, pointer = true) {
            BrowserBrowseScreen(Screen.Movies, posters, controls, browse.query, false, null, null, {}, browse.changed, browse.multiple, browse.apply)
        }.use {
            assertEquals(listOf("Catalog: Popular", "Sort: Required", "Genre: Any", "Languages: Any", "Search: Any"), it.triggers())
            assertFalse(it.has("Filters") || it.has("Clear all") || it.has("New"), "no rail, segments or applied-pill row")
            it.capture("web-movies-closed")
            it.open("Sort: Required")
            assertEquals(listOf("Popular", "New", "Top rated"), it.options(), "a three-option sort is the same chip, without a search field")
            it.pick("New")
            assertEquals(listOf("sort=new"), browse.requests)
            it.open("Search: Any")
            it.type("Naruto")
            assertEquals(listOf("sort=new"), browse.requests, "typing alone never applies")
            it.key(Key.Enter)
            assertEquals(listOf("sort=new", "search=Naruto"), browse.requests, "Enter applies the text")
            it.press("Free only")
            assertEquals("free=true", browse.requests.last(), "a toggle chip applies at once")
            it.open("Genre: Any")
            it.capture("web-movies-open")
            it.type("dra")
            it.pick("Drama")
            it.open("Catalog: Popular")
            assertEquals(listOf("Popular", "Featured"), it.options())
            it.pick("Featured")
            assertEquals(BrowseQuery("movie", "featured", "c2"), browse.query, "a catalog pick starts a fresh query")
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

    @Test fun myListFiltersAreTheSameChipsOnEveryLayout() {
        for ((name, size) in listOf("phone" to Triple(390, 844, 1f), "tablet" to Triple(1024, 768, 1f), "web" to Triple(1440, 900, 1f))) {
            var query by mutableStateOf(defaultLibraryQuery())
            val changes = mutableListOf<LibraryQuery>()
            Page(size.first, size.second, size.third, pointer = name == "web") {
                LibraryScreen(posters, true, false, {}, {}, {}, library, query, { changes += it; query = it })
            }.use {
                val all = listOf("List: My List", "Type: All", "Sort by: Last watched", "Status: Any", "Genre: Any")
                // The phone row scrolls: only the chips on screen are composed.
                assertEquals(if (name == "phone") all.take(2) else all, it.triggers().let { t -> if (name == "phone") t.take(2) else t }, name)
                assertFalse(it.has("Filters") || it.has("Apply"), "$name: no Filters button or Apply")
                it.capture("$name-mylist-closed")
                it.open("Type: All")
                assertEquals(name == "phone", it.sheet("Type"), "$name: phone sheet, otherwise popover")
                it.capture("$name-mylist-open")
                it.pick("Movies")
                assertEquals("movie", changes.last().type, "$name: the type applies at once")
                assertTrue("Type: Movies" in it.triggers())
                if (name != "phone") {
                    it.open("Status: Any")
                    it.pick("Watching")
                    assertEquals("watching", query.status)
                    assertEquals("movie", query.type, "$name: one change keeps the others")
                    it.press("Clear")
                    assertEquals(defaultLibraryQuery(), query, "$name: Clear restores the default query")
                }
                it.open("List: My List")
                it.pick("Trakt watchlist")
                assertEquals("trakt", query.source)
            }
        }
    }

    private class Page(width: Int, height: Int, density: Float = 1f, pointer: Boolean, content: @Composable () -> Unit) : AutoCloseable {
        private var frame = 0L
        val scene = ImageComposeScene(width, height, Density(density)) {
            ViptvTheme(false, pointer = pointer) { Surface(Modifier.fillMaxSize(), color = Palette.background) { content() } }
        }.also { s -> repeat(30) { s.render(frame++ * 16_666_667L).close() } }

        fun frames(count: Int = 30) = repeat(count) { scene.render(frame++ * 16_666_667L).close(); Thread.sleep(2) }
        fun nodes() = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
        private fun flatten(n: SemanticsNode): List<SemanticsNode> = listOf(n) + n.children.flatMap(::flatten)
        fun text(n: SemanticsNode) = n.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString(" ") { it.text }
        fun has(label: String) = nodes().any { text(it) == label }
        fun triggers() = nodes().filter { it.config.getOrNull(SemanticsProperties.Role) == Role.DropdownList }
            .sortedBy { it.boundsInRoot.left }.map(::text)
        private fun rows() = nodes().filter { it.config.getOrNull(SemanticsProperties.Role) in listOf(Role.RadioButton, Role.Checkbox) }
            .sortedBy { it.boundsInRoot.top }
        private fun label(n: SemanticsNode) = n.config.getOrNull(SemanticsProperties.Text).orEmpty().first().text.removeSuffix(" · unavailable")
        fun options() = rows().map(::label)
        fun disabled(label: String) = rows().single { label(it) == label }.config.contains(SemanticsProperties.Disabled)
        /** The bottom sheet is titled with the filter's name; the popover has no title. */
        fun sheet(title: String) = nodes().any { it.config.contains(SemanticsProperties.Heading) && text(it) == title }

        fun open(label: String) {
            val trigger = nodes().single { it.config.getOrNull(SemanticsProperties.Role) == Role.DropdownList && text(it) == label }
            assertTrue(trigger.config[SemanticsActions.OnClick].action!!.invoke())
            frames()
            assertTrue(rows().isNotEmpty() || nodes().any { it.config.contains(SemanticsActions.SetText) }, "$label opened")
        }
        fun pick(label: String) {
            assertTrue(rows().single { label(it) == label }.config[SemanticsActions.OnClick].action!!.invoke())
            frames()
        }
        fun press(label: String) {
            assertTrue(nodes().first { text(it) == label && it.config.contains(SemanticsActions.OnClick) }.config[SemanticsActions.OnClick].action!!.invoke())
            frames()
        }
        fun type(query: String) {
            val field = nodes().single { it.config.contains(SemanticsActions.SetText) }
            assertTrue(field.config[SemanticsActions.SetText].action!!.invoke(AnnotatedString(query)))
            frames(10)
        }
        fun key(key: Key) {
            scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyDown)); scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyUp))
            frames()
        }
        fun capture(name: String) { System.getenv("VIPTV_FILTER_CAPTURE_DIR")?.let { path ->
            File(path).mkdirs(); scene.render(frame++ * 16_666_667L).use { image -> image.encodeToData()!!.use { File(path, "$name.png").writeBytes(it.bytes) } }
        } }
        override fun close() = scene.close()
    }
}
