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
import org.junit.Rule
import kotlin.test.*

/**
 * The source picker without its best-match footer hint on every layout, and the wider
 * web/desktop drawer (app#60): min(720 dp, 55 % of the window), never under 460 dp,
 * so a long release line wraps to fewer lines than in the old 460 dp drawer.
 */
class SourceDrawerLayoutTest {
    @get:Rule val uiThread = UiThreadRule()
    private val title = Card(MediaIdentity("tt13293588", "movie", emptyList()), provider_id = "addon:21", title = "Oak Street", badges = emptyList())
    private val release = "Oak.Street.2026.2160p.WEB-DL.DUAL.DDP5.1.Atmos.DV.HDR10.H.265.MSubs-ToonsHub 👤 412 💾 14.2 GB ⚙️ ThePirateBay [RD+] multi-audio"
    private val sources = (1..3).map { n ->
        SourceCandidate("s$n", "addon:torrentio", "Torrentio 4K RD+ $n", detail = "$release #$n", quality_height = 2160, release_type = "web_dl", delivery = "direct")
    }

    private fun render(width: Int, height: Int, television: Boolean = false, busy: Boolean = false, loadingMore: Boolean = false, tablet: Boolean = false,
        check: (List<SemanticsNode>) -> Unit) {
        val scene = ImageComposeScene(width, height, Density(1f)) {
            ViptvTheme(television) { Box(Modifier.fillMaxSize().background(Palette.background)) {
                SourcesScreen(title, sources, busy, null, {}, {}, television = television, tablet = tablet, loadingMore = loadingMore)
            } }
        }
        try {
            repeat(10) { scene.render(it * 16_666_667L).close() }
            check(scene.semanticsOwners.flatMap { owner -> owner.getAllSemanticsNodes(mergingEnabled = false) })
        } finally { scene.close() }
    }

    private fun texts(nodes: List<SemanticsNode>) = nodes.flatMap { node -> node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } }
    private fun releaseHeight(nodes: List<SemanticsNode>) = nodes.first { node ->
        node.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text == "$release #1" } }.boundsInRoot.height
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
            assertTrue(texts(nodes).any { it.startsWith("Checking add-ons · 3 sources so far") }, texts(nodes).toString())
        }
        render(1920, 1080, television = true, busy = true) { nodes ->
            assertTrue(texts(nodes).any { it == "Opening stream…" }, texts(nodes).toString())
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

    @Test fun aLongReleaseLineWrapsLessInTheWiderDrawer() {
        var wide = 0f
        var narrow = 0f
        render(1440, 900) { nodes ->
            assertTrue(drawerWidth(nodes) >= 640f, "the drawer is at least 640 dp at 1440x900, not ${drawerWidth(nodes)}")
            wide = releaseHeight(nodes)
        }
        render(800, 900) { nodes ->
            assertEquals(460f, drawerWidth(nodes))
            narrow = releaseHeight(nodes)
        }
        assertTrue(wide < narrow * 0.75f, "the release line takes fewer lines at 720 dp ($wide px) than at 460 dp ($narrow px)")
    }
}
