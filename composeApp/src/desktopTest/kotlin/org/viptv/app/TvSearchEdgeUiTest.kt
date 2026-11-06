@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.ui.InternalComposeUiApi::class)

package org.viptv.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Density
import org.viptv.app.connection.SearchResultRow
import org.viptv.app.features.search.SEARCH_PLACEHOLDER
import org.viptv.app.features.search.SearchScreen
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.MediaIdentity
import org.junit.Rule
import java.io.File
import kotlin.math.abs
import kotlin.test.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

// AND-KEYBOARD-001: Search is an editable field with a Results action, not an app key grid.
class TvSearchEdgeUiTest {
    @get:Rule val uiThread = UiThreadRule()
    @get:Rule val deterministicImages = DeterministicImageLoaderRule()
    private fun ui(block: suspend CoroutineScope.() -> Unit) = runBlocking(block = block)

    private val screenWidth = 1920

    private val columnStart = 96f

    private val cardWidth = 212f

    private fun cards(count: Int = 14) = (1..count).map { index ->
        Card(MediaIdentity("search-$index", "movie", emptyList()), provider_id = "addon:1",
            title = "Result $index", badges = emptyList())
    }

    private val rows = listOf(SearchResultRow("addon:1", "First provider", "stremio", addon_id = 1, cards = cards()))

