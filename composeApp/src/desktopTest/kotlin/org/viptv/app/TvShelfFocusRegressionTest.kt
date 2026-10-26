package org.viptv.app

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.viptv.app.features.home.TvHomeShelf
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.*
import java.awt.EventQueue
import kotlin.coroutines.CoroutineContext
import kotlin.test.*
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

/**
 * Regression proof for the two reported shelf-focus defects.
 *
 * 1. After a long run to the right, walking back left must keep the focused card
 *    on screen — the first card used to end up outside the viewport ("focus moves
 *    off the screen to the left").
 * 2. Coming back Down from the billboard must resume at the card the viewer left,
 *    not flash it and then snap to the first card.
 *
 * The shelf is rendered at TV geometry and driven through the same focus system
 * the device uses, so the assertions are about real focus/scroll state.
 */
@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class TvShelfFocusRegressionTest {
    private val edt = object : CoroutineDispatcher() {
        override fun isDispatchNeeded(context: CoroutineContext) = !EventQueue.isDispatchThread()
        override fun dispatch(context: CoroutineContext, block: Runnable) = EventQueue.invokeLater(block)
    }
    private fun onEdt(block: suspend CoroutineScope.() -> Unit) = runBlocking(edt, block)

    private fun cards(count: Int) = (1..count).map { index ->
        Card(MediaIdentity("tt$index", "series", emptyList()), provider_id = "simkl",
            title = "Title $index", subtitle = "S1 E$index", badges = emptyList())
    }

    private fun nodes(scene: ImageComposeScene): List<SemanticsNode> =
        scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(true) }

    private fun focused(scene: ImageComposeScene): SemanticsNode? =
        nodes(scene).firstOrNull { it.config.getOrNull(SemanticsProperties.Focused) == true }

    private fun text(node: SemanticsNode): String =
        node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ").orEmpty()
            .ifBlank { node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty() }

    private fun press(scene: ImageComposeScene, key: Key) {
        scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyDown))
        scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyUp))
    }

    /** Focus a card by its label through the semantics RequestFocus action. */
    private fun focusCard(scene: ImageComposeScene, label: String): Boolean {
        val node = nodes(scene).firstOrNull { text(it).contains(label) && it.config.contains(SemanticsActions.RequestFocus) }
            ?: return false
        return node.config[SemanticsActions.RequestFocus].action?.invoke() ?: false
    }

    /** Composed cards ordered left→right; a LazyRow only composes what is near the viewport. */
    private fun visibleCards(scene: ImageComposeScene): List<SemanticsNode> =
        nodes(scene).filter { text(it).startsWith("Title ") && it.config.contains(SemanticsActions.RequestFocus) }
            .sortedBy { it.boundsInRoot.left }

    private fun focusNode(node: SemanticsNode): Boolean =
        node.config[SemanticsActions.RequestFocus].action?.invoke() ?: false

    @Test fun f_leftWalkAfterLongRunKeepsFocusOnScreen() = onEdt {
        val first = FocusRequester()
        var frame = 0L
        var stranded = 0
        val scene = ImageComposeScene(900, 400, Density(1f)) { ViptvTheme(true) {
            Box(Modifier.fillMaxSize().background(Palette.background)) {
                TvHomeShelf(
                    row = HomeRow(id = "continue", title = "Continue Watching", provider_id = "simkl",
                        state = RowState.READY, cards = cards(18)),
                    scale = 1f, first = first, up = {}, focus = {}, open = {},
                    modifier = Modifier.padding(top = 40.dp),
                )
            }
        } }
        fun render(n: Int = 4) = repeat(n) { scene.render(frame++ * 16_666_667L).close() }
        try {
            render(8); delay(30)
            first.requestFocus(); render(4)
            // Walk right like a remote: always step to the right-most composed card.
            repeat(24) {
                val next = visibleCards(scene).lastOrNull() ?: return@repeat
                if (text(next).startsWith("Title 18")) return@repeat
                focusNode(next); render(5); delay(5)
            }
            // Walk all the way back to the left the same way.
            repeat(30) {
                render(5); delay(5)
                val prev = visibleCards(scene).firstOrNull() ?: return@repeat
                focusNode(prev)
            }
            render(12); delay(40); render(12)
            val node = focused(scene) ?: error("no focus after walking back")
            assertTrue(text(node).contains("Title 1"), "expected the first card focused, got '${text(node)}'")
            val bounds = node.boundsInRoot
            // The reported defect: focus stayed on the first card but the card was
            // outside the viewport (focus "off the screen to the left").
            assertTrue(bounds.left >= 90f, "first card left edge ${bounds.left} is outside the 96dp inset")
            assertTrue(bounds.right <= 910f, "first card right edge ${bounds.right} is outside the viewport")
        } finally { scene.close() }
    }

    @Test fun g_returnDownResumesTheCardTheViewerLeft() = onEdt {
        val first = FocusRequester()
        val resume = FocusRequester()
        val hero = FocusRequester()
        var frame = 0L
        val scene = ImageComposeScene(1920, 400, Density(1f)) { ViptvTheme(true) {
            Column(Modifier.fillMaxSize().background(Palette.background)) {
                androidx.compose.foundation.layout.Box(Modifier.height(40.dp).fillMaxWidth()
                    .focusRequester(hero).focusable()) { }
                TvHomeShelf(
                    row = HomeRow(id = "continue", title = "Continue Watching", provider_id = "simkl",
                        state = RowState.READY, cards = cards(12)),
                    scale = 1f, first = first, up = { hero.requestFocus() }, focus = {}, open = {},
                    resume = resume,
                )
            }
        } }
        fun render(n: Int = 4) = repeat(n) { scene.render(frame++ * 16_666_667L).close() }
        try {
            render(8); delay(30)
            first.requestFocus(); render(4)
            // The viewer focuses the third card, leaves to the billboard, comes back.
            assertTrue(focusCard(scene, "Title 3"), "Title 3 must be focusable")
            render(6); delay(10)
            hero.requestFocus(); render(4)
            resume.requestFocus(); render(8); delay(20); render(8)
            val back = focused(scene) ?: error("no focus after returning")
            assertTrue(text(back).contains("Title 3"),
                "returning from the billboard must resume the card the viewer left, got '${text(back)}'")
        } finally { scene.close() }
    }
}