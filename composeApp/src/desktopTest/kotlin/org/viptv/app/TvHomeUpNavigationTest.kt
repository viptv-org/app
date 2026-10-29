package org.viptv.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import org.viptv.app.connection.Profile
import org.viptv.app.features.home.TvHomeScreen
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.*
import org.junit.Rule
import kotlin.test.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class TvHomeUpNavigationTest {
    @get:Rule val uiThread = UiThreadRule()
    private fun onEdt(block: suspend CoroutineScope.() -> Unit) = runBlocking(block = block)
    private fun nodes(scene: ImageComposeScene): List<SemanticsNode> = scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(true) }
    private fun text(node: SemanticsNode): String =
        node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ").orEmpty()
            .ifBlank { node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty() }
    private fun focused(scene: ImageComposeScene) = nodes(scene).firstOrNull { it.config.getOrNull(SemanticsProperties.Focused) == true }
    private fun focusCard(scene: ImageComposeScene, label: String): Boolean {
        val node = nodes(scene).firstOrNull { text(it).contains(label) && it.config.contains(SemanticsActions.RequestFocus) } ?: return false
        return node.config[SemanticsActions.RequestFocus].action?.invoke() ?: false
    }
    private var focusManager: androidx.compose.ui.focus.FocusManager? = null
    // Desktop maps no arrow key to a focus move, so an unconsumed D-pad press has to drive the focus manager.
    private fun press(scene: ImageComposeScene, key: Key) {
        val consumed = scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyDown)); scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyUp))
        if (!consumed) requireNotNull(focusManager).moveFocus(when (key) {
            Key.DirectionUp -> androidx.compose.ui.focus.FocusDirection.Up
            Key.DirectionDown -> androidx.compose.ui.focus.FocusDirection.Down
            Key.DirectionLeft -> androidx.compose.ui.focus.FocusDirection.Left
            else -> androidx.compose.ui.focus.FocusDirection.Right
        })
    }
    private fun row(letter: String) = HomeRow(id = "row$letter", title = "Row $letter", provider_id = "fixture", state = RowState.READY,
        cards = (1..10).map { Card(MediaIdentity("$letter$it", "movie", emptyList()), provider_id = "fixture", title = "Card $letter$it", badges = emptyList()) })
    private val home = HomeSnapshot(Scope("server", "owner", "12", 1), 1,
        hero = Card(MediaIdentity("hero", "movie", emptyList()), provider_id = "fixture", title = "Hero title", badges = emptyList()),
        featured = emptyList(), rows = listOf(row("A"), row("B"), row("C"), row("D")), complete = true)

    @Test fun returningToTheBillboardSlidesRowsWithoutClippingThem() = onEdt {
        var frame = 0L
        val scene = ImageComposeScene(1920, 1080, Density(1f)) { ViptvTheme(true) {
            focusManager = androidx.compose.ui.platform.LocalFocusManager.current
            Box(Modifier.fillMaxSize().background(Palette.background)) {
                TvHomeScreen(home, open = {}, play = {}, retry = {}, navigate = {}, profile = Profile("12", "Parent", null), busy = false)
            }
        } }
        fun render(n: Int = 6) = repeat(n) { scene.render(frame++ * 16_666_667L).close() }
        suspend fun settle() { repeat(4) { render(10); delay(20) } }
        fun title(name: String) = nodes(scene).firstOrNull { text(it) == name }
        try {
            settle()
            assertTrue(focusCard(scene, "Card A2")); settle()
            fun cardHeight() = (nodes(scene).firstOrNull { text(it).contains("Card A1") } ?: error("Card A1 is not composed")).boundsInRoot.height
            val resting = cardHeight()
            System.getenv("VIPTV_CLIP_CAPTURE")?.let { dir ->
                java.io.File(dir).mkdirs()
                java.io.File(dir, "before.png").writeBytes(requireNotNull(scene.render(frame++ * 16_666_667L).encodeToData()).bytes)
            }
            press(scene, Key.DirectionUp)
            // boundsInRoot is clipped by ancestors: a list that loses height mid-slide cuts the card.
            for (n in 0 until 30) {
                val image = scene.render(frame++ * 16_666_667L)
                System.getenv("VIPTV_CLIP_CAPTURE")?.let { dir -> if (n % 3 == 0) java.io.File(dir, "up-%02d.png".format(n)).writeBytes(requireNotNull(image.encodeToData()).bytes) }
                image.close()
                assertEquals(resting, cardHeight(), 1f, "Row A was clipped ${n * 16} ms into the slide back to the billboard")
            }
        } finally { scene.close() }
    }

    @Test fun upFromALowerRowFocusesTheRowAboveAndUpFromTheFirstRowReturnsToTheBillboard() = onEdt {
        var frame = 0L
        val scene = ImageComposeScene(1920, 1080, Density(1f)) { ViptvTheme(true) {
            focusManager = androidx.compose.ui.platform.LocalFocusManager.current
            Box(Modifier.fillMaxSize().background(Palette.background)) {
                TvHomeScreen(home, open = {}, play = {}, retry = {}, navigate = {}, profile = Profile("12", "Parent", null), busy = false)
            }
        } }
        fun render(n: Int = 6) = repeat(n) { scene.render(frame++ * 16_666_667L).close() }
        suspend fun settle() { repeat(4) { render(10); delay(20) } }
        try {
            settle()
            assertTrue(focusCard(scene, "Card A2"), "Card A2 must be focusable"); settle()
            press(scene, Key.DirectionDown); settle(); press(scene, Key.DirectionDown); settle()
            val lower = focused(scene) ?: error("Down twice left nothing focused")
            assertTrue(text(lower).contains("Card C"), "Down twice from row A must reach row C, got '${text(lower)}'")
            press(scene, Key.DirectionUp); settle()
            val above = focused(scene) ?: error("Up from row C left nothing focused")
            assertTrue(text(above).contains("Card B"), "Up from row C must focus row B, got '${text(above)}'")
            press(scene, Key.DirectionUp); settle()
            val first = focused(scene) ?: error("Up from row B left nothing focused")
            assertTrue(text(first).contains("Card A"), "Up from row B must focus row A, got '${text(first)}'")
            press(scene, Key.DirectionUp); settle()
            val top = focused(scene)
            assertFalse(top != null && text(top).startsWith("Card "), "Up from the first row returns to the billboard, got '${top?.let(::text)}'")
        } finally { scene.close() }
    }
}
