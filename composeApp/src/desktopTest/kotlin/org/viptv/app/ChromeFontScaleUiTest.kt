package org.viptv.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import org.junit.Rule
import org.viptv.app.components.BottomNavigation
import org.viptv.app.components.PhoneHeader
import org.viptv.app.state.Screen
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class ChromeFontScaleUiTest {
    @get:Rule val uiThread = UiThreadRule()
    @get:Rule val deterministicImages = DeterministicImageLoaderRule()

    @Test fun headerTitleNeverClipsOrWrapsAtLargerFontScales() {
        for (scale in listOf(1.3f, 2.0f)) {
            val scene = scene(390, 160, scale) { PhoneHeader(Screen.Library, null, {}) }
            try { assertFit(scene, listOf("My List"), "header title ${scale}x") } finally { scene.close() }
        }
    }

    @Test fun headerBrandNeverClipsOrWrapsAtLargerFontScales() {
        for (scale in listOf(1.3f, 2.0f)) {
            val scene = scene(390, 220, scale) { PhoneHeader(Screen.Home, null, {}) }
            try { assertFit(scene, listOf("VIPTV"), "header brand ${scale}x") } finally { scene.close() }
        }
    }

    @Test fun bottomBarLabelsNeverClipOrWrapAtLargerFontScales() {
        for (scale in listOf(1.3f, 2.0f)) {
            val scene = scene(390, 160, scale) { BottomNavigation(Screen.Home, {}) }
            try { assertFit(scene, listOf("Home", "Live", "Calendar", "My List"), "bottom bar ${scale}x") } finally { scene.close() }
        }
    }

    private fun assertFit(scene: ImageComposeScene, expected: List<String>, where: String) {
        frames(scene)
        val nodes = unmerged(scene).associateBy(::text)
        expected.forEach { label ->
            val node = nodes[label] ?: fail("$where: '$label' not rendered; saw ${nodes.keys}")
            val layout = layout(node)
            assertEquals(1, layout.lineCount, "$where: '$label' wraps to ${layout.lineCount} lines")
            assertFalse(clippedVertically(layout), "$where: '$label' clips vertically")
        }
    }

    private fun clippedVertically(layout: TextLayoutResult): Boolean {
        val maxHeight = layout.layoutInput.constraints.maxHeight
        return maxHeight != Constraints.Infinity && layout.size.height > maxHeight
    }

    private fun layout(node: SemanticsNode): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        assertTrue(node.config[SemanticsActions.GetTextLayoutResult].action!!.invoke(results))
        return results.single()
    }

    private fun scene(width: Int, height: Int, scale: Float, content: @Composable () -> Unit) =
        ImageComposeScene(width, height, Density(1f, scale)) {
            ViptvTheme(false) { Surface(Modifier.fillMaxSize(), color = Palette.background) { content() } }
        }

    private fun frames(scene: ImageComposeScene) = repeat(30) { scene.render((it + 1) * 16_666_667L).close() }

    private fun unmerged(scene: ImageComposeScene) = scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(mergingEnabled = false) }

    private fun text(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty()
}