package org.viptv.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import org.junit.Rule
import org.viptv.app.components.rememberListEntrance
import org.viptv.app.features.home.HomeCatalogRow
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.*
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class HomeRowPagingUiTest {
    @get:Rule val uiThread = UiThreadRule()
    @get:Rule val deterministicImages = DeterministicImageLoaderRule()

    private fun card(n: Int) = Card(identity = MediaIdentity("tt$n", "movie", emptyList()),
        provider_id = "stremio", title = "Movie Title $n", badges = emptyList())

    private fun row() = HomeRow(id = "new", title = "New this week", provider_id = "stremio",
        state = RowState.READY, cards = listOf(card(1)), next_cursor = "1")

    @Test fun errorTileDoesNotTruncateAtPhoneWidth() {
        val scene = ImageComposeScene(390, 844, Density(1f)) {
            ViptvTheme(false) { Surface(Modifier.fillMaxSize(), color = Palette.background) {
                HomeCatalogRow(row(), compact = true, television = false, Modifier,
                    focus = {}, open = {}, entrance = rememberListEntrance(true),
                    more = {}, appending = false, appendError = "Could not load more titles. Try again.")
            } }
        }
        try {
            frames(scene)
            assertTextFits(scene, "Couldn’t load more")
            assertTextFits(scene, "Retry")
        } finally { scene.close() }
    }

    private fun assertTextFits(scene: ImageComposeScene, expected: String) {
        val node = unmerged(scene).single { text(it) == expected }
        assertTrue(node.boundsInRoot.left >= -1f && node.boundsInRoot.right <= 390 + 1f,
            "'$expected' runs off the viewport (${node.boundsInRoot})")
        val results = mutableListOf<TextLayoutResult>()
        assertTrue(node.config[SemanticsActions.GetTextLayoutResult].action!!.invoke(results))
        val layout = results.single()
        for (line in 0 until layout.lineCount) {
            assertTrue(layout.getLineRight(line) <= layout.size.width + 0.5f,
                "'$expected' line $line is cut horizontally (${layout.getLineRight(line)}px in a ${layout.size.width}px box)")
        }
        assertTrue(layout.size.height + 0.5f >= layout.getLineBottom(layout.lineCount - 1),
            "'$expected' is cut vertically")
    }

    private fun frames(scene: ImageComposeScene) = repeat(20) { scene.render((it + 1) * 16_666_667L).close() }
    private fun unmerged(scene: ImageComposeScene) = scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(mergingEnabled = false) }
    private fun text(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString(" ") { it.text }
}