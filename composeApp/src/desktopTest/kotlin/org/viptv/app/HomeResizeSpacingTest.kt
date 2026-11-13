package org.viptv.app

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
        fun scene(width: Int, height: Int) = ImageComposeScene(width, height, Density(1f)) {
            ViptvTheme(pointer = true) { HomeScreen(PreviewData.home, false, {}, {}, browser = true, heroRotates = false) }
        }
        fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
        fun shelf(scene: ImageComposeScene) = scene.semanticsOwners.flatMap { flatten(it.unmergedRootSemanticsNode) }
            .firstOrNull { node -> node.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text == "Continue Watching" } }
        val resized = scene(1440, 832)
        val fresh = scene(420, 632)
        try {
            resized.renderUntil(what = "wide Home shelf") { shelf(resized) != null }
            fresh.renderUntil(what = "fresh narrow Home shelf") { shelf(fresh) != null }
            val expected = shelf(fresh)!!.boundsInRoot.top
            resized.constraints = Constraints.fixed(420, 632)
            resized.renderUntil(what = "narrow Home gutter") { shelf(resized)?.boundsInRoot?.left?.let { it < 20f } == true }
            assertEquals(expected, shelf(resized)!!.boundsInRoot.top, 1f,
                "Resized Home must use the same hero-to-shelf geometry as a fresh narrow viewport")
        } finally { resized.close(); fresh.close() }
    }
}
