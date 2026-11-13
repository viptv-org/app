package org.viptv.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import org.junit.Rule
import org.viptv.app.features.home.HomeScreen
import org.viptv.app.preview.PreviewData
import org.viptv.app.theme.ViptvTheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalComposeUiApi::class)
class HomeResizeSpacingTest {
    @get:Rule val uiThread = UiThreadRule()
    @get:Rule val deterministicImages = DeterministicImageLoaderRule()

    @Test fun shrinkingTheViewportDoesNotKeepTheWideHeroHeight() {
        for (browser in listOf(true, false)) for (width in listOf(420, 900)) assertResizedMatchesFresh(width, browser)
    }

    private fun assertResizedMatchesFresh(width: Int, browser: Boolean) {
        fun scene(width: Int, height: Int, measured: (IntSize) -> Unit = {}) = ImageComposeScene(width, height, Density(1f)) {
            ViptvTheme(pointer = true) {
                Box(Modifier.fillMaxSize().onSizeChanged(measured)) {
                    HomeScreen(PreviewData.home, false, {}, {}, browser = browser, desktopChrome = !browser, heroRotates = false)
                }
            }
        }
        fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
        fun shelf(scene: ImageComposeScene) = scene.semanticsOwners.flatMap { flatten(it.unmergedRootSemanticsNode) }
            .firstOrNull { node -> node.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text == "Continue Watching" } }
        var actualSize = IntSize.Zero
        val resized = scene(1440, 832) { actualSize = it }
        val fresh = scene(width, 632)
        try {
            resized.renderUntil(what = "wide Home shelf") { shelf(resized) != null }
            fresh.renderUntil(what = "fresh narrow Home shelf") { shelf(fresh) != null }
            val expected = shelf(fresh)!!.boundsInRoot.top
            resized.constraints = Constraints.fixed(width, 632)
            resized.renderUntil(what = "resized Home bounds") { actualSize == IntSize(width, 632) && shelf(resized) != null }
            assertEquals(expected, shelf(resized)!!.boundsInRoot.top, 1f,
                "Resized Home must use the same hero-to-shelf geometry as a fresh $width-pixel viewport (browser=$browser)")
        } finally { resized.close(); fresh.close() }
    }
}
