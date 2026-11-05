package org.viptv.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import org.junit.Rule
import org.viptv.app.features.details.DetailsScreen
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.*
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class MyListActionBoundsTest {
    @get:Rule val uiThread = UiThreadRule()
    @get:Rule val deterministicImages = DeterministicImageLoaderRule()

    private val movie = Card(MediaIdentity("tt5607616", "movie", emptyList()), provider_id = "addon:21",
        title = "Obsession", badges = emptyList(), poster = "https://images.example/poster.jpg",
        landscape = "https://images.example/landscape.jpg", logo = "https://images.example/logo.png",
        description = "A movie.")

    private fun scene(saved: Boolean, width: Int = 390, height: Int = 844) = ImageComposeScene(width, height, Density(1f)) { ViptvTheme(pointer = true) {
        Box(Modifier.fillMaxSize().background(Palette.background)) {
            DetailsScreen(DetailSnapshot(movie, episodes = emptyList()), busy = false, error = null, back = {},
                sources = {}, browser = true, saved = saved)
        }
    } }

    private fun ImageComposeScene.nodes() = semanticsOwners.flatMap { it.getAllSemanticsNodes(true) }
    private fun SemanticsNode.text() = config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString(" ") { it.text }

    private fun frames(scene: ImageComposeScene) = repeat(14) { index ->
        scene.render((index + 1) * 16_666_667L).close()
    }

    @Test fun theMyListActionKeepsTheSameUsableHeightInBothStates() {
        val heights = mutableMapOf<String, Int>()
        for ((saved, label) in listOf(false to "Add to My List", true to "Remove from My List")) {
            val page = scene(saved)
            try {
                frames(page)
                val node = page.nodes().firstOrNull { it.text().contains(label) }
                assertNotNull(node, "$label renders")
                assertTrue(node!!.size.height > 0, "$label has a usable height, not ${node.size}")
                heights[label] = node.size.height
            } finally { page.close() }
        }
        assertEquals(heights.getValue("Add to My List"), heights.getValue("Remove from My List"),
            "the added and removed states render the same height")
        val desktop = scene(saved = true, width = 1280, height = 900)
        try {
            frames(desktop)
            val node = desktop.nodes().firstOrNull { it.text().contains("Remove from My List") }
            assertNotNull(node, "the desktop dialog keeps its My List action")
            assertTrue(node!!.size.height > 0, "the desktop action has a usable height")
        } finally { desktop.close() }
    }
}