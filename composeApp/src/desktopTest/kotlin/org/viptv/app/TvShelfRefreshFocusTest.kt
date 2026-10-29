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
import org.junit.Rule
import kotlin.math.abs
import kotlin.test.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class TvShelfRefreshFocusTest {
    @get:Rule val uiThread = UiThreadRule()
    private fun onEdt(block: suspend CoroutineScope.() -> Unit) = runBlocking(block = block)
    private fun cards(vararg ids: Int) = ids.map { index ->
        Card(MediaIdentity("tt$index", "series", emptyList()), provider_id = "simkl",
            title = "Title $index", subtitle = "S1 E$index", badges = emptyList())
    }
    private fun nodes(scene: ImageComposeScene): List<SemanticsNode> = scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(true) }
    private fun text(node: SemanticsNode): String =
        node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ").orEmpty()
            .ifBlank { node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty() }
    private fun focused(scene: ImageComposeScene) = nodes(scene).firstOrNull { it.config.getOrNull(SemanticsProperties.Focused) == true }
    private fun focusCard(scene: ImageComposeScene, label: String): Boolean {
        val node = nodes(scene).firstOrNull { text(it).contains(label) && it.config.contains(SemanticsActions.RequestFocus) } ?: return false
        return node.config[SemanticsActions.RequestFocus].action?.invoke() ?: false
    }

    private fun shelf(row: MutableState<HomeRow>, hero: FocusRequester) = ImageComposeScene(1920, 400, Density(1f)) { ViptvTheme(true) {
        Column(Modifier.fillMaxSize().background(Palette.background)) {
            Box(Modifier.height(40.dp).fillMaxWidth().focusRequester(hero).focusable()) { }
            TvHomeShelf(row = row.value, scale = 1f, first = null, up = { hero.requestFocus() }, focus = {}, open = {})
        }
    } }

    @Test fun removingTheFocusedCardMovesFocusToTheCardNowAtItsPosition() = onEdt {
        val hero = FocusRequester()
        val row = mutableStateOf(HomeRow(id = "continue", title = "Continue Watching", provider_id = "library", state = RowState.READY, cards = cards(1, 2, 3, 4, 5)))
        val scene = shelf(row, hero)
        var frame = 0L
        fun render(n: Int = 4) = repeat(n) { scene.render(frame++ * 16_666_667L).close() }
        try {
            render(8); delay(30)
            assertTrue(focusCard(scene, "Title 3")); render(6); delay(10)
            row.value = row.value.copy(cards = cards(1, 2, 4, 5))
            render(8); delay(30); render(8)
            val now = focused(scene) ?: error("focus fell out of the shelf after a refresh removed its card")
            assertTrue(text(now).contains("Title 4"), "expected the card now at the same position, got '${text(now)}'")
            row.value = row.value.copy(cards = cards(9, 1, 2, 4, 5))
            render(8); delay(30); render(8)
            assertTrue(text(requireNotNull(focused(scene))).contains("Title 4"), "a surviving focused card keeps focus when cards are added")
        } finally { scene.close() }
    }

    @Test fun aShelfAtItsStartShowsTheNewFirstCardAfterARefresh() = onEdt {
        val hero = FocusRequester()
        val row = mutableStateOf(HomeRow(id = "continue", title = "Continue Watching", provider_id = "library", state = RowState.READY, cards = cards(1, 2, 3)))
        val scene = shelf(row, hero)
        var frame = 0L
        fun render(n: Int = 4) = repeat(n) { scene.render(frame++ * 16_666_667L).close() }
        fun card(label: String) = nodes(scene).firstOrNull { text(it).startsWith(label) }
        try {
            render(8); delay(30)
            val start = card("Title 1")!!.positionInRoot.x
            row.value = row.value.copy(cards = cards(0, 1, 2, 3))
            render(8); delay(30); render(8)
            assertTrue(abs(start - card("Title 0")!!.positionInRoot.x) <= 1f, "the new first card takes the shelf's start")
            assertTrue(card("Title 1")!!.positionInRoot.x > start + 1f, "the card it pushed right follows it")
        } finally { scene.close() }
    }

    @Test fun aShelfWithoutFocusNeverTakesItWhenItsCardsChange() = onEdt {
        val hero = FocusRequester()
        val row = mutableStateOf(HomeRow(id = "continue", title = "Continue Watching", provider_id = "library", state = RowState.READY, cards = cards(1, 2, 3)))
        val scene = shelf(row, hero)
        var frame = 0L
        fun render(n: Int = 4) = repeat(n) { scene.render(frame++ * 16_666_667L).close() }
        try {
            render(8); delay(30)
            assertTrue(focusCard(scene, "Title 2")); render(6); delay(10)
            hero.requestFocus(); render(6); delay(10)
            row.value = row.value.copy(cards = cards(1, 3))
            render(8); delay(30); render(8)
            val now = focused(scene)
            assertFalse(now != null && text(now).startsWith("Title"), "the refresh moved focus from the billboard into the shelf")
        } finally { scene.close() }
    }
}
