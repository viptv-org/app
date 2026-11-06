package org.viptv.app

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import org.viptv.app.features.player.PlayerScreen
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
                PlayerScreen(null, true, null, {}, {}, {}, title = title, startup = startup.value)
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
