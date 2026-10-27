package org.viptv.app

import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import org.viptv.app.features.home.HomeScreen
import org.viptv.app.state.Screen
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.*
import java.io.File
import java.awt.EventQueue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class HomeRecoveryUiTest {
    @get:Rule val uiThread = UiThreadRule()
    private fun onEdt(block: suspend CoroutineScope.() -> Unit) = runBlocking(block = block)
    private val scope = Scope("local", "device", "one", 1)
    private val card = Card(MediaIdentity("tt1", "movie", emptyList()), provider_id = "simkl", title = "Cached title", badges = emptyList())
    private fun home(rows: List<HomeRow> = emptyList()) = HomeSnapshot(scope, 1, rows = rows, complete = true)
    private val row = HomeRow(id = "saved", title = "Your titles", provider_id = "simkl", state = RowState.READY, cards = listOf(card))

    @Test fun heroLessReadyHomeFocusesShelfAndUpReturnsToVisibleNavigationWithoutRefocusingShelf() = onEdt {
        var snapshot by mutableStateOf(home(listOf(row)))
        var back: (() -> Unit)? = null
        val scene = ImageComposeScene(1920, 1080, Density(1f)) { ViptvTheme(true) {
            HomeScreen(snapshot, true, {}, {}, busy = false, backChanged = { back = it })
        } }
        try {
            var frame = awaitState(scene, "Initial shelf focus", 0) { cardFocused(scene) && back != null }
            assertEquals(true, action(scene, "Cached title").config.getOrNull(SemanticsProperties.Focused))
            assertNotNull(back)
            scene.sendKeyEvent(KeyEvent(Key.DirectionUp, KeyEventType.KeyDown))
            scene.sendKeyEvent(KeyEvent(Key.DirectionUp, KeyEventType.KeyUp))
            frame = awaitState(scene, "Up reaches navigation", frame) { navigationSearchFocused(scene) && back == null }
            assertTrue(navigationSearchFocused(scene)); assertNull(back)
            snapshot = snapshot.copy(revision = 2); frames(scene, frame)
            assertTrue(navigationSearchFocused(scene), "Home recomposition must not trap focus back in the shelf")
            capture(scene, "tv-home-no-hero-navigation")
        } finally { scene.close() }
    }

    @Test fun heroLessReadyHomeBackReturnsToVisibleNavigation() = onEdt {
        var back: (() -> Unit)? = null
        val scene = ImageComposeScene(1920, 1080, Density(1f)) { ViptvTheme(true) {
            HomeScreen(home(listOf(row)), true, {}, {}, busy = false, backChanged = { back = it })
        } }
        try {
            val frame = awaitState(scene, "Initial shelf Back action", 0) { cardFocused(scene) && back != null }
            assertNotNull(back).invoke()
            awaitState(scene, "Back reaches navigation", frame) { navigationSearchFocused(scene) && back == null }
            assertTrue(navigationSearchFocused(scene)); assertNull(back)
        } finally { scene.close() }
    }

    @Test fun heroLessHomeWaitsForReadyShelfAfterPendingOrFailedLeadingRows() = onEdt {
        for (leading in listOf(RowState.PENDING, RowState.FAILED)) {
            val snapshot = home(listOf(row.copy(id = "leading", cards = emptyList(), state = leading), row))
            val scene = ImageComposeScene(1920, 1080, Density(1f)) { ViptvTheme(true) {
                HomeScreen(snapshot, true, {}, {}, busy = false)
            } }
            try {
                awaitState(scene, "Leading $leading row attaches and focuses READY target", 0) { cardFocused(scene) }
                assertEquals(true, action(scene, "Cached title").config.getOrNull(SemanticsProperties.Focused), "Leading $leading row must not leave the lazy target unattached")
            } finally { scene.close() }
        }
    }

    @Test fun tvInitialFailureFocusesRetryAndRecoversWithoutAnEndlessLoadingState() = onEdt {
        var snapshot by mutableStateOf<HomeSnapshot?>(null)
        var error by mutableStateOf<String?>("Could not reach your server. Check the connection and try again.")
        var busy by mutableStateOf(false); var retried = 0
        val scene = ImageComposeScene(1920, 1080, Density(1f)) { ViptvTheme(true) {
            HomeScreen(snapshot, true, {}, { retried++; busy = true }, busy = busy, error = error)
        } }
        try {
            frames(scene)
            assertEquals(true, action(scene, "Retry Home").config.getOrNull(SemanticsProperties.Focused))
            assertTrue(nodes(scene).any { text(it) == error }); capture(scene, "tv-home-failure")
            click(scene, "Retry Home"); frames(scene, 20)
            assertEquals(1, retried); assertTrue(action(scene, "Refreshing Home…").config.contains(SemanticsProperties.Disabled))
            snapshot = home(); busy = false; error = null; frames(scene, 40)
            assertFalse(nodes(scene).any { text(it) == "Could not load Home" })
            assertEquals(true, action(scene, "Open settings").config.getOrNull(SemanticsProperties.Focused))
        } finally { scene.close() }
    }

    @Test fun completeEmptyTvHomeDistinguishesUnconfiguredCatalogsFromEmptyResults() = onEdt {
        var snapshot by mutableStateOf(home()); var destination: Screen? = null; var retried = 0
        val scene = ImageComposeScene(1920, 1080, Density(1f)) { ViptvTheme(true) {
            HomeScreen(snapshot, true, {}, { retried++ }, navigate = { destination = it }, busy = false)
        } }
        try {
            frames(scene); click(scene, "Open settings"); assertEquals(Screen.Settings, destination)
            assertTrue(nodes(scene).any { text(it) == "No catalogs configured" }); capture(scene, "tv-home-unconfigured")
            snapshot = home(listOf(row.copy(state = RowState.EMPTY, cards = emptyList()))); frames(scene, 20)
            assertTrue(nodes(scene).any { text(it) == "Nothing to show yet" }); click(scene, "Retry Home"); assertEquals(1, retried)
            assertFalse(nodes(scene).any { text(it) == "No catalogs configured" }); capture(scene, "tv-home-empty")
        } finally { scene.close() }
    }

    @Test fun phoneRefreshFailureRetainsCachedShelfAndOffersOneDisabledRetryWhileBusy() = onEdt {
        var busy by mutableStateOf(false); var retried = 0
        val scene = ImageComposeScene(390, 844, Density(1f)) { ViptvTheme {
            HomeScreen(home(listOf(row)), false, {}, { retried++; busy = true }, busy = busy, error = "Could not refresh these titles.")
        } }
        try {
            frames(scene); assertTrue(nodes(scene).any { text(it) == "Your titles" })
            assertTrue(nodes(scene).any { text(it) == "Could not refresh these titles." }); capture(scene, "phone-home-cached-error")
            click(scene, "Retry Home"); frames(scene, 20); assertEquals(1, retried)
            assertTrue(action(scene, "Refreshing Home…").config.contains(SemanticsProperties.Disabled))
            assertTrue(nodes(scene).any { text(it) == "Your titles" })
        } finally { scene.close() }
    }

    @Test fun cachedTvHomeKeepsItsHeroAndRetryVisible() = onEdt {
        var retried = 0
        val scene = ImageComposeScene(1920, 1080, Density(1f)) { ViptvTheme(true) {
            HomeScreen(home(listOf(row)).copy(hero = card), true, {}, { retried++ }, busy = false, error = "Could not refresh these titles.")
        } }
        try {
            frames(scene)
            assertEquals(true, action(scene, "Retry Home").config.getOrNull(SemanticsProperties.Focused))
            assertTrue(nodes(scene).any { text(it) == "Cached title" })
            assertTrue(action(scene, "Retry Home").boundsInRoot.bottom < 306f, "Refresh notice must stay above the cached hero artwork")
            assertTrue(action(scene, "Play").boundsInRoot.top > action(scene, "Retry Home").boundsInRoot.bottom)
            capture(scene, "tv-home-cached-error"); click(scene, "Retry Home"); assertEquals(1, retried)
        } finally { scene.close() }
    }

    @Test fun allFailedTvCatalogsFocusRetryWithoutATopLevelTransportError() = onEdt {
        var retried = 0
        val scene = ImageComposeScene(1920, 1080, Density(1f)) { ViptvTheme(true) {
            HomeScreen(home(listOf(row.copy(state = RowState.FAILED, cards = emptyList()))), true, {}, { retried++ }, busy = false)
        } }
        try {
            frames(scene)
            assertEquals(true, action(scene, "Retry Home").config.getOrNull(SemanticsProperties.Focused))
            assertTrue(nodes(scene).any { text(it) == "Could not load Home" })
            capture(scene, "tv-home-all-catalogs-failed"); click(scene, "Retry Home"); assertEquals(1, retried)
        } finally { scene.close() }
    }

    @Test fun idleHomeWithoutSnapshotOffersRecoveryOnPhoneAndTv() = onEdt {
        for (tv in listOf(false, true)) {
            var retried = 0
            val scene = ImageComposeScene(if (tv) 1920 else 390, if (tv) 1080 else 844, Density(1f)) { ViptvTheme(tv) {
                HomeScreen(null, tv, {}, { retried++ }, busy = false)
            } }
            try { frames(scene); click(scene, "Retry Home"); assertEquals(1, retried) } finally { scene.close() }
        }
    }

    @Test fun phoneHomeDropsEmptyAndFailedRowsAndShowsARecoveredRow() = onEdt {
        val ready = row.copy(id = "ready", title = "Saved row")
        val empty = row.copy(id = "empty", title = "Empty row", state = RowState.EMPTY, cards = emptyList())
        val failed = row.copy(id = "failed", title = "Failed row", state = RowState.FAILED, cards = emptyList())
        val pending = row.copy(id = "loading", title = "Loading row", state = RowState.PENDING, cards = emptyList())
        var snapshot by mutableStateOf(home(listOf(empty, ready, failed, pending)))
        val scene = ImageComposeScene(390, 844, Density(1f)) { ViptvTheme {
            HomeScreen(snapshot, false, {}, {}, busy = false)
        } }
        try {
            frames(scene, 24)
            assertTrue(labels(scene).contains("Saved row"))
            assertTrue(labels(scene).contains("Loading row"), "pending rows keep their skeleton heading")
            assertFalse(labels(scene).contains("Empty row"), "an empty row must not draw a heading")
            assertFalse(labels(scene).contains("Failed row"), "a failed row must not draw a heading")
            assertFalse(labels(scene).contains("Try again"), "a failed row must not draw a retry")
            capture(scene, "phone-home-hidden-empty-failed")
            // The quiet lane rebuilds Home; a recovered row appears, a row that
            // resolved to failed disappears, and a pending row keeps its skeleton.
            snapshot = home(listOf(ready, failed.copy(state = RowState.READY, cards = listOf(card.copy(title = "Recovered title"))), pending))
            frames(scene, 24)
            assertTrue(labels(scene).contains("Failed row"), "a row that loads appears on the next snapshot")
            assertTrue(labels(scene).contains("Recovered title"))
            snapshot = home(listOf(ready, failed.copy(state = RowState.READY, cards = listOf(card.copy(title = "Recovered title"))), pending.copy(state = RowState.FAILED, cards = emptyList())))
            frames(scene, 24)
            assertFalse(labels(scene).contains("Loading row"), "a pending row that fails disappears")
        } finally { scene.close() }
    }

    @Test fun homeWithOnlyHiddenRowsShowsItsExistingEmptyOrErrorState() = onEdt {
        for (tv in listOf(false, true)) {
            var retried = 0
            val hidden = listOf(
                row.copy(id = "empty", title = "Empty row", state = RowState.EMPTY, cards = emptyList()),
                row.copy(id = "failed", title = "Failed row", state = RowState.FAILED, cards = emptyList()),
            )
            val scene = ImageComposeScene(if (tv) 1920 else 390, if (tv) 1080 else 844, Density(1f)) { ViptvTheme(tv) {
                HomeScreen(home(hidden), tv, {}, { retried++ }, busy = false)
            } }
            try {
                frames(scene, 24)
                assertFalse(labels(scene).contains("Empty row")); assertFalse(labels(scene).contains("Failed row"))
                assertTrue(labels(scene).any { it == "Nothing to show yet" || it == "Could not load Home" }, "TV=$tv: ${labels(scene)}")
                click(scene, "Retry Home"); assertEquals(1, retried)
                capture(scene, if (tv) "tv-home-hidden-rows" else "phone-home-hidden-rows")
            } finally { scene.close() }
        }
    }

    @Test fun tvHomeMovesFocusOffARowThatDisappears() = onEdt {
        val firstCard = card.copy(identity = MediaIdentity("tt-a", "movie", emptyList()), title = "First card")
        val secondCard = card.copy(identity = MediaIdentity("tt-b", "movie", emptyList()), title = "Second card")
        val firstRow = row.copy(id = "first", title = "First row", cards = listOf(firstCard))
        val secondRow = row.copy(id = "second", title = "Second row", cards = listOf(secondCard))
        var snapshot by mutableStateOf(home(listOf(firstRow, secondRow)))
        val scene = ImageComposeScene(1920, 1080, Density(1f)) { ViptvTheme(true) {
            focusManager = androidx.compose.ui.platform.LocalFocusManager.current
            HomeScreen(snapshot, true, {}, {}, busy = false)
        } }
        try {
            var frame = awaitState(scene, "first row focus", 0) { focusedLabel(scene)?.contains("First card") == true }
            press(scene, Key.DirectionDown); frames(scene, 12)
            frame = awaitState(scene, "second row focus", frame) { focusedLabel(scene)?.contains("Second card") == true }
            snapshot = home(listOf(firstRow, secondRow.copy(state = RowState.FAILED, cards = emptyList())))
            frame = awaitState(scene, "focus returns to the surviving row", frame) { focusedLabel(scene)?.contains("First card") == true }
            assertFalse(labels(scene).contains("Second row")); assertFalse(labels(scene).contains("Try again"))
            frames(scene, 24)
            capture(scene, "tv-home-focused-row-removed")
        } finally { scene.close() }
    }

    @Test fun tvHomeKeepsFocusOnASurvivingRowWhenARowAboveDisappears() = onEdt {
        val firstCard = card.copy(identity = MediaIdentity("tt-a", "movie", emptyList()), title = "First card")
        val secondCard = card.copy(identity = MediaIdentity("tt-b", "movie", emptyList()), title = "Second card")
        val firstRow = row.copy(id = "first", title = "First row", cards = listOf(firstCard))
        val secondRow = row.copy(id = "second", title = "Second row", cards = listOf(secondCard))
        var snapshot by mutableStateOf(home(listOf(firstRow, secondRow)))
        val scene = ImageComposeScene(1920, 1080, Density(1f)) { ViptvTheme(true) {
            focusManager = androidx.compose.ui.platform.LocalFocusManager.current
            HomeScreen(snapshot, true, {}, {}, busy = false)
        } }
        try {
            var frame = awaitState(scene, "first row focus", 0) { focusedLabel(scene)?.contains("First card") == true }
            press(scene, Key.DirectionDown); frames(scene, 12)
            frame = awaitState(scene, "second row focus", frame) { focusedLabel(scene)?.contains("Second card") == true }
            snapshot = home(listOf(firstRow.copy(state = RowState.EMPTY, cards = emptyList()), secondRow))
            frames(scene, 24)
            assertTrue(focusedLabel(scene)?.contains("Second card") == true, "focus left the surviving shelf: ${focusedLabel(scene)}")
            assertFalse(labels(scene).contains("First row"))
            frames(scene, 24)
            capture(scene, "tv-home-row-above-removed")
        } finally { scene.close() }
    }

    private fun labels(scene: ImageComposeScene) = nodes(scene).map(::text).filter { it.isNotBlank() }
    private fun focusedLabel(scene: ImageComposeScene): String? =
        nodes(scene).firstOrNull { it.config.getOrNull(SemanticsProperties.Focused) == true }?.let(::text)?.takeIf { it.isNotBlank() }
    private var focusManager: androidx.compose.ui.focus.FocusManager? = null
    /** Like Android: key handlers see the press first; an unconsumed D-pad press moves focus. */
    private fun press(scene: ImageComposeScene, key: Key) {
        val consumed = scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyDown)); scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyUp))
        if (!consumed) requireNotNull(focusManager).moveFocus(when (key) {
            Key.DirectionUp -> androidx.compose.ui.focus.FocusDirection.Up
            Key.DirectionDown -> androidx.compose.ui.focus.FocusDirection.Down
            Key.DirectionLeft -> androidx.compose.ui.focus.FocusDirection.Left
            else -> androidx.compose.ui.focus.FocusDirection.Right
        })
    }

    private suspend fun frames(scene: ImageComposeScene, start: Int = 0) = repeat(16) {
        check(EventQueue.isDispatchThread())
        scene.render((start + it) * 16_666_667L).close(); delay(4)
    }
    private suspend fun awaitState(scene: ImageComposeScene, description: String, start: Int, condition: () -> Boolean): Int {
        val deadline = System.nanoTime() + 2_000_000_000L
        var frame = start
        do {
            check(EventQueue.isDispatchThread())
            scene.render(frame++ * 16_666_667L).close()
            if (condition()) return frame
            delay(4)
        } while (System.nanoTime() < deadline)
        fail("$description timed out; focused=" + nodes(scene).filter { it.config.getOrNull(SemanticsProperties.Focused) == true }
            .map { text(it) to it.config.getOrNull(SemanticsProperties.ContentDescription) } + "; visible texts=" + nodes(scene).map(::text).filter { it.isNotBlank() })
    }
    private fun nodes(scene: ImageComposeScene) = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
    private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
    private fun text(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty()
    private fun cardFocused(scene: ImageComposeScene) = nodes(scene).any {
        text(it) == "Cached title" && it.config.getOrNull(SemanticsProperties.Focused) == true
    }
    private fun navigationSearchFocused(scene: ImageComposeScene) = nodes(scene).any {
        it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains("Search") == true &&
            it.config.getOrNull(SemanticsProperties.Focused) == true
    }
    private fun action(scene: ImageComposeScene, label: String) = nodes(scene).single { text(it) == label && it.config.contains(SemanticsActions.OnClick) }
    private fun click(scene: ImageComposeScene, label: String) { assertTrue(action(scene, label).config[SemanticsActions.OnClick].action!!.invoke()) }
    private fun capture(scene: ImageComposeScene, name: String) { System.getenv("VIPTV_HOME_CAPTURE_DIR")?.let { path ->
        File(path).mkdirs(); scene.render(4_000_000_000L).use { image -> image.encodeToData()!!.use { File(path, "$name.png").writeBytes(it.bytes) } }
    } }
}
