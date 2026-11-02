package org.viptv.app

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Rule
import org.viptv.app.features.player.PlayerScreen
import org.viptv.app.features.player.TvPlayerScene
import org.viptv.app.features.player.TvPlayerState
import org.viptv.app.player.PlaybackControl
import org.viptv.app.player.PlaybackInput
import org.viptv.app.player.PlayerCommand
import org.viptv.app.player.PlayerFact
import org.viptv.app.player.controlFailed
import org.viptv.app.player.playbackControlErrorCopy
import org.viptv.app.player.playbackControlErrorVisibleMillis
import org.viptv.app.theme.ViptvTheme
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class PlayerControlFailureTest {
    @get:Rule val uiThread = UiThreadRule()

    @Test fun automaticWorkReportsItsFailureOnlyInDiagnostics() {
        val lines = mutableListOf<String>()
        val input = PlaybackInput("opaque", "opaque", diagnostics = { lines += it })
        val observed = mutableListOf<PlayerFact>()
        input.controlFailed(PlaybackControl.Track, "sub-add-failed", null)
        assertTrue(observed.isEmpty(), "an automatic subtitle attach is not the viewer's command")
        assertEquals(listOf("control_failed control=Track cause=sub-add-failed user=false"), lines)
    }

    @Test fun aViewerCommandFailureCarriesItsControl() {
        val lines = mutableListOf<String>()
        val input = PlaybackInput("opaque", "opaque", diagnostics = { lines += it })
        val observed = mutableListOf<PlayerFact>()
        input.controlFailed(PlaybackControl.Seek, "IllegalStateException", observed::add)
        assertEquals(listOf<PlayerFact>(PlayerFact.ControlFailed(PlaybackControl.Seek)), observed)
        assertEquals(listOf("control_failed control=Seek cause=IllegalStateException user=true"), lines)
    }

    @Test fun theTvPlayerShowsTheNoticeUntilTheNextControl() {
        val state = TvPlayerState(null, emptyList<PlayerFact>(), TvPlayerScene.Controls)
        state.observe(PlayerFact.ControlFailed(PlaybackControl.Seek))
        assertEquals(playbackControlErrorCopy, state.controlError)
        state.send(PlayerCommand.Volume(0.4f), null)
        assertNull(state.controlError)
    }

    @Test fun theNoticeAppearsCentrallyAndDoesNotOutliveItsWindow() {
        val facts = MutableSharedFlow<PlayerFact>(extraBufferCapacity = 4)
        val scene = ImageComposeScene(1280, 720, Density(1f), Dispatchers.Unconfined) { ViptvTheme {
            PlayerScreen(null, true, null, {}, {}, {}, preview = PlayerFact.Progress(842_000, 3_132_000), externalFacts = facts)
        } }
        var frame = 0L
        fun render(times: Int = 1) = repeat(times) { scene.render(frame++ * 16_666_667L).close(); Thread.sleep(4) }
        try {
            render()
            assertFalse(showsNotice(scene), "a healthy player shows no control error")
            assertTrue(facts.tryEmit(PlayerFact.ControlFailed(PlaybackControl.Seek)))
            render(6)
            assertTrue(showsNotice(scene), "a failed viewer command is visible in the middle of the picture")
            val deadline = System.nanoTime() + (playbackControlErrorVisibleMillis + 10_000) * 1_000_000
            while (showsNotice(scene) && System.nanoTime() < deadline) { render(); Thread.sleep(50) }
            assertFalse(showsNotice(scene), "the notice clears instead of staying over working playback")
        } finally { scene.close() }
    }

    private fun showsNotice(scene: ImageComposeScene) = nodes(scene).any { text(it) == playbackControlErrorCopy }
    private fun nodes(scene: ImageComposeScene) = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
    private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
    private fun text(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty()
}
