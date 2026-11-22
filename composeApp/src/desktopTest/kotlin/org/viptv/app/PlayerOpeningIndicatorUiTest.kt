package org.viptv.app

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.viptv.app.features.player.PlayerScreen
import org.viptv.app.features.player.PlayerChromeTag
import org.viptv.app.features.player.TransportBufferingTag
import org.viptv.app.player.PlayerFact
import kotlinx.coroutines.flow.flowOf
import org.viptv.app.features.player.StartupArtTag
import org.viptv.app.features.player.StartupProgressScreen
import org.viptv.app.state.StartupProgress
import org.viptv.app.state.StartupSignals
import org.viptv.app.state.StartupStage
import org.viptv.app.state.startupProgress
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.MediaIdentity
import org.junit.Rule
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class PlayerOpeningIndicatorUiTest {
    @get:Rule val uiThread = UiThreadRule()
    @get:Rule val deterministicImages = DeterministicImageLoaderRule()
    private val title = Card(MediaIdentity("movie", "movie", emptyList()), provider_id = "addon:1", title = "Movie", badges = emptyList())
    private val opening = startupProgress(null, StartupSignals(opening = true, sourceLabel = "Torrentio 4K"))

    @Test fun theStartupSurfaceDrawsTheStageLineAndClearsWithTheState() {
        val startup = mutableStateOf<StartupProgress?>(opening)
        val scene = ImageComposeScene(844, 390, Density(1f)) {
            ViptvTheme {
                PlayerScreen(null, true, {}, {}, {}, title = title, startup = startup.value)
            }
        }
        try {
            frames(scene)
            assertTrue(texts(scene).contains("Opening Torrentio 4K…"), texts(scene).toString())
            assertTrue(texts(scene).contains("Movie"), texts(scene).toString())

            startup.value = null
            frames(scene)
            assertFalse(texts(scene).any { it.startsWith("Opening ") }, texts(scene).toString())
        } finally { scene.close() }
    }

    @Test fun theArtFillsFromItsLeftEdgeByTheProgressShare() {
        val wide = title.copy(title = "WWWWWWWWWWWW")
        for (share in listOf(0.25f, 0.5f, 0.75f)) {
            val progress = StartupProgress(StartupStage.Buffering, share, "Buffering…")
            val scene = ImageComposeScene(900, 600, Density(1f)) {
                ViptvTheme { StartupProgressScreen(progress, wide, artWidth = 600.dp, titleSize = 48.sp, reduceMotion = true) }
            }
            try {
                frames(scene)
                val art = scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(mergingEnabled = false) }
                    .first { it.config.getOrNull(SemanticsProperties.TestTag) == StartupArtTag }.boundsInRoot
                val pixels = scene.render(16 * 16_666_667L).toComposeImageBitmap().toPixelMap()
                val brightest = (art.left.toInt() until art.right.toInt()).map { x ->
                    (art.top.toInt() until art.bottom.toInt()).maxOf { y -> pixels[x, y].let { it.red + it.green + it.blue } / 3f }
                }
                val edge = (art.width * share).toInt()
                val full = brightest.max()
                assertTrue(brightest.take(edge - 2).count { it > full * 0.8f } > edge / 4,
                    "the left ${(share * 100).toInt()} % of the art is drawn at full strength")
                assertTrue(brightest.drop(edge + 2).all { it < full * 0.5f },
                    "everything right of ${(share * 100).toInt()} % stays muted")
            } finally { scene.close() }
        }
    }

    @Test fun bufferingAfterTheFirstFrameRingsThePlayButtonAndKeepsTheControls() {
        val scene = ImageComposeScene(844, 390, Density(1f)) {
            ViptvTheme {
                PlayerScreen(null, false, {}, {}, {}, title = title, phone = true,
                    externalFacts = flowOf(PlayerFact.Progress(600_000, 2_400_000), PlayerFact.Status("Buffering")))
            }
        }
        try {
            frames(scene)
            val tags = scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(mergingEnabled = false) }
                .mapNotNull { it.config.getOrNull(SemanticsProperties.TestTag) }
            assertTrue(TransportBufferingTag in tags, tags.toString())
            assertTrue(PlayerChromeTag in tags, "the controls stay up while it buffers: $tags")
            assertTrue(texts(scene).none { it == "Buffering…" }, "the opening screen never stands in for buffering: ${texts(scene)}")
        } finally { scene.close() }
    }

    @Test fun everyStageCarriesTheLineThePickerWouldShow() {
        assertEquals("Finding sources", StartupStage.Discovery.let { startupProgress(null, StartupSignals(discovering = true))!!.status })
        assertEquals("Still checking sources", startupProgress(null, StartupSignals(discovering = true, sourcesArrived = true))!!.status)
        assertEquals("Opening stream…", startupProgress(null, StartupSignals(opening = true))!!.status)
        assertEquals("Buffering…", startupProgress(null, StartupSignals(buffering = true))!!.status)
    }

    private fun frames(scene: ImageComposeScene, start: Int = 0) = repeat(16) { scene.render((start + it) * 16_666_667L).close() }
    private fun texts(scene: ImageComposeScene) = scene.semanticsOwners.flatMap { owner ->
        owner.getAllSemanticsNodes(mergingEnabled = false)
    }.flatMap { node -> node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } }
}
