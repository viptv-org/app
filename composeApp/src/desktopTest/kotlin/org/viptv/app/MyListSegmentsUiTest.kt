package org.viptv.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
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
import coil3.annotation.DelicateCoilApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Rule
import org.viptv.app.connection.PreferencesSnapshot
import org.viptv.app.connection.Profile
import org.viptv.app.features.continuewatching.ContinueWatchingPage
import org.viptv.app.features.continuewatching.ContinueWatchingRoute
import org.viptv.app.features.library.ContinueWatchingSection
import org.viptv.app.features.library.LibraryScreen
import org.viptv.app.features.library.MyListSegment
import org.viptv.app.state.ApplicationState
import org.viptv.app.state.ApplicationController
import org.viptv.app.state.Screen
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.EpisodeCoordinates
import org.viptv.engine.wire.MediaIdentity

@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class, DelicateCoilApi::class)
class MyListSegmentsUiTest {
    @get:Rule val uiThread = UiThreadRule()
    @get:Rule val deterministicImages = DeterministicImageLoaderRule()

    private fun nodes(scene: ImageComposeScene) = scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(false) }
    private fun flat(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flat)
    private fun label(node: SemanticsNode) = (node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() +
        node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }).joinToString(" ")
    private fun subtree(node: SemanticsNode) = flat(node).map(::label).filter { it.isNotBlank() }.joinToString(" ")
    private fun clickables(scene: ImageComposeScene) = nodes(scene).filter { it.config.contains(SemanticsActions.OnClick) }
    // A card also holds its overflow target's name, so the smallest control that reads as the name wins.
    private fun clickable(scene: ImageComposeScene, text: String) =
        clickables(scene).firstOrNull { label(it) == text }
            ?: clickables(scene).firstOrNull { subtree(it).trim() == text }
            ?: clickables(scene).filter { subtree(it).contains(text) }.minByOrNull { flat(it).size }
    private fun tap(scene: ImageComposeScene, text: String) =
        assertTrue(requireNotNull(clickable(scene, text)) { "$text is not on screen" }.config[SemanticsActions.OnClick].action?.invoke() == true)
    private var frame = 0L
    private fun frames(scene: ImageComposeScene, count: Int = 10) = repeat(count) { scene.render(frame++ * 16_666_667L).close() }
    private fun selected(scene: ImageComposeScene, text: String) = nodes(scene).any {
        it.config.getOrNull(SemanticsProperties.Selected) == true && (label(it) == text || subtree(it).contains(text))
    }

    private fun savedCard(number: Int) = Card(MediaIdentity("saved-$number", "series", emptyList()), provider_id = "addon:4",
        title = "Saved %02d".format(number), badges = emptyList())

    private fun resumeCard(number: Int) = Card(
        MediaIdentity("resume-$number:1:2", "series", emptyList(), EpisodeCoordinates("aired", 1, 2)), provider_id = "addon:4",
        title = "Resume %02d".format(number), subtitle = "S1 E2 · 10m left", progress = 0.4, badges = emptyList(),
        parent_identity = MediaIdentity("resume-$number", "series", emptyList()))

    private val saved = (1..6).map(::savedCard)
    private val resumed = (1..4).map(::resumeCard)

    private fun section(page: ContinueWatchingPage, remove: ((Card) -> Unit)? = null, play: (Card) -> Unit = {}) =
        ContinueWatchingSection(page, play = play, more = null, retry = {}, remove = remove, home = {})

    private fun scene(width: Int, height: Int, television: Boolean = false, content: @androidx.compose.runtime.Composable () -> Unit) =
        ImageComposeScene(width, height, Density(1f)) {
            ViptvTheme(television = television) { Box(Modifier.fillMaxSize().background(Palette.background)) { content() } }
        }

    @Test fun everyLayoutCarriesBothSegmentsAndSwitchesInPlace() {
        for ((name, size) in listOf("phone" to (390 to 844), "desktop" to (1280 to 800), "web" to (1440 to 900), "tv" to (1920 to 1080))) {
            val television = name == "tv"
            var segment by mutableStateOf(MyListSegment.Saved)
            val scene = scene(size.first, size.second, television) {
                LibraryScreen(saved, available = true, television = television, open = {}, history = {}, home = {},
                    segment = segment, selectSegment = { segment = it }, remove = {},
                    continueWatching = section(ContinueWatchingPage(resumed, loaded = true)))
            }
            try {
                frames(scene)
                assertNotNull(clickable(scene, "Continue Watching"), "$name draws the second segment")
                assertNotNull(clickable(scene, "My List"), "$name draws the first segment")
                assertTrue(selected(scene, "My List"), "$name starts on My List")
                assertNotNull(clickable(scene, "Saved 01"), "$name draws the saved titles")

                tap(scene, "Continue Watching")
                frames(scene)
                assertEquals(MyListSegment.Continue, segment, "$name switches in place")
                assertTrue(selected(scene, "Continue Watching"), "$name fills the segment it shows")
                assertNotNull(clickable(scene, "Resume 01"), "$name swaps the grid for the in-progress list")
                assertNull(clickable(scene, "Saved 01"), "$name drops the saved titles while the second segment shows")

                tap(scene, "My List")
                frames(scene)
                assertEquals(MyListSegment.Saved, segment)
                assertNotNull(clickable(scene, "Saved 01"), "$name comes back to the saved titles")
            } finally { scene.close() }
        }
    }

    @Test fun theSecondSegmentResumesItsOwnCardsAndLeavesTheMyListRowBehind() {
        var played: Card? = null
        var segment by mutableStateOf(MyListSegment.Continue)
        val scene = scene(390, 844) {
            LibraryScreen(saved, available = true, television = false, open = {}, history = {}, home = {},
                segment = segment, selectSegment = { segment = it },
                continueWatching = section(ContinueWatchingPage(resumed, loaded = true), play = { played = it }))
        }
        try {
            frames(scene)
            tap(scene, "Resume 02")
            assertEquals(resumeCard(2), played, "the in-progress card resumes that episode")
            assertNull(clickable(scene, "History"), "the My List row belongs to the first segment")
        } finally { scene.close() }
    }

    @Test fun theSecondSegmentRemovesThroughTheSharedConfirmation() {
        for ((width, name) in listOf(390 to "phone", 1280 to "desktop")) {
            val removed = mutableListOf<Card>()
            val scene = scene(width, 900) {
                LibraryScreen(saved, available = true, television = false, open = {}, history = {}, home = {},
                    segment = MyListSegment.Continue, continueWatching = section(ContinueWatchingPage(resumed, loaded = true), remove = { removed += it }))
            }
            try {
                frames(scene)
                tap(scene, "More actions")
                frames(scene, 40)
                                tap(scene, "Remove from Continue Watching")
                frames(scene)
                assertTrue(nodes(scene).any { label(it) == "Remove from Continue Watching?" }, "$name confirms before removing")
                tap(scene, "Remove")
                assertEquals(listOf(resumeCard(1)), removed, "$name removes the card the menu belonged to")
            } finally { scene.close() }
        }
    }

    @Test fun theContinueWatchingRouteOpensOnTheSecondSegment(): Unit = runBlocking {
        ListRemovalHost().use { host ->
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val controller = ApplicationController(scope, host.sessionStore)
            try {
                controller.connection = host.remote
                controller.mutable.value = ApplicationState(screen = Screen.ContinueWatching, selectedProfile = "1",
                    profiles = listOf(Profile("1", "QA", null)), preferences = PreferencesSnapshot())
                val scene = scene(390, 844) {
                    val state by controller.state.collectAsState()
                    ContinueWatchingRoute(state, controller, television = false)
                }
                try {
                    renderUntil(scene) { controller.state.value.continueWatching != null }
                    assertTrue(selected(scene, "Continue Watching"), "See all lands on My List's second segment")
                    assertNotNull(clickable(scene, "Show 01"), "the in-progress list is already there")
                    tap(scene, "My List")
                    renderUntil(scene) { controller.state.value.libraryPage != null }
                    assertNotNull(clickable(scene, "Show 01"), "the saved titles load for the first segment")
                } finally { scene.close() }
            } finally { scope.cancel() }
        }
    }

    @Test fun tvSwitchingSegmentFocusesThatSegmentsFirstCardAtItsFirstRow() {
        val many = (1..20).map(::savedCard)
        val manyResumed = (1..12).map(::resumeCard)
        var segment by mutableStateOf(MyListSegment.Saved)
        var focusManager: FocusManager? = null
        val scene = ImageComposeScene(1920, 1080, Density(1f)) { ViptvTheme(true) { Column(Modifier.fillMaxSize().background(Palette.background)) {
            focusManager = LocalFocusManager.current
            LibraryScreen(many, available = true, television = true, open = {}, history = {}, home = {},
                segment = segment, selectSegment = { segment = it }, remove = {},
                continueWatching = section(ContinueWatchingPage(manyResumed, loaded = true)))
        } } }
        fun press(key: Key, times: Int = 1) = repeat(times) {
            val consumed = scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyDown))
            scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyUp))
            val direction = when (key) {
                Key.DirectionUp -> FocusDirection.Up; Key.DirectionDown -> FocusDirection.Down
                Key.DirectionLeft -> FocusDirection.Left; Key.DirectionRight -> FocusDirection.Right
                else -> null
            }
            if (!consumed && direction != null) requireNotNull(focusManager).moveFocus(direction)
            frames(scene, 6)
        }
        fun focused() = nodes(scene).filter { it.config.getOrNull(SemanticsProperties.Focused) == true }.map(::subtree)
        fun focusedOn(text: String, why: String) {
            val deadline = System.nanoTime() + 10_000_000_000L
            while (focused().none { it.startsWith(text) }) {
                check(System.nanoTime() < deadline) { "$why: focused ${focused()}" }
                frames(scene, 2)
            }
        }
        try {
            frames(scene)
            focusedOn("Saved 01", "My List opens on its first card")
            press(Key.DirectionDown)
            focusedOn("Saved 08", "Down reaches the second row")
            press(Key.DirectionUp)
            focusedOn("Saved 01", "Up returns to the first row")
            press(Key.DirectionUp)
            focusedOn("My List", "Up from the first row reaches the segments")
            press(Key.DirectionRight)
            focusedOn("Continue Watching", "Right picks the second segment")
            press(Key.Enter)
            focusedOn("Resume 01", "the second segment takes the first card at its first row")
            assertEquals(MyListSegment.Continue, segment)

            press(Key.DirectionDown)
            focusedOn("Resume 05", "Down walks the in-progress grid")
            press(Key.DirectionUp)
            focusedOn("Resume 01", "Up returns to the first row")
            press(Key.DirectionUp)
            focusedOn("Continue Watching", "Up from the first row reaches the segments again")
            press(Key.DirectionLeft)
            focusedOn("My List", "Left picks the first segment")
            press(Key.Enter)
            focusedOn("Saved 01", "My List returns to its first card, not the row it was left on")
            assertEquals(MyListSegment.Saved, segment)
        } finally { scene.close() }
    }
}

// Renders, letting the controller's own work land between frames, until the page shows what the test waits for.
private fun renderUntil(scene: ImageComposeScene, condition: () -> Boolean) {
    val deadline = System.nanoTime() + 10_000_000_000L
    var frame = 0L
    while (!condition()) {
        check(System.nanoTime() < deadline) { "timed out waiting for the page" }
        scene.render(frame++ * 16_666_667L).close()
    }
    repeat(10) { scene.render(frame++ * 16_666_667L).close() }
}