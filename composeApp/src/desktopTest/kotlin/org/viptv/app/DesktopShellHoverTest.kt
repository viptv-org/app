package org.viptv.app

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.*
import org.jetbrains.skia.Bitmap
import org.junit.Rule
import org.viptv.app.connection.RemoteSessionStore
import org.viptv.app.connection.SavedRemoteSession
import org.viptv.app.features.home.MEDIA_CARD_HOVER_RING_TAG
import org.viptv.app.features.home.MEDIA_CARD_HOVER_TAG
import org.viptv.app.preview.PreviewData
import org.viptv.app.state.*
import kotlin.test.*

/**
 * app#92: the desktop chrome itself (title bar, rail, App, card actions: the
 * same [DesktopShell] the desktop window runs) answers hover on every control
 * the owner found dead: media cards, rail items, the rail footer, the title-bar
 * search and a row chevron. Hover changes paint only, never layout.
 *
 * The window half of the bug (a frame that never reached the screen) needs a
 * real window and display; see `DesktopWindowRepaintTest`.
 */
@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class DesktopShellHoverTest {
    @get:Rule val uiThread = UiThreadRule()

    private val store = object : RemoteSessionStore {
        override suspend fun read(): SavedRemoteSession? = null
        override suspend fun save(session: SavedRemoteSession) = Unit
        override suspend fun clear() = Unit
    }

    private class Shell(val scene: ImageComposeScene) {
        private var frame = 0L
        fun pixels(): Bitmap {
            repeat(6) { scene.render(frame++ * 16_666_667L).close() }
            return scene.render(frame++ * 16_666_667L).use { Bitmap.makeFromImage(it) }
        }
        fun nodes(merged: Boolean = true) = scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(merged) }
        fun clickable(name: String, match: (String) -> Boolean): SemanticsNode =
            nodes().filter { it.config.contains(SemanticsActions.OnClick) && match(label(it)) }
                .also { assertTrue(it.isNotEmpty(), "$name is on screen") }.first()
        fun tagged(tag: String) = nodes(merged = false).filter { it.config.getOrNull(SemanticsProperties.TestTag) == tag }
        private fun label(node: SemanticsNode) = (node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() +
            node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } +
            listOfNotNull(node.config.getOrNull(SemanticsActions.OnClick)?.label)).joinToString("|")
    }

    private fun shell(test: suspend Shell.() -> Unit) = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val controller = ApplicationController(scope, store)
        controller.mutable.value = ApplicationState(screen = Screen.Home, profiles = listOf(PreviewData.profile),
            selectedProfile = PreviewData.profile.id, home = PreviewData.home)
        val scene = ImageComposeScene(1440, 1100, Density(1f)) {
            DesktopShell(controller, GlobalBack(controller), mac = false,
                DesktopWindowControls(minimize = {}, maximize = {}, close = {}), manageControllerLifecycle = false)
        }
        try { Shell(scene).apply { pixels() }.test() }
        finally { scene.close(); controller.disconnect(); scope.cancel() }
    }

    /** Empty rail space between History and the footer: nothing there reacts to the mouse. */
    private val rest = Offset(36f, 700f)

    private fun Shell.changedPixels(before: Bitmap, after: Bitmap, bounds: Rect): Int {
        var changed = 0
        for (x in bounds.left.toInt() until bounds.right.toInt()) for (y in bounds.top.toInt() until bounds.bottom.toInt())
            if (before.getColor(x, y) != after.getColor(x, y)) changed++
        return changed
    }

    /** Hovering [name] repaints it, leaving restores it, and its bounds never move. */
    private fun Shell.assertNodeHoverRepaints(name: String, find: Shell.() -> SemanticsNode) {
        scene.sendPointerEvent(PointerEventType.Move, rest)
        val resting = find().boundsInRoot
        val before = pixels()
        scene.sendPointerEvent(PointerEventType.Move, resting.center)
        val hovered = pixels()
        assertEquals(resting, find().boundsInRoot, "$name: hover does not move the control")
        assertTrue(changedPixels(before, hovered, resting) > 0, "$name: hovering it changes how it looks")
        scene.sendPointerEvent(PointerEventType.Move, rest)
        assertEquals(0, changedPixels(before, pixels(), resting), "$name: leaving it restores the resting look")
    }

    private fun Shell.assertHoverRepaints(name: String, match: (String) -> Boolean) = assertNodeHoverRepaints(name) { clickable(name, match) }

    @Test fun railItemsAndTheRailFooterAnswerHover() = shell {
        assertHoverRepaints("rail Home") { it.startsWith("Home|") }
        assertHoverRepaints("rail Live") { it.startsWith("Live|") }
        assertHoverRepaints("rail My List") { it.startsWith("My List|") }
        assertHoverRepaints("rail Settings") { it.startsWith("Settings|") }
    }

    @Test fun theTitleBarSearchAndProfileAnswerHover() = shell {
        assertHoverRepaints("title-bar search") { it.contains("Search movies, series and channels") }
        assertHoverRepaints("Switch profile") { it.contains("Switch profile") }
    }

    @Test fun anEnabledRowChevronAnswersHover() = shell {
        // "New this week" has more cards than fit, so its Next chevron is live
        // (Continue Watching's fits, and a disabled chevron keeps no hover).
        assertNodeHoverRepaints("live Next items") {
            nodes().filter { it.config.contains(SemanticsActions.OnClick) && !it.config.contains(SemanticsProperties.Disabled) &&
                it.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().contains("Next items") }
                .also { assertTrue(it.isNotEmpty(), "a row with more cards than fit has a live Next chevron") }.first()
        }
    }

    @Test fun mediaCardsShowTheirHoverStateInTheDesktopChrome() = shell {
        for (title in listOf("Monster: The Jeffrey Dahmer Story", "The Whisper Man")) {
            scene.sendPointerEvent(PointerEventType.Move, rest); pixels()
            assertTrue(tagged(MEDIA_CARD_HOVER_TAG).isEmpty(), "$title: no card is hovered at rest")
            val card = clickable(title) { it.contains(title) }.boundsInRoot
            scene.sendPointerEvent(PointerEventType.Move, Offset(card.center.x, card.top + 40f)); pixels()
            val shown = tagged(MEDIA_CARD_HOVER_TAG)
            assertEquals(1, shown.size, "$title: hovering shows exactly one card hover state")
            assertTrue(card.contains(shown.single().boundsInRoot.center), "$title: the hover state is on that card")
            assertEquals(1, tagged(MEDIA_CARD_HOVER_RING_TAG).size, "$title: the inset ring is drawn")
            assertEquals(card, clickable(title) { it.contains(title) }.boundsInRoot, "$title: hover does not move the card")
        }
        scene.sendPointerEvent(PointerEventType.Move, rest); pixels()
        assertTrue(tagged(MEDIA_CARD_HOVER_TAG).isEmpty(), "the hover state clears when the mouse leaves")
    }
}
