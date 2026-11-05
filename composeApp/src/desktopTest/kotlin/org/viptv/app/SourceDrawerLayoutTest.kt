package org.viptv.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.viptv.app.features.sources.SourcesScreen
import org.viptv.app.features.sources.sourceDrawerWidth
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.MediaIdentity
import org.viptv.engine.wire.SourceCandidate
import org.viptv.engine.wire.SourcePresentation
import org.junit.Rule
import kotlin.test.*

class SourceDrawerLayoutTest {
    @get:Rule val uiThread = UiThreadRule()
    @get:Rule val deterministicImages = DeterministicImageLoaderRule()
    private val title = Card(MediaIdentity("tt13293588", "movie", emptyList()), provider_id = "addon:21", title = "Oak Street", badges = emptyList())
    private val release = "Oak.Street.2026.2160p.WEB-DL.DUAL.DDP5.1.Atmos.DV.HDR10.H.265.MSubs-ToonsHub 👤 412 💾 14.2 GB ⚙️ ThePirateBay [RD+] multi-audio"
    private val sources = (1..3).map { n ->
        SourceCandidate("s$n", "addon:torrentio", "Torrentio 4K RD+ $n", detail = "$release #$n", quality_height = 2160, release_type = "web_dl", delivery = "direct")
    }

    private fun render(width: Int, height: Int, television: Boolean = false, busy: Boolean = false, loadingMore: Boolean = false, tablet: Boolean = false,
        opening: Boolean = false, rows: List<SourceCandidate> = sources, check: (List<SemanticsNode>) -> Unit) {
        val scene = ImageComposeScene(width, height, Density(1f)) {
            ViptvTheme(television) { Box(Modifier.fillMaxSize().background(Palette.background)) {
                SourcesScreen(title, rows, busy, null, {}, {}, television = television, tablet = tablet, loadingMore = loadingMore, opening = opening)
            } }
        }
        try {
            // The drawer slides in over 300 ms; measure it once it has settled.
            repeat(30) { scene.render(it * 16_666_667L).close() }
            check(scene.semanticsOwners.flatMap { owner -> owner.getAllSemanticsNodes(mergingEnabled = false) })
        } finally { scene.close() }
    }

    private fun texts(nodes: List<SemanticsNode>) = nodes.flatMap { node -> node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } }
    private fun drawerWidth(nodes: List<SemanticsNode>) = nodes.first { it.config.getOrNull(SemanticsProperties.PaneTitle) == "Choose a source" }.boundsInRoot.width

    @Test fun noLayoutShowsTheBestMatchHint() {
        for ((width, height, television) in listOf(Triple(390, 844, false), Triple(1024, 768, false), Triple(1440, 900, false), Triple(1920, 1080, true))) {
            render(width, height, television) { nodes ->
                assertTrue(texts(nodes).isNotEmpty() && texts(nodes).none { it.contains("Play always starts") },
                    "no best-match hint at ${width}x$height (TV $television): ${texts(nodes).filter { it.contains("best match", ignoreCase = true) }}")
            }
        }
    }

    @Test fun tvKeepsItsLoadingAndOpeningStatus() {
        render(1920, 1080, television = true, loadingMore = true) { nodes ->
            assertTrue(texts(nodes).any { it == "Still checking sources" }, texts(nodes).toString())
        }
        render(1920, 1080, television = true, busy = true) { nodes ->
            assertTrue(texts(nodes).any { it == "Opening stream…" }, texts(nodes).toString())
        }
    }

    @Test fun aRowTappedWhileDiscoveryIsPendingShowsTheOpeningStatus() {
        for (television in listOf(false, true)) {
            render(if (television) 1920 else 1440, if (television) 1080 else 900, television = television,
                busy = true, loadingMore = true, opening = true) { nodes ->
                assertTrue(texts(nodes).any { it.contains("Opening stream…") }, "TV $television: ${texts(nodes)}")
                assertFalse(texts(nodes).any { it == "Still checking sources" || it == "Finding sources" }, "TV $television: ${texts(nodes)}")
            }
        }
    }

    @Test fun theWebDrawerShowsItsOpeningStatus() {
        render(1440, 900, busy = true) { nodes ->
            assertTrue(texts(nodes).any { it.contains("Opening stream…") }, texts(nodes).toString())
        }
    }

    @Test fun theDrawerWidthFollowsTheWindow() {
        assertEquals(720.dp, sourceDrawerWidth(1440.dp))
        assertEquals(720.dp, sourceDrawerWidth(2560.dp))
        assertEquals(1100.dp * 0.55f, sourceDrawerWidth(1100.dp))
        assertEquals(460.dp, sourceDrawerWidth(800.dp), "never narrower than the old drawer")
        assertEquals(400.dp, sourceDrawerWidth(400.dp), "a window narrower than that gets a full-width drawer")
    }

    @Test fun theTabletKeepsTheDesignsDrawer() {
        render(1280, 800, tablet = true) { nodes ->
            assertEquals(461f, drawerWidth(nodes))
            assertTrue(texts(nodes).none { it.contains("Play always starts") })
        }
    }

    @Test fun theFactsLineStaysOnOneLineRatherThanWrapping() {
        val long = SourceCandidate(
            "long", "addon:torrentio", "[TB+] Torrentio\n2160p",
            detail = "Oak.Street.2026.2160p.WEB-DL.DV.HDR10.HEVC.10bit (Batch)\n👤 412 💾 14.2 GB ⚙️ ThePirateBay",
            quality_height = 2160, release_type = "web_dl", seeders = 412, delivery = "direct",
            presentation = SourcePresentation(provider_name = "A.Very.Long.Provider.Name", size_label = "14.2 GB", video_codec = "HEVC"),
        )
        for (width in listOf(800, 1440)) {
            render(width, 900, rows = listOf(long)) { nodes ->
                val facts = nodes.first { node ->
                    node.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text.contains("14.2 GB") && it.text.contains("ThePirateBay") }
                }
                val result = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
                assertTrue(facts.config[SemanticsActions.GetTextLayoutResult].action!!.invoke(result))
                assertEquals(1, result.single().lineCount, "the facts line stays on one line at $width")
            }
        }
    }
}
