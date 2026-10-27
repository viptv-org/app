package org.viptv.app

import org.junit.Rule
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import org.viptv.app.connection.SearchResultRow
import org.viptv.app.features.search.SearchScreen
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.MediaIdentity
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class GroupedSearchUiTest {
    @get:Rule val uiThread = UiThreadRule()
    @Test fun suppliedCarouselTitleRemainsVisibleForOneRowAndLaterProviderResultsKeepTheirOwnActions() {
        val first = Card(MediaIdentity("same-id", "movie", emptyList()), provider_id = "stremio", title = "First copy", badges = emptyList())
        val second = first.copy(title = "Second copy")
        val firstRow = SearchResultRow("addon:1", "First provider", "stremio", addon_id = 1, cards = listOf(first))
        val rows = mutableStateOf(listOf(firstRow))
        var opened: Card? = null
        val scene = ImageComposeScene(1440, 1100, Density(1f)) {
            ViptvTheme {
                Surface(Modifier.fillMaxSize(), color = Palette.background) {
                    SearchScreen("same", emptyList(), emptyList(), false, null, true, {}, { opened = it },
                        editQuery = {}, searchRows = rows.value)
                }
            }
        }
        try {
            frames(scene, 0)
            assertTrue(nodes(scene).any { text(it).contains("First provider") }, "A single supplied row must keep its carousel title")
            rows.value = listOf(firstRow,
                SearchResultRow("addon:2", "Second provider", "stremio", addon_id = 2, cards = listOf(second)),
                SearchResultRow("addon:3", "Unavailable provider", "stremio", addon_id = 3, state = "Failed", message = "private upstream sentinel"))
            frames(scene, 20)
            val nodes = nodes(scene)
            assertTrue(nodes.any { text(it).contains("First copy") })
            assertTrue(nodes.any { text(it).contains("Second copy") })
            assertTrue(nodes.any { text(it).contains("This search provider could not respond") })
            assertFalse(nodes.any { text(it).contains("private upstream sentinel") })
            val choice = nodes.single { it.config.contains(SemanticsActions.OnClick) && text(it).contains("Second copy") }
            assertTrue(choice.config[SemanticsActions.OnClick].action!!.invoke())
            assertSame(second, opened)
        } finally { scene.close() }
    }

    private fun frames(scene: ImageComposeScene, start: Int) = repeat(20) {
        scene.render((start + it) * 16_666_667L).close(); Thread.sleep(4)
    }
    private fun nodes(scene: ImageComposeScene): List<SemanticsNode> = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
    private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
    private fun text(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty()
}
