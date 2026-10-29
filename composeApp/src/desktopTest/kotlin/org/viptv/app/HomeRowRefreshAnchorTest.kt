package org.viptv.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import org.viptv.app.features.home.HomeScreen
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.*
import kotlin.math.abs
import org.junit.Rule
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class HomeRowRefreshAnchorTest {
    @get:Rule val uiThread = UiThreadRule()

    private fun card(id: String, title: String) =
        Card(MediaIdentity(id, "movie", emptyList()), provider_id = "addon:1", title = title, badges = emptyList())

    private fun titles(prefix: String, count: Int) = (1..count).map { card("$prefix$it", "$prefix title $it") }

    private fun home(rowId: String, rowTitle: String, cards: List<Card>, revision: Long) = HomeSnapshot(
        Scope("server", "owner", "12", revision), revision,
        rows = listOf(HomeRow(id = rowId, title = rowTitle, provider_id = "addon:1", state = RowState.READY, cards = cards)),
        complete = true)

    private fun scene(width: Int, height: Int, home: MutableState<HomeSnapshot>): ImageComposeScene {
        frame = 0
        return ImageComposeScene(width, height, Density(1f)) {
            ViptvTheme(false) { Surface(Modifier.fillMaxSize(), color = Palette.background) { HomeScreen(home.value, false, {}, {}) } }
        }
    }

    @Test fun aPhoneRowAtItsStartShowsTheNewFirstCardAfterARefresh() {
        val shown = mutableStateOf(home("continue", "Continue Watching", listOf(card("old", "Watched yesterday")), 6))
        val scene = scene(390, 800, shown)
        try {
            frames(scene)
            assertNear(16f, card(nodes(scene), "Watched yesterday")!!.positionInRoot.x, "the row starts on the gutter")
            shown.value = home("continue", "Continue Watching",
                listOf(card("new", "Started on the TV"), card("old", "Watched yesterday")), 7)
            frames(scene, 40)
            assertNear(16f, card(nodes(scene), "Started on the TV")!!.positionInRoot.x, "the new first card lands on the gutter")
            assertNear(268f, card(nodes(scene), "Watched yesterday")!!.positionInRoot.x, "the card it pushed right follows it")
        } finally { scene.close() }
    }

    @Test fun aScrolledRowKeepsItsPlace() {
        val shown = mutableStateOf(home("top", "Popular", titles("Popular", 14), 6))
        val scene = scene(1440, 900, shown)
        try {
            frames(scene)
            press(scene, "Next items")
            val parked = card(nodes(scene), "Popular title 8")!!.positionInRoot.x
            shown.value = home("top", "Popular", listOf(card("fresh", "New title")) + titles("Popular", 14), 7)
            frames(scene, 40)
            assertNear(parked, card(nodes(scene), "Popular title 8")!!.positionInRoot.x, "a scrolled row keeps the card it shows at the left")
            val fresh = card(nodes(scene), "New title")
            assertTrue(fresh == null || fresh.boundsInRoot.right <= 0f, "the new card stays off-screen to the left")
        } finally { scene.close() }
    }

    @Test fun aFocusedRowKeepsItsPlace() {
        val shown = mutableStateOf(home("top", "Popular", titles("Popular", 14), 6))
        val scene = scene(1440, 900, shown)
        try {
            frames(scene)
            assertTrue(focusCard(scene, "Popular title 1"), "the row card takes focus")
            frames(scene)
            assertTrue(focusedCard(nodes(scene), "Popular title 1"), "the card holds focus")
            val parked = card(nodes(scene), "Popular title 1")!!.positionInRoot.x
            shown.value = home("top", "Popular", listOf(card("fresh", "New title")) + titles("Popular", 14), 7)
            frames(scene, 40)
            assertTrue(focusedCard(nodes(scene), "Popular title 1"), "the refreshed row keeps its focused card focused")
            assertNear(parked, card(nodes(scene), "Popular title 1")!!.positionInRoot.x, "the focused card keeps its place")
            val fresh = card(nodes(scene), "New title")
            assertTrue(fresh == null || fresh.boundsInRoot.right <= parked, "the new card peeks in from the left, not at the row's start")
        } finally { scene.close() }
    }

    private fun card(nodes: List<SemanticsNode>, title: String) = nodes.firstOrNull {
        it.config.contains(SemanticsActions.OnClick) && (title in texts(it) || title in it.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty())
    }
    private fun focusCard(scene: ImageComposeScene, title: String): Boolean {
        val node = nodes(scene).firstOrNull { it.config.contains(SemanticsActions.RequestFocus) && texts(it).any { text -> text.contains(title) } }
            ?: return false
        return node.config[SemanticsActions.RequestFocus].action?.invoke() ?: false
    }
    private fun focusedCard(nodes: List<SemanticsNode>, title: String) = nodes.any {
        it.config.getOrNull(SemanticsProperties.Focused) == true &&
            (title in texts(it) || title in it.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty())
    }
    private fun press(scene: ImageComposeScene, label: String) {
        val arrow = nodes(scene).first { it.config.contains(SemanticsActions.OnClick) && description(it).contains(label) }
        assertTrue(arrow.config[SemanticsActions.OnClick].action!!.invoke())
        frames(scene, 90)
    }
    private fun assertNear(expected: Float, actual: Float, message: String) = assertTrue(abs(expected - actual) <= 1f, "$message: expected $expected, was $actual")

    private var frame = 0L
    private fun frames(scene: ImageComposeScene, count: Int = 20) = repeat(count) { scene.render(frame++ * 16_666_667L).close() }
    private fun nodes(scene: ImageComposeScene) = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
    private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
    private fun texts(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }
    private fun description(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().joinToString(" ")
}
