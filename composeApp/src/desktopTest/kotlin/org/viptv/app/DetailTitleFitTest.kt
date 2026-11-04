package org.viptv.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import org.junit.Rule
import org.viptv.app.components.TITLE_LOGO_TAG
import org.viptv.app.features.details.DetailsScreen
import org.viptv.app.features.details.SharedTitlePage
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.*
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class DetailTitleFitTest {
    @get:Rule val uiThread = UiThreadRule()

    private val artwork = "https://images.example/backdrop.jpg"
    private val title = "The Extraordinary Chronicle of a Night City Mercenary Crew Without Any Logo Artwork to Stand In for the Name of the Year"
    private val series = Card(MediaIdentity("tt677", "series", emptyList()), provider_id = "addon:21", title = title,
        badges = emptyList(), landscape = artwork, poster = artwork,
        description = "A mercenary crew takes the long way through a city that never shortens a name.",
        metadata = CardMetadata(year = 2077, runtime_minutes = 52, genres = listOf("Animation", "Action"), rating = 8.4))
    private fun episode(number: Long) = series.copy(
        identity = MediaIdentity("tt677:1:$number", "series", emptyList(), EpisodeCoordinates("aired", 1, number)),
        parent_identity = series.identity, title = "Episode $number", description = null,
        metadata = CardMetadata(runtime_minutes = 52))
    private val detail = DetailSnapshot(series, episodes = (1L..3L).map(::episode),
        seasons = listOf(EpisodeSeason(1, 3, 0)))

    private fun scene(width: Int, height: Int, television: Boolean, content: @Composable () -> Unit) =
        ImageComposeScene(width, height, Density(1f)) { ViptvTheme(television) {
            Box(Modifier.fillMaxSize().background(Palette.background)) { content() }
        } }

    private fun ImageComposeScene.render() = repeat(14) { index -> render((index + 1) * 16_666_667L).close() }

    private fun ImageComposeScene.nodes() = semanticsOwners.flatMap { it.getAllSemanticsNodes(mergingEnabled = false) }

    private fun SemanticsNode.text() = config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString(" ") { it.text }

    private fun ImageComposeScene.titleNode() = nodes().single { node ->
        node.text() == title && node.config.contains(SemanticsActions.GetTextLayoutResult) }

    private fun SemanticsNode.layout(): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        assertTrue(config[SemanticsActions.GetTextLayoutResult].action!!.invoke(results), "the title exposes its layout")
        return results.single()
    }

    private fun ImageComposeScene.titleArea() =
        nodes().single { node -> node.config.getOrNull(SemanticsProperties.TestTag) == TITLE_LOGO_TAG }

    private fun ImageComposeScene.assertEveryGlyphIsWhole(surface: String): TextLayoutResult {
        val node = titleNode()
        val layout = node.layout()
        val area = titleArea().boundsInRoot
        val bounds = node.boundsInRoot
        assertTrue(bounds.left >= area.left - 1f && bounds.top >= area.top - 1f &&
            bounds.right <= area.right + 1f && bounds.bottom <= area.bottom + 1f,
            "$surface draws the title outside its ${area.width} x ${area.height} title area: $bounds")
        assertTrue(!layout.didOverflowHeight || layout.isLineEllipsized(layout.lineCount - 1),
            "$surface cuts the title through a glyph: ${layout.lineCount} lines at ${layout.size.height} px")
        return layout
    }

    @Test fun phoneTitleTakesEveryLineAndPushesTheActionsDown() {
        val page = scene(390, 844, television = false) {
            DetailsScreen(detail, busy = false, error = null, back = {}, sources = {})
        }
        try {
            page.render()
            val layout = page.assertEveryGlyphIsWhole("phone")
            assertTrue(layout.lineCount >= 3, "a 120-character title wraps at 390 wide: ${layout.lineCount} lines")
            assertTrue(layout.size.height >= layout.multiParagraph.height - 1f,
                "the whole title is laid out, not a clipped part: ${layout.size.height} of ${layout.multiParagraph.height}")
            val bounds = page.titleNode().boundsInRoot
            val play = page.nodes().filter { it.text() == "Play" }.minByOrNull { it.boundsInRoot.top }
            assertNotNull(play, "the primary action renders")
            assertTrue(play.boundsInRoot.top > bounds.bottom, "the metadata and Play sit below the grown title: $bounds then ${play.boundsInRoot}")
        } finally { page.close() }
    }

    @Test fun tabletTitleTakesEveryLine() {
        val page = scene(1024, 768, television = false) {
            DetailsScreen(detail, busy = false, error = null, back = {}, sources = {}, tablet = true)
        }
        try {
            page.render()
            assertTrue(page.assertEveryGlyphIsWhole("tablet").lineCount >= 3, "the tablet title wraps")
        } finally { page.close() }
    }

    @Test fun desktopAndWebTitlesTakeEveryLine() {
        for ((surface, content) in listOf<Pair<String, @Composable () -> Unit>>(
            "tablet" to { DetailsScreen(detail, busy = false, error = null, back = {}, sources = {}, tablet = true) },
            "desktop" to { DetailsScreen(detail, busy = false, error = null, back = {}, sources = {}) },
            "browser details" to { DetailsScreen(detail, busy = false, error = null, back = {}, sources = {}, browser = true) },
            "shared title page" to { SharedTitlePage(detail) {} })) {
            for (width in listOf(1280, 1440)) {
                val page = scene(width, 900, television = false, content = content)
                try {
                    page.render()
                    assertTrue(page.assertEveryGlyphIsWhole("$surface at $width").lineCount >= 2,
                        "$surface at $width wraps the title")
                } finally { page.close() }
            }
        }
    }

    @Test fun tvTitleEndsOnAWholeLineOfItsBoardRegion() {
        val page = scene(1920, 1080, television = true) {
            DetailsScreen(detail, busy = false, error = null, back = {}, sources = {}, television = true)
        }
        try {
            page.render()
            page.assertEveryGlyphIsWhole("TV")
        } finally { page.close() }
    }
}
