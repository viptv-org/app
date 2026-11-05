@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.ui.InternalComposeUiApi::class)

package org.viptv.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.io.File
import org.junit.Rule
import org.viptv.app.features.search.SearchScreen
import org.viptv.app.features.search.SEARCH_PLACEHOLDER
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SearchPlaceholderUiTest {
    @get:Rule val uiThread = UiThreadRule()
    @get:Rule val deterministicImages = DeterministicImageLoaderRule()
    private fun ui(block: suspend CoroutineScope.() -> Unit) = runBlocking(block = block)

    private class Fixture(val width: Int, height: Int, content: @Composable () -> Unit) : AutoCloseable {
        private var frame = 0L
        val scene = ImageComposeScene(width, height, Density(1f)) {
            ViptvTheme(false) { Surface(Modifier.fillMaxSize(), color = Palette.background) { content() } }
        }
        suspend fun settle(frames: Int = 40) { repeat(frames) { scene.render(frame++ * 16_666_667L).close(); delay(2) } }
        fun nodes(): List<SemanticsNode> = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
        fun texts(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }
        override fun close() = scene.close()
    }

    private fun search(width: Int, height: Int) = Fixture(width, height) {
        SearchScreen("", emptyList(), emptyList(), false, null, false, {}, {}, editQuery = {})
    }

    @Test fun theEmptySearchFieldShowsTheDesignPlaceholderAtEveryTouchWidth() = ui {
        for ((width, height) in listOf(390 to 844, 800 to 1000)) search(width, height).use { screen ->
            screen.settle()
            val texts = screen.nodes().flatMap(screen::texts)
            assertEquals(1, texts.count { it == SEARCH_PLACEHOLDER },
                "${width}dp: the search field's placeholder matches copy.md, found ${texts.filter { it.contains("Search") || it.contains("Movies") }}")
        }
    }

    @Test fun thePlaceholderFitsTheFieldWithoutClipping() = ui {
        search(390, 844).use { screen ->
            screen.settle()
            val placeholder = assertNotNull(screen.nodes().firstOrNull { screen.texts(it) == listOf(SEARCH_PLACEHOLDER) },
                "the placeholder is on screen")
            assertTrue(placeholder.boundsInRoot.left >= 0f && placeholder.boundsInRoot.right <= screen.width + 1f,
                "the placeholder stays inside the screen, was ${placeholder.boundsInRoot}")
        }
    }

    @Test fun thePlaceholderIsWrittenOnceForEveryShell() {
        assertEquals("Search movies and series", SEARCH_PLACEHOLDER, "copy.md's search placeholder")
        val source = File("src")
        val holders = source.walkTopDown()
            .filter { it.isFile && it.extension == "kt" && !it.path.contains("Test") }
            .filter { it.readText().contains("\"$SEARCH_PLACEHOLDER\"") }
            .map { it.relativeTo(source).invariantSeparatorsPath }.toList()
        assertEquals(listOf("commonMain/kotlin/org/viptv/app/features/search/SearchField.kt"), holders,
            "the phone, tablet, web and desktop search fields read one constant")
    }
}

private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
