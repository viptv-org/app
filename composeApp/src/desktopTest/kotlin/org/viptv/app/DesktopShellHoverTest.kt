package org.viptv.app

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import org.viptv.app.theme.Palette
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
            repeat(13) { scene.render(frame++ * 16_666_667L).close() }
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

    private val rest = Offset(36f, 700f)

    private fun changedPixels(before: Bitmap, after: Bitmap, bounds: Rect): Int {
        var changed = 0
        for (x in bounds.left.toInt() until bounds.right.toInt()) for (y in bounds.top.toInt() until bounds.bottom.toInt())
            if (before.getColor(x, y) != after.getColor(x, y)) changed++
        return changed
    }

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

    @Test fun railRowsHoverWithTheDesignRowTokens() = shell {
        fun fillOf(name: String, hovered: Boolean): Int {
            val row = clickable(name) { it.startsWith("$name|") }.boundsInRoot
            scene.sendPointerEvent(PointerEventType.Move, if (hovered) row.center else rest)
            return pixels().getColor(row.left.toInt() + 4, row.center.y.toInt())
        }
        fun argb(value: Long) = value.toInt()
        assertEquals(argb(0xFF2A2A2E), fillOf("Home", hovered = false), "the selected row rests on surface-3")
        assertEquals(argb(0xFF34343A), fillOf("Home", hovered = true), "the hovered selected row lifts to surface-4")
        assertEquals(argb(0xFF0B0B0C), fillOf("Live", hovered = false), "an unselected row rests on the ground")
        assertEquals(argb(0xFF212124), fillOf("Live", hovered = true), "a hovered row is surface-2")
    }

    @Test fun theTitleBarSearchAndProfileAnswerHover() = shell {
        assertHoverRepaints("title-bar search") { it.contains("Search movies, series and channels") }
        assertHoverRepaints("Switch profile") { it.contains("Switch profile") }
    }

    @Test fun anEnabledRowChevronAnswersHover() = shell {
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

    private fun Shell.windowButton(name: String) = clickable(name) { it.startsWith("$name|") }

    private fun Shell.windowButtonFill(bounds: Rect, pressed: Boolean): Int {
        scene.sendPointerEvent(PointerEventType.Move, bounds.center)
        if (pressed) scene.sendPointerEvent(PointerEventType.Press, bounds.center)
        val colour = pixels().getColor(bounds.left.toInt() + 6, bounds.top.toInt() + 6)
        if (pressed) scene.sendPointerEvent(PointerEventType.Release, bounds.center)
        scene.sendPointerEvent(PointerEventType.Move, rest)
        return colour
    }

    private fun assertNearArgb(expected: Int, actual: Int, message: String, tolerance: Int = 2) {
        val close = (16 downTo 0 step 8).all { shift ->
            kotlin.math.abs(((expected shr shift) and 0xFF) - ((actual shr shift) and 0xFF)) <= tolerance
        }
        assertTrue(close, "$message: expected #${expected.toUInt().toString(16)}, got #${actual.toUInt().toString(16)}")
    }

    @Test fun windowButtonsHoverWithTheNativeColours() = shell {
        val wash = Color.White.copy(alpha = .08f).compositeOver(Palette.background).toArgb()
        assertEquals(Color(0xFFE81123).toArgb(), windowButtonFill(windowButton("Close").boundsInRoot, pressed = false),
            "Close hovers #E81123")
        assertNearArgb(wash, windowButtonFill(windowButton("Minimise").boundsInRoot, pressed = false),
            "Minimise hovers the 8 % white wash")
        assertNearArgb(wash, windowButtonFill(windowButton("Maximise").boundsInRoot, pressed = false),
            "Maximise hovers the 8 % white wash")
    }

    @Test fun windowButtonsPressWithASlightlyStrongerColour() = shell {
        val wash = Color.White.copy(alpha = .12f).compositeOver(Palette.background).toArgb()
        assertEquals(Color(0xFFF1707A).toArgb(), windowButtonFill(windowButton("Close").boundsInRoot, pressed = true),
            "Close presses #F1707A")
        assertNearArgb(wash, windowButtonFill(windowButton("Minimise").boundsInRoot, pressed = true),
            "Minimise presses a slightly stronger white")
    }

    @Test fun aFocusedWindowButtonDrawsNothingOutsideItsBounds() = shell {
        scene.sendPointerEvent(PointerEventType.Move, rest); pixels()
        val minimise = windowButton("Minimise")
        val bounds = minimise.boundsInRoot
        val before = pixels()
        assertTrue(minimise.config.contains(SemanticsActions.RequestFocus), "Minimise is keyboard-focusable")
        assertTrue(minimise.config[SemanticsActions.RequestFocus].action!!.invoke(), "Minimise takes focus")
        val after = pixels()
        val around = Rect((bounds.left - 8f).coerceAtLeast(0f), (bounds.top - 8f).coerceAtLeast(0f),
            1440f, bounds.bottom + 8f)
        assertEquals(0, changedPixels(before, after, around),
            "focusing a window button paints nothing on or around it")
    }
}
