package org.viptv.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.viptv.app.components.SearchableSelect
import org.viptv.app.components.SelectOption
import org.viptv.app.components.filterOptions
import org.viptv.app.components.selectTriggerLabel
import org.viptv.app.components.useSelect
import org.viptv.app.connection.BrowseQuery
import org.viptv.app.connection.DiscoveryControls
import org.viptv.app.connection.FeatureAvailability
import org.viptv.app.features.browse.BrowserBrowseScreen
import org.viptv.app.state.Screen
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.Choice
import org.viptv.engine.wire.ControlKind
import org.viptv.engine.wire.QueryControl
import org.junit.Rule
import kotlin.test.*

/**
 * app#57: the shared searchable, virtualised dropdown and its first caller, the
 * browser Browse filter rail (Genre used to render as an empty segmented bar).
 */
@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class SearchableSelectTest {
    @get:Rule val uiThread = UiThreadRule()
    private val genres = listOf("Action", "Adventure", "Animation", "Comedy", "Crime", "Documentary", "Drama", "Café Society")
        .map { SelectOption(it.lowercase(), it) }

    @Test fun theTriggerReadsTitleAndValueForUnsetSetAndMultipleSelections() {
        assertEquals("Genre: Any", selectTriggerLabel("Genre", genres, emptyList(), "Any"))
        assertEquals("Genre: Comedy", selectTriggerLabel("Genre", genres, listOf("comedy"), "Any"))
        assertEquals("Genre: Comedy, Drama", selectTriggerLabel("Genre", genres, listOf("comedy", "drama"), "Any"))
        assertEquals("Genre: 3 selected", selectTriggerLabel("Genre", genres, listOf("comedy", "drama", "crime"), "Any"))
        assertEquals("Quality: 1080p", selectTriggerLabel("Quality", listOf(SelectOption("1080p", "1080p", "5")), listOf("1080p"), "All"))
        assertEquals("Genre: unknown-id", selectTriggerLabel("Genre", genres, listOf("unknown-id"), "Any"), "an id the provider no longer lists still shows")
    }

    @Test fun searchIsCaseAndAccentInsensitive() {
        assertEquals(listOf("Comedy"), filterOptions(genres, "com").map { it.label })
        assertEquals(listOf("Comedy"), filterOptions(genres, "  COM ").map { it.label })
        assertEquals(listOf("Café Society"), filterOptions(genres, "cafe").map { it.label })
        assertEquals(listOf("Café Society"), filterOptions(genres, "CAFÉ").map { it.label })
        assertEquals(genres, filterOptions(genres, ""))
        assertTrue(filterOptions(genres, "zzz").isEmpty())
    }

    @Test fun theInlineVersusDropdownRuleLivesInOnePlace() {
        assertFalse(useSelect(listOf("Any", "Popular", "New", "Rated").map { SelectOption(it, it) }), "a short Sort set stays segments")
        assertFalse(useSelect(listOf("Any", "5+", "6+", "7+", "8+").map { SelectOption(it, it) }), "five short options stay segments")
        assertTrue(useSelect(listOf("Any", "5+", "6+", "7+", "8+", "9+").map { SelectOption(it, it) }), "more than five options use the dropdown")
        assertTrue(useSelect(listOf("Any", "Recently added", "Most watched").map { SelectOption(it, it) }), "labels too long for segments use the dropdown")
        assertTrue(useSelect(genres))
    }

    @Test fun pickingAFilteredSingleChoiceAppliesItAndClosesThePanel() {
        val picked = mutableListOf<List<String>>()
        val ui = Ui(800, 700) { var value by remember { mutableStateOf(emptyList<String>()) }
            SearchableSelect("Genre", genres, value, { picked += it; value = it }, Modifier.width(240.dp))
        }
        ui.use {
            assertEquals("Genre: Any", it.trigger())
            it.open("Genre: Any")
            it.type("com")
            assertEquals(listOf("Comedy"), it.rows())
            it.click("Comedy")
            assertEquals(listOf(listOf("comedy")), picked)
            assertFalse(it.panelOpen())
            assertEquals("Genre: Comedy", it.trigger())
            it.open("Genre: Comedy")
            assertTrue(it.rows().first() == "Any", "an optional control offers Any first")
            it.click("Any")
            assertEquals(emptyList(), picked.last())
        }
    }

    @Test fun keyboardMovesPicksAndEscapeCloses() {
        val picked = mutableListOf<List<String>>()
        Ui(800, 700) { SearchableSelect("Genre", genres, emptyList(), { picked += it }, Modifier.width(240.dp)) }.use {
            it.open("Genre: Any")
            assertTrue(it.searchFocused(), "the search field takes focus when the panel opens")
            it.key(Key.Escape)
            assertFalse(it.panelOpen(), "Escape closes")
            assertTrue(picked.isEmpty())
            it.open("Genre: Any")
            it.key(Key.DirectionDown); it.key(Key.DirectionDown); it.key(Key.Enter)
            assertEquals(listOf(listOf("adventure")), picked, "Any, Action, Adventure: two steps down picks Adventure")
        }
    }

    @Test fun arrowsSkipUnavailableRowsWhichReadUnavailable() {
        val options = genres.map { if (it.id == "action") it.copy(enabled = false) else it }
        val picked = mutableListOf<List<String>>()
        Ui(800, 700) { SearchableSelect("Genre", options, emptyList(), { picked += it }, Modifier.width(240.dp)) }.use {
            it.open("Genre: Any")
            assertEquals("Action · unavailable", it.row("Action").config[SemanticsProperties.Text].first().text)
            assertTrue(it.row("Action").config.contains(SemanticsProperties.Disabled))
            it.key(Key.DirectionDown); it.key(Key.Enter)
            assertEquals(listOf(listOf("adventure")), picked, "Down from Any skips the unavailable Action")
        }
    }

    @Test fun multipleModeTogglesWithCheckboxesAndRespectsTheCap() {
        var value by mutableStateOf(listOf("comedy"))
        Ui(800, 700) { SearchableSelect("Genre", genres, value, { value = it }, Modifier.width(240.dp), multiple = true, maximumSelections = 2) }.use {
            it.open("Genre: Comedy")
            it.click("Drama")
            assertEquals(listOf("comedy", "drama"), value)
            assertTrue(it.panelOpen(), "a multiple panel stays open while ticking")
            assertEquals("Genre: Comedy, Drama", it.trigger())
            val crime = it.row("Crime")
            assertEquals(ToggleableState.Off, crime.config[SemanticsProperties.ToggleableState])
            assertTrue(crime.config.contains(SemanticsProperties.Disabled), "the provider's cap disables the rest")
            it.click("Comedy")
            assertEquals(listOf("drama"), value)
            it.key(Key.Escape)
            assertFalse(it.panelOpen())
        }
    }

    @Test fun twoHundredChoicesRenderThroughALazyListAndScrollToTheEnd() {
        val many = (1..200).map { SelectOption("g$it", "Genre $it") }
        val picked = mutableListOf<List<String>>()
        Ui(800, 700) { SearchableSelect("Genre", many, emptyList(), { picked += it }, Modifier.width(240.dp)) }.use {
            it.open("Genre: Any")
            val composed = it.rows().size
            assertTrue(composed in 3..40, "only the visible window is composed, not 201 rows: $composed")
            repeat(200) { _ -> it.key(Key.DirectionDown, frames = 2) }
            it.frames(20)
            assertTrue("Genre 200" in it.rows(), "keyboard travel scrolls the last choice into view: ${it.rows()}")
            it.key(Key.Enter)
            assertEquals(listOf(listOf("g200")), picked)
        }
    }

    @Test fun browseRailUsesTheDropdownForGenreAndKeepsSegmentsForASmallSort() {
        val genre = QueryControl("genre", "Genre", ControlKind.SINGLECHOICE, false,
            (listOf("Action", "Comedy", "Drama") + (1..197).map { "Genre $it" }).map { Choice(it.lowercase(), it) })
        val sort = QueryControl("sort", "Sort", ControlKind.SINGLECHOICE, true, listOf(Choice("popular", "Popular"), Choice("new", "New")))
        val controls = DiscoveryControls("stremio", listOf("movie"), listOf(sort, genre),
            discovery = FeatureAvailability("Available"), search = FeatureAvailability("Available"),
            filters = FeatureAvailability("Available"), calendar = FeatureAvailability("Unsupported"))
        var query by mutableStateOf(BrowseQuery("movie"))
        val changed = mutableListOf<Pair<String, String>>()
        Ui(1440, 900) {
            BrowserBrowseScreen(Screen.Movies, emptyList(), controls, query, false, null, null, {},
                { id, value -> changed += id to value; query = if (id == "genre") query.copy(genre = value.ifEmpty { null }) else query },
                { _, _ -> }, {})
        }.use {
            assertEquals("Genre: Any", it.trigger())
            assertTrue(it.clickable("Popular") && it.clickable("New"), "Sort keeps its segmented control")
            assertFalse(it.nodes().any { n -> it.text(n).startsWith("Sort:") }, "Sort has no dropdown trigger")
            it.open("Genre: Any")
            it.type("com")
            assertEquals(listOf("Comedy"), it.rows())
            it.click("Comedy")
            assertEquals(listOf("genre" to "comedy"), changed)
            assertEquals("Genre: Comedy", it.trigger())
        }
    }

    @Test fun browseRailUsesTheMultipleDropdownForAManyChoiceMultiFilter() {
        val genres = QueryControl("genres", "Genres", ControlKind.MULTICHOICE, false,
            (1..40).map { Choice("g$it", "Genre $it") }, maximum_selections = 3)
        val few = QueryControl("audio", "Audio", ControlKind.MULTICHOICE, false, listOf(Choice("en", "EN"), Choice("ja", "JA")))
        val controls = DiscoveryControls("stremio", listOf("movie"), listOf(genres, few),
            discovery = FeatureAvailability("Available"), search = FeatureAvailability("Available"),
            filters = FeatureAvailability("Available"), calendar = FeatureAvailability("Unsupported"))
        val changed = mutableListOf<Pair<String, List<String>>>()
        Ui(1440, 900) {
            BrowserBrowseScreen(Screen.Movies, emptyList(), controls, BrowseQuery("movie"), false, null, null, {},
                { _, _ -> }, { id, values -> changed += id to values }, {})
        }.use {
            assertTrue(it.clickable("EN") && it.clickable("JA"), "a small multi set keeps its chips")
            assertFalse(it.nodes().any { n -> it.text(n).startsWith("+") && it.text(n).endsWith("more") }, "no '+N more' wall of chips")
            it.open("Genres: Any")
            it.click("Genre 7")
            assertEquals(listOf("genres" to listOf("g7")), changed)
        }
    }

    private class Ui(width: Int, height: Int, content: @Composable () -> Unit) : AutoCloseable {
        private var frame = 0L
        val scene = ImageComposeScene(width, height, Density(1f)) {
            ViptvTheme(false) { Surface(Modifier.fillMaxSize(), color = Palette.background) { Box(Modifier.padding(20.dp)) { content() } } }
        }.also { s -> repeat(20) { s.render(frame++ * 16_666_667L).close() } }

        fun frames(count: Int = 20) = repeat(count) { scene.render(frame++ * 16_666_667L).close(); Thread.sleep(2) }
        fun nodes() = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
        private fun flatten(n: SemanticsNode): List<SemanticsNode> = listOf(n) + n.children.flatMap(::flatten)
        fun text(n: SemanticsNode) = n.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString(" ") { it.text }

        fun trigger(): String = nodes().single { it.config.getOrNull(SemanticsProperties.Role) == Role.DropdownList }.let(::text)
        private fun options() = nodes().filter { it.config.getOrNull(SemanticsProperties.Role) in listOf(Role.RadioButton, Role.Checkbox) }
            .sortedBy { it.boundsInRoot.top }
        /** The labels of the composed option rows, top to bottom (each row's first text is its label). */
        private fun label(n: SemanticsNode) = n.config.getOrNull(SemanticsProperties.Text).orEmpty().first().text.removeSuffix(" · unavailable")
        fun rows(): List<String> = options().map(::label)
        fun row(label: String) = options().single { label(it) == label }
        fun panelOpen() = nodes().any { it.config.contains(SemanticsActions.SetText) }
        fun searchFocused() = nodes().single { it.config.contains(SemanticsActions.SetText) }.config.getOrNull(SemanticsProperties.Focused) == true
        fun clickable(label: String) = nodes().any { text(it) == label && it.config.contains(SemanticsActions.OnClick) }

        fun open(label: String) {
            val trigger = nodes().single { it.config.getOrNull(SemanticsProperties.Role) == Role.DropdownList }
            assertEquals(label, text(trigger))
            assertTrue(trigger.config[SemanticsActions.OnClick].action!!.invoke())
            frames(30)
            assertTrue(panelOpen(), "the panel opened")
        }
        fun type(query: String) {
            val field = nodes().single { it.config.contains(SemanticsActions.SetText) }
            assertTrue(field.config[SemanticsActions.SetText].action!!.invoke(AnnotatedString(query)))
            frames(10)
        }
        fun click(label: String) {
            assertTrue(row(label).config[SemanticsActions.OnClick].action!!.invoke())
            frames(30)
        }
        /** 30 frames by default: the popover's exit animation must finish before "closed" is checked. */
        fun key(key: Key, frames: Int = 30) {
            scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyDown)); scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyUp))
            frames(frames)
        }
        override fun close() = scene.close()
    }
}
