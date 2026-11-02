package org.viptv.app

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import org.viptv.app.features.player.PlayerScreen
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.MediaIdentity
import org.viptv.engine.wire.SourceCandidate
import org.junit.Rule
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class PlayerOpeningIndicatorUiTest {
    @get:Rule val uiThread = UiThreadRule()
    private val title = Card(MediaIdentity("movie", "movie", emptyList()), provider_id = "addon:1", title = "Movie", badges = emptyList())
    private val source = SourceCandidate("s1", "addon:torrentio", "Torrentio 4K")

    @Test fun openingSourceShowsAnIndicatorThatClearsWithTheState() {
        val opening = mutableStateOf<SourceCandidate?>(source)
        val scene = ImageComposeScene(844, 390, Density(1f)) {
            ViptvTheme {
                PlayerScreen(null, true, null, {}, {}, {}, title = title, openingSource = opening.value)
            }
        }
        try {
            frames(scene)
            assertTrue(texts(scene).contains("Opening Torrentio 4K…"), texts(scene).toString())

            opening.value = null
            frames(scene)
            assertFalse(texts(scene).any { it.contains("Opening ") }, texts(scene).toString())
        } finally { scene.close() }
    }

    private fun frames(scene: ImageComposeScene, start: Int = 0) = repeat(16) { scene.render((start + it) * 16_666_667L).close(); Thread.sleep(4) }
    private fun texts(scene: ImageComposeScene) = scene.semanticsOwners.flatMap { owner ->
        owner.getAllSemanticsNodes(mergingEnabled = false)
    }.flatMap { node -> node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } }
}