    private class Fixture(width: Int, content: @Composable () -> Unit) : AutoCloseable {
        private var focusManager: FocusManager? = null
        private var frame = 0L
        val scene = ImageComposeScene(width, 1080, Density(1f)) {
            ViptvTheme(true) { Surface(Modifier.fillMaxSize(), color = Palette.background) {
                focusManager = LocalFocusManager.current
                content()
            } }
        }
        suspend fun settle(frames: Int = 40) { repeat(frames) { scene.render(frame++ * 16_666_667L).close(); delay(2) } }
        fun nodes(): List<SemanticsNode> = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
        fun focused(): SemanticsNode = nodes().firstOrNull { it.config.getOrNull(SemanticsProperties.Focused) == true }
            ?: fail("no focused control")
        fun field(): SemanticsNode = nodes().firstOrNull { it.config.contains(SemanticsActions.SetText) }
            ?: fail("no editable field")
        fun type(value: String) {
            field().config[SemanticsActions.SetText].action!!.invoke(AnnotatedString(value))
        }
        fun press(key: Key) {
            val consumed = scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyDown))
            if (!consumed) focusManager!!.moveFocus(when (key) {
                Key.DirectionLeft -> FocusDirection.Left
                Key.DirectionRight -> FocusDirection.Right
                Key.DirectionUp -> FocusDirection.Up
                else -> FocusDirection.Down
            })
            scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyUp))
        }
        fun capture(name: String) {
            val dir = System.getenv("VIPTV_SEARCH_CAPTURE_DIR") ?: return
            File(dir).mkdirs()
            scene.render().use { image -> File(dir, "$name.png").writeBytes(requireNotNull(image.encodeToData()).bytes) }
        }
        override fun close() = scene.close()
    }

    private fun searchScreen(term: String = "whisper", query: (String) -> Unit = {}, editQuery: ((String) -> String) -> Unit = {}) =
        Fixture(screenWidth) {
            SearchScreen(term, emptyList(), emptyList(), false, null, true, query, {},
                television = true, editQuery = editQuery, searchRows = rows)
        }

    private fun idleScreen(recents: List<String> = listOf("the end of oak street", "monster", "dune"),
        trending: List<String> = listOf("Project Hail Mary", "Mayday"), query: (String) -> Unit = {},
        clearRecents: () -> Unit = {}) =
        Fixture(screenWidth) {
            SearchScreen("", emptyList(), emptyList(), false, null, false, query, {},
                television = true, editQuery = {}, recentSearches = recents, trendingSearches = trending, clearRecents = clearRecents)
        }

    @Test fun anEmptyQueryDrawsRecentClearAndTrendingUnderTheField() = ui {
        idleScreen().use { screen ->
            screen.settle()
            screen.capture("search-tv-idle")
            val nodes = screen.nodes()
            val recent = nodes.single { titles(it) == "Recent" }
            val clear = nodes.single { it.config.contains(SemanticsActions.OnClick) && titles(it) == "Clear" }
            val first = nodes.single { it.config.contains(SemanticsActions.OnClick) && titles(it) == "the end of oak street" }
            val last = nodes.single { it.config.contains(SemanticsActions.OnClick) && titles(it) == "dune" }
            val trending = nodes.single { titles(it) == "Trending searches" }
            val trendingFirst = nodes.single { it.config.contains(SemanticsActions.OnClick) && titles(it) == "Project Hail Mary" }
            assertNear(columnStart, recent.boundsInRoot.left, "the Recent heading rests on the page gutter")
            assertTrue(clear.boundsInRoot.left >= recent.boundsInRoot.right, "Clear ends the Recent heading row")
            assertTrue(first.boundsInRoot.top >= recent.boundsInRoot.bottom, "the recent terms sit under their heading")
            assertTrue(last.boundsInRoot.top > first.boundsInRoot.top, "the recent terms keep their order")
            assertTrue(trending.boundsInRoot.top >= last.boundsInRoot.bottom, "Trending searches follows the recent terms")
            assertTrue(trendingFirst.boundsInRoot.top >= trending.boundsInRoot.bottom, "the trending terms sit under their heading")
        }
    }

    @Test fun dPadWalksTheIdleListAndLeftReturnsToTheField() = ui {
        idleScreen().use { screen ->
            screen.settle()
            screen.press(Key.DirectionDown); screen.settle()
            assertEquals("Clear", titles(screen.focused()), "down from the field reaches the Recent Clear action")
            screen.press(Key.DirectionDown); screen.settle()
            assertEquals("the end of oak street", titles(screen.focused()), "down walks into the recent terms")
            screen.press(Key.DirectionDown); screen.settle()
            assertEquals("monster", titles(screen.focused()))
            screen.capture("search-tv-idle-focused")
            screen.press(Key.DirectionUp); screen.settle()
            assertEquals("the end of oak street", titles(screen.focused()), "up walks back")
            repeat(3) { screen.press(Key.DirectionDown); screen.settle() }
            assertEquals("Project Hail Mary", titles(screen.focused()), "the walk continues past the Trending heading")
            screen.press(Key.DirectionDown); screen.settle()
            assertEquals("Mayday", titles(screen.focused()))
            screen.press(Key.DirectionLeft); screen.settle()
            assertTrue(screen.focused().config.contains(SemanticsActions.SetText),
                "left from a term returns to the field, was ${titles(screen.focused())}")
            assertEquals("", screen.field().config.getOrNull(SemanticsProperties.EditableText)?.text.orEmpty(),
                "the field keeps its empty query")
        }
    }

    @Test fun selectOnAnIdleTermRunsIt() = ui {
        val ran = mutableListOf<String>()
        idleScreen(query = { ran += it }).use { screen ->
            screen.settle()
            val term = screen.nodes().single { it.config.contains(SemanticsActions.OnClick) && titles(it) == "Mayday" }
            assertTrue(term.config[SemanticsActions.RequestFocus].action!!.invoke(), "the D-pad reaches the term")
            screen.settle()
            assertEquals("Mayday", titles(screen.focused()))
            assertTrue(term.config[SemanticsActions.OnClick].action!!.invoke(), "Select runs the term")
            assertEquals(listOf("Mayday"), ran)
        }
    }

    @Test fun clearEmptiesTheRecentTermsOnly() = ui {
        var recents by mutableStateOf(listOf("the end of oak street", "dune"))
        var cleared = 0
        Fixture(screenWidth) {
            SearchScreen("", emptyList(), emptyList(), false, null, false, {}, {}, television = true, editQuery = {},
                recentSearches = recents, trendingSearches = listOf("Project Hail Mary"),
                clearRecents = { cleared++; recents = emptyList() })
        }.use { screen ->
            screen.settle()
            val clear = screen.nodes().single { it.config.contains(SemanticsActions.OnClick) && titles(it) == "Clear" }
            assertTrue(clear.config[SemanticsActions.OnClick].action!!.invoke())
            screen.settle()
            assertEquals(1, cleared)
            val texts = screen.nodes().flatMap(::texts)
            assertFalse(texts.any { it == "the end of oak street" || it == "Recent" || it == "Clear" }, "Clear drops the recent terms: $texts")
            assertTrue(texts.any { it == "Trending searches" }, "Trending stays: $texts")
            assertTrue(texts.any { it == "Project Hail Mary" })
            assertTrue(screen.focused().config.contains(SemanticsActions.SetText),
                "focus returns to the field, was ${titles(screen.focused())}")
        }
    }

    @Test fun anEmptyRecentAndTrendingListLeavesThePagesOwnResultsArea() = ui {
        idleScreen(recents = emptyList(), trending = emptyList()).use { screen ->
            screen.settle()
            val texts = screen.nodes().flatMap(::texts)
            assertFalse(texts.any { it == "Recent" || it == "Trending searches" }, "no empty idle headings: $texts")
            assertTrue(texts.any { it == SEARCH_PLACEHOLDER }, "the page keeps its own empty state")
        }
    }

    @Test fun searchEntryIsAnEditableFieldWithTheDesignPlaceholderAndAResultsAction() = ui {
        searchScreen(term = "").use { screen ->
            screen.settle()
            val field = screen.field()
            assertTrue(field.config.getOrNull(SemanticsProperties.Focused) == true, "the field takes focus when Search opens")
            assertTrue(screen.nodes().flatMap(::texts).any { it == SEARCH_PLACEHOLDER },
                "the field shows copy.md's placeholder, was ${screen.nodes().flatMap(::texts)}")
            val results = screen.nodes().singleOrNull { it.config.contains(SemanticsActions.OnClick) && titles(it) == "Results" }
                ?: fail("no Results action: ${screen.nodes().map(::titles)}")
            assertTrue(results.boundsInRoot.left > field.boundsInRoot.right, "Results sits to the right of the field")
            assertFalse(screen.nodes().any { isKeyboardKey(it) }, "no app-rendered character grid remains")
        }
    }

    @Test fun physicalInputTypesIntoTheQuery() = ui {
        var term by mutableStateOf("")
        var typed = ""
        Fixture(screenWidth) {
            SearchScreen(term, emptyList(), emptyList(), false, null, true, { term = it }, {},
                television = true, editQuery = { transform -> term = transform(term); typed = term }, searchRows = rows)
        }.use { screen ->
            screen.settle()
            screen.type("dune"); screen.settle()
            assertEquals("dune", typed, "characters reach the query")
            screen.settle()
            assertEquals("dune", screen.field().config.getOrNull(SemanticsProperties.EditableText)?.text.orEmpty(),
                "the field shows what was typed")
        }
    }

    @Test fun theResultsActionDismissesInputAndFocusesTheFirstCard() = ui {
        searchScreen().use { screen ->
            screen.settle()
            screen.type("dune"); screen.settle()
            val results = screen.nodes().first { it.config.contains(SemanticsActions.OnClick) && titles(it) == "Results" }
            assertTrue(results.config[SemanticsActions.RequestFocus].action!!.invoke(), "the D-pad reaches Results")
            screen.settle()
            assertEquals("Results", titles(screen.focused()), "focus is on Results")
            assertTrue(screen.focused().config[SemanticsActions.OnClick].action!!.invoke(), "Results runs")
            screen.settle()
            assertTrue("Result 1" in titles(screen.focused()), "Results enters the first card, was ${titles(screen.focused())}")
        }
    }

    @Test fun searchOnTheFieldEntersTheFirstResult() = ui {
        searchScreen().use { screen ->
            screen.settle()
            screen.press(Key.Enter); screen.settle()
            assertTrue("Result 1" in titles(screen.focused()), "the native Search action enters the first card, was ${titles(screen.focused())}")
        }
    }

    @Test fun resultShelvesFillThePageBelowTheField() = ui {
        searchScreen().use { screen ->
            screen.settle()
            val nodes = screen.nodes()
            val first = card(nodes, "Result 1") ?: fail("no first result card")
            val strip = nodes.filter { it.config.contains(SemanticsProperties.HorizontalScrollAxisRange) }
                .single { strip -> flatten(strip).any { it.id == first.id } }
            assertNear(columnStart, first.boundsInRoot.left, "the first card rests on the page gutter")
            assertNear(columnStart, strip.boundsInRoot.left, "the shelf starts on the page gutter")
            assertNear(screenWidth.toFloat(), strip.boundsInRoot.right, "the shelf runs to the right screen edge")
            val field = screen.field()
            assertTrue(strip.boundsInRoot.top >= field.boundsInRoot.bottom - 1f, "the shelves sit below the field")
        }
    }

    @Test fun dPadKeepsEveryFocusedCardOnScreenAndLeftReturnsToTheField() = ui {
        searchScreen().use { screen ->
            screen.settle()
            screen.capture("search-tv-rest")
            screen.field().config[SemanticsActions.RequestFocus].action?.invoke()
            screen.settle()
            focusCard(screen, "Result 1"); screen.settle()
            assertFocusedCardFullyOnScreen(screen, "Result 1")
            for (index in 2..10) {
                screen.press(Key.DirectionRight); screen.settle()
                assertTrue("Result $index" in titles(screen.focused()),
                    "right through the row reaches Result $index, was ${titles(screen.focused())}")
                assertFocusedCardFullyOnScreen(screen, "Result $index")
            }
            screen.capture("search-tv-row-scrolled")
            repeat(9) { screen.press(Key.DirectionLeft); screen.settle() }
            assertTrue("Result 1" in titles(screen.focused()), "left returns to the first card")
            screen.press(Key.DirectionLeft); screen.settle()
            val field = screen.focused()
            assertTrue(field.config.contains(SemanticsActions.SetText), "left from the first card returns to the field, was ${titles(field)}")
            assertEquals("whisper", field.config.getOrNull(SemanticsProperties.EditableText)?.text.orEmpty(),
                "the field keeps the current query")
            screen.capture("search-tv-field-left")
        }
    }

    @Test fun aFailedProviderShelfOffersTheSameTryAgainAsAFailedPage() = ui {
        var retried = 0
        val rows = listOf(
            SearchResultRow("addon:1", "First provider", "stremio", addon_id = 1, cards = cards(2)),
            SearchResultRow("simkl:tv", "TV Shows", "simkl", state = "Failed"))
        Fixture(screenWidth) {
            SearchScreen("whisper", emptyList(), emptyList(), false, null, true, {}, {},
                television = true, editQuery = {}, searchRows = rows, retry = { retried++ })
        }.use { screen ->
            screen.settle()
            screen.capture("search-tv-provider-retry")
            val nodes = screen.nodes()
            val line = nodes.single { "could not respond" in titles(it) }
            val action = nodes.single { it.config.contains(SemanticsActions.OnClick) && titles(it) == "Try again" }
            assertTrue(action.boundsInRoot.top >= line.boundsInRoot.bottom - 1f, "the action sits below the failure line")
            assertTrue(action.config[SemanticsActions.RequestFocus].action!!.invoke(), "the D-pad can reach Try again")
            screen.settle()
            assertTrue(titles(screen.focused()) == "Try again", "focus is on the retry action, was ${titles(screen.focused())}")
            assertEquals(0, retried)
            assertTrue(action.config[SemanticsActions.OnClick].action!!.invoke())
            assertEquals(1, retried)
        }
    }

    @Test fun aQueryWhereEveryProviderFailedShowsOneFailureLineInsteadOfProviderBlocks() = ui {
        val rows = listOf("Movies Search", "Series Search", "People Search Movies", "People Search Series")
            .map { SearchResultRow(it.lowercase().replace(' ', '-'), it, "stremio", state = "Failed") }
        Fixture(screenWidth) {
            SearchScreen("zzzqx", emptyList(), emptyList(), false, null, true, {}, {},
                television = true, editQuery = {}, searchRows = rows)
        }.use { screen ->
            screen.settle()
            val texts = screen.nodes().flatMap(::texts)
            assertEquals(1, texts.count { it == "This search provider could not respond. Try again." })
            assertFalse(texts.any { it == "Movies Search" || it == "People Search Series" })
            assertFalse(texts.any { it == "No matching titles" })
        }
    }

    @Test fun aResultWithNoCardsInAnyProviderShowsOneEmptyState() = ui {
        Fixture(screenWidth) {
            SearchScreen("whisper", emptyList(), emptyList(), false, null, true, {}, {},
                television = true, editQuery = {},
                searchRows = listOf(SearchResultRow("addon:1", "First provider", "stremio", addon_id = 1)))
        }.use { screen ->
            screen.settle()
            val texts = screen.nodes().flatMap(::texts)
            assertEquals(1, texts.count { it == "No matching titles" })
            assertFalse(texts.any { it.contains("No matches") })
            assertFalse(texts.any { it == "First provider" })
        }
    }

    @Test fun anEmptySearchShowsTheDesignEmptyStateCopy() = ui {
        Fixture(screenWidth) {
            SearchScreen("whisper", emptyList(), emptyList(), false, null, true, {}, {},
                television = true, editQuery = {})
        }.use { screen ->
            screen.settle()
            val texts = screen.nodes().flatMap(::texts)
            assertEquals(1, texts.count { it == "No matching titles" })
            assertFalse(texts.any { it.contains("No matches") })
        }
    }

    @Test fun aBlankTermShowsItsPlaceholderAndNoEmptyStateCopy() = ui {
        Fixture(screenWidth) {
            SearchScreen("", emptyList(), emptyList(), false, null, true, {}, {},
                television = true, editQuery = {})
        }.use { screen ->
            screen.settle()
            val texts = screen.nodes().flatMap(::texts)
            assertFalse(texts.any { it == "No matching titles" })
            assertTrue(texts.any { it == SEARCH_PLACEHOLDER }, "the empty field shows the design placeholder")
        }
    }

    @Test fun previewResultsRunToTheRightScreenEdge() = ui {
        Fixture(screenWidth) {
            org.viptv.app.preview.UiPreview(television = true, screen = org.viptv.app.state.Screen.Search)
        }.use { screen ->
            screen.settle(150)
            screen.capture("search-preview-rest")
            val row = screen.nodes().filter { it.config.contains(SemanticsProperties.HorizontalScrollAxisRange) }
                .maxByOrNull { it.boundsInRoot.right }
                ?: fail("the preview has no result row")
            assertNear(columnStart, row.boundsInRoot.left, "the preview shelf starts on the page gutter")
            assertTrue(row.boundsInRoot.right >= screenWidth - columnStart, "the preview shelf runs to the page's right edge, was ${row.boundsInRoot}")
        }
    }

    @Test fun aFailedSearchOffersATryAgainActionRightAfterTheErrorLine() = ui {
        var retried = 0
        Fixture(screenWidth) {
            SearchScreen("whisper", emptyList(), emptyList(), false,
                "Your server took too long to answer this search. Try again.", true, {}, {},
                television = true, editQuery = {}, searchRows = rows, retry = { retried++ })
        }.use { screen ->
            screen.settle()
            screen.capture("search-tv-retry")
            val nodes = screen.nodes()
            val error = nodes.single { "took too long" in titles(it) }
            val action = nodes.single { it.config.contains(SemanticsActions.OnClick) && titles(it) == "Try again" }
            assertTrue(action.boundsInRoot.top >= error.boundsInRoot.bottom - 1f, "the action sits below the error line")
            assertTrue(action.config[SemanticsActions.RequestFocus].action!!.invoke(), "the D-pad can reach Try again")
            screen.settle()
            assertTrue(titles(screen.focused()) == "Try again", "focus is on the retry action, was ${titles(screen.focused())}")
            assertEquals(0, retried)
            assertTrue(action.config[SemanticsActions.OnClick].action!!.invoke())
            assertEquals(1, retried)
            screen.press(Key.DirectionDown); screen.settle()
            assertTrue("Result 1" in titles(screen.focused()), "down from Try again enters the results, was ${titles(screen.focused())}")
        }
    }

    private fun assertFocusedCardFullyOnScreen(screen: Fixture, name: String) {
        val focused = screen.focused()
        assertTrue(name in titles(focused), "focus is on $name, was ${titles(focused)}")
        val bounds = focused.boundsInRoot
        assertNear(cardWidth, bounds.width, "$name keeps its full width")
        assertTrue(bounds.left >= columnStart - 1f, "$name stays right of the page gutter, was $bounds")
        assertTrue(bounds.right <= screenWidth + 1f, "$name never runs under the screen edge, was $bounds")
    }

    private fun isKeyboardKey(node: SemanticsNode) = texts(node).any { it.length == 1 } ||
        description(node).contains("Space") || description(node).contains("Delete")

    private fun card(nodes: List<SemanticsNode>, title: String) = nodes.firstOrNull {
        it.config.contains(SemanticsActions.OnClick) && title in titles(it)
    }

    private fun focusCard(screen: Fixture, title: String) {
        val node = card(screen.nodes(), title) ?: fail("no $title card")
        assertTrue(node.config[SemanticsActions.RequestFocus].action!!.invoke(), "$title accepts focus")
    }

    private fun assertNear(expected: Float, actual: Float, message: String) =
        assertTrue(abs(expected - actual) <= 1f, "$message: expected $expected, was $actual")

    private fun texts(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }
    private fun titles(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString(" ") { it.text }
    private fun description(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().joinToString(" ")
}

private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)