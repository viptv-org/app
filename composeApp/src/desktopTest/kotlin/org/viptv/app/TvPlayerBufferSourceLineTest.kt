package org.viptv.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import org.junit.Rule
import org.viptv.app.features.player.TvPlaybackExtras
import org.viptv.app.features.player.TvPlayerBuffer
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.MediaIdentity
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class TvPlayerBufferSourceLineTest {
    @get:Rule val uiThread = UiThreadRule()
    @get:Rule val deterministicImages = DeterministicImageLoaderRule()

    private val title = Card(MediaIdentity("movie", "movie", emptyList()), provider_id = "addon:1",
        title = "Movie", badges = emptyList())

    private fun texts(scene: ImageComposeScene): List<String> =
        scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(mergingEnabled = false) }
            .flatMap { node -> node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } }

    @Test fun theStartingPlaybackSourceLineNamesTheAddOnAndQualityOnce() {
        val scene = ImageComposeScene(1920, 1080, Density(1f)) {
            ViptvTheme(television = true) {
                Box(Modifier.fillMaxSize()) {
                    TvPlayerBuffer(title, TvPlaybackExtras(sourceLabel = "Torrentio", sourceQuality = "1080p"), scale = 1f)
                }
            }
        }
        try {
            repeat(60) { scene.render(it * 16_666_667L).close() }
            val labels = texts(scene)
            assertTrue(labels.any { it == "Movie · Torrentio · 1080p" }, labels.toString())
            assertFalse(labels.any { "1080p 1080p" in it || "1080p · 1080p" in it }, labels.toString())
        } finally { scene.close() }
    }
}