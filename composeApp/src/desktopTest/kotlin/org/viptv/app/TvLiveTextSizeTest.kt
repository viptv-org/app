@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.ui.InternalComposeUiApi::class)

package org.viptv.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import org.viptv.app.features.live.*
import org.viptv.app.preview.PreviewTvLive
import org.viptv.app.theme.LocalTvTextScale
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.junit.Rule
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class TvLiveTextSizeTest {
    @get:Rule val uiThread = UiThreadRule()
    @get:Rule val deterministicImages = DeterministicImageLoaderRule()

    private val longProgramme = PreviewTvLive.channels.first().let { first ->
        first.copy(card = first.card.copy(title = "Sky Sports Main Event Ultra HD"),
            programs = listOf(LiveProgram("p1", "Premier League: Matchday Live from the stadium",
                0, 120, "Full commentary and analysis of the evening's fixture, with reports from the ground and reaction.")))
    }

    private fun channels() = listOf(longProgramme) + PreviewTvLive.channels.drop(1)

    private fun screen(scale: Float) = ImageComposeScene(1920, 1080, Density(1f)) {
        CompositionLocalProvider(LocalTvTextScale provides scale) {
            ViptvTheme(television = true) {
                Surface(Modifier.fillMaxSize(), color = Palette.background) {
                    LiveScreen(channels(), television = true, start = 0, now = 30, watch = {}, favoritesFilter = {}, favoriteChanged = {})
                }
            }
        }
    }

    @Test fun thePreviewKeepsItsControlsClearOfItsTextAtEveryDisplayTextSize() {
        for (scale in listOf(1f, 1.15f, 1.3f)) {
            Page(screen(scale)).use { page ->
                val action = assertNotNull(page.control("Favourite channel"), "the favourite action is drawn at ${scale}x")
                val tile = assertNotNull(page.control("OK to watch"), "the watch tile is drawn at ${scale}x")
                val summary = page.nodes().filter { it.boundsInRoot.left < 900f && it.config.getOrNull(SemanticsProperties.Text) != null }
                    .filter { !action.boundsInRoot.contains(it.boundsInRoot.center) }
                    .filter { it.boundsInRoot.top < tile.boundsInRoot.bottom }
                assertTrue(summary.isNotEmpty(), "the preview draws its summary")
                for (node in summary) {
                    assertFalse(node.boundsInRoot.overlaps(action.boundsInRoot),
                        "${scale}x: '${page.text(node)}' runs through the Favourite channel action: ${node.boundsInRoot} vs ${action.boundsInRoot}")
                }
                for (node in page.nodes().filter { it.config.getOrNull(SemanticsProperties.Text) != null }) {
                    assertFalse(node.boundsInRoot.overlaps(tile.boundsInRoot) && !tile.boundsInRoot.contains(node.boundsInRoot.center),
                        "${scale}x: '${page.text(node)}' runs under the watch tile")
                }
            }
        }
    }

    @Test fun theChipsAndGuideStayBelowThePreviewAtEveryDisplayTextSize() {
        for (scale in listOf(1f, 1.15f, 1.3f)) {
            Page(screen(scale)).use { page ->
                val action = assertNotNull(page.control("Favourite channel"))
                val chip = assertNotNull(page.control("Favourites"), "the chip row is drawn at ${scale}x")
                assertTrue(chip.boundsInRoot.top >= action.boundsInRoot.bottom,
                    "${scale}x: the chip row (${chip.boundsInRoot}) starts above the action's bottom (${action.boundsInRoot})")
                val guide = page.nodes().filter { it.boundsInRoot.top > chip.boundsInRoot.bottom && it.config.getOrNull(SemanticsActions.OnClick) != null }
                    .minByOrNull { it.boundsInRoot.top }
                assertNotNull(guide, "the guide is drawn below the chips at ${scale}x")
                assertTrue(guide.boundsInRoot.top >= chip.boundsInRoot.bottom,
                    "${scale}x: the guide (${guide.boundsInRoot}) overlaps the chip row (${chip.boundsInRoot})")
            }
        }
    }

    @Test fun theHeadlineEllipsizesInsteadOfCuttingAWord() {
        for (scale in listOf(1.15f, 1.3f)) {
            Page(screen(scale)).use { page ->
                val node = page.nodes().firstOrNull { page.text(it).startsWith("Premier League: Matchday Live") && it.boundsInRoot.top < 300f }
                assertNotNull(node, "the headline is laid out at ${scale}x")
                val headline = assertNotNull(page.layoutOf(node), "the headline reports its layout at ${scale}x")
                if (headline.multiParagraph.didExceedMaxLines)
                    assertEquals(TextOverflow.Ellipsis, headline.layoutInput.overflow, "${scale}x: the headline clips instead of ellipsizing")
                assertTrue(headline.lineCount <= 2, "${scale}x: the headline draws ${headline.lineCount} lines")
            }
        }
    }

    @Test fun thePageKeepsATopInsetUnderTheNavigation() {
        val scene = ImageComposeScene(1920, 1080, Density(1f)) {
            org.viptv.app.preview.UiPreview(television = true, screen = org.viptv.app.state.Screen.Live, tvDetailVariant = "Guide")
        }
        Page(scene).use { page ->
            val navBottom = 132f
            val first = page.nodes().filter { it.config.getOrNull(SemanticsProperties.Text) != null }
                .filter { it.boundsInRoot.top >= navBottom }
                .minByOrNull { it.boundsInRoot.top }
            assertNotNull(first, "the page draws its first line below the navigation")
            assertTrue(first.boundsInRoot.top - navBottom >= 12f,
                "the page's first line '${page.text(first)}' starts ${first.boundsInRoot.top - navBottom} px under the 132 px TV navigation")
        }
    }

    private class Page(private val scene: ImageComposeScene) : AutoCloseable {
        private var frame = 0L

        init { frames(30) }

        fun frames(count: Int) = repeat(count) { scene.render(frame++ * 16_666_667L).close() }
        fun nodes() = scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(mergingEnabled = false) }
        fun text(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString(" ") { it.text }
        // The smallest clickable node drawing the label: the control itself, not the text node inside it.
        fun control(label: String) = nodes().filter { it.config.getOrNull(SemanticsActions.OnClick) != null }
            .filter { node -> nodes().any { text(it) == label && node.boundsInRoot.contains(it.boundsInRoot.center) } }
            .minByOrNull { it.boundsInRoot.width * it.boundsInRoot.height }

        fun layoutOf(node: SemanticsNode): TextLayoutResult? {
            val action = node.config.getOrNull(SemanticsActions.GetTextLayoutResult) ?: return null
            val results = mutableListOf<TextLayoutResult>()
            action.action?.invoke(results)
            return results.singleOrNull()
        }

        override fun close() = scene.close()
    }
}

private fun Rect.contains(point: androidx.compose.ui.geometry.Offset): Boolean =
    point.x > left + 1f && point.x < right - 1f && point.y > top + 1f && point.y < bottom - 1f

private fun Rect.overlaps(other: Rect): Boolean =
    left < other.right - 1f && other.left < right - 1f && top < other.bottom - 1f && other.top < bottom - 1f