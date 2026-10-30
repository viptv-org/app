package org.viptv.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.unit.Density
import org.junit.Rule
import org.viptv.app.ContinueWatchingHost.Companion.card
import org.viptv.app.connection.HistoryDisplay
import org.viptv.app.connection.SearchResultRow
import org.viptv.app.features.browse.BrowseScreen
import org.viptv.app.features.continuewatching.ContinueWatchingPage
import org.viptv.app.features.continuewatching.ContinueWatchingScreen
import org.viptv.app.features.continuewatching.TvContinueWatchingScreen
import org.viptv.app.features.history.HistoryScreen
import org.viptv.app.features.history.TvHistoryScreen
import org.viptv.app.features.library.LibraryScreen
import org.viptv.app.features.search.SearchScreen
import org.viptv.app.state.Screen
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.HistoryEntry
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class PageRestoreUiTest {
    @get:Rule val uiThread = UiThreadRule()

    private val shows = (1..30).map(::card)
    private var frame = 0L

    @Test fun continueWatchingComesBackToTheOpenedCard() {
        val cards = mutableStateOf(shows)
        val visiting = mutableStateOf(true)
        var opened: Card? = null
        val scene = page(1280, 900, visiting) {
            ContinueWatchingScreen(ContinueWatchingPage(cards.value, loaded = true), play = { opened = it }, open = { opened = it },
                more = null, retry = {}, back = {}, home = {})
        }
        try {
            frames(scene)
            scrollDown(scene, 1600f)
            val title = openCard(scene, 900, { opened })
            visiting.value = false; frames(scene)
            cards.value = shows.reversed()
            visiting.value = true; frames(scene)
            assertTrue(onScreen(scene, title), "$title is back on screen")
        } finally { scene.close() }
    }

    @Test fun myListComesBackToTheOpenedCard() {
        for ((width, height) in listOf(393 to 852, 1440 to 900)) {
            val cards = mutableStateOf(shows)
            val visiting = mutableStateOf(true)
            var opened: Card? = null
            val scene = page(width, height, visiting) {
                LibraryScreen(cards.value, available = true, television = false, open = { opened = it }, history = {}, home = {})
            }
            try {
                frames(scene)
                scrollDown(scene, 1200f)
                val title = openCard(scene, height, { opened })
                visiting.value = false; frames(scene)
                cards.value = shows.reversed()
                visiting.value = true; frames(scene)
                assertTrue(onScreen(scene, title), "$width: $title is back on screen")
            } finally { scene.close() }
        }
    }

    @Test fun browseComesBackToTheOpenedCard() {
        val cards = mutableStateOf(shows)
        val visiting = mutableStateOf(true)
        var opened: Card? = null
        val scene = page(1440, 900, visiting) {
            BrowseScreen(Screen.Discover, null, false, open = { opened = it }, loadedCards = cards.value)
        }
        try {
            frames(scene)
            scrollDown(scene, 1400f)
            val title = openCard(scene, 900, { opened })
            visiting.value = false; frames(scene)
            cards.value = shows.reversed()
            visiting.value = true; frames(scene)
            assertTrue(onScreen(scene, title), "$title is back on screen")
        } finally { scene.close() }
    }

    @Test fun searchResultsComeBackToTheOpenedCard() {
        val rows = mutableStateOf(listOf(SearchResultRow("results", "Search results", "addon:4", cards = shows)))
        val visiting = mutableStateOf(true)
        var opened: Card? = null
        val scene = page(1280, 900, visiting) {
            SearchScreen("show", shows, emptyList(), busy = false, error = null, desktop = true,
                query = {}, open = { opened = it }, editQuery = { it }, searchRows = rows.value)
        }
        try {
            frames(scene)
            scrollRight(scene, 2400f)
            val title = openCard(scene, 900, { opened }, rightmost = true)
            visiting.value = false; frames(scene)
            rows.value = listOf(SearchResultRow("results", "Search results", "addon:4", cards = shows.reversed()))
            visiting.value = true; frames(scene)
            assertTrue(onScreen(scene, title), "$title is back on screen")
        } finally { scene.close() }
    }

    @Test fun historyComesBackToTheOpenedRow() {
        val entries = mutableStateOf((1..24).map { HistoryDisplay(HistoryEntry(card(it), it.toLong()), "Today") })
        val visiting = mutableStateOf(true)
        var opened: Card? = null
        val scene = page(1280, 900, visiting) {
            HistoryScreen(entries.value, busy = false, error = null, open = { opened = it }, clear = {})
        }
        try {
            frames(scene)
            scrollDown(scene, 1600f)
            val title = openCard(scene, 900, { opened })
            visiting.value = false; frames(scene)
            // A newer play pushes the whole list down by a row.
            entries.value = listOf(HistoryDisplay(HistoryEntry(card(99), 99L), "Today")) + entries.value
            visiting.value = true; frames(scene)
            assertTrue(onScreen(scene, title), "$title is back on screen")
        } finally { scene.close() }
    }

    @Test fun tvContinueWatchingComesBackToTheFocusedCard() {
        val cards = mutableStateOf(shows)
        val visiting = mutableStateOf(true)
        var focusManager: FocusManager? = null
        var opened: Card? = null
        val scene = page(1920, 1080, visiting, television = true) {
            focusManager = LocalFocusManager.current
            TvContinueWatchingScreen(ContinueWatchingPage(cards.value, loaded = true), play = { opened = it }, open = { opened = it },
                more = null, retry = {}, home = {})
        }
        try {
            frames(scene)
            focusOn(scene, "Show 01")
            repeat(2) { move(scene, Key.DirectionRight, FocusDirection.Right, focusManager) }
            val title = requireNotNull(focusedTitle(scene)) { "the remote is on a card" }
            ok(scene)
            assertNotNull(opened, "the remote opens the focused card")
            visiting.value = false; frames(scene)
            cards.value = shows.reversed()
            visiting.value = true; frames(scene)
            assertEquals(title, focusedTitle(scene), "$title keeps the focus on the way back")
        } finally { scene.close() }
    }

    @Test fun tvMyListComesBackToTheFocusedCard() {
        val cards = mutableStateOf(shows)
        val visiting = mutableStateOf(true)
        var focusManager: FocusManager? = null
        var opened: Card? = null
        val scene = page(1920, 1080, visiting, television = true) {
            focusManager = LocalFocusManager.current
            LibraryScreen(cards.value, available = true, television = true, open = { opened = it }, history = {}, home = {})
        }
        try {
            frames(scene)
            focusOn(scene, "Show 01")
            repeat(3) { move(scene, Key.DirectionRight, FocusDirection.Right, focusManager) }
            val title = requireNotNull(focusedTitle(scene)) { "the remote is on a card" }
            ok(scene)
            assertNotNull(opened, "the remote opens the focused card")
            visiting.value = false; frames(scene)
            cards.value = shows.reversed()
            visiting.value = true; frames(scene)
            assertEquals(title, focusedTitle(scene), "$title keeps the focus on the way back")
        } finally { scene.close() }
    }

    @Test fun tvBrowseComesBackToTheFocusedCard() {
        val cards = mutableStateOf(shows)
        val visiting = mutableStateOf(true)
        var focusManager: FocusManager? = null
        var opened: Card? = null
        val scene = page(1920, 1080, visiting, television = true) {
            focusManager = LocalFocusManager.current
            BrowseScreen(Screen.Discover, null, true, open = { opened = it }, loadedCards = cards.value)
        }
        try {
            frames(scene)
            focusOn(scene, "Show 01")
            repeat(9) { move(scene, Key.DirectionRight, FocusDirection.Right, focusManager) }
            move(scene, Key.DirectionDown, FocusDirection.Down, focusManager)
            val title = requireNotNull(focusedTitle(scene)) { "the remote is on a card" }
            ok(scene)
            assertNotNull(opened, "the remote opens the focused card")
            visiting.value = false; frames(scene)
            cards.value = shows.reversed()
            visiting.value = true; frames(scene)
            assertEquals(title, focusedTitle(scene), "$title keeps the focus on the way back")
        } finally { scene.close() }
    }

    @Test fun tvHistoryComesBackToTheFocusedRow() {
        val entries = mutableStateOf((1..24).map { HistoryDisplay(HistoryEntry(card(it), it.toLong()), "Today") })
        val visiting = mutableStateOf(true)
        var focusManager: FocusManager? = null
        var opened: Card? = null
        val scene = page(1920, 1080, visiting, television = true) {
            focusManager = LocalFocusManager.current
            TvHistoryScreen(entries.value, busy = false, error = null, open = { opened = it }, clear = {})
        }
        try {
            frames(scene)
            focusOn(scene, "Show 01")
            repeat(2) { move(scene, Key.DirectionDown, FocusDirection.Down, focusManager) }
            val title = requireNotNull(focusedTitle(scene)) { "the remote is on a row" }
            ok(scene)
            assertNotNull(opened, "the remote opens the focused row")
            visiting.value = false; frames(scene)
            entries.value = listOf(HistoryDisplay(HistoryEntry(card(99), 99L), "Today")) + entries.value
            visiting.value = true; frames(scene)
            assertEquals(title, focusedTitle(scene), "$title keeps the focus on the way back")
        } finally { scene.close() }
    }

    private fun page(width: Int, height: Int, visiting: MutableState<Boolean>, television: Boolean = false,
        content: @Composable () -> Unit): ImageComposeScene =
        ImageComposeScene(width, height, Density(1f)) { ViptvTheme(television) {
            val routes = rememberSaveableStateHolder()
            if (visiting.value) routes.SaveableStateProvider("page") {
                Box(Modifier.fillMaxSize().background(Palette.background)) { content() }
            } else Box(Modifier.fillMaxSize().background(Palette.background))
        } }

    private fun frames(scene: ImageComposeScene, count: Int = 10) =
        repeat(count) { scene.render(frame++ * 16_666_667L).close(); Thread.sleep(4) }

    private fun nodes(scene: ImageComposeScene): List<SemanticsNode> = scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(true) }

    private fun label(node: SemanticsNode): String =
        (node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() +
            node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }).joinToString(" ")

    private fun onScreen(scene: ImageComposeScene, title: String): Boolean =
        nodes(scene).any { it.config.contains(SemanticsActions.OnClick) && label(it).contains(title) }

    private fun scrollDown(scene: ImageComposeScene, by: Float) {
        val node = nodes(scene).filter { it.config.contains(SemanticsActions.ScrollBy) && it.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange) != null }
            .maxBy { it.config[SemanticsProperties.VerticalScrollAxisRange].maxValue() }
        node.config[SemanticsActions.ScrollBy].action?.invoke(0f, by)
        frames(scene); frames(scene)
    }

    private fun scrollRight(scene: ImageComposeScene, by: Float) {
        val node = nodes(scene).filter { it.config.contains(SemanticsActions.ScrollBy) && it.config.getOrNull(SemanticsProperties.HorizontalScrollAxisRange) != null }
            .maxBy { it.config[SemanticsProperties.HorizontalScrollAxisRange].maxValue() }
        node.config[SemanticsActions.ScrollBy].action?.invoke(by, 0f)
        frames(scene); frames(scene)
    }

    /** Opens the last card in the direction the list scrolls and returns its title. */
    private fun openCard(scene: ImageComposeScene, height: Int, opened: () -> Card?, rightmost: Boolean = false): String {
        val candidates = nodes(scene).filter { it.config.contains(SemanticsActions.OnClick) && it.boundsInRoot().bottom < height }
        assertTrue(candidates.isNotEmpty(), "cards are on screen")
        val target = if (rightmost) candidates.maxBy { it.boundsInRoot().left } else candidates.maxBy { it.boundsInRoot().top }
        target.config[SemanticsActions.OnClick].action?.invoke()
        frames(scene)
        return requireNotNull(opened()) { "a card opens" }.title
    }

    /** The remote's OK on whatever holds focus; desktop has no D-pad centre for a clickable. */
    private fun ok(scene: ImageComposeScene) {
        val node = nodes(scene).firstOrNull { it.config.getOrNull(SemanticsProperties.Focused) == true }
        assertTrue(requireNotNull(node) { "nothing is focused" }.config[SemanticsActions.OnClick].action?.invoke() == true)
    }

    private fun focusedTitle(scene: ImageComposeScene): String? =
        nodes(scene).filter { it.config.getOrNull(SemanticsProperties.Focused) == true }
            .map(::label).firstOrNull { it.contains("Show ") }
            ?.let { Regex("Show \\d+").find(it)?.value }

    private fun focusOn(scene: ImageComposeScene, title: String) {
        val deadline = System.nanoTime() + 5_000_000_000L
        while (!nodes(scene).any { it.config.getOrNull(SemanticsProperties.Focused) == true && label(it).contains(title) }) {
            check(System.nanoTime() < deadline) { "focus never reached $title" }
            frames(scene, 2)
        }
    }

    /** A remote key; an unconsumed arrow moves focus as Android TV would. */
    private fun move(scene: ImageComposeScene, key: Key, direction: FocusDirection, manager: FocusManager?) {
        val consumed = scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyDown))
        scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyUp))
        if (!consumed) requireNotNull(manager).moveFocus(direction)
        frames(scene, 6)
    }
}
