package org.viptv.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import org.junit.Rule
import org.viptv.app.features.player.PlaybackFailure
import org.viptv.app.features.player.TvPlaybackExtras
import org.viptv.app.features.player.TvPlayerScreen
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.*
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class TvPlayerFailureUiTest {
    @get:Rule val uiThread = UiThreadRule()
    private val title = Card(MediaIdentity("private-movie", "movie", emptyList()), provider_id = "addon:7", title = "A Movie", badges = emptyList())

    @Test fun aControllerFailureDrawsTheFailurePageWithRetry() {
        var retried = 0
        val scene = ImageComposeScene(1920, 1080, Density(1f)) { ViptvTheme(television = true) {
            Box(Modifier.fillMaxSize().background(Palette.background)) {
                TvPlayerScreen(null, false, "This source could not play. Choose another source.", {}, {}, {}, title,
                    TvPlaybackExtras(), null, null, { retried++ }, {}, failure = PlaybackFailure.Failed, failedSource = "LordStreams 4K")
            }
        } }
        try {
            frames(scene)
            val texts = nodes(scene).map(::text)
            assertTrue("This source stopped working" in texts)
            assertTrue("LordStreams 4K didn’t respond. Choose another source." in texts, "the page names the source; saw $texts")
            val retry = nodes(scene).single { text(it) == "Retry" && it.config.contains(SemanticsActions.OnClick) }
            retry.config[SemanticsActions.OnClick].action!!.invoke()
            assertEquals(1, retried)
        } finally { scene.close() }
    }

    private fun frames(scene: ImageComposeScene) = repeat(16) { scene.render(it * 16_666_667L).close(); Thread.sleep(4) }
    private fun nodes(scene: ImageComposeScene) = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
    private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
    private fun text(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty()
}